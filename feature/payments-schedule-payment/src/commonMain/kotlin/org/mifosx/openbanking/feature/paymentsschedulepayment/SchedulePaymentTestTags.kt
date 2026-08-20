/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.paymentsschedulepayment

import org.mifosx.openbanking.core.model.banking.payment.ChargeBearer
import org.mifosx.openbanking.core.model.banking.payment.PaymentRail

/**
 * Stable tags the three UI suites drive this screen by. Append-only: a renamed tag silently drops
 * whichever assertions referenced it.
 */
internal object SchedulePaymentTestTags {
    const val SKELETON = "schedulePayment:skeleton"

    const val FORM_PAGE = "schedulePayment:formPage"
    const val REVIEW_PAGE = "schedulePayment:reviewPage"
    const val RAIL_TOGGLE = "schedulePayment:railToggle"
    const val FORM_ACTIONS = "schedulePayment:formActions"
    const val FORM_TRUST_NOTE = "schedulePayment:formTrustNote"

    /** The always-present collapsed summary. Tapping it toggles [DEBTOR_LIST] in and out. */
    const val PAYER_PICKER = "schedulePayment:payerPicker"

    /** The expanded half of the picker. Absent entirely while it is collapsed. */
    const val DEBTOR_LIST = "schedulePayment:debtorList"

    /** The picker's collapsed row, which opens and closes it. */
    const val PAYER_HEADER = "schedulePayment:payerHeader"

    /** The recent scheduled payments under the form. */
    const val HISTORY = "schedulePayment:history"

    /** The full list of scheduled payments. */
    const val HISTORY_SCREEN = "schedulePayment:historyScreen"
    const val PAYER_BANK_CHOICE = "schedulePayment:payerBankChoice"
    const val AMOUNT_CARD = "schedulePayment:amountCard"
    const val AMOUNT_BALANCE = "schedulePayment:amountBalance"
    const val NON_GBP_NOTICE = "schedulePayment:nonGbpNotice"
    const val CREDITOR_LIST = "schedulePayment:creditorList"

    /** The payee read is still in flight, and the row is showing placeholders in its place. */
    const val PAYEES_LOADING = "schedulePayment:payeesLoading"
    const val MANUAL_ENTRY_BUTTON = "schedulePayment:manualEntryButton"
    const val MANUAL_SORT_CODE = "schedulePayment:manualSortCode"
    const val MANUAL_ACCOUNT_NUMBER = "schedulePayment:manualAccountNumber"
    const val MANUAL_NAME = "schedulePayment:manualName"
    const val MANUAL_CONFIRM = "schedulePayment:manualConfirm"

    const val AMOUNT_FIELD = "schedulePayment:amountField"
    const val AMOUNT_ERROR = "schedulePayment:amountError"
    const val REFERENCE_FIELD = "schedulePayment:referenceField"
    const val MANUAL_IBAN = "schedulePayment:manualIban"
    const val MANUAL_IBAN_ERROR = "schedulePayment:manualIbanError"
    const val CHARGE_BEARER_PICKER = "schedulePayment:chargeBearerPicker"

    /**
     * The currency, in the box at the LEADING edge of the amount row. International only.
     *
     * The form's only currency control. `CURRENCY_MISMATCH` and the separate transfer-currency
     * picker are gone with the second selector — a tag with nothing behind it would send the next
     * reader looking for a field that no longer exists.
     */
    const val INSTRUCTED_CURRENCY_PICKER = "schedulePayment:instructedCurrencyPicker"

    /**
     * The domestic rail's `£`, in the same box and at the same place, with nothing to open.
     *
     * Its own tag rather than sharing [INSTRUCTED_CURRENCY_PICKER], because the two are the same
     * shape and the difference between them is exactly what wants asserting: the domestic rail must
     * name its currency AND must not offer a menu, and one tag could not say both.
     */
    const val STATIC_CURRENCY_BOX = "schedulePayment:staticCurrencyBox"
    const val REVIEW_BUTTON = "schedulePayment:reviewButton"

    const val REVIEW_LEAD = "schedulePayment:reviewLead"
    const val REVIEW_SUMMARY = "schedulePayment:reviewSummary"
    const val REVIEW_FROM = "schedulePayment:reviewFrom"
    const val REVIEW_TO = "schedulePayment:reviewTo"
    const val REVIEW_AMOUNT = "schedulePayment:reviewAmount"
    const val REVIEW_REFERENCE = "schedulePayment:reviewReference"
    const val CONFIRM_BUTTON = "schedulePayment:confirmButton"
    const val CANCEL_BUTTON = "schedulePayment:cancelButton"
    const val REVIEW_HERO = "schedulePayment:reviewHero"
    const val REVIEW_PAYEE_CHIP = "schedulePayment:reviewPayeeChip"
    const val REVIEW_CHARGE_BEARER = "schedulePayment:reviewChargeBearer"
    const val REVIEW_AUTH_NOTICE = "schedulePayment:reviewAuthNotice"
    const val EDIT_PAYMENT_BUTTON = "schedulePayment:editPaymentButton"

    const val SUBMITTING_INDICATOR = "schedulePayment:submittingIndicator"
    const val SUBMITTING_AMOUNT = "schedulePayment:submittingAmount"
    const val SUBMITTING_LOCK_NOTE = "schedulePayment:submittingLockNote"

    const val SUCCESS_STATE = "schedulePayment:successState"
    const val SUCCESS_AMOUNT = "schedulePayment:successAmount"
    const val SUCCESS_STATUS_CHIP = "schedulePayment:successStatusChip"
    const val SUCCESS_PAYMENT_ID = "schedulePayment:successPaymentId"
    const val VIEW_PAYMENT_STATUS_BUTTON = "schedulePayment:viewPaymentStatusButton"

    const val ERROR_STATE = "schedulePayment:errorState"
    const val ERROR_SUPPORT_REFERENCE = "schedulePayment:errorSupportReference"
    const val RETRY_BUTTON = "schedulePayment:retryButton"
    const val REAUTHORISE_BUTTON = "schedulePayment:reauthoriseButton"
    const val VIEW_CONSENTS_BUTTON = "schedulePayment:viewConsentsButton"
    const val EDIT_AMOUNT_BUTTON = "schedulePayment:editAmountButton"
    const val CHANGE_PAYER_BUTTON = "schedulePayment:changePayerButton"
    const val CHANGE_DATE_BUTTON = "schedulePayment:changeDateButton"

    // region — The execution date, the only control this feature adds over an immediate payment.
    const val DATE_FIELD = "schedulePayment:dateField"
    const val DATE_PICKER = "schedulePayment:datePicker"
    const val DATE_PICKER_CONFIRM = "schedulePayment:datePickerConfirm"
    const val DATE_PICKER_CANCEL = "schedulePayment:datePickerCancel"
    // endregion

    const val CHARGES_ROW = "schedulePayment:chargesRow"
    const val REVIEW_DATE_ROW = "schedulePayment:reviewDateRow"
    const val REVIEW_CHARGE_ROW = "schedulePayment:reviewChargeRow"
    const val REVIEW_SENT_AS = "schedulePayment:reviewSentAs"
    const val REVIEW_CHARGE_CAVEAT = "schedulePayment:reviewChargeCaveat"
    const val REVIEW_NOT_YET_MADE = "schedulePayment:reviewNotYetMade"
    const val ABANDON_AUTHORISATION_BUTTON = "schedulePayment:abandonAuthorisationButton"

    /** One rail option in the toggle. */
    fun railOption(rail: PaymentRail): String = "schedulePayment:rail:" + when (rail) {
        PaymentRail.Domestic -> "domestic"
        PaymentRail.International -> "international"
    }

    /**
     * One option in the currency menu, not a chip on the page.
     *
     * Renamed from `*Chip` when the chip rows became dropdowns: a tag that still said chip would
     * have sent the next reader looking for a control that is no longer there. Absent from the tree
     * entirely while the menu is shut, so every assertion on one has to open it first.
     *
     * There was a `transferCurrencyOption` beside this, for the second selector's identical
     * nineteen-code menu; it went with the selector.
     */
    fun instructedCurrencyOption(code: String): String = "schedulePayment:instructedCurrency:$code"

    /** One charge-bearer option. */
    fun chargeBearerOption(bearer: ChargeBearer): String = "schedulePayment:chargeBearer:" + bearer.name

    /** One debtor row, keyed by OBIE `AccountId`. */
    fun debtorRow(accountId: String): String = "schedulePayment:debtorRow:$accountId"

    /** One payee row, keyed by OBIE `BeneficiaryId`. */
    fun creditorRow(beneficiaryId: String): String = "schedulePayment:creditorRow:$beneficiaryId"

    /** One scheduled-payment row, keyed by the bank's payment id. */
    fun historyRow(paymentId: String): String = "schedulePayment:historyRow:$paymentId"
}
