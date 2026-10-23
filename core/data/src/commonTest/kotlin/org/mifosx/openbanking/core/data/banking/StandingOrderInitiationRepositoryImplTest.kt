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
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.mifosx.openbanking.core.common.AccountScheme
import org.mifosx.openbanking.core.data.WORKER_SESSION
import org.mifosx.openbanking.core.data.bffClient
import org.mifosx.openbanking.core.data.consentStartJson
import org.mifosx.openbanking.core.data.jsonHeaders
import org.mifosx.openbanking.core.data.banking.impl.StandingOrderInitiationRepositoryImpl
import org.mifosx.openbanking.core.data.callback.PaymentAuthSession
import org.mifosx.openbanking.core.data.callback.SettingsPaymentAuthSession
import org.mifosx.openbanking.core.model.banking.BankAccount
import org.mifosx.openbanking.core.model.banking.BeneficiaryScheme
import org.mifosx.openbanking.core.model.banking.payment.ChargeBearer
import org.mifosx.openbanking.core.model.banking.payment.ConsentType
import org.mifosx.openbanking.core.model.banking.payment.CreditorSelection
import org.mifosx.openbanking.core.model.banking.payment.PaymentDraft
import org.mifosx.openbanking.core.model.banking.payment.PaymentHistoryRow
import org.mifosx.openbanking.core.model.banking.payment.PaymentParties
import org.mifosx.openbanking.core.model.banking.payment.PaymentReceipt
import org.mifosx.openbanking.core.model.banking.payment.PaymentStageTimestamps
import org.mifosx.openbanking.core.model.banking.payment.ScheduledPaymentDraft
import org.mifosx.openbanking.core.model.banking.payment.StagedConsent
import org.mifosx.openbanking.core.model.banking.payment.StandingOrderDraft
import org.mifosx.openbanking.core.model.banking.payment.StandingOrderFrequency
import org.mifosx.openbanking.core.network.api.Pisp
import template.core.base.network.NetworkResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val CONSENT_ID = "45171"
private const val STANDING_ORDER_ID = "19916"

private const val STANDING_ORDER_JSON =
    """{"Data":{"DomesticStandingOrderId":"$STANDING_ORDER_ID","ConsentId":"$CONSENT_ID","Status":"PDNG"}}"""
private const val INTL_STANDING_ORDER_JSON =
    """{"Data":{"InternationalStandingOrderId":"19917","ConsentId":"$CONSENT_ID","Status":"INCO"}}"""

/**
 * The standing-order write path at the Worker boundary, with one recurring question: **did it reach the
 * standing-order endpoint rather than one of the other four?**
 *
 * Every routing case therefore asserts twice — that the standing-order path was called, and that the
 * neighbouring ones were not. A single positive assertion would pass against a repository that called
 * both, or called the wrong one and happened to get a parseable answer. That is not hypothetical on
 * this product: a mandate submitted to `domestic-scheduled-payments` would look plausible right up
 * until the bank executed something other than what the customer approved.
 */
class StandingOrderInitiationRepositoryImplTest {

    private data class Recorded(
        val path: String,
        val authorization: String?,
        val idempotencyKey: String?,
        val body: String,
    )

    private val captured = mutableListOf<Recorded>()

    private fun draft(
        debtor: BankAccount? = BankAccount(
            accountId = "acc-1",
            accountHolderName = "",
            accountTypeCode = "CACC",
            currency = "GBP",
            identification = "80200110203349",
            scheme = AccountScheme.SortCode,
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
        override suspend fun consentTypeOf(paymentId: String): ConsentType? = null
        override suspend fun stageTimestampsOf(paymentId: String): PaymentStageTimestamps? = null
        override suspend fun partiesOf(paymentId: String): PaymentParties? = null
        override fun observeHistory(
            types: Set<ConsentType>,
            limit: Int,
        ): Flow<List<PaymentHistoryRow>> = flowOf(emptyList())
        override suspend fun recordStatus(paymentId: String, receipt: PaymentReceipt) = Unit
    }

    private val history = FakePaymentHistoryRepo()

    private fun repository(
        session: PaymentAuthSession = SettingsPaymentAuthSession(MapSettings()),
        errorBody: String? = null,
        status: HttpStatusCode = HttpStatusCode.Created,
    ): StandingOrderInitiationRepositoryImpl {
        val client = bffClient { request: HttpRequestData ->
            val path = request.url.encodedPath
            captured += Recorded(
                path = path,
                authorization = request.headers[HttpHeaders.Authorization],
                idempotencyKey = request.headers["X-Idempotency-Key"],
                body = request.body.toByteArray().decodeToString(),
            )
            val body = when {
                errorBody != null -> errorBody
                path.startsWith("/v1/consents/payment/") -> consentStartJson(CONSENT_ID)
                path == "/v1/payments/international-standing-order" -> INTL_STANDING_ORDER_JSON
                else -> STANDING_ORDER_JSON
            }
            respond(body, status, jsonHeaders)
        }
        return StandingOrderInitiationRepositoryImpl(
            pisp = Pisp(client),
            paymentAuthSession = session,
            paymentHistoryRepository = history,
        )
    }

    private fun authorisedSession(
        type: ConsentType = ConsentType.DomesticStandingOrder,
    ): PaymentAuthSession =
        SettingsPaymentAuthSession(MapSettings()).apply {
            savePending(consentId = CONSENT_ID, state = "s", flowId = "f", type = type)
        }

    private fun requestTo(fragment: String): Recorded =
        assertNotNull(captured.lastOrNull { it.path.contains(fragment) }, "no request to $fragment")

    // region — endpoint routing

    @Test
    fun stagingADomesticMandateReachesTheStandingOrderEndpointOnly() = runTest {
        repository().stageStandingOrder(draft())

        assertNotNull(captured.lastOrNull { it.path == "/v1/consents/payment/domestic-standing-order" })
        assertNull(captured.firstOrNull { it.path == "/v1/consents/payment/domestic" })
        assertNull(captured.firstOrNull { it.path == "/v1/consents/payment/domestic-scheduled" })
    }

    @Test
    fun stagingAnInternationalMandateReachesItsOwnEndpointOnly() = runTest {
        repository().stageStandingOrder(intlDraft())

        assertNotNull(captured.lastOrNull { it.path == "/v1/consents/payment/international-standing-order" })
        assertNull(captured.firstOrNull { it.path == "/v1/consents/payment/domestic-standing-order" })
        assertNull(captured.firstOrNull { it.path == "/v1/consents/payment/international-scheduled" })
    }

    @Test
    fun submittingReachesTheStandingOrderEndpointOnly() = runTest {
        repository(authorisedSession()).submitStandingOrder(draft(), CONSENT_ID)

        assertNotNull(captured.lastOrNull { it.path == "/v1/payments/domestic-standing-order" })
        assertNull(captured.firstOrNull { it.path == "/v1/payments/domestic" })
        assertNull(captured.firstOrNull { it.path == "/v1/payments/domestic-scheduled" })
    }

    @Test
    fun submittingAnInternationalMandateUsesItsOwnEndpoint() = runTest {
        repository(authorisedSession(ConsentType.InternationalStandingOrder))
            .submitStandingOrder(intlDraft(), CONSENT_ID)

        assertNotNull(captured.lastOrNull { it.path == "/v1/payments/international-standing-order" })
        assertNull(captured.firstOrNull { it.path == "/v1/payments/domestic-standing-order" })
    }

    // endregion

    // region — credentials

    /** Every call presents the Worker session; the Worker holds every bank token. */
    @Test
    fun everyCallPresentsTheWorkerSession() = runTest {
        repository().stageStandingOrder(draft())
        repository(authorisedSession()).submitStandingOrder(draft(), CONSENT_ID)

        assertEquals("Session $WORKER_SESSION", requestTo("/v1/consents/payment/domestic-standing-order").authorization)
        assertEquals("Session $WORKER_SESSION", requestTo("/v1/payments/domestic-standing-order").authorization)
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
        assertTrue(staged.authorizationUrl.startsWith("https://sandbox.ob.hsbc.co.uk/"))
        assertEquals("flow-1", staged.flowId)
    }

    // endregion

    // region — the body on the wire

    /** Mandatory on this product; omitting it is `400 U004`. */
    @Test
    fun theConsentBodyCarriesTheCreatePermission() = runTest {
        repository().stageStandingOrder(draft())

        assertTrue(requestTo("/v1/consents/payment/domestic-standing-order").body.contains("\"Permission\":\"Create\""))
    }

    /** The submission must not repeat it, or the two bodies diverge and the bank refuses `U008`. */
    @Test
    fun theSubmissionBodyDoesNotRepeatThePermission() = runTest {
        repository(authorisedSession()).submitStandingOrder(draft(), CONSENT_ID)

        val body = requestTo("/v1/payments/domestic-standing-order").body
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

        val body = requestTo("/v1/consents/payment/domestic-standing-order").body
        assertTrue(body.contains("\"Frequency\":{\"Type\":\"MNTH\"}"), "actual body: $body")
    }

    // endregion

    // region — failures

    @Test
    fun aWorkerAnswerWithNoConsentIdIsAnError() = runTest {
        val result = repository(errorBody = """{"flowId":"f","authorizeUrl":"https://x/?state=s"}""").stageStandingOrder(draft())

        assertIs<NetworkResult.Error<*>>(result)
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
        repository(authorisedSession()).submitStandingOrder(draft(), CONSENT_ID)

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
     * Staged and submitted through separate sessions, as in the real journey: the authorisation at
     * the bank happens between the two.
     */
    @Test
    fun theConsentAndResourcePostsCarryDifferentIdempotencyKeys() = runTest {
        repository().stageStandingOrder(draft())
        repository(authorisedSession()).submitStandingOrder(draft(), CONSENT_ID)

        assertEquals("so-consent-key-1", requestTo("/v1/consents/payment/domestic-standing-order").idempotencyKey)
        assertEquals("so-payment-key-1", requestTo("/v1/payments/domestic-standing-order").idempotencyKey)
    }

    // endregion
}
