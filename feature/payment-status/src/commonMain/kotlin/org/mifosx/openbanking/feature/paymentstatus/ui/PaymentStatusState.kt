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

import org.mifosx.openbanking.core.data.util.RemoteException
import org.mifosx.openbanking.core.model.banking.payment.ConsentType
import org.mifosx.openbanking.core.model.banking.payment.PaymentCharge
import org.mifosx.openbanking.core.model.banking.payment.PaymentDisposition
import org.mifosx.openbanking.core.model.banking.payment.PaymentStatus
import org.mifosx.openbanking.core.model.banking.payment.StandingOrderFrequency
import template.core.base.network.NetworkError

/**
 * Why a status read failed.
 *
 * There is no `Empty`: a payment id either resolves or it does not, and one that cannot be found is
 * [PaymentNotFound] — a failure to explain, not an absence to shrug at.
 */
enum class PaymentStatusErrorKind {
    PaymentNotFound,
    TokenExpired,
    ConsentRevoked,
    NetworkError,
}

/**
 * The four stages a payment passes through, in the order they happen.
 *
 * Rendered newest-first, so the stage someone is waiting on sits at the top rather than at the
 * bottom of a list they have to scroll to reach.
 */
enum class PaymentTimelineStep {
    RequestCreated,
    ApprovedAtBank,
    Submitted,
    Completed,
}

/**
 * How far a stage got.
 *
 * [Pending] and [Current] are a real distinction, not decoration: the bank having merely received an
 * instruction (`RCVD`, `PDNG`) is not the same claim as it actively settling one (`ACSP`), and only
 * the second justifies telling someone their money is on the way.
 */
enum class PaymentStepState {
    Done,
    Current,
    Pending,
    Failed,
}

/**
 * One stage of the timeline.
 *
 * @property timestamp Formatted, or empty when nothing observed this stage. Empty draws the stage
 *   undated — a payment made on another device has no local approval time, and borrowing a nearby
 *   timestamp to fill the gap would be a fabricated claim about when someone approved a payment.
 */
data class PaymentTimelineEntry(
    val step: PaymentTimelineStep,
    val state: PaymentStepState,
    val timestamp: String = "",
)

sealed interface PaymentStatusUiState {

    data object Loading : PaymentStatusUiState

    /**
     * @property status The bank's own status, rendered as its OBIE meaning beneath the chip. The
     *   chip says the plain-English thing; this is the evidence behind it, and the difference
     *   matters when someone is asking whether a refresh actually did anything.
     * @property inProgress Whether the payment is still in flight, which is what decides the note
     *   and the chip's colour. In-flight is not a fault, so it must not render as one.
     * @property submittedAt When the payment was made, formatted. From the bank's
     *   `CreationDateTime` — not its `StatusUpdateDateTime`, which means something else.
     * @property settledAt When the funds are expected to settle, formatted, or empty when the bank
     *   did not say. Empty means the row is not drawn at all rather than drawn blank.
     * @property statusChangedAt When the status last moved, formatted. HSBC returns this equal to
     *   [submittedAt] even on a settled payment; that is its answer, shown as given.
     * @property charges What the bank actually charged. Empty renders no fee row — silence is not
     *   the same claim as "£0.00", and only the bank can tell us which is true.
     * @property lastCheckedAt Local clock time of the most recent successful read. Exists so an
     *   unchanged status reads as "checked, no change yet" instead of a dead button.
     * @property refreshing Whether a manual re-read is running. Distinct from [Loading]: the current
     *   status stays on screen while it refreshes rather than collapsing back to a skeleton.
     * @property refreshFailure Why the last re-read failed, or null when the last one succeeded. A
     *   refresh that fails must not take the answer already on screen with it — what is displayed is
     *   still true, only older than the user asked for, and that is a notice rather than an error
     *   page.
     * @property timeline The four stages, newest first. Empty only for a state built by hand.
     */
    data class Content(
        val paymentId: String,
        val status: PaymentStatus,
        val disposition: PaymentDisposition,
        val amountLabel: String,
        val creditorName: String,
        val reference: String,
        val debtorLabel: String,
        val submittedAt: String,
        val settledAt: String = "",
        /**
         * The date a scheduled payment is due, formatted, or empty on an immediate one.
         *
         * Read from `requestedExecutionDateTime` and **not** from `settlementDateTime`, which the
         * scheduled rails return equal to the creation timestamp — today, not the date the customer
         * chose. Rendering that field would tell someone their payment settled today when it is due
         * next week, so the scheduled mapper leaves it empty and this carries the real date instead.
         */
        val scheduledForAt: String = "",
        /**
         * The product and rail this payment was made on, or null when no local row names it.
         *
         * Decides both the disposition and the word for it — a settled mandate is "Set up", not
         * "Completed". Null falls back to the rail-neutral reading.
         */
        val consentType: ConsentType? = null,
        /**
         * How often a standing order repeats, or null on any product that runs once.
         *
         * Null rather than blank for an unrecognised code: the row is then omitted rather than
         * claiming a mandate repeats on a schedule this build cannot name.
         */
        val frequency: StandingOrderFrequency? = null,
        /** When a standing order stops, formatted, or empty when it runs until it is stopped. */
        val finalPaymentAt: String = "",
        /** What each repeat after the first is for, formatted, or empty. */
        val recurringAmountLabel: String = "",
        val statusChangedAt: String = "",
        val charges: List<PaymentCharge> = emptyList(),
        val lastCheckedAt: String = "",
        val refreshing: Boolean = false,
        val refreshFailure: PaymentStatusErrorKind? = null,
        val timeline: List<PaymentTimelineEntry> = emptyList(),
    ) : PaymentStatusUiState {

        val inProgress: Boolean
            get() = disposition == PaymentDisposition.InProgress
    }

    data class Error(val kind: PaymentStatusErrorKind) : PaymentStatusUiState
}

/**
 * Whether a read is running, as the pull-to-refresh indicator sees it.
 *
 * Covers both the first load and a manual refresh, so the gesture spins for either. The error page
 * is deliberately not "reading": nothing is in flight there until the pull dispatches one, and that
 * is exactly where someone reaches for the gesture.
 */
val PaymentStatusUiState.isReading: Boolean
    get() = this is PaymentStatusUiState.Loading ||
        (this is PaymentStatusUiState.Content && refreshing)

data class PaymentStatusState(
    val paymentId: String,
    val uiState: PaymentStatusUiState = PaymentStatusUiState.Loading,
)

sealed interface PaymentStatusAction {
    /** Re-reads on demand. Never polled: a terminal status will not change again. */
    data object RefreshStatus : PaymentStatusAction
}

internal fun classifyPaymentStatusError(throwable: Throwable): PaymentStatusErrorKind =
    when ((throwable as? RemoteException)?.networkError) {
        is NetworkError.Client.NotFound -> PaymentStatusErrorKind.PaymentNotFound
        is NetworkError.Client.Unauthorized -> PaymentStatusErrorKind.TokenExpired
        is NetworkError.Client.Forbidden -> PaymentStatusErrorKind.ConsentRevoked
        else -> PaymentStatusErrorKind.NetworkError
    }
