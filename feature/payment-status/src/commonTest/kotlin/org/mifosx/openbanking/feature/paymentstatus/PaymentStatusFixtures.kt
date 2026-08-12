/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.paymentstatus

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.mifosx.openbanking.core.data.banking.PaymentHistoryRepository
import org.mifosx.openbanking.core.data.banking.PaymentStatusRepository
import org.mifosx.openbanking.core.model.banking.payment.ConsentType
import org.mifosx.openbanking.core.model.banking.payment.PaymentCharge
import org.mifosx.openbanking.core.model.banking.payment.PaymentDisposition
import org.mifosx.openbanking.core.model.banking.payment.PaymentDraft
import org.mifosx.openbanking.core.model.banking.payment.PaymentHistoryItem
import org.mifosx.openbanking.core.model.banking.payment.PaymentReceipt
import org.mifosx.openbanking.core.model.banking.payment.PaymentStageTimestamps
import org.mifosx.openbanking.core.model.banking.payment.PaymentStatus
import org.mifosx.openbanking.core.model.banking.payment.ScheduledPaymentDraft
import org.mifosx.openbanking.feature.paymentstatus.ui.PaymentStatusErrorKind
import org.mifosx.openbanking.feature.paymentstatus.ui.PaymentStatusState
import org.mifosx.openbanking.feature.paymentstatus.ui.PaymentStatusUiState
import org.mifosx.openbanking.feature.paymentstatus.ui.PaymentStepState
import org.mifosx.openbanking.feature.paymentstatus.ui.PaymentTimelineEntry
import org.mifosx.openbanking.feature.paymentstatus.ui.PaymentTimelineStep
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult

/** The same payment the rest of the PISP spec is told through. */
object PaymentStatusFixtures {

    const val PAYMENT_ID = "PMT-812774903-01"

    /**
     * Shaped like HSBC's real answer: all three timestamps equal, and a non-zero `CHAPSOut` charge
     * on a Faster Payments instruction. Both of those look like mistakes and are not — see
     * [receiptWithDistinctTimestamps] for the case where the dates genuinely differ.
     */
    fun receipt(
        status: PaymentStatus = PaymentStatus.AcceptedSettlementInProcess,
        charges: List<PaymentCharge> = listOf(
            PaymentCharge(
                bearer = "BorneByDebtor",
                typeLabel = "UK.OBIE.CHAPSOut",
                amountLabel = "£0.05",
            ),
        ),
    ): PaymentReceipt =
        PaymentReceipt(
            domesticPaymentId = PAYMENT_ID,
            consentId = "812774903",
            status = status,
            creationDateTime = "2026-08-03T14:22:00+00:00",
            statusUpdateDateTime = "2026-08-03T14:22:00+00:00",
            settlementDateTime = "2026-08-03T14:22:00+00:00",
            amountLabel = "£850.00",
            creditorName = "Jameson Lettings",
            reference = "RENT-FLAT12",
            debtorIdentification = "40051512345678",
            charges = charges,
        )

    /** An ASPSP that maintains the fields properly, so the three rows are proven independent. */
    fun receiptWithDistinctTimestamps(): PaymentReceipt = receipt().copy(
        creationDateTime = "2026-08-03T14:22:00+00:00",
        statusUpdateDateTime = "2026-08-03T16:40:00+00:00",
        settlementDateTime = "2026-08-04T09:00:00+00:00",
    )

    /** What the local row recorded for this payment's two unreported stages. */
    fun stages(): PaymentStageTimestamps = PaymentStageTimestamps(
        approvedAt = "2026-08-03T14:20:00Z",
        submittedAt = "2026-08-03T14:22:00Z",
    )

    /**
     * The timeline as the view model builds it for an in-flight payment: three stages done, the
     * fourth still with the bank and therefore undated — and the first undated too, because nothing
     * records when the consent was staged.
     *
     * [completedAt] must be given the same value the caller gives [contentState]'s `settledAt` (on
     * a success) or `statusChangedAt` (on a rejection) — the view model derives the fourth stage
     * from those very fields, so a fixture where they disagree depicts a state the app cannot
     * produce, and a golden of it would be reviewed as if it could.
     */
    fun timeline(
        completedState: PaymentStepState = PaymentStepState.Current,
        completedAt: String = "",
    ): List<PaymentTimelineEntry> = listOf(
        PaymentTimelineEntry(PaymentTimelineStep.Completed, completedState, completedAt),
        PaymentTimelineEntry(
            PaymentTimelineStep.Submitted,
            PaymentStepState.Done,
            "3 Aug 2026, 14:22",
        ),
        PaymentTimelineEntry(
            PaymentTimelineStep.ApprovedAtBank,
            PaymentStepState.Done,
            "3 Aug 2026, 14:20",
        ),
        // Undated on purpose: nothing records when the consent was staged, and the receipt's
        // CreationDateTime means submission, not request.
        PaymentTimelineEntry(PaymentTimelineStep.RequestCreated, PaymentStepState.Done),
    )

    fun contentState(
        disposition: PaymentDisposition = PaymentDisposition.InProgress,
        reference: String = "RENT-FLAT12",
        refreshing: Boolean = false,
        status: PaymentStatus = PaymentStatus.AcceptedSettlementInProcess,
        settledAt: String = "3 Aug 2026, 14:22",
        charges: List<PaymentCharge> = listOf(
            PaymentCharge(
                bearer = "BorneByDebtor",
                typeLabel = "UK.OBIE.CHAPSOut",
                amountLabel = "£0.05",
            ),
        ),
        lastCheckedAt: String = "14:25",
        refreshFailure: PaymentStatusErrorKind? = null,
        statusChangedAt: String = "3 Aug 2026, 14:22",
        timeline: List<PaymentTimelineEntry> = timeline(),
    ): PaymentStatusState = PaymentStatusState(
        paymentId = PAYMENT_ID,
        uiState = PaymentStatusUiState.Content(
            paymentId = PAYMENT_ID,
            status = status,
            disposition = disposition,
            amountLabel = "£850.00",
            creditorName = "Jameson Lettings",
            reference = reference,
            debtorLabel = "40-05-15 12345678",
            submittedAt = "3 Aug 2026, 14:22",
            settledAt = settledAt,
            statusChangedAt = statusChangedAt,
            charges = charges,
            lastCheckedAt = lastCheckedAt,
            refreshing = refreshing,
            refreshFailure = refreshFailure,
            timeline = timeline,
        ),
    )

    /** Two charges, the case a single `DETAIL_FEE` tag could not address. */
    fun twoCharges(): List<PaymentCharge> = listOf(
        PaymentCharge(bearer = "BorneByDebtor", typeLabel = "UK.OBIE.CHAPSOut", amountLabel = "£0.05"),
        PaymentCharge(bearer = "BorneByDebtor", typeLabel = "UK.OBIE.FX", amountLabel = "£1.20"),
    )

    fun loadingState(): PaymentStatusState =
        PaymentStatusState(paymentId = PAYMENT_ID, uiState = PaymentStatusUiState.Loading)

    fun errorState(
        kind: PaymentStatusErrorKind = PaymentStatusErrorKind.NetworkError,
    ): PaymentStatusState =
        PaymentStatusState(paymentId = PAYMENT_ID, uiState = PaymentStatusUiState.Error(kind))
}

/**
 * The read-only half of the payment path.
 *
 * `PaymentStatusRepository` declares one method, which is the point: this screen never writes, and
 * that is what lets it read on a client-credentials token after the PSU token has expired. The four
 * write methods this fake used to stub out with `error(...)` went with the interface split.
 */
class FakePaymentStatusRepository(
    receipt: PaymentReceipt = PaymentStatusFixtures.receipt(),
    private var statusResult: NetworkResult<PaymentReceipt, NetworkError> =
        NetworkResult.Success(receipt),
) : PaymentStatusRepository {

    val statusReads = mutableListOf<String>()

    override suspend fun paymentStatus(
        paymentId: String,
    ): NetworkResult<PaymentReceipt, NetworkError> {
        statusReads += paymentId
        return statusResult
    }

    fun statusReturns(result: NetworkResult<PaymentReceipt, NetworkError>) {
        statusResult = result
    }

    /** Convenience for the common case of a later read returning a moved-on receipt. */
    fun receiptReturns(receipt: PaymentReceipt) {
        statusResult = NetworkResult.Success(receipt)
    }
}

/**
 * Only [stageTimestampsOf] is exercised: this screen reads the two stage times the bank never
 * reports and writes nothing back.
 *
 * A null [stages] is the real case of a payment made on another device, or one the five-row cap has
 * since evicted — the timeline must draw those stages undated rather than borrow a timestamp.
 */
class FakePaymentHistoryRepository(
    private val stages: PaymentStageTimestamps? = PaymentStatusFixtures.stages(),
) : PaymentHistoryRepository {

    val stageReads = mutableListOf<String>()

    override fun observeRecent(): Flow<List<PaymentHistoryItem>> = flowOf(emptyList())

    override suspend fun saveSubmitted(receipt: PaymentReceipt, draft: PaymentDraft) =
        error("payment-status never writes history")

    override suspend fun saveFailed(draft: PaymentDraft, errorKind: String, errorDescription: String) =
        error("payment-status never writes history")

    override suspend fun saveSubmitted(receipt: PaymentReceipt, draft: ScheduledPaymentDraft) =
        error("payment-status never writes history")

    override suspend fun saveFailed(
        draft: ScheduledPaymentDraft,
        errorKind: String,
        errorDescription: String,
    ) = error("payment-status never writes history")

    override suspend fun consentTypeOf(paymentId: String): ConsentType? = ConsentType.DomesticSinglePayment

    override suspend fun stageTimestampsOf(paymentId: String): PaymentStageTimestamps? {
        stageReads += paymentId
        return stages
    }

    override suspend fun refreshStatuses() = error("payment-status never refreshes the whole hub")
}
