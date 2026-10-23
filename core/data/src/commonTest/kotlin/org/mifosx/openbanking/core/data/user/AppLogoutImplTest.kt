/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.data.user

import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.mifosx.openbanking.core.data.callback.ConsentSession
import org.mifosx.openbanking.core.data.callback.PaymentAuthSession
import org.mifosx.openbanking.core.data.gatewayClient
import org.mifosx.openbanking.core.data.jsonHeaders
import org.mifosx.openbanking.core.data.user.impl.AppLogoutImpl
import org.mifosx.openbanking.core.data.vrp.VrpAuthSession
import org.mifosx.openbanking.core.data.vrp.VrpConsentRepository
import org.mifosx.openbanking.core.database.banking.dao.PaymentHistoryDao
import org.mifosx.openbanking.core.database.banking.entity.PaymentHistoryEntity
import org.mifosx.openbanking.core.database.vrp.dao.VrpConsentDao
import org.mifosx.openbanking.core.database.vrp.dao.VrpPaymentDao
import org.mifosx.openbanking.core.database.vrp.entity.VrpConsentEntity
import org.mifosx.openbanking.core.database.vrp.entity.VrpPaymentEntity
import org.mifosx.openbanking.core.model.banking.payment.ConsentType
import org.mifosx.openbanking.core.model.banking.payment.PaymentDraft
import org.mifosx.openbanking.core.model.banking.payment.ScheduledPaymentDraft
import org.mifosx.openbanking.core.model.banking.payment.StandingOrderDraft
import org.mifosx.openbanking.core.model.user.DarkThemeConfig
import org.mifosx.openbanking.core.model.user.LanguageConfig
import org.mifosx.openbanking.core.model.user.ThemeBrand
import org.mifosx.openbanking.core.model.user.UserData
import org.mifosx.openbanking.core.model.vrp.VrpConsent
import org.mifosx.openbanking.core.model.vrp.VrpConsentDraft
import org.mifosx.openbanking.core.network.api.Session
import org.mifosx.openbanking.core.store.infra.StoreCacheManager
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Pins [AppLogoutImpl]'s contract: the sequence always completes and always ends signed-out, whether
 * or not there is a consent to revoke and whether or not the Worker answers. Every bank-side step
 * runs on the still-live session, so all of them happen before `/v1/session/logout`.
 */
@OptIn(ExperimentalTime::class)
class AppLogoutImplTest {

    private fun logout(
        revoke: RecordingWorker = RecordingWorker(),
        session: RecordingConsentSession = RecordingConsentSession(consentId = "cn-1"),
        userData: RecordingUserDataRepository = RecordingUserDataRepository(),
        cache: RecordingStoreCacheManager = RecordingStoreCacheManager(),
        paymentAuth: RecordingPaymentAuthSession = RecordingPaymentAuthSession(),
        vrpConsents: VrpConsentRepository = RecordingVrpConsentRepository(),
        vrpConsentDao: FakeVrpConsentDao = FakeVrpConsentDao(),
        vrpPaymentDao: FakeVrpPaymentDao = FakeVrpPaymentDao(),
        vrpAuthSession: RecordingVrpAuthSession = RecordingVrpAuthSession(),
    ) = AppLogoutImpl(
        gatewaySession = revoke.session,
        consentSession = session,
        paymentAuthSession = paymentAuth,
        userDataRepository = userData,
        storeCacheManager = cache,
        paymentHistoryDao = FakePaymentHistoryDao(),
        vrpConsentRepository = vrpConsents,
        vrpConsentDao = vrpConsentDao,
        vrpPaymentDao = vrpPaymentDao,
        vrpAuthSession = vrpAuthSession,
    )

    private fun vrpConsentRow(consentId: String) = VrpConsentEntity(
        consentId = consentId,
        status = "Authorised",
        createdAt = "2026-08-15T20:36:00Z",
        currency = "GBP",
        maxIndividualAmountMinor = 10_000L,
        interactionType = "UK.OBIE.VRPType.Sweeping",
        payeeScheme = "UK.OBIE.SortCodeAccountNumber",
        payeeIdentification = "80200110203350",
        payeeName = "Mr Dharani C",
    )

    /**
     * The payment session keeps its own keys precisely so that clearing one leg cannot disturb the
     * other — which means signing out has to clear it explicitly, or a payments-scoped token outlives
     * the session that authorised it.
     */
    @Test
    fun signingOutRevokesEveryStandingAuthorityAtTheBank() = runTest {
        val consents = RecordingVrpConsentRepository()
        val dao = FakeVrpConsentDao(listOf(vrpConsentRow("45411"), vrpConsentRow("45412")))

        logout(vrpConsents = consents, vrpConsentDao = dao).logOut()

        assertEquals(listOf("45411", "45412"), consents.revoked)
    }

    @Test
    fun signingOutClearsTheStandingAuthorityTablesAndCredentials() = runTest {
        val consentDao = FakeVrpConsentDao(listOf(vrpConsentRow("45411")))
        val paymentDao = FakeVrpPaymentDao()
        val vrpSession = RecordingVrpAuthSession()

        logout(
            vrpConsentDao = consentDao,
            vrpPaymentDao = paymentDao,
            vrpAuthSession = vrpSession,
        ).logOut()

        assertTrue(consentDao.cleared)
        assertTrue(paymentDao.cleared)
        assertTrue(vrpSession.cleared)
    }

    @Test
    fun signingOutCompletesEvenWhenTheBankRefusesToRevokeAStandingAuthority() = runTest {
        val consents = RefusingVrpConsentRepository()
        val consentDao = FakeVrpConsentDao(listOf(vrpConsentRow("45411"), vrpConsentRow("45412")))
        val vrpSession = RecordingVrpAuthSession()

        logout(
            vrpConsents = consents,
            vrpConsentDao = consentDao,
            vrpAuthSession = vrpSession,
        ).logOut()

        assertEquals(listOf("45411", "45412"), consents.revoked)
        assertTrue(consentDao.cleared)
        assertTrue(vrpSession.cleared)
    }

    @Test
    fun signingOutAlsoDropsThePaymentAuthorisation() = runTest {
        val paymentAuth = RecordingPaymentAuthSession()

        logout(paymentAuth = paymentAuth).logOut()

        assertTrue(paymentAuth.cleared)
    }

    @Test
    fun theHappyPathRevokesForgetsAndClearsEverything() = runTest {
        val revoke = RecordingWorker()
        val session = RecordingConsentSession(consentId = "cn-1")
        val userData = RecordingUserDataRepository()
        val cache = RecordingStoreCacheManager()

        logout(revoke, session, userData, cache).logOut()

        assertEquals(listOf("/v1/session/logout?revoke=true"), revoke.calls)
        assertTrue(session.forgotAll)
        assertTrue(userData.cleared)
        assertTrue(cache.clearedAll)
    }

    @Test
    fun noConsentIdEndsTheSessionWithoutRevokingAndStillClears() = runTest {
        val revoke = RecordingWorker()
        val session = RecordingConsentSession(consentId = null)
        val userData = RecordingUserDataRepository()
        val cache = RecordingStoreCacheManager()

        logout(revoke, session, userData, cache).logOut()

        assertEquals(listOf("/v1/session/logout"), revoke.calls)
        assertTrue(session.forgotAll)
        assertTrue(userData.cleared)
        assertTrue(cache.clearedAll)
    }

    @Test
    fun aFailedLogoutDoesNotStopTheLocalTeardown() = runTest {
        val revoke = RecordingWorker(status = HttpStatusCode.BadGateway)
        val session = RecordingConsentSession(consentId = "cn-1")
        val userData = RecordingUserDataRepository()
        val cache = RecordingStoreCacheManager()

        logout(revoke, session, userData, cache).logOut()

        assertTrue(session.forgotAll)
        assertTrue(userData.cleared)
        assertTrue(cache.clearedAll)
    }

    @Test
    fun anUnreachableWorkerIsSwallowedAndTeardownStillRuns() = runTest {
        val revoke = RecordingWorker(throwable = IllegalStateException("offline"))
        val session = RecordingConsentSession(consentId = "cn-1")
        val userData = RecordingUserDataRepository()
        val cache = RecordingStoreCacheManager()

        logout(revoke, session, userData, cache).logOut()

        assertTrue(session.forgotAll)
        assertTrue(userData.cleared)
        assertTrue(cache.clearedAll)
    }

    @Test
    fun aSignedOutDeviceSendsNoLogout() = runTest {
        val revoke = RecordingWorker()

        logout(revoke = revoke, session = RecordingConsentSession(consentId = "cn-1", tokenPresent = false)).logOut()

        assertTrue(revoke.calls.isEmpty())
    }

    /** A revoke sent after the Worker session is wiped would carry no session and be refused. */
    @Test
    fun standingAuthoritiesAreRevokedBeforeTheWorkerSessionEnds() = runTest {
        val order = mutableListOf<String>()
        val revoke = RecordingWorker(order = order)
        val consents = RecordingVrpConsentRepository(order)

        logout(
            revoke = revoke,
            vrpConsents = consents,
            vrpConsentDao = FakeVrpConsentDao(listOf(vrpConsentRow("45411"))),
        ).logOut()

        assertEquals(listOf("vrp:45411", "/v1/session/logout?revoke=true"), order)
    }

    @Test
    fun theSessionIsForgottenBeforeTheUserDataIsCleared() = runTest {
        val order = mutableListOf<String>()
        val session = RecordingConsentSession(consentId = "cn-1", onForget = { order += "forget" })
        val userData = RecordingUserDataRepository(onClear = { order += "clear" })

        logout(session = session, userData = userData).logOut()

        // The root navigator reads isActive() when userData emits, so the session must be gone first.
        assertEquals(listOf("forget", "clear"), order)
    }

    @Test
    fun forgettingTheSessionIsWhatTurnsIsActiveFalse() = runTest {
        val session = RecordingConsentSession(consentId = "cn-1", tokenPresent = true)
        assertTrue(session.isActive())

        logout(session = session).logOut()

        assertFalse(session.isActive())
    }
}

// ── Recording doubles (test source sets do not cross Gradle modules) ─────────────────────────────

/** The Worker over a mock engine; records each logout call (path + query) in order. */
private class RecordingWorker(
    status: HttpStatusCode = HttpStatusCode.NoContent,
    throwable: Throwable? = null,
    val order: MutableList<String> = mutableListOf(),
) {
    val calls = mutableListOf<String>()
    val session = Session(
        gatewayClient { req ->
            val call = req.url.encodedPath + (req.url.encodedQuery.takeIf { it.isNotEmpty() }?.let { "?$it" } ?: "")
            calls += call
            order += call
            throwable?.let { throw it }
            respond("", status, jsonHeaders)
        },
    )
}

@OptIn(ExperimentalTime::class)
private class RecordingConsentSession(
    private var consentId: String?,
    private var tokenPresent: Boolean = true,
    private val onForget: () -> Unit = {},
) : ConsentSession {
    var forgotAll: Boolean = false
        private set

    override fun isActive(): Boolean = tokenPresent
    override fun save(session: String) = Unit
    override fun saveConsentMeta(consentId: String, expirationDateTime: String) = Unit
    override fun consentId(): String? = consentId
    override fun consentExpiration(): Instant? = null

    override fun clear() {
        tokenPresent = false
        consentId = null
    }

    override fun forgetAll() {
        forgotAll = true
        tokenPresent = false
        consentId = null
        onForget()
    }
}

private class RecordingUserDataRepository(
    private val onClear: () -> Unit = {},
) : UserDataRepository {
    var cleared: Boolean = false
        private set

    override val userData: StateFlow<UserData> = MutableStateFlow(UserData.DEFAULT)
    override val passcode: String = ""
    override val observeLanguage = MutableStateFlow(LanguageConfig.ENGLISH)
    override val observeDarkThemeConfig = MutableStateFlow(DarkThemeConfig.FOLLOW_SYSTEM)
    override val observeDynamicColorPreference = MutableStateFlow(false)
    override val observeScreenCapturePreference = MutableStateFlow(false)

    override suspend fun setLanguage(language: LanguageConfig) = Unit
    override suspend fun setThemeBrand(themeBrand: ThemeBrand) = Unit
    override suspend fun setDarkThemeConfig(darkThemeConfig: DarkThemeConfig) = Unit
    override suspend fun setDynamicColorPreference(useDynamicColor: Boolean) = Unit
    override suspend fun setIsAuthenticated(isAuthenticated: Boolean) = Unit
    override suspend fun setIsUnlocked(isUnlocked: Boolean) = Unit
    override suspend fun setIsPasscodeEnabled(isPasscodeEnabled: Boolean) = Unit
    override suspend fun setIsBiometricsEnabled(isBiometricsEnabled: Boolean) = Unit
    override suspend fun setSelectedAccountId(accountId: String) = Unit

    override suspend fun clearUserData() {
        cleared = true
        onClear()
    }
}

private class RecordingStoreCacheManager : StoreCacheManager {
    var clearedAll: Boolean = false
        private set

    override suspend fun clearAll() {
        clearedAll = true
    }

    override suspend fun pruneExpiredDrafts(maxAgeMs: Long) = Unit
}

private class RecordingPaymentAuthSession : PaymentAuthSession {

    var cleared: Boolean = false
        private set

    override fun savePending(consentId: String, state: String, flowId: String, type: ConsentType) = Unit
    override fun pendingConsentId(): String? = null
    override fun matchesPendingState(state: String?): Boolean = false
    override fun pendingFlowId(): String? = null
    override fun saveDraft(draft: PaymentDraft) = Unit
    override fun draft(): PaymentDraft? = null
    override fun saveScheduledDraft(draft: ScheduledPaymentDraft) = Unit
    override fun scheduledDraft(): ScheduledPaymentDraft? = null
    override fun saveStandingOrderDraft(draft: StandingOrderDraft) = Unit
    override fun standingOrderDraft(): StandingOrderDraft? = null
    override fun saveApprovedAt(instant: String) = Unit
    override fun approvedAt(): String? = null

    override fun pendingConsentType(): ConsentType? = ConsentType.DomesticSinglePayment

    override fun clear() {
        cleared = true
    }
}

private class RecordingVrpConsentRepository(
    private val order: MutableList<String> = mutableListOf(),
) : VrpConsentRepository {
    val revoked = mutableListOf<String>()

    override fun observeActive(): Flow<List<VrpConsent>> = MutableStateFlow(emptyList())
    override fun observeById(consentId: String): Flow<VrpConsent?> = MutableStateFlow(null)

    override suspend fun stageConsent(draft: VrpConsentDraft): NetworkResult<VrpConsent, NetworkError> =
        NetworkResult.Error(NetworkError.Client.BadRequest("not used"))

    override suspend fun refreshStatus(consentId: String): NetworkResult<VrpConsent, NetworkError> =
        NetworkResult.Error(NetworkError.Client.BadRequest("not used"))

    override suspend fun revoke(consentId: String): NetworkResult<Unit, NetworkError> {
        revoked += consentId
        order += "vrp:$consentId"
        return NetworkResult.Success(Unit)
    }
}

/** Refuses every revoke, to prove that signing out completes regardless. */
private class RefusingVrpConsentRepository : VrpConsentRepository {
    val revoked = mutableListOf<String>()

    override fun observeActive(): Flow<List<VrpConsent>> = MutableStateFlow(emptyList())
    override fun observeById(consentId: String): Flow<VrpConsent?> = MutableStateFlow(null)

    override suspend fun stageConsent(draft: VrpConsentDraft): NetworkResult<VrpConsent, NetworkError> =
        NetworkResult.Error(NetworkError.Client.BadRequest("not used"))

    override suspend fun refreshStatus(consentId: String): NetworkResult<VrpConsent, NetworkError> =
        NetworkResult.Error(NetworkError.Client.BadRequest("not used"))

    override suspend fun revoke(consentId: String): NetworkResult<Unit, NetworkError> {
        revoked += consentId
        error("the bank refused")
    }
}

private class FakeVrpConsentDao(private val active: List<VrpConsentEntity> = emptyList()) : VrpConsentDao {
    var cleared = false
        private set

    override fun observeActive(): Flow<List<VrpConsentEntity>> = MutableStateFlow(active)
    override fun observeById(consentId: String): Flow<VrpConsentEntity?> = MutableStateFlow(null)
    override suspend fun findActive(): List<VrpConsentEntity> = active
    override suspend fun findById(consentId: String): VrpConsentEntity? = null
    override suspend fun upsert(consent: VrpConsentEntity) = Unit
    override suspend fun updateStatus(consentId: String, status: String, syncedAt: String) = Unit
    override suspend fun markRevoked(consentId: String, revokedAt: String) = Unit

    override suspend fun clear() {
        cleared = true
    }
}

private class FakeVrpPaymentDao : VrpPaymentDao {
    var cleared = false
        private set

    override fun observeForConsent(consentId: String): Flow<List<VrpPaymentEntity>> =
        MutableStateFlow(emptyList())

    override suspend fun findForConsent(consentId: String): List<VrpPaymentEntity> = emptyList()
    override suspend fun findByLocalId(localId: String): VrpPaymentEntity? = null
    override suspend fun upsert(payment: VrpPaymentEntity) = Unit

    override suspend fun clear() {
        cleared = true
    }
}

private class RecordingVrpAuthSession : VrpAuthSession {
    var cleared = false
        private set

    override fun saveAuthorisationInFlight(
        consentId: String,
        state: String,
        flowId: String,
        authorizeUrl: String,
    ) = Unit
    override fun pendingConsentId(): String? = null
    override fun pendingFlowId(): String? = null
    override fun pendingAuthorizeUrl(): String? = null
    override fun matchesPendingState(state: String?): Boolean = false
    override fun clearAuthorisationInFlight() = Unit

    override fun clear() {
        cleared = true
    }
}

private class FakePaymentHistoryDao : PaymentHistoryDao {
    var clearCallCount = 0

    override fun observeById(paymentId: String): Flow<PaymentHistoryEntity?> =
        MutableStateFlow(null)

    override fun observeByType(
        types: List<String>,
        limit: Int,
    ): Flow<List<PaymentHistoryEntity>> = MutableStateFlow(emptyList())

    @Suppress("LongParameterList")
    override suspend fun updateFromReceipt(
        paymentId: String,
        status: String,
        settledAt: String?,
        syncedAt: String,
        debtorName: String,
        debtorIdentification: String,
        debtorScheme: String,
        creditorName: String,
        creditorIdentification: String,
        creditorScheme: String,
    ) = Unit

    override suspend fun upsert(entity: PaymentHistoryEntity) {}

    override suspend fun clear() {
        clearCallCount++
    }
}
