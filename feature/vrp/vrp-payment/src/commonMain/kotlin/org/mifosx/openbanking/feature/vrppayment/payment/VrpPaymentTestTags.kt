/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.vrppayment.payment

/**
 * Node tags for paying under a VRP, across both phases.
 *
 * Append-only: renaming or removing one a suite references makes that suite silently stop asserting
 * what it claims to.
 */
internal object VrpPaymentTestTags {
    const val LOADING_SKELETON = "vrpPayment:loadingSkeleton"
    const val AMOUNT_PAGE = "vrpPayment:amountPage"
    const val AMOUNT_FIELD = "vrpPayment:amountField"
    const val AMOUNT_ERROR = "vrpPayment:amountError"
    const val PER_PAYMENT_LIMIT = "vrpPayment:perPaymentLimit"
    const val REMAINING = "vrpPayment:remaining"
    const val REMAINING_NOTE = "vrpPayment:remainingNote"
    const val CHECK_FUNDS_BUTTON = "vrpPayment:checkFundsButton"
    const val FUNDS_WARNING = "vrpPayment:fundsWarning"
    const val CONTINUE_BUTTON = "vrpPayment:continueButton"

    const val REVIEW_PAGE = "vrpPayment:reviewPage"
    const val REVIEW_AMOUNT = "vrpPayment:reviewAmount"
    const val REVIEW_REMAINING_AFTER = "vrpPayment:reviewRemainingAfter"
    const val CONFIRM_BUTTON = "vrpPayment:confirmButton"
    const val BACK_BUTTON = "vrpPayment:backButton"

    const val SENT_STATE = "vrpPayment:sentState"
    const val REFRESH_BUTTON = "vrpPayment:refreshButton"
    const val DONE_BUTTON = "vrpPayment:doneButton"
    const val FAILED_STATE = "vrpPayment:failedState"
    const val FAILED_BODY = "vrpPayment:failedBody"
    const val SUPPORT_REFERENCE = "vrpPayment:supportReference"
    const val RETRY_SUBMISSION_BUTTON = "vrpPayment:retrySubmissionButton"

    const val UNUSABLE_STATE = "vrpPayment:unusableState"
    const val ERROR_STATE = "vrpPayment:errorState"
    const val RETRY_BUTTON = "vrpPayment:retryButton"
}
