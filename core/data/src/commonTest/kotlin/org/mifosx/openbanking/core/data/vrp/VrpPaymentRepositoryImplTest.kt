/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.data.vrp

import com.russhwolf.settings.MapSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.mifosx.openbanking.core.data.TestSigningKey
import org.mifosx.openbanking.core.database.vrp.dao.VrpConsentDao
import org.mifosx.openbanking.core.database.vrp.dao.VrpPaymentDao
import org.mifosx.openbanking.core.database.vrp.entity.VrpConsentEntity
import org.mifosx.openbanking.core.database.vrp.entity.VrpPaymentEntity
import org.mifosx.openbanking.core.model.banking.payment.PaymentStatus
import org.mifosx.openbanking.core.model.callback.ConsentStatus
import org.mifosx.openbanking.core.model.vrp.AccountIdentity
import org.mifosx.openbanking.core.model.vrp.FundsAvailability
import org.mifosx.openbanking.core.model.vrp.Money
import org.mifosx.openbanking.core.model.vrp.PeriodType
import org.mifosx.openbanking.core.model.vrp.PeriodicLimit
import org.mifosx.openbanking.core.model.vrp.VrpConsent
import org.mifosx.openbanking.core.model.vrp.VrpControlParameters
import org.mifosx.openbanking.core.network.api.OAuth
import org.mifosx.openbanking.core.network.api.Vrp
import template.core.base.network.NetworkResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val TOKEN_URL = "https://secure.example.test/oauth2/token"
private const val CONSENT_ID = "45411"
private const val PAYMENT_ID = "19975"

/** The credential the client-credentials grant returns. Reads a payment resource. */
private const val TPP_TOKEN = "tpp-client-credentials-token"

/** The credential the refresh-token grant returns. Pays, and confirms funds. */
private const val PSU_TOKEN = "psu-access-token"

private const val FUNDS_JSON =
    """{"Data":{"ConsentId":"$CONSENT_ID","CreationDateTime":"2026-08-15T18:19:00+00:00",""" +
        """"FundsAvailableResult":{"FundsAvailableDateTime":"2026-08-15T18:19:00+00:00",""" +
        """"FundsAvailable":"Available"},"InstructedAmount":{"Amount":"5.00","Currency":"GBP"}}}"""

/**
 * Covers [VrpPaymentRepositoryImpl] at the wire, with one recurring question: **which credential
 * does each call present?**
 *
 * The two are not interchangeable. Funds confirmation refuses the TPP token with a `401` carrying
 * no body, and a payment resource is read on the TPP token rather than the customer's. These cases
 * pin the split so it cannot invert.
 */
class VrpPaymentRepositoryImplTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private data class Recorded(
        val path: String,
        val method: String,
        val authorization: String?,
        val idempotencyKey: String?,
    )

    private val captured = mutableListOf<Recorded>()

    private fun paymentJson(status: String) =
        """{"Data":{"DomesticVRPId":"$PAYMENT_ID","ConsentId":"$CONSENT_ID","Status":"$status",""" +
            """"CreationDateTime":"2026-08-15T20:38:00+00:00","Initiation":{"CreditorAccount":""" +
            """{"SchemeName":"UK.OBIE.SortCodeAccountNumber","Identification":"80200110203350",""" +
            """"Name":"Mr Dharani C"}},"Instruction":{"InstructionIdentification":"INSTR1",""" +
            """"EndToEndIdentification":"E2E1","InstructedAmount":{"Amount":"2.00","Currency":"GBP"},""" +
            """"CreditorAccount":{"SchemeName":"UK.OBIE.SortCodeAccountNumber",""" +
            """"Identification":"80200110203350","Name":"Mr Dharani C"}}}}"""

    private class FakeConsentDao(seed: VrpConsentEntity? = null) : VrpConsentDao {
        val rows = MutableStateFlow(listOfNotNull(seed))
        override fun observeActive(): Flow<List<VrpConsentEntity>> = rows
        override fun observeById(consentId: String): Flow<VrpConsentEntity?> =
            rows.map { list -> list.firstOrNull { it.consentId == consentId } }
        override suspend fun findActive(): List<VrpConsentEntity> =
            rows.value.filter { it.revokedAt == null }
        override suspend fun findById(consentId: String): VrpConsentEntity? =
            rows.value.firstOrNull { it.consentId == consentId }
        override suspend fun upsert(consent: VrpConsentEntity) {
            rows.value = rows.value.filterNot { it.consentId == consent.consentId } + consent
        }
        override suspend fun updateStatus(consentId: String, status: String, syncedAt: String) = Unit
        override suspend fun markRevoked(consentId: String, revokedAt: String) = Unit
        override suspend fun clear() {
            rows.value = emptyList()
        }
    }

    private class FakePaymentDao : VrpPaymentDao {
        val rows = MutableStateFlow(emptyList<VrpPaymentEntity>())
        override fun observeForConsent(consentId: String): Flow<List<VrpPaymentEntity>> =
            rows.map { list -> list.filter { it.consentId == consentId } }
        override suspend fun findForConsent(consentId: String): List<VrpPaymentEntity> =
            rows.value.filter { it.consentId == consentId }
        override suspend fun findByLocalId(localId: String): VrpPaymentEntity? =
            rows.value.firstOrNull { it.localId == localId }
        override suspend fun upsert(payment: VrpPaymentEntity) {
            rows.value = rows.value.filterNot { it.localId == payment.localId } + payment
        }
        override suspend fun clear() {
            rows.value = emptyList()
        }
    }

    private fun consent() = VrpConsent(
        consentId = CONSENT_ID,
        status = ConsentStatus.Authorised,
        createdAt = Instant.parse("2026-08-15T20:36:00Z"),
        controlParameters = VrpControlParameters(
            maximumIndividualAmount = Money(10_000L, "GBP"),
            periodicLimits = listOf(PeriodicLimit(PeriodType.Day, Money(50_000L, "GBP"))),
            interactionType = "UK.OBIE.VRPType.Sweeping",
        ),
        payee = AccountIdentity(
            schemeName = "UK.OBIE.SortCodeAccountNumber",
            identification = "80200110203350",
            name = "Mr Dharani C",
        ),
    )

    /**
     * @param resourceStatus Applied to the VRP resource calls only; the token endpoint always
     *   succeeds, so a failure case exercises the refusal path rather than dying at authentication.
     */
    private suspend fun repository(
        consentDao: FakeConsentDao = FakeConsentDao(consent().toEntity()),
        paymentDao: FakePaymentDao = FakePaymentDao(),
        paymentStatus: String = "ACSP",
        resourceStatus: HttpStatusCode = HttpStatusCode.Created,
        refusePsuOnce: Boolean = false,
    ): Triple<VrpPaymentRepositoryImpl, FakePaymentDao, MutableList<Recorded>> {
        var psuCalls = 0
        val client = HttpClient(
            MockEngine { request: HttpRequestData ->
                val path = request.url.encodedPath
                val body = request.body.toByteArray().decodeToString()
                val isToken = path.contains("oauth2/token")
                if (!isToken) {
                    captured += Recorded(
                        path = path,
                        method = request.method.value,
                        authorization = request.headers[HttpHeaders.Authorization],
                        idempotencyKey = request.headers["x-idempotency-key"],
                    )
                }

                when {
                    isToken -> {
                        val token = if (body.contains("refresh_token")) PSU_TOKEN else TPP_TOKEN
                        respond(
                            """{"access_token":"$token","token_type":"Bearer","expires_in":299,""" +
                                """"refresh_token":"stored-refresh","scope":"openid payments"}""",
                            HttpStatusCode.OK,
                            jsonHeaders,
                        )
                    }

                    refusePsuOnce && psuCalls++ == 0 ->
                        respond("", HttpStatusCode.Unauthorized, jsonHeaders)

                    path.endsWith("funds-confirmation") ->
                        respond(FUNDS_JSON, resourceStatus, jsonHeaders)

                    else -> respond(paymentJson(paymentStatus), resourceStatus, jsonHeaders)
                }
            },
        ) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val settings = MapSettings()
        val session = SettingsVrpAuthSession(settings)
        val oauth = OAuth(client, TOKEN_URL, "test-client", "test-kid", TestSigningKey.pem())
        val repository = VrpPaymentRepositoryImpl(
            vrp = Vrp(
                httpClient = client,
                kid = "test-kid",
                signingKeyPem = TestSigningKey.pem(),
                financialId = "",
                signingIssuer = "mifos_init_00000/0000000000000000000000",
            ),
            oauth = oauth,
            consentDao = consentDao,
            paymentDao = paymentDao,
            tokens = VrpTokenProviderImpl(oauth, session),
        )
        session.saveRefreshToken(CONSENT_ID, "stored-refresh")
        return Triple(repository, paymentDao, captured)
    }

    @Test
    fun confirmsFundsOnTheCustomerCredentialNotTheTppOne() = runTest {
        val (repository, _, calls) = repository()

        val result = repository.checkFunds(CONSENT_ID, Money(500L, "GBP"))

        assertIs<NetworkResult.Success<*>>(result)
        val call = assertNotNull(calls.firstOrNull { it.path.endsWith("funds-confirmation") })
        assertEquals("Bearer $PSU_TOKEN", call.authorization)
    }

    @Test
    fun readsTheFundsAnswer() = runTest {
        val (repository, _, _) = repository()

        val result = repository.checkFunds(CONSENT_ID, Money(500L, "GBP"))

        val funds = assertIs<NetworkResult.Success<FundsAvailability>>(result).data
        assertTrue(funds.available)
        assertEquals(Money(500L, "GBP"), funds.amount)
    }

    @Test
    fun paysOnTheCustomerCredential() = runTest {
        val (repository, _, calls) = repository()

        val result = repository.pay(consent(), Money(200L, "GBP"), "INSTR1", "E2E1", "pay-key-1")

        assertIs<NetworkResult.Success<*>>(result)
        val call = assertNotNull(calls.firstOrNull { it.method == "POST" && it.path.endsWith("domestic-vrps") })
        assertEquals("Bearer $PSU_TOKEN", call.authorization)
        assertEquals("pay-key-1", call.idempotencyKey)
    }

    @Test
    fun readsAPaymentBackOnTheTppCredential() = runTest {
        val paymentDao = FakePaymentDao()
        val (repository, _, calls) = repository(paymentDao = paymentDao)
        val paid = repository.pay(consent(), Money(200L, "GBP"), "INSTR1", "E2E1", "pay-key-1")
        assertIs<NetworkResult.Success<*>>(paid)
        calls.clear()

        repository.refreshStatus(paymentDao.rows.value.single().toVrpPayment())

        val call = assertNotNull(calls.firstOrNull { it.method == "GET" })
        assertEquals("Bearer $TPP_TOKEN", call.authorization)
    }

    @Test
    fun mintsAFreshCustomerTokenAndRetriesOnceWhenTheHeldOneIsRefused() = runTest {
        val (repository, _, calls) = repository(refusePsuOnce = true)

        val result = repository.pay(consent(), Money(200L, "GBP"), "INSTR1", "E2E1", "pay-key-1")

        assertIs<NetworkResult.Success<*>>(result)
        assertEquals(2, calls.count { it.method == "POST" && it.path.endsWith("domestic-vrps") })
    }

    @Test
    fun storesOnePaymentPerIdempotencyKeySoAReplayDoesNotDuplicateIt() = runTest {
        val paymentDao = FakePaymentDao()
        val (repository, _, _) = repository(paymentDao = paymentDao)

        repository.pay(consent(), Money(200L, "GBP"), "INSTR1", "E2E1", "pay-key-1")
        repository.pay(consent(), Money(200L, "GBP"), "INSTR1", "E2E1", "pay-key-1")

        assertEquals(1, paymentDao.rows.value.size)
    }

    @Test
    fun leavesSettledAtUnsetWhileThePaymentIsStillInProgress() = runTest {
        val paymentDao = FakePaymentDao()
        val (repository, _, _) = repository(paymentDao = paymentDao)

        val result = repository.pay(consent(), Money(200L, "GBP"), "INSTR1", "E2E1", "pay-key-1")

        assertIs<NetworkResult.Success<*>>(result)
        assertNull(paymentDao.rows.value.single().settledAt)
    }

    @Test
    fun readsTheLongFormSettledStatusTheBankActuallySends() = runTest {
        val paymentDao = FakePaymentDao()
        val (repository, _, _) = repository(
            paymentDao = paymentDao,
            paymentStatus = "AcceptedCreditSettlementCompleted",
        )
        repository.pay(consent(), Money(200L, "GBP"), "INSTR1", "E2E1", "pay-key-1")

        val refreshed = repository.refreshStatus(paymentDao.rows.value.single().toVrpPayment())

        assertIs<NetworkResult.Success<*>>(refreshed)
        val stored = paymentDao.rows.value.single().toVrpPayment()
        assertEquals(PaymentStatus.AcceptedCreditSettlementCompleted, stored.status)
        assertNotNull(stored.settledAt)
    }

    @Test
    fun keepsTheFirstSettlementTimeOnALaterRead() = runTest {
        val paymentDao = FakePaymentDao()
        val (repository, _, _) = repository(
            paymentDao = paymentDao,
            paymentStatus = "AcceptedCreditSettlementCompleted",
        )
        repository.pay(consent(), Money(200L, "GBP"), "INSTR1", "E2E1", "pay-key-1")
        repository.refreshStatus(paymentDao.rows.value.single().toVrpPayment())
        val first = paymentDao.rows.value.single().settledAt

        repository.refreshStatus(paymentDao.rows.value.single().toVrpPayment())

        assertEquals(first, paymentDao.rows.value.single().settledAt)
    }

    @Test
    fun returnsAnUnsubmittedAttemptUnchangedRatherThanCallingTheBank() = runTest {
        val paymentDao = FakePaymentDao()
        val (repository, _, calls) = repository(paymentDao = paymentDao)
        val never = repository.pay(consent(), Money(200L, "GBP"), "INSTR1", "E2E1", "pay-key-1")
        assertIs<NetworkResult.Success<*>>(never)
        val unsubmitted = paymentDao.rows.value.single().toVrpPayment().copy(paymentId = null)
        calls.clear()

        val result = repository.refreshStatus(unsubmitted)

        assertIs<NetworkResult.Success<*>>(result)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun reportsUsageAgainstTheConsentsCap() = runTest {
        val paymentDao = FakePaymentDao()
        val consentDao = FakeConsentDao(consent().toEntity())
        val (repository, _, _) = repository(consentDao = consentDao, paymentDao = paymentDao)
        repository.pay(consent(), Money(200L, "GBP"), "INSTR1", "E2E1", "pay-key-1")

        val periods = repository.observeUsage(CONSENT_ID).first()

        assertEquals(1, periods.size)
        assertEquals(PeriodType.Day, periods.single().limit.periodType)
    }
}
