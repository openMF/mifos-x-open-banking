/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.vrpsetup.setup

/**
 * Node tags for VRP setup, across both phases.
 *
 * Append-only: renaming or removing one a suite references makes that suite silently stop asserting
 * what it claims to.
 */
internal object VrpSetupTestTags {
    const val LOADING_SKELETON = "vrpSetup:loadingSkeleton"
    const val FORM = "vrpSetup:form"
    const val PAYER_PICKER = "vrpSetup:payerPicker"
    const val PAYER_BANK_CHOICE = "vrpSetup:payerBankChoice"
    const val PAYER_CARD_WARNING = "vrpSetup:payerCardWarning"
    const val PAYEE_ROW = "vrpSetup:payeeRow"
    const val PAYEE_PAY_NEW = "vrpSetup:payeePayNew"
    const val PAYEE_IDENTIFICATION = "vrpSetup:payeeIdentification"
    const val PAYEE_ERROR = "vrpSetup:payeeError"
    const val NEW_PAYEE_NAME = "vrpSetup:newPayeeName"
    const val NEW_PAYEE_SORT_CODE = "vrpSetup:newPayeeSortCode"
    const val NEW_PAYEE_ACCOUNT_NUMBER = "vrpSetup:newPayeeAccountNumber"
    const val PER_PAYMENT_AMOUNT = "vrpSetup:perPaymentAmount"
    const val PER_PAYMENT_ERROR = "vrpSetup:perPaymentError"
    const val PERIODIC_AMOUNT = "vrpSetup:periodicAmount"
    const val PERIODIC_ERROR = "vrpSetup:periodicError"
    const val PERIOD_DROPDOWN = "vrpSetup:periodDropdown"
    const val VALID_TO_ROW = "vrpSetup:validToRow"
    const val VALID_TO_CLEAR = "vrpSetup:validToClear"
    const val VALID_TO_HINT = "vrpSetup:validToHint"
    const val CONTINUE_BUTTON = "vrpSetup:continueButton"

    const val REVIEW = "vrpSetup:review"
    const val REVIEW_PAYER = "vrpSetup:reviewPayer"
    const val REVIEW_PAYEE = "vrpSetup:reviewPayee"
    const val REVIEW_PER_PAYMENT = "vrpSetup:reviewPerPayment"
    const val REVIEW_PERIODIC = "vrpSetup:reviewPeriodic"
    const val REVIEW_VALIDITY = "vrpSetup:reviewValidity"
    const val REVIEW_IRREVERSIBLE = "vrpSetup:reviewIrreversible"
    const val REVIEW_CONFIRM = "vrpSetup:reviewConfirm"
    const val REVIEW_BACK = "vrpSetup:reviewBack"

    const val NO_ACCOUNTS_STATE = "vrpSetup:noAccountsState"
    const val ERROR_STATE = "vrpSetup:errorState"
    const val RETRY_BUTTON = "vrpSetup:retryButton"

    fun payeeAvatar(payeeId: String): String = "vrpSetup:payeeAvatar:$payeeId"

    fun periodOption(period: String): String = "vrpSetup:periodOption:$period"
}
