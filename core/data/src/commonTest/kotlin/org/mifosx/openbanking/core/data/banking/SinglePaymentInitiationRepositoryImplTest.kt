/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.data.banking

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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.mifosx.openbanking.core.data.TestSigningKey
import org.mifosx.openbanking.core.data.banking.impl.SinglePaymentInitiationRepositoryImpl
import org.mifosx.openbanking.core.data.callback.PaymentAuthSession
import org.mifosx.openbanking.core.data.callback.SettingsPaymentAuthSession
import org.mifosx.openbanking.core.model.banking.BankAccount
import org.mifosx.openbanking.core.model.banking.BeneficiaryScheme
import org.mifosx.openbanking.core.model.banking.payment.ConsentType
import org.mifosx.openbanking.core.model.banking.payment.CreditorSelection
import org.mifosx.openbanking.core.model.banking.payment.PaymentDraft
import org.mifosx.openbanking.core.model.banking.payment.PaymentHistoryRow
import org.mifosx.openbanking.core.model.banking.payment.PaymentReceipt
import org.mifosx.openbanking.core.model.banking.payment.PaymentStageTimestamps
import org.mifosx.openbanking.core.model.banking.payment.ScheduledPaymentDraft
import org.mifosx.openbanking.core.model.banking.payment.StagedConsent
import org.mifosx.openbanking.core.model.banking.payment.StandingOrderDraft
import org.mifosx.openbanking.core.network.api.OAuth
import org.mifosx.openbanking.core.network.api.Pisp
import org.mifosx.openbanking.core.network.model.oauth.PsuTokenResponse
import template.core.base.network.NetworkResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val CLIENT_CREDENTIALS_TOKEN = "tpp-client-credentials-token"
private const val PSU_TOKEN = "psu-payments-token"
private const val CONSENT_ID = "812774903"
private const val PAYMENT_ID = "PMT-1"
private const val CONSENT_KEY = "consent-key-1"
private const val PAYMENT_KEY = "payment-key-1"

private const val TOKEN_JSON =
    """{"access_token":"$CLIENT_CREDENTIALS_TOKEN","expires_in":300,"scope":"payments","token_type":"Bearer"}"""
private const val CONSENT_JSON =
    """{"Data":{"ConsentId":"$CONSENT_ID","Status":"AwaitingAuthorisation"}}"""
private const val FUNDS_JSON = """{"Data":{"FundsAvailableResult":{"FundsAvailable":true}}}"""

private const val INTL_PAYMENT_JSON =
    """{"Data":{"InternationalPaymentId":"$PAYMENT_ID","ConsentId":"$CONSENT_ID",""" +
        """"Status":"AcceptedSettlementInProcess"}}"""
private const val PAYMENT_JSON =
    """{"Data":{"DomesticPaymentId":"$PAYMENT_ID","ConsentId":"$CONSENT_ID","Status":"AcceptedSettlementInProcess"}}"""

/**
 * Covers [SinglePaymentInitiationRepositoryImpl] at the wire, with one recurring question: **which
 * credential does each call present?**
 *
 * That question is not academic. Reading a submitted payment back on the PSU token returned `401`
 * against the live HSBC sandbox immediately after an authorisation that had actually succeeded,
 * because a payment resource is TPP-authenticated while the PSU token authorises only the payment
 * itself. These cases pin the split so it cannot invert again.
 */
class SinglePaymentInitiationRepositoryImplTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private data class Recorded(
        val path: String,
        val authorization: String?,
        val idempotencyKey: String?,
        val body: String,
    )

    private val captured = mutableListOf<Recorded>()

    private fun draft() = PaymentDraft(
        debtorAccount = BankAccount(
            accountId = "acc-1",
            nickname = "",
            accountSubType = "CurrentAccount",
            currency = "GBP",
            sortCode = "802001",
            accountNumber = "10203349",
            rawIdentification = "80200110203349",
        ),
        creditor = CreditorSelection(
            name = "Liam Walker",
            scheme = BeneficiaryScheme.SortCode,
            identification = "40120965872310",
        ),
        amountMinorUnits = 50_000L,
        currency = "GBP",
        reference = "Invoice 2026-05",
        instructionIdentification = "MFX20260805T1042330001",
        endToEndIdentification = "E2E-RENT-FLAT12-202608",
        consentIdempotencyKey = CONSENT_KEY,
        paymentIdempotencyKey = PAYMENT_KEY,
    )

    /**
     * The same draft on the other rail.
     *
     * `CurrencyOfTransfer` is the only difference that matters here: it is the discriminator the app
     * uses everywhere to tell the two rails apart, and setting it is what makes this draft
     * international.
     */
    private fun intlDraft() = draft().copy(currencyOfTransfer = "USD")

    private suspend fun repository(
        session: PaymentAuthSession = SettingsPaymentAuthSession(MapSettings()),
        errorBody: String? = null,
        status: HttpStatusCode = HttpStatusCode.Created,
        storedType: ConsentType? = null,
    ): SinglePaymentInitiationRepositoryImpl {
        val client = HttpClient(
            MockEngine { request: HttpRequestData ->
                captured += Recorded(
                    path = request.url.encodedPath,
                    authorization = request.headers[HttpHeaders.Authorization],
                    idempotencyKey = request.headers["x-idempotency-key"],
                    body = request.body.toByteArray().decodeToString(),
                )
                // The token call always succeeds; errorBody applies to the OBIE call under test, so
                // a failure case exercises the refusal path rather than dying at authentication.
                val isToken = request.url.encodedPath.contains("oauth2/token")
                val body = when {
                    isToken -> TOKEN_JSON
                    errorBody != null -> errorBody
                    request.url.encodedPath.endsWith("funds-confirmation") -> FUNDS_JSON
                    request.url.encodedPath.contains("domestic-payment-consents") -> CONSENT_JSON
                    request.url.encodedPath.contains("international-payments") -> INTL_PAYMENT_JSON
                    request.url.encodedPath.contains("domestic-payments") -> PAYMENT_JSON
                    else -> "{}"
                }
                respond(body, if (isToken) HttpStatusCode.OK else status, jsonHeaders)
            },
        ) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val signingKey = TestSigningKey.pem()
        return SinglePaymentInitiationRepositoryImpl(
            pisp = Pisp(
                httpClient = client,
                kid = "test-kid",
                signingKeyPem = signingKey,
                financialId = "",
                signingIssuer = "mifos_init_00000/0000000000000000000000",
            ),
            oauth = OAuth(client, "https://sandbox.test/oauth2/token", "test-client", "test-kid", signingKey),
            paymentAuthSession = session,
            signingKeyPem = signingKey,
            clientId = "test-client",
            kid = "test-kid",
            bankHost = "sandbox.test",
            authorizeHost = "authorize.sandbox.test",
            redirectUri = "https://cb/",
            paymentHistoryRepository = FakePaymentHistoryRepo(storedType),
        )
    }

    /**
     * [rail] drives which status endpoint the repository is expected to call. Null is the
     * no-stored-row case, which must fall back to domestic rather than failing to read at all.
     */
    private class FakePaymentHistoryRepo(
        private val type: ConsentType? = null,
    ) : PaymentHistoryRepository {
        override suspend fun saveSubmitted(receipt: PaymentReceipt, draft: PaymentDraft) {}
        override suspend fun saveFailed(draft: PaymentDraft, errorKind: String, errorDescription: String) {}
        override suspend fun saveSubmitted(receipt: PaymentReceipt, draft: ScheduledPaymentDraft) {}
        override suspend fun saveFailed(
            draft: ScheduledPaymentDraft,
            errorKind: String,
            errorDescription: String,
        ) = Unit
        override suspend fun saveSubmitted(receipt: PaymentReceipt, draft: StandingOrderDraft) {}
        override suspend fun saveFailed(
            draft: StandingOrderDraft,
            errorKind: String,
            errorDescription: String,
        ) = Unit
        override suspend fun consentTypeOf(paymentId: String): ConsentType? = type
        override suspend fun stageTimestampsOf(paymentId: String): PaymentStageTimestamps? = null
        override fun observeHistory(
            types: Set<ConsentType>,
            limit: Int,
        ): Flow<List<PaymentHistoryRow>> = flowOf(emptyList())
        override suspend fun recordStatus(paymentId: String, receipt: PaymentReceipt) = Unit
    }

    private fun sessionHoldingAPsuToken(
        type: ConsentType = ConsentType.DomesticSinglePayment,
    ): PaymentAuthSession =
        SettingsPaymentAuthSession(MapSettings()).apply {
            savePending(consentId = CONSENT_ID, state = "s", nonce = "n", type = type)
            savePaymentToken(
                PsuTokenResponse(accesstoken = PSU_TOKEN, tokentype = "Bearer", expiresin = 300),
            )
        }

    private fun requestTo(fragment: String): Recorded =
        assertNotNull(captured.lastOrNull { it.path.contains(fragment) }, "no request to $fragment")

    // region — which credential each call presents

    /**
     * Staging happens before any PSU is involved, so it can only be the TPP's own credential.
     */
    @Test
    fun stagingPresentsTheClientCredentialsToken() = runTest {
        repository().stagePayment(draft())

        assertEquals("Bearer $CLIENT_CREDENTIALS_TOKEN", requestTo("domestic-payment-consents").authorization)
    }

    /** Funds confirmation and submission are the two calls the PSU actually authorised. */
    @Test
    fun confirmingFundsPresentsThePsuToken() = runTest {
        repository(sessionHoldingAPsuToken()).confirmFunds(CONSENT_ID)

        assertEquals("Bearer $PSU_TOKEN", requestTo("funds-confirmation").authorization)
    }

    /**
     * The second instance of the hardcoded-rail bug, alongside the consent read-back.
     *
     * Both rails' paths end in `funds-confirmation`, so the assertion above passes whichever endpoint
     * is called — which is exactly why this went unnoticed. These two assert on the segment that
     * differs.
     *
     * The draft is hoisted into a local because inside `apply` on the session a bare `draft()`
     * resolves to the session's own accessor rather than this file's fixture.
     */
    @Test
    fun confirmingFundsForAnInternationalConsentUsesTheInternationalEndpoint() = runTest {
        val staged = intlDraft()
        val session = sessionHoldingAPsuToken(ConsentType.InternationalSinglePayment).apply { saveDraft(staged) }

        repository(session).confirmFunds(CONSENT_ID)

        assertNotNull(captured.lastOrNull { "international-payment-consents" in it.path })
        assertNull(
            captured.lastOrNull { "/domestic-payment-consents" in it.path },
            "an international funds check must never go to the domestic path",
        )
    }

    @Test
    fun confirmingFundsForADomesticConsentUsesTheDomesticEndpoint() = runTest {
        val staged = draft()
        val session = sessionHoldingAPsuToken().apply { saveDraft(staged) }

        repository(session).confirmFunds(CONSENT_ID)

        assertNotNull(captured.lastOrNull { "/domestic-payment-consents" in it.path })
        assertNull(
            captured.lastOrNull { "international-payment-consents" in it.path },
            "a domestic funds check must never go to the international path",
        )
    }

    /**
     * A session that records no consent type cannot pick an endpoint, so the funds check fails.
     *
     * Previously this resolved to domestic. That is the trap this change removes: the same silent
     * default would send a standing order's consent id to the single-payment funds endpoint.
     */
    @Test
    fun confirmingFundsWithNoRecordedTypeFailsRatherThanGuessing() = runTest {
        val session = SettingsPaymentAuthSession(MapSettings()).apply {
            savePaymentToken(PsuTokenResponse(accesstoken = PSU_TOKEN, tokentype = "Bearer", expiresin = 300))
        }

        val result = repository(session).confirmFunds(CONSENT_ID)

        assertIs<NetworkResult.Error<*>>(result)
        assertNull(
            captured.lastOrNull { "funds-confirmation" in it.path },
            "an unknown type must not reach any funds-confirmation endpoint",
        )
    }

    @Test
    fun submittingPresentsThePsuToken() = runTest {
        repository(sessionHoldingAPsuToken()).submitPayment(draft(), CONSENT_ID)

        assertEquals("Bearer $PSU_TOKEN", requestTo("domestic-payments").authorization)
    }

    /** No authorisation means no credential to submit under, and nothing should reach the bank. */
    @Test
    fun submittingWithoutAPsuTokenFailsWithoutCallingTheBank() = runTest {
        val result = repository().submitPayment(draft(), CONSENT_ID)

        assertIs<NetworkResult.Error<*>>(result)
        assertTrue(captured.none { it.path.contains("domestic-payments") })
    }

    // endregion

    // region — idempotency keys

    /**
     * The consent and submission bodies differ — only the latter carries `Data.ConsentId` — and the
     * Read/Write profile answers a repeated key over a changed body with `400 U029`, permitting the
     * ASPSP to treat it as fraudulent. So each POST carries its own key.
     */
    @Test
    fun theTwoWritesCarryTheirOwnIdempotencyKeys() = runTest {
        val session = SettingsPaymentAuthSession(MapSettings())
        val repository = repository(session)
        val draft = draft()

        repository.stagePayment(draft)
        session.savePaymentToken(
            PsuTokenResponse(accesstoken = PSU_TOKEN, tokentype = "Bearer", expiresin = 300),
        )
        repository.submitPayment(draft, CONSENT_ID)

        assertEquals(CONSENT_KEY, requestTo("domestic-payment-consents").idempotencyKey)
        assertEquals(PAYMENT_KEY, requestTo("domestic-payments").idempotencyKey)
    }

    /**
     * Staging wipes whatever the last attempt left behind, so an abandoned payment cannot lend its
     * PSU token to the next one. The token that submits is only ever the one this payment's own
     * authorisation issued.
     */
    @Test
    fun stagingDiscardsATokenLeftBehindByAnAbandonedAttempt() = runTest {
        val session = sessionHoldingAPsuToken()

        repository(session).stagePayment(draft())

        assertNull(session.paymentToken())
    }

    // endregion

    // region — the draft that crosses the browser hop

    /**
     * The screen that built the draft does not survive the hop to the bank, so staging is the last
     * moment the instruction can be written somewhere the returning leg will find it.
     */
    @Test
    fun stagingStoresTheDraftForTheReturningLeg() = runTest {
        val session = SettingsPaymentAuthSession(MapSettings())
        val draft = draft()

        repository(session).stagePayment(draft)

        assertEquals(draft, session.draft())
        assertEquals(draft, repository(session).stagedDraft())
    }

    @Test
    fun thereIsNoStagedDraftBeforeAPaymentIsStaged() = runTest {
        assertNull(repository().stagedDraft())
    }

    /** A staged consent hands back the id the authorisation and the submission both hang off. */
    @Test
    fun stagingReturnsTheConsentIdAndAnAuthorisationUrl() = runTest {
        val result = repository().stagePayment(draft())

        val staged = assertIs<NetworkResult.Success<StagedConsent>>(result).data
        assertEquals(CONSENT_ID, staged.consentId)
        assertTrue(staged.authorizationUrl.isNotBlank())
        assertTrue(staged.state.isNotBlank())
    }

    // endregion
}
