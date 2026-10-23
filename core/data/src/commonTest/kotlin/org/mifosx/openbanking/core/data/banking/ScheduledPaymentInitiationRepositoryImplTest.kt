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
import org.mifosx.openbanking.core.data.banking.impl.ScheduledPaymentInitiationRepositoryImpl
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
import org.mifosx.openbanking.core.model.banking.payment.StandingOrderDraft
import org.mifosx.openbanking.core.network.api.Pisp
import template.core.base.network.NetworkResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val CONSENT_ID = "45149"
private const val PAYMENT_ID = "19919"

private const val PAYMENT_JSON =
    """{"Data":{"DomesticScheduledPaymentId":"$PAYMENT_ID","ConsentId":"$CONSENT_ID","Status":"INCO"}}"""
private const val INTL_PAYMENT_JSON =
    """{"Data":{"InternationalScheduledPaymentId":"19921","ConsentId":"$CONSENT_ID","Status":"INCO"}}"""

/**
 * The scheduled write path at the Worker boundary, with one recurring question: **did it reach the scheduled
 * endpoint rather than the immediate one?**
 *
 * Every routing case therefore asserts twice — that the scheduled path was called, and that the
 * immediate path was not. A single positive assertion would pass against a repository that called
 * both, or called the wrong one and happened to get a parseable answer.
 */
class ScheduledPaymentInitiationRepositoryImplTest {

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
    ): ScheduledPaymentInitiationRepositoryImpl {
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
                path == "/v1/payments/international-scheduled" -> INTL_PAYMENT_JSON
                else -> PAYMENT_JSON
            }
            respond(body, status, jsonHeaders)
        }
        return ScheduledPaymentInitiationRepositoryImpl(
            pisp = Pisp(client),
            paymentAuthSession = session,
            paymentHistoryRepository = history,
        )
    }

    private fun authorisedSession(
        type: ConsentType = ConsentType.DomesticScheduledPayment,
    ): PaymentAuthSession =
        SettingsPaymentAuthSession(MapSettings()).apply {
            savePending(consentId = CONSENT_ID, state = "s", flowId = "f", type = type)
        }

    private fun requestTo(fragment: String): Recorded =
        assertNotNull(captured.lastOrNull { it.path.contains(fragment) }, "no request to $fragment")

    // region — endpoint routing

    /** The pair that catches a scheduled consent going to the immediate endpoint. */
    @Test
    fun stagingADomesticScheduledConsentReachesTheScheduledEndpointOnly() = runTest {
        repository().stagePayment(draft())

        assertNotNull(captured.lastOrNull { it.path == "/v1/consents/payment/domestic-scheduled" })
        assertNull(captured.firstOrNull { it.path == "/v1/consents/payment/domestic" })
    }

    @Test
    fun stagingAnInternationalScheduledConsentReachesTheScheduledEndpointOnly() = runTest {
        repository().stagePayment(intlDraft())

        assertNotNull(captured.lastOrNull { it.path == "/v1/consents/payment/international-scheduled" })
        assertNull(captured.firstOrNull { it.path == "/v1/consents/payment/international" })
        assertNull(captured.firstOrNull { it.path == "/v1/consents/payment/domestic-scheduled" })
    }

    @Test
    fun submittingReachesTheScheduledPaymentEndpointOnly() = runTest {
        repository(authorisedSession()).submitPayment(draft(), CONSENT_ID)

        assertNotNull(captured.lastOrNull { it.path == "/v1/payments/domestic-scheduled" })
        assertNull(captured.firstOrNull { it.path == "/v1/payments/domestic" })
    }

    @Test
    fun submittingAnInternationalScheduledPaymentUsesItsOwnEndpoint() = runTest {
        repository(authorisedSession(ConsentType.InternationalScheduledPayment))
            .submitPayment(intlDraft(), CONSENT_ID)

        assertNotNull(captured.lastOrNull { it.path == "/v1/payments/international-scheduled" })
        assertNull(captured.firstOrNull { it.path == "/v1/payments/domestic-scheduled" })
    }

    // endregion

    // region — credentials

    /** Every call presents the Worker session; the Worker holds every bank token. */
    @Test
    fun everyCallPresentsTheWorkerSession() = runTest {
        repository().stagePayment(draft())
        repository(authorisedSession()).submitPayment(draft(), CONSENT_ID)

        assertEquals("Session $WORKER_SESSION", requestTo("/v1/consents/payment/domestic-scheduled").authorization)
        assertEquals("Session $WORKER_SESSION", requestTo("/v1/payments/domestic-scheduled").authorization)
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
        assertTrue(staged.authorizationUrl.startsWith("https://sandbox.ob.hsbc.co.uk/"))
        assertEquals("flow-1", staged.flowId)
    }

    // endregion

    // region — failures

    @Test
    fun aWorkerAnswerWithNoConsentIdIsAnError() = runTest {
        val result = repository(errorBody = """{"flowId":"f","authorizeUrl":"https://x/?state=s"}""").stagePayment(draft())

        assertIs<NetworkResult.Error<*>>(result)
    }

    // endregion

    // region — history

    @Test
    fun aSubmittedPaymentIsRecordedInHistoryWithItsDate() = runTest {
        repository(authorisedSession()).submitPayment(draft(), CONSENT_ID)

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
     * Staged and submitted through separate sessions, as in the real journey: the authorisation at
     * the bank happens between the two.
     */
    @Test
    fun theConsentAndPaymentPostsCarryDifferentIdempotencyKeys() = runTest {
        repository().stagePayment(draft())
        repository(authorisedSession()).submitPayment(draft(), CONSENT_ID)

        assertEquals("consent-key-1", requestTo("/v1/consents/payment/domestic-scheduled").idempotencyKey)
        assertEquals("payment-key-1", requestTo("/v1/payments/domestic-scheduled").idempotencyKey)
    }

    // endregion
}
