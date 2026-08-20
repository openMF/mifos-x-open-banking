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

import org.mifosx.openbanking.feature.paymentstatus.ui.PaymentStepState
import org.mifosx.openbanking.feature.paymentstatus.ui.PaymentTimelineStep

/** Stable tags the UI suites drive this screen by. Append-only. */
internal object PaymentStatusTestTags {
    const val SKELETON = "paymentStatus:skeleton"
    const val SUMMARY_CARD = "paymentStatus:summaryCard"
    const val AMOUNT = "paymentStatus:amount"
    const val STATUS_CHIP = "paymentStatus:statusChip"
    const val IN_PROGRESS_NOTE = "paymentStatus:inProgressNote"
    const val DETAILS_LIST = "paymentStatus:detailsList"
    const val DETAIL_REFERENCE = "paymentStatus:detailReference"
    const val DETAIL_FROM = "paymentStatus:detailFrom"
    const val DETAIL_SUBMITTED = "paymentStatus:detailSubmitted"
    const val DETAIL_PAYMENT_ID = "paymentStatus:detailPaymentId"
    const val STATUS_DETAIL = "paymentStatus:statusDetail"
    const val APP_BAR = "paymentStatus:appBar"
    const val APP_BAR_STATUS = "paymentStatus:appBarStatus"
    const val DETAIL_SETTLED = "paymentStatus:detailSettled"
    const val DETAIL_SCHEDULED_FOR = "paymentStatus:detailScheduledFor"
    const val DETAIL_STATUS_CHANGED = "paymentStatus:detailStatusChanged"
    const val LAST_CHECKED = "paymentStatus:lastChecked"
    const val REFRESH_BUTTON = "paymentStatus:refreshButton"

    /** Standing orders only: how often it repeats, when it ends, and each later amount. */
    const val DETAIL_REPEATS = "paymentStatus:detailRepeats"
    const val DETAIL_FINAL_PAYMENT = "paymentStatus:detailFinalPayment"
    const val DETAIL_RECURRING_AMOUNT = "paymentStatus:detailRecurringAmount"
    const val ERROR_STATE = "paymentStatus:errorState"
    const val RETRY_BUTTON = "paymentStatus:retryButton"
    const val TIMELINE = "paymentStatus:timeline"
    const val REFRESH_FAILURE = "paymentStatus:refreshFailure"

    /**
     * Indexed because the bank may apply more than one charge, and a tag repeated across siblings
     * makes `onNodeWithTag` ambiguous — it matched the single-charge fixture and would have failed
     * the first time a real payment carried two.
     */
    fun detailFee(index: Int): String = "paymentStatus:detailFee:$index"

    /** The row for one stage, whatever state it is in. */
    fun timelineStep(step: PaymentTimelineStep): String = "paymentStatus:timelineStep:${step.name}"

    /**
     * The marker beside a stage, carrying the state in the tag so a suite can assert that a
     * rejected payment's fourth stage reads as failed rather than merely present.
     */
    fun timelineState(step: PaymentTimelineStep, state: PaymentStepState): String =
        "paymentStatus:timelineState:${step.name}:${state.name}"
}
