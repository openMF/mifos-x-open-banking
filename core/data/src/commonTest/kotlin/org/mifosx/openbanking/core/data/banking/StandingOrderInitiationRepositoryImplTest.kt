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
import org.mifosx.openbanking.core.data.banking.impl.StandingOrderInitiationRepositoryImpl
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
import org.mifosx.openbanking.core.model.banking.payment.StagedConsent
import org.mifosx.openbanking.core.model.banking.payment.StandingOrderDraft
import org.mifosx.openbanking.core.model.banking.payment.StandingOrderFrequency
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
private const val CONSENT_ID = "45171"
private const val STANDING_ORDER_ID = "19916"

private const val TOKEN_JSON =
    """{"access_token":"$CLIENT_CREDENTIALS_TOKEN","expires_in":300,"scope":"payments","token_type":"Bearer"}"""
private const val CONSENT_JSON =
    """{"Data":{"ConsentId":"$CONSENT_ID","Status":"AWAU"}}"""
private const val STANDING_ORDER_JSON =
    """{"Data":{"DomesticStandingOrderId":"$STANDING_ORDER_ID","ConsentId":"$CONSENT_ID","Status":"PDNG"}}"""
private const val INTL_STANDING_ORDER_JSON =
    """{"Data":{"InternationalStandingOrderId":"19917","ConsentId":"$CONSENT_ID","Status":"INCO"}}"""

/** The card refusal on the domestic rail: a scheme the product cannot express. */
private const val CARD_REFUSAL_BODY =
    """{"Code":"400","Id":"ref-1","Message":"Bad Request","Errors":[{"ErrorCode":"U027",""" +
        """"Message":"Unsupported scheme","Path":"Data.Initiation.DebtorAccount.SchemeName"}]}"""

/** A refusal about the payee, which must not be read as a statement about the payer. */
private const val CREDITOR_REFUSAL_BODY =
    """{"Code":"400","Id":"ref-2","Message":"Bad Request","Errors":[{"ErrorCode":"U027",""" +
        """"Message":"Unsupported scheme","Path":"Data.Initiation.CreditorAccount.SchemeName"}]}"""

/**
 * The standing-order write path at the wire, with one recurring question: **did it reach the
 * standing-order endpoint rather than one of the other four?**
 *
 * Every routing case therefore asserts twice — that the standing-order path was called, and that the
 * neighbouring ones were not. A single positive assertion would pass against a repository that called
 * both, or called the wrong one and happened to get a parseable answer. That is not hypothetical on
 * this product: a mandate submitted to `domestic-scheduled-payments` would look plausible right up
 * until the bank executed something other than what the customer approved.
 */
class StandingOrderInitiationRepositoryImplTest {

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
    ) = StandingOrderDraft(
        debtorAccount = debtor,
        creditor = CreditorSelection(
            name = "Mr Dharani C",
            scheme = BeneficiaryScheme.SortCode,
            identification = "80200110203350",
        ),
        frequency = StandingOrderFrequency.Monthly,
        firstPaymentDate = "2026-08-20",
        finalPaymentDate = "2026-12-11",
        firstPaymentAmountMinorUnits = 25_000L,
        currency = "GBP",
        reference = "FLAT 4B RENT",
        consentIdempotencyKey = "so-consent-key-1",
        paymentIdempotencyKey = "so-payment-key-1",
    )

    private fun intlDraft() = draft().copy(
        currencyOfTransfer = "USD",
        chargeBearer = ChargeBearer.BorneByCreditor,
        creditor = CreditorSelection(
            name = "Klara Weiss",
            scheme = BeneficiaryScheme.Iban,
            identification = "DE89370400440532013000",
        ),
    )

    private class FakePaymentHistoryRepo : PaymentHistoryRepository {
        val submitted = mutableListOf<Pair<PaymentReceipt, StandingOrderDraft>>()
        override fun observeRecent(): Flow<List<PaymentHistoryItem>> = MutableStateFlow(emptyList())
        override suspend fun saveSubmitted(receipt: PaymentReceipt, draft: PaymentDraft) {}
        override suspend fun saveSubmitted(receipt: PaymentReceipt, draft: ScheduledPaymentDraft) {}
        override suspend fun saveSubmitted(receipt: PaymentReceipt, draft: StandingOrderDraft) {
            submitted += receipt to draft
        }
        override suspend fun saveFailed(draft: PaymentDraft, errorKind: String, errorDescription: String) {}
        override suspend fun saveFailed(
            draft: ScheduledPaymentDraft,
            errorKind: String,
            errorDescription: String,
        ) = Unit
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
    ): StandingOrderInitiationRepositoryImpl {
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
                    request.url.encodedPath.contains("standing-order-consents") -> CONSENT_JSON
                    request.url.encodedPath.contains("international-standing-orders") -> INTL_STANDING_ORDER_JSON
                    else -> STANDING_ORDER_JSON
                }
                respond(body, if (isToken) HttpStatusCode.OK else status, jsonHeaders)
            },
        ) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val signingKey = TestSigningKey.pem()
        return StandingOrderInitiationRepositoryImpl(
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
        type: ConsentType = ConsentType.DomesticStandingOrder,
    ): PaymentAuthSession =
        SettingsPaymentAuthSession(MapSettings()).apply {
            savePending(consentId = CONSENT_ID, state = "s", nonce = "n", type = type)
            savePaymentToken(PsuTokenResponse(accesstoken = PSU_TOKEN, tokentype = "Bearer", expiresin = 300))
        }

    private fun requestTo(fragment: String): Recorded =
        assertNotNull(captured.lastOrNull { it.path.contains(fragment) }, "no request to $fragment")

    // region — endpoint routing

    @Test
    fun stagingADomesticMandateReachesTheStandingOrderEndpointOnly() = runTest {
        repository().stageStandingOrder(draft())

        assertNotNull(captured.lastOrNull { it.path.endsWith("/domestic-standing-order-consents") })
        assertNull(captured.firstOrNull { it.path.endsWith("/domestic-payment-consents") })
        assertNull(captured.firstOrNull { it.path.endsWith("/domestic-scheduled-payment-consents") })
    }

    @Test
    fun stagingAnInternationalMandateReachesItsOwnEndpointOnly() = runTest {
        repository().stageStandingOrder(intlDraft())

        assertNotNull(captured.lastOrNull { it.path.endsWith("/international-standing-order-consents") })
        assertNull(captured.firstOrNull { it.path.endsWith("/domestic-standing-order-consents") })
        assertNull(captured.firstOrNull { it.path.endsWith("/international-scheduled-payment-consents") })
    }

    @Test
    fun submittingReachesTheStandingOrderEndpointOnly() = runTest {
        repository(sessionHoldingAPsuToken()).submitStandingOrder(draft(), CONSENT_ID)

        assertNotNull(captured.lastOrNull { it.path.endsWith("/domestic-standing-orders") })
        assertNull(captured.firstOrNull { it.path.endsWith("/domestic-payments") })
        assertNull(captured.firstOrNull { it.path.endsWith("/domestic-scheduled-payments") })
    }

    @Test
    fun submittingAnInternationalMandateUsesItsOwnEndpoint() = runTest {
        repository(sessionHoldingAPsuToken(ConsentType.InternationalStandingOrder))
            .submitStandingOrder(intlDraft(), CONSENT_ID)

        assertNotNull(captured.lastOrNull { it.path.endsWith("/international-standing-orders") })
        assertNull(captured.firstOrNull { it.path.endsWith("/domestic-standing-orders") })
    }

    // endregion

    // region — credentials

    @Test
    fun stagingPresentsTheClientCredentialsToken() = runTest {
        repository().stageStandingOrder(draft())

        assertEquals(
            "Bearer $CLIENT_CREDENTIALS_TOKEN",
            requestTo("domestic-standing-order-consents").authorization,
        )
    }

    @Test
    fun submittingPresentsThePsuToken() = runTest {
        repository(sessionHoldingAPsuToken()).submitStandingOrder(draft(), CONSENT_ID)

        assertEquals("Bearer $PSU_TOKEN", requestTo("domestic-standing-orders").authorization)
    }

    @Test
    fun submittingWithoutAPsuTokenFailsBeforeReachingTheBank() = runTest {
        val result = repository().submitStandingOrder(draft(), CONSENT_ID)

        assertIs<NetworkResult.Error<*>>(result)
        assertNull(captured.firstOrNull { it.path.contains("standing-orders") })
    }

    // endregion

    // region — what staging records

    @Test
    fun stagingRecordsTheStandingOrderConsentType() = runTest {
        val session = SettingsPaymentAuthSession(MapSettings())

        repository(session).stageStandingOrder(draft())

        assertEquals(ConsentType.DomesticStandingOrder, session.pendingConsentType())
    }

    @Test
    fun stagingAnInternationalDraftRecordsTheInternationalType() = runTest {
        val session = SettingsPaymentAuthSession(MapSettings())

        repository(session).stageStandingOrder(intlDraft())

        assertEquals(ConsentType.InternationalStandingOrder, session.pendingConsentType())
    }

    /**
     * The mandate is stored under its own key, and neither sibling reader sees it.
     *
     * That separation is what makes the return leg's dispatch safe. The session decodes with
     * `ignoreUnknownKeys = true`, so a mandate stored under the scheduled key would decode
     * *successfully* as a scheduled payment — losing the schedule and submitting to the wrong rail.
     */
    @Test
    fun stagingStoresTheDraftUnderTheStandingOrderKeyOnly() = runTest {
        val session = SettingsPaymentAuthSession(MapSettings())

        repository(session).stageStandingOrder(draft())

        assertNotNull(session.standingOrderDraft())
        assertNull(session.draft(), "a mandate must not be readable as an immediate payment")
        assertNull(session.scheduledDraft(), "nor as a scheduled one")
    }

    @Test
    fun theStagedDraftIsTheOneReadBackForSubmission() = runTest {
        val session = SettingsPaymentAuthSession(MapSettings())
        val repo = repository(session)

        repo.stageStandingOrder(draft())

        assertEquals("2026-08-20", repo.stagedDraft()?.firstPaymentDate)
        assertEquals(StandingOrderFrequency.Monthly, repo.stagedDraft()?.frequency)
    }

    @Test
    fun stagingReturnsTheConsentIdAndAnAuthorizationUrl() = runTest {
        val result = repository().stageStandingOrder(draft())

        val staged = assertIs<NetworkResult.Success<StagedConsent>>(result).data
        assertEquals(CONSENT_ID, staged.consentId)
        assertTrue(staged.authorizationUrl.startsWith("https://authorize.sandbox.test"))
    }

    // endregion

    // region — the body on the wire

    /** Mandatory on this product; omitting it is `400 U004`. */
    @Test
    fun theConsentBodyCarriesTheCreatePermission() = runTest {
        repository().stageStandingOrder(draft())

        assertTrue(requestTo("domestic-standing-order-consents").body.contains("\"Permission\":\"Create\""))
    }

    /** The submission must not repeat it, or the two bodies diverge and the bank refuses `U008`. */
    @Test
    fun theSubmissionBodyDoesNotRepeatThePermission() = runTest {
        repository(sessionHoldingAPsuToken()).submitStandingOrder(draft(), CONSENT_ID)

        val body = requestTo("domestic-standing-orders").body
        assertTrue(!body.contains("\"Permission\""), "Permission must not appear on the submission")
        assertTrue(body.contains("\"ConsentId\":\"$CONSENT_ID\""))
    }

    /**
     * The frequency reaches the wire as its OBIE code, inside the `Frequency` object.
     *
     * Not the v3 interval string, and not `MONT` — which is not an OBIE code, is refused `U002`, and
     * is one character from the one that means monthly.
     */
    @Test
    fun theFrequencyIsSentAsAnObjectCarryingItsCode() = runTest {
        repository().stageStandingOrder(draft())

        val body = requestTo("domestic-standing-order-consents").body
        assertTrue(body.contains("\"Frequency\":{\"Type\":\"MNTH\"}"), "actual body: $body")
    }

    // endregion

    // region — failures

    @Test
    fun aConsentWithNoIdIsAnError() = runTest {
        val result = repository(errorBody = """{"Data":{"Status":"AWAU"}}""").stageStandingOrder(draft())

        assertIs<NetworkResult.Error<*>>(result)
    }

    /**
     * A refused payer is remembered so the picker stops offering it.
     *
     * Matched on the OBIE **path**, which is why the same helper works unchanged on a third product
     * whose refusal codes differ from both siblings'.
     */
    @Test
    fun aRefusedPayerIsRecordedAgainstTheAccount() = runTest {
        repository(errorBody = CARD_REFUSAL_BODY, status = HttpStatusCode.BadRequest)
            .stageStandingOrder(draft())

        assertEquals(listOf("acc-1" to AccountEndpoint.PaymentDebtor), registry.marked)
    }

    /** A refusal naming the payee says nothing about the payer, and must not disable their account. */
    @Test
    fun aRefusalAboutThePayeeDoesNotDisableThePayer() = runTest {
        repository(errorBody = CREDITOR_REFUSAL_BODY, status = HttpStatusCode.BadRequest)
            .stageStandingOrder(draft())

        assertTrue(registry.marked.isEmpty(), "nothing should have been marked unsupported")
    }

    /** With no debtor named, the bank was refusing its own choice — there is nothing to remember. */
    @Test
    fun aRefusalWithNoNamedDebtorRecordsNothing() = runTest {
        repository(errorBody = CARD_REFUSAL_BODY, status = HttpStatusCode.BadRequest)
            .stageStandingOrder(draft(debtor = null))

        assertTrue(registry.marked.isEmpty(), "nothing should have been marked unsupported")
    }

    // endregion

    // region — history

    /**
     * The hub row is the only record the app keeps that a mandate was set up.
     *
     * A standing order cannot be found again through the AIS read side, which returns no
     * `StandingOrderId` on any element, so there is nothing to correlate against later.
     */
    @Test
    fun aSubmittedMandateIsRecordedWithItsScheduleIntact() = runTest {
        repository(sessionHoldingAPsuToken()).submitStandingOrder(draft(), CONSENT_ID)

        val (receipt, saved) = history.submitted.single()
        assertEquals(STANDING_ORDER_ID, receipt.domesticPaymentId)
        assertEquals(StandingOrderFrequency.Monthly, saved.frequency)
        assertEquals("2026-08-20", saved.firstPaymentDate)
    }

    /**
     * Two keys, not one.
     *
     * The consent and the resource send different bodies — only the submission carries `ConsentId` —
     * and the Read/Write profile answers a repeated key over a changed body with `400 U029`.
     *
     * Staged and submitted through separate sessions on purpose: `stageStandingOrder` clears the
     * session as its first act, which would discard the very PSU token the submission then needs.
     * That is correct behaviour, not a test workaround — the real journey acquires that token at the
     * bank, after staging.
     */
    @Test
    fun theConsentAndResourcePostsCarryDifferentIdempotencyKeys() = runTest {
        repository().stageStandingOrder(draft())
        repository(sessionHoldingAPsuToken()).submitStandingOrder(draft(), CONSENT_ID)

        assertEquals("so-consent-key-1", requestTo("domestic-standing-order-consents").idempotencyKey)
        assertEquals("so-payment-key-1", requestTo("domestic-standing-orders").idempotencyKey)
    }

    // endregion
}
