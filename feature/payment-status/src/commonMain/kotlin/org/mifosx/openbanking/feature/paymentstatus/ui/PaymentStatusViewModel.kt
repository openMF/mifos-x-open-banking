/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.paymentstatus.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import org.mifosx.openbanking.core.common.formatDateTime
import org.mifosx.openbanking.core.common.formatSortCode
import org.mifosx.openbanking.core.common.formatTimeOfDay
import org.mifosx.openbanking.core.data.banking.PaymentHistoryRepository
import org.mifosx.openbanking.core.data.banking.PaymentStatusRepository
import org.mifosx.openbanking.core.data.util.toThrowable
import org.mifosx.openbanking.core.model.banking.payment.PaymentDisposition
import org.mifosx.openbanking.core.model.banking.payment.PaymentReceipt
import org.mifosx.openbanking.core.model.banking.payment.PaymentStageTimestamps
import org.mifosx.openbanking.core.model.banking.payment.PaymentStatus
import template.core.base.network.NetworkResult
import template.core.base.ui.viewmodel.BaseViewModel
import kotlin.time.Clock

private const val SORT_CODE_DIGITS = 6

/**
 * Reads one submitted payment's settlement status.
 *
 * Refresh-on-demand rather than polled: a terminal status will not change again, and polling one is
 * wasted work. The read is cheap enough to survive the PSU token expiring — it falls back to a
 * client-credentials payments token — so a customer can come back tomorrow and still see the
 * outcome.
 *
 * The status itself comes from the bank; the timeline's approval and submission times cannot. OBIE
 * answers with one `CreationDateTime` and no stage history, so those two are read back from the
 * local payment-history row that recorded them as they happened. A missing row means an undated
 * stage, never a borrowed timestamp.
 */
class PaymentStatusViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: PaymentStatusRepository,
    private val paymentHistoryRepository: PaymentHistoryRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : BaseViewModel<PaymentStatusState, Nothing, PaymentStatusAction>(
    initialState = PaymentStatusState(
        paymentId = savedStateHandle.get<String>(PAYMENT_ID_ARG).orEmpty(),
    ),
) {

    init {
        load(refreshing = false)
    }

    override fun handleAction(action: PaymentStatusAction) {
        when (action) {
            PaymentStatusAction.RefreshStatus -> load(refreshing = true)
        }
    }

    /**
     * A manual refresh keeps the current status on screen rather than collapsing to a skeleton —
     * replacing a known answer with a placeholder reads as losing it.
     *
     * That holds when the refresh fails, too. A read that could not reach the bank has not
     * invalidated what is already displayed; it is still the bank's last word, only older than
     * asked for. So a failure over existing content becomes a notice beside it, and only a failure
     * with nothing to preserve — the first load — becomes the error page.
     */
    private fun load(refreshing: Boolean) {
        val current = state.uiState
        if (refreshing && current is PaymentStatusUiState.Content) {
            updateState { copy(uiState = current.copy(refreshing = true, refreshFailure = null)) }
        }

        viewModelScope.launch {
            when (val result = repository.paymentStatus(state.paymentId)) {
                is NetworkResult.Success -> {
                    val stages = paymentHistoryRepository.stageTimestampsOf(state.paymentId)
                    updateState { copy(uiState = result.data.toContent(stages)) }
                }

                is NetworkResult.Error -> {
                    val kind = classifyPaymentStatusError(result.error.toThrowable())
                    updateState {
                        copy(
                            uiState = (uiState as? PaymentStatusUiState.Content)
                                ?.copy(refreshing = false, refreshFailure = kind)
                                ?: PaymentStatusUiState.Error(kind),
                        )
                    }
                }
            }
        }
    }

    /**
     * The three timestamps are separate facts and are shown separately.
     *
     * [PaymentReceipt.creationDateTime] is when the payment was made — the only one that means
     * "submitted". [PaymentReceipt.statusUpdateDateTime] is when the status last moved, which HSBC
     * returns unchanged even on a settled payment. Conflating them, as this did, put the wrong
     * label on the wrong value and left the right one unread.
     */
    private fun PaymentReceipt.toContent(
        stages: PaymentStageTimestamps?,
    ): PaymentStatusUiState.Content {
        val settled = settlementDateTime.formatted()
        val statusChanged = statusUpdateDateTime.formatted()
        return PaymentStatusUiState.Content(
            paymentId = domesticPaymentId,
            status = status,
            disposition = status.disposition,
            amountLabel = amountLabel,
            creditorName = creditorName,
            reference = reference,
            debtorLabel = debtorIdentification.toAccountLabel(),
            submittedAt = formatDateTime(creationDateTime, timeZone),
            settledAt = settled,
            // Guarded exactly as `settledAt` is. Unguarded, a blank wire value went through
            // `formatDateTime`, which returns its input unparsed — so a blank became a blank, and
            // whatever it returned drove the conditional row rather than the fact of the absence.
            statusChangedAt = statusChanged,
            charges = charges,
            lastCheckedAt = formatTimeOfDay(clock.now(), timeZone),
            timeline = buildTimeline(
                stages = stages,
                settledAt = settled,
                statusChangedAt = statusChanged,
            ),
        )
    }

    /**
     * The four stages, newest first.
     *
     * Steps 1–3 are [PaymentStepState.Done] unconditionally because this screen is only reachable
     * with a bank-issued payment id: the request was created, the PSU approved it, and the POST
     * succeeded, or there would be no id to look up. Their timestamps come from the local row and
     * are dropped, not substituted, when it is missing.
     *
     * [PaymentTimelineStep.RequestCreated] is always undated. Nothing records when the consent was
     * staged — v5 stores `approvedAt` and `submittedAt` and no `stagedAt` — and the obvious
     * substitute is wrong: on a payment resource OBIE's `CreationDateTime` is when the bank created
     * the *payment*, which is submission. Feeding it here dated the first stage with the third
     * stage's event, so a payment approved at 14:20 rendered as requested at 14:22 — later than its
     * own approval, in a list that claims to run newest first.
     *
     * Step 4 is the only one the bank decides, and it is never filled in optimistically —
     * settlement is an asynchronous batch, so a completed stage appears on a later refresh or not
     * at all.
     */
    private fun PaymentReceipt.buildTimeline(
        stages: PaymentStageTimestamps?,
        settledAt: String,
        statusChangedAt: String,
    ): List<PaymentTimelineEntry> {
        val completed = when (status.disposition) {
            PaymentDisposition.TerminalSuccess ->
                PaymentTimelineEntry(PaymentTimelineStep.Completed, PaymentStepState.Done, settledAt)

            PaymentDisposition.TerminalFailure -> PaymentTimelineEntry(
                PaymentTimelineStep.Completed,
                PaymentStepState.Failed,
                statusChangedAt,
            )

            PaymentDisposition.InProgress ->
                PaymentTimelineEntry(PaymentTimelineStep.Completed, status.inFlightStepState())
        }

        return listOf(
            completed,
            PaymentTimelineEntry(
                step = PaymentTimelineStep.Submitted,
                state = PaymentStepState.Done,
                timestamp = stages?.submittedAt.formatted(),
            ),
            PaymentTimelineEntry(
                step = PaymentTimelineStep.ApprovedAtBank,
                state = PaymentStepState.Done,
                timestamp = stages?.approvedAt.formatted(),
            ),
            PaymentTimelineEntry(
                step = PaymentTimelineStep.RequestCreated,
                state = PaymentStepState.Done,
            ),
        )
    }

    /** Blank in, blank out — `formatDateTime` returns an unparseable input verbatim. */
    private fun String?.formatted(): String =
        this?.takeIf { it.isNotBlank() }?.let { formatDateTime(it, timeZone) }.orEmpty()

    companion object {
        /** Must match the [org.mifosx.openbanking.feature.paymentstatus.PaymentStatusRoute] property. */
        const val PAYMENT_ID_ARG: String = "paymentId"
    }
}

/**
 * Whether the bank is actually settling this payment, or has merely taken it in.
 *
 * `ACSP`/`ACTC` say settlement is under way, which is the stage someone is waiting on. `RCVD`,
 * `PDNG` and an unrecognised code say only that the instruction arrived — telling someone their
 * money is moving on that evidence would be a claim the bank has not made.
 */
private fun PaymentStatus.inFlightStepState(): PaymentStepState = when (this) {
    PaymentStatus.AcceptedSettlementInProcess,
    PaymentStatus.AcceptedTechnicalValidation,
    -> PaymentStepState.Current

    else -> PaymentStepState.Pending
}

/**
 * Renders the OBIE identification the way it is written down — `40-05-15 12345678` — rather than the
 * unpunctuated fourteen digits the wire carries.
 */
private fun String.toAccountLabel(): String {
    val digits = filter(Char::isDigit)
    if (digits.length <= SORT_CODE_DIGITS) return this
    return "${formatSortCode(digits.take(SORT_CODE_DIGITS))} ${digits.drop(SORT_CODE_DIGITS)}"
}
