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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.mifosx.openbanking.core.data.TestSigningKey
import org.mifosx.openbanking.core.data.banking.impl.ScheduledPaymentInitiationRepositoryImpl
import org.mifosx.openbanking.core.data.callback.PaymentAuthSession
import org.mifosx.openbanking.core.data.callback.SettingsPaymentAuthSession
import org.mifosx.openbanking.core.model.banking.BankAccount
import org.mifosx.openbanking.core.model.banking.BeneficiaryScheme
import org.mifosx.openbanking.core.model.banking.payment.ChargeBearer
import org.mifosx.openbanking.core.model.banking.payment.ConsentType
import org.mifosx.openbanking.core.model.banking.payment.CreditorSelection
import org.mifosx.openbanking.core.model.banking.payment.PaymentDraft
import org.mifosx.openbanking.core.model.banking.payment.PaymentHistoryItem
import org.mifosx.openbanking.core.model.banking.payment.PaymentReceipt
import org.mifosx.openbanking.core.model.banking.payment.PaymentStageTimestamps
import org.mifosx.openbanking.core.model.banking.payment.ScheduledPaymentDraft
import org.mifosx.openbanking.core.model.banking.payment.StandingOrderDraft
import org.mifosx.openbanking.core.model.hsbcProduct.AccountEndpoint
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
private const val CONSENT_ID = "45149"
private const val PAYMENT_ID = "19919"

private const val TOKEN_JSON =
    """{"access_token":"$CLIENT_CREDENTIALS_TOKEN","expires_in":300,"scope":"payments","token_type":"Bearer"}"""
private const val CONSENT_JSON =
    """{"Data":{"ConsentId":"$CONSENT_ID","Status":"AWAU"}}"""
private const val PAYMENT_JSON =
    """{"Data":{"DomesticScheduledPaymentId":"$PAYMENT_ID","ConsentId":"$CONSENT_ID","Status":"INCO"}}"""
private const val INTL_PAYMENT_JSON =
    """{"Data":{"InternationalScheduledPaymentId":"19921","ConsentId":"$CONSENT_ID","Status":"INCO"}}"""

/** The card refusal on this rail: a different code from the immediate one, the same path. */
private const val CARD_REFUSAL_BODY =
    """{"Code":"400","Id":"ref-1","Message":"Bad Request","Errors":[{"ErrorCode":"U027",""" +
        """"Message":"Unsupported scheme","Path":"Data.Initiation.DebtorAccount.SchemeName"}]}"""

/** A refusal about the payee, which must not be read as a statement about the payer. */
private const val CREDITOR_REFUSAL_BODY =
    """{"Code":"400","Id":"ref-2","Message":"Bad Request","Errors":[{"ErrorCode":"U027",""" +
        """"Message":"Unsupported scheme","Path":"Data.Initiation.CreditorAccount.SchemeName"}]}"""

/**
 * The scheduled write path at the wire, with one recurring question: **did it reach the scheduled
 * endpoint rather than the immediate one?**
 *
 * Every routing case therefore asserts twice — that the scheduled path was called, and that the
 * immediate path was not. A single positive assertion would pass against a repository that called
 * both, or called the wrong one and happened to get a parseable answer.
 */
class ScheduledPaymentInitiationRepositoryImplTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private data class Recorded(
        val path: String,
        val authorization: String?,
        val idempotencyKey: String?,
        val body: String,
    )

    private val captured = mutableListOf<Recorded>()

    private val registry = FakeAccountCapabilityRegistry()

    private fun draft(
        debtor: BankAccount? = BankAccount(
            accountId = "acc-1",
            nickname = "",
            accountSubType = "CurrentAccount",
            currency = "GBP",
            sortCode = "802001",
            accountNumber = "10203349",
            rawIdentification = "80200110203349",
        ),
    ) = ScheduledPaymentDraft(
        debtorAccount = debtor,
        creditor = CreditorSelection(
            name = "Mr Dharani C",
            scheme = BeneficiaryScheme.SortCode,
            identification = "80200110203350",
        ),
        amountMinorUnits = 25_000L,
        currency = "GBP",
        reference = "RENT-AUG",
        instructionIdentification = "MFX20260811T1000000001",
        endToEndIdentification = "E2E-SCHED-202608",
        consentIdempotencyKey = "consent-key-1",
        paymentIdempotencyKey = "payment-key-1",
        requestedExecutionDate = "2026-08-14",
    )

    private fun intlDraft() = draft().copy(
        currencyOfTransfer = "EUR",
        chargeBearer = ChargeBearer.BorneByCreditor,
        creditor = CreditorSelection(
            name = "Klara Weiss",
            scheme = BeneficiaryScheme.Iban,
            identification = "DE89370400440532013000",
        ),
    )

    private class FakePaymentHistoryRepo : PaymentHistoryRepository {
        val submitted = mutableListOf<Pair<PaymentReceipt, ScheduledPaymentDraft>>()
        override fun observeRecent(): Flow<List<PaymentHistoryItem>> = MutableStateFlow(emptyList())
        override suspend fun saveSubmitted(receipt: PaymentReceipt, draft: PaymentDraft) {}
        override suspend fun saveSubmitted(receipt: PaymentReceipt, draft: ScheduledPaymentDraft) {
            submitted += receipt to draft
        }
        override suspend fun saveFailed(draft: PaymentDraft, errorKind: String, errorDescription: String) {}
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
        override suspend fun refreshStatuses() {}
        override suspend fun consentTypeOf(paymentId: String): ConsentType? = null
        override suspend fun stageTimestampsOf(paymentId: String): PaymentStageTimestamps? = null
    }

    private val history = FakePaymentHistoryRepo()

    private suspend fun repository(
        session: PaymentAuthSession = SettingsPaymentAuthSession(MapSettings()),
        errorBody: String? = null,
        status: HttpStatusCode = HttpStatusCode.Created,
    ): ScheduledPaymentInitiationRepositoryImpl {
        val client = HttpClient(
            MockEngine { request: HttpRequestData ->
                captured += Recorded(
                    path = request.url.encodedPath,
                    authorization = request.headers[HttpHeaders.Authorization],
                    idempotencyKey = request.headers["x-idempotency-key"],
                    body = request.body.toByteArray().decodeToString(),
                )
                val isToken = request.url.encodedPath.contains("oauth2/token")
                val body = when {
                    isToken -> TOKEN_JSON
                    errorBody != null -> errorBody
                    request.url.encodedPath.contains("scheduled-payment-consents") -> CONSENT_JSON
                    request.url.encodedPath.contains("international-scheduled-payments") -> INTL_PAYMENT_JSON
                    else -> PAYMENT_JSON
                }
                respond(body, if (isToken) HttpStatusCode.OK else status, jsonHeaders)
            },
        ) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val signingKey = TestSigningKey.pem()
        return ScheduledPaymentInitiationRepositoryImpl(
            pisp = Pisp(
                httpClient = client,
                kid = "test-kid",
                signingKeyPem = signingKey,
                financialId = "",
                signingIssuer = "mifos_init_00000/0000000000000000000000",
            ),
            oauth = OAuth(client, "https://sandbox.test/oauth2/token", "test-client", "test-kid", signingKey),
            paymentAuthSession = session,
            capabilityRegistry = registry,
            signingKeyPem = signingKey,
            clientId = "test-client",
            kid = "test-kid",
            bankHost = "sandbox.test",
            authorizeHost = "authorize.sandbox.test",
            redirectUri = "https://cb/",
            paymentHistoryRepository = history,
        )
    }

    private fun sessionHoldingAPsuToken(
        type: ConsentType = ConsentType.DomesticScheduledPayment,
    ): PaymentAuthSession =
        SettingsPaymentAuthSession(MapSettings()).apply {
            savePending(consentId = CONSENT_ID, state = "s", nonce = "n", type = type)
            savePaymentToken(PsuTokenResponse(accesstoken = PSU_TOKEN, tokentype = "Bearer", expiresin = 300))
        }

    private fun requestTo(fragment: String): Recorded =
        assertNotNull(captured.lastOrNull { it.path.contains(fragment) }, "no request to $fragment")

    // region — endpoint routing

    /** The pair that catches a scheduled consent going to the immediate endpoint. */
    @Test
    fun stagingADomesticScheduledConsentReachesTheScheduledEndpointOnly() = runTest {
        repository().stagePayment(draft())

        assertNotNull(captured.lastOrNull { it.path.endsWith("/domestic-scheduled-payment-consents") })
        assertNull(captured.firstOrNull { it.path.endsWith("/domestic-payment-consents") })
    }

    @Test
    fun stagingAnInternationalScheduledConsentReachesTheScheduledEndpointOnly() = runTest {
        repository().stagePayment(intlDraft())

        assertNotNull(captured.lastOrNull { it.path.endsWith("/international-scheduled-payment-consents") })
        assertNull(captured.firstOrNull { it.path.endsWith("/international-payment-consents") })
        assertNull(captured.firstOrNull { it.path.endsWith("/domestic-scheduled-payment-consents") })
    }

    @Test
    fun submittingReachesTheScheduledPaymentEndpointOnly() = runTest {
        repository(sessionHoldingAPsuToken()).submitPayment(draft(), CONSENT_ID)

        assertNotNull(captured.lastOrNull { it.path.endsWith("/domestic-scheduled-payments") })
        assertNull(captured.firstOrNull { it.path.endsWith("/domestic-payments") })
    }

    @Test
    fun submittingAnInternationalScheduledPaymentUsesItsOwnEndpoint() = runTest {
        repository(sessionHoldingAPsuToken(ConsentType.InternationalScheduledPayment))
            .submitPayment(intlDraft(), CONSENT_ID)

        assertNotNull(captured.lastOrNull { it.path.endsWith("/international-scheduled-payments") })
        assertNull(captured.firstOrNull { it.path.endsWith("/domestic-scheduled-payments") })
    }

    // endregion

    // region — credentials

    @Test
    fun stagingPresentsTheClientCredentialsToken() = runTest {
        repository().stagePayment(draft())

        assertEquals(
            "Bearer $CLIENT_CREDENTIALS_TOKEN",
            requestTo("domestic-scheduled-payment-consents").authorization,
        )
    }

    @Test
    fun submittingPresentsThePsuToken() = runTest {
        repository(sessionHoldingAPsuToken()).submitPayment(draft(), CONSENT_ID)

        assertEquals("Bearer $PSU_TOKEN", requestTo("domestic-scheduled-payments").authorization)
    }

    @Test
    fun submittingWithoutAPsuTokenFailsBeforeReachingTheBank() = runTest {
        val result = repository().submitPayment(draft(), CONSENT_ID)

        assertIs<NetworkResult.Error<*>>(result)
        assertNull(captured.firstOrNull { it.path.contains("scheduled-payments") })
    }

    // endregion

    // region — what staging records

    @Test
    fun stagingRecordsTheScheduledConsentType() = runTest {
        val session = SettingsPaymentAuthSession(MapSettings())

        repository(session).stagePayment(draft())

        assertEquals(ConsentType.DomesticScheduledPayment, session.pendingConsentType())
    }

    @Test
    fun stagingAnInternationalDraftRecordsTheInternationalScheduledType() = runTest {
        val session = SettingsPaymentAuthSession(MapSettings())

        repository(session).stagePayment(intlDraft())

        assertEquals(ConsentType.InternationalScheduledPayment, session.pendingConsentType())
    }

    /**
     * The scheduled draft is stored under its own key, and the single-payment reader sees nothing.
     *
     * That separation is what makes a lost consent type resolve to "cannot say" rather than to a
     * domestic single payment.
     */
    @Test
    fun stagingStoresTheDraftUnderTheScheduledKeyOnly() = runTest {
        val session = SettingsPaymentAuthSession(MapSettings())

        repository(session).stagePayment(draft())

        assertNotNull(session.scheduledDraft())
        assertNull(session.draft(), "a scheduled draft must not be readable as an immediate one")
    }

    @Test
    fun theStagedDraftIsTheOneReadBackForSubmission() = runTest {
        val session = SettingsPaymentAuthSession(MapSettings())
        val repo = repository(session)

        repo.stagePayment(draft())

        assertEquals("2026-08-14", repo.stagedDraft()?.requestedExecutionDate)
    }

    @Test
    fun stagingReturnsTheConsentIdAndAnAuthorizationUrl() = runTest {
        val result = repository().stagePayment(draft())

        val staged = assertIs<NetworkResult.Success<*>>(result).data
        assertEquals(CONSENT_ID, (staged as org.mifosx.openbanking.core.model.banking.payment.StagedConsent).consentId)
        assertTrue(staged.authorizationUrl.startsWith("https://authorize.sandbox.test"))
    }

    // endregion

    // region — failures

    @Test
    fun aConsentWithNoIdIsAnError() = runTest {
        val result = repository(errorBody = """{"Data":{"Status":"AWAU"}}""").stagePayment(draft())

        assertIs<NetworkResult.Error<*>>(result)
    }

    /**
     * A refused payer is remembered so the picker stops offering it.
     *
     * The match is on the OBIE **path**, which is why this works on a rail whose refusal code
     * (`U027`) is different from the immediate rail's (`U002`) and is mapped nowhere.
     */
    @Test
    fun aRefusedPayerIsRecordedAgainstTheAccount() = runTest {
        repository(errorBody = CARD_REFUSAL_BODY, status = HttpStatusCode.BadRequest).stagePayment(draft())

        assertEquals(listOf("acc-1" to AccountEndpoint.PaymentDebtor), registry.marked)
    }

    /** A refusal naming the payee says nothing about the payer, and must not disable their account. */
    @Test
    fun aRefusalAboutThePayeeDoesNotDisableThePayer() = runTest {
        repository(errorBody = CREDITOR_REFUSAL_BODY, status = HttpStatusCode.BadRequest).stagePayment(draft())

        assertTrue(registry.marked.isEmpty(), "nothing should have been marked unsupported")
    }

    /** With no debtor named, the bank was refusing its own choice — there is nothing to remember. */
    @Test
    fun aRefusalWithNoNamedDebtorRecordsNothing() = runTest {
        repository(errorBody = CARD_REFUSAL_BODY, status = HttpStatusCode.BadRequest)
            .stagePayment(draft(debtor = null))

        assertTrue(registry.marked.isEmpty(), "nothing should have been marked unsupported")
    }

    // endregion

    // region — history

    @Test
    fun aSubmittedPaymentIsRecordedInHistoryWithItsDate() = runTest {
        repository(sessionHoldingAPsuToken()).submitPayment(draft(), CONSENT_ID)

        val (receipt, saved) = history.submitted.single()
        assertEquals(PAYMENT_ID, receipt.domesticPaymentId)
        assertEquals("2026-08-14", saved.requestedExecutionDate)
    }

    /**
     * Two keys, not one.
     *
     * The consent and the payment send different bodies — only the submission carries `ConsentId` —
     * and the Read/Write profile answers a repeated key over a changed body with `400 U029`.
     *
     * Staged and submitted through separate sessions on purpose: `stagePayment` clears the session as
     * its first act, which would discard the very PSU token the submission then needs. That is
     * correct behaviour, not a test workaround — the real journey acquires that token at the bank,
     * after staging.
     */
    @Test
    fun theConsentAndPaymentPostsCarryDifferentIdempotencyKeys() = runTest {
        repository().stagePayment(draft())
        repository(sessionHoldingAPsuToken()).submitPayment(draft(), CONSENT_ID)

        assertEquals("consent-key-1", requestTo("domestic-scheduled-payment-consents").idempotencyKey)
        assertEquals("payment-key-1", requestTo("domestic-scheduled-payments").idempotencyKey)
    }

    // endregion
}
