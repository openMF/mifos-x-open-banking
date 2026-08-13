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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.mifosx.openbanking.core.data.banking.ConsentRevokeRepository
import org.mifosx.openbanking.core.data.callback.ConsentSession
import org.mifosx.openbanking.core.data.callback.PaymentAuthSession
import org.mifosx.openbanking.core.data.user.impl.AppLogoutImpl
import org.mifosx.openbanking.core.database.banking.dao.PaymentHistoryDao
import org.mifosx.openbanking.core.database.banking.entity.PaymentHistoryEntity
import org.mifosx.openbanking.core.model.banking.payment.ConsentType
import org.mifosx.openbanking.core.model.banking.payment.PaymentDraft
import org.mifosx.openbanking.core.model.banking.payment.ScheduledPaymentDraft
import org.mifosx.openbanking.core.model.banking.payment.StandingOrderDraft
import org.mifosx.openbanking.core.model.user.DarkThemeConfig
import org.mifosx.openbanking.core.model.user.LanguageConfig
import org.mifosx.openbanking.core.model.user.ThemeBrand
import org.mifosx.openbanking.core.model.user.UserData
import org.mifosx.openbanking.core.network.model.oauth.PsuTokenResponse
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
 * or not there is a consent to revoke and whether or not the revoke succeeds.
 */
@OptIn(ExperimentalTime::class)
class AppLogoutImplTest {

    private fun logout(
        revoke: RecordingConsentRevokeRepository = RecordingConsentRevokeRepository(),
        session: RecordingConsentSession = RecordingConsentSession(consentId = "cn-1"),
        userData: RecordingUserDataRepository = RecordingUserDataRepository(),
        cache: RecordingStoreCacheManager = RecordingStoreCacheManager(),
        paymentAuth: RecordingPaymentAuthSession = RecordingPaymentAuthSession(),
    ) = AppLogoutImpl(revoke, session, paymentAuth, userData, cache, paymentHistoryDao = FakePaymentHistoryDao())

    /**
     * The payment session keeps its own keys precisely so that clearing one leg cannot disturb the
     * other — which means signing out has to clear it explicitly, or a payments-scoped token outlives
     * the session that authorised it.
     */
    @Test
    fun signingOutAlsoDropsThePaymentAuthorisation() = runTest {
        val paymentAuth = RecordingPaymentAuthSession()

        logout(paymentAuth = paymentAuth).logOut()

        assertTrue(paymentAuth.cleared)
    }

    @Test
    fun theHappyPathRevokesForgetsAndClearsEverything() = runTest {
        val revoke = RecordingConsentRevokeRepository()
        val session = RecordingConsentSession(consentId = "cn-1")
        val userData = RecordingUserDataRepository()
        val cache = RecordingStoreCacheManager()

        logout(revoke, session, userData, cache).logOut()

        assertEquals("cn-1", revoke.revokedConsentId)
        assertTrue(session.forgotAll)
        assertTrue(userData.cleared)
        assertTrue(cache.clearedAll)
    }

    @Test
    fun noConsentIdSkipsTheRevokeButStillClears() = runTest {
        val revoke = RecordingConsentRevokeRepository()
        val session = RecordingConsentSession(consentId = null)
        val userData = RecordingUserDataRepository()
        val cache = RecordingStoreCacheManager()

        logout(revoke, session, userData, cache).logOut()

        assertEquals(0, revoke.callCount)
        assertTrue(session.forgotAll)
        assertTrue(userData.cleared)
        assertTrue(cache.clearedAll)
    }

    @Test
    fun aFailedRevokeDoesNotStopTheLocalTeardown() = runTest {
        val revoke = RecordingConsentRevokeRepository(
            result = NetworkResult.Error(NetworkError.Network(IllegalStateException("offline"))),
        )
        val session = RecordingConsentSession(consentId = "cn-1")
        val userData = RecordingUserDataRepository()
        val cache = RecordingStoreCacheManager()

        logout(revoke, session, userData, cache).logOut()

        assertTrue(session.forgotAll)
        assertTrue(userData.cleared)
        assertTrue(cache.clearedAll)
    }

    @Test
    fun aThrowingRevokeIsSwallowedAndTeardownStillRuns() = runTest {
        val revoke = RecordingConsentRevokeRepository(throwable = IllegalStateException("boom"))
        val session = RecordingConsentSession(consentId = "cn-1")
        val userData = RecordingUserDataRepository()
        val cache = RecordingStoreCacheManager()

        logout(revoke, session, userData, cache).logOut()

        assertTrue(session.forgotAll)
        assertTrue(userData.cleared)
        assertTrue(cache.clearedAll)
    }

    @Test
    fun theSessionIsForgottenBeforeTheUserDataIsCleared() = runTest {
        val order = mutableListOf<String>()
        val session = RecordingConsentSession(consentId = "cn-1", onForget = { order += "forget" })
        val userData = RecordingUserDataRepository(onClear = { order += "clear" })

        logout(session = session, userData = userData).logOut()

        // The root navigator reads isActive() when userData emits, so the tokens must be gone first.
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

private class RecordingConsentRevokeRepository(
    private val result: NetworkResult<Unit, NetworkError> = NetworkResult.Success(Unit),
    private val throwable: Throwable? = null,
) : ConsentRevokeRepository {
    var revokedConsentId: String? = null
        private set
    var callCount: Int = 0
        private set

    override suspend fun revokeConsent(consentId: String): NetworkResult<Unit, NetworkError> {
        callCount++
        revokedConsentId = consentId
        throwable?.let { throw it }
        return result
    }
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
    override fun tokens(): PsuTokenResponse? = null
    override fun save(tokens: PsuTokenResponse) = Unit
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

    override fun savePending(consentId: String, state: String, nonce: String, type: ConsentType) = Unit
    override fun pendingConsentId(): String? = null
    override fun matchesPendingState(state: String?): Boolean = false
    override fun pendingNonce(): String? = null
    override fun paymentToken(): PsuTokenResponse? = null
    override fun savePaymentToken(tokens: PsuTokenResponse) = Unit
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

private class FakePaymentHistoryDao : PaymentHistoryDao {
    var clearCallCount = 0

    override fun observeRecent(): Flow<List<PaymentHistoryEntity>> =
        MutableStateFlow(emptyList())

    override fun observeById(paymentId: String): Flow<PaymentHistoryEntity?> =
        MutableStateFlow(null)

    override suspend fun upsert(entity: PaymentHistoryEntity) {}

    override suspend fun clear() {
        clearCallCount++
    }
}
