/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.paymentsstandingorder

import org.mifosx.openbanking.core.model.banking.payment.ChargeBearer
import org.mifosx.openbanking.core.model.banking.payment.PaymentRail
import org.mifosx.openbanking.core.model.banking.payment.StandingOrderFrequency

/**
 * Stable tags the three UI suites drive this screen by. Append-only: a renamed tag silently drops
 * whichever assertions referenced it.
 */
internal object StandingOrderTestTags {
    const val SKELETON = "standingOrder:skeleton"

    const val FORM_PAGE = "standingOrder:formPage"
    const val REVIEW_PAGE = "standingOrder:reviewPage"
    const val RAIL_TOGGLE = "standingOrder:railToggle"
    const val FORM_ACTIONS = "standingOrder:formActions"
    const val FORM_TRUST_NOTE = "standingOrder:formTrustNote"

    /** The always-present collapsed summary. Tapping it toggles [DEBTOR_LIST] in and out. */
    const val PAYER_PICKER = "standingOrder:payerPicker"

    /** The expanded half of the picker. Absent entirely while it is collapsed. */
    const val DEBTOR_LIST = "standingOrder:debtorList"
    const val PAYER_BANK_CHOICE = "standingOrder:payerBankChoice"
    const val AMOUNT_CARD = "standingOrder:amountCard"
    const val AMOUNT_BALANCE = "standingOrder:amountBalance"
    const val PAYEE_NEEDS_PAYER = "standingOrder:payeeNeedsPayer"
    const val NON_GBP_NOTICE = "standingOrder:nonGbpNotice"
    const val CREDITOR_LIST = "standingOrder:creditorList"
    const val NO_SAVED_PAYEES = "standingOrder:noSavedPayees"

    /**
     * The payee read failed. Distinct from [NO_SAVED_PAYEES], which is the bank saying there are
     * none — asserting one where the other belongs is the defect these two tags exist to separate.
     */
    const val PAYEES_FAILED = "standingOrder:payeesFailed"

    /**
     * The payee read is still in flight — a third thing again, and the one that used to render as
     * [NO_SAVED_PAYEES]. Every assertion on this tag is worth double: that it is there while the
     * read is running, and that it is GONE on all four of the outcomes that end the read.
     */
    const val PAYEES_LOADING = "standingOrder:payeesLoading"
    const val PAYEES_RETRY_BUTTON = "standingOrder:payeesRetryButton"
    const val MANUAL_ENTRY_BUTTON = "standingOrder:manualEntryButton"
    const val MANUAL_SORT_CODE = "standingOrder:manualSortCode"
    const val MANUAL_ACCOUNT_NUMBER = "standingOrder:manualAccountNumber"
    const val MANUAL_NAME = "standingOrder:manualName"
    const val MANUAL_CONFIRM = "standingOrder:manualConfirm"

    const val AMOUNT_FIELD = "standingOrder:amountField"
    const val AMOUNT_ERROR = "standingOrder:amountError"
    const val REFERENCE_FIELD = "standingOrder:referenceField"
    const val MANUAL_IBAN = "standingOrder:manualIban"
    const val MANUAL_IBAN_ERROR = "standingOrder:manualIbanError"
    const val CHARGE_BEARER_PICKER = "standingOrder:chargeBearerPicker"

    /**
     * The currency, in the box at the LEADING edge of the amount row. International only.
     *
     * The form's only currency control. `CURRENCY_MISMATCH` and the separate transfer-currency
     * picker are gone with the second selector — a tag with nothing behind it would send the next
     * reader looking for a field that no longer exists.
     */
    const val INSTRUCTED_CURRENCY_PICKER = "standingOrder:instructedCurrencyPicker"

    /**
     * The domestic rail's `£`, in the same box and at the same place, with nothing to open.
     *
     * Its own tag rather than sharing [INSTRUCTED_CURRENCY_PICKER], because the two are the same
     * shape and the difference between them is exactly what wants asserting: the domestic rail must
     * name its currency AND must not offer a menu, and one tag could not say both.
     */
    const val STATIC_CURRENCY_BOX = "standingOrder:staticCurrencyBox"
    const val REVIEW_BUTTON = "standingOrder:reviewButton"

    const val REVIEW_LEAD = "standingOrder:reviewLead"
    const val REVIEW_SUMMARY = "standingOrder:reviewSummary"
    const val REVIEW_FROM = "standingOrder:reviewFrom"
    const val REVIEW_TO = "standingOrder:reviewTo"
    const val REVIEW_AMOUNT = "standingOrder:reviewAmount"
    const val REVIEW_REFERENCE = "standingOrder:reviewReference"
    const val CONFIRM_BUTTON = "standingOrder:confirmButton"
    const val CANCEL_BUTTON = "standingOrder:cancelButton"
    const val REVIEW_HERO = "standingOrder:reviewHero"
    const val REVIEW_PAYEE_CHIP = "standingOrder:reviewPayeeChip"
    const val REVIEW_CHARGE_BEARER = "standingOrder:reviewChargeBearer"
    const val REVIEW_AUTH_NOTICE = "standingOrder:reviewAuthNotice"
    const val EDIT_PAYMENT_BUTTON = "standingOrder:editPaymentButton"

    const val SUBMITTING_INDICATOR = "standingOrder:submittingIndicator"
    const val SUBMITTING_AMOUNT = "standingOrder:submittingAmount"
    const val SUBMITTING_LOCK_NOTE = "standingOrder:submittingLockNote"

    const val SUCCESS_STATE = "standingOrder:successState"
    const val SUCCESS_AMOUNT = "standingOrder:successAmount"
    const val SUCCESS_STATUS_CHIP = "standingOrder:successStatusChip"
    const val SUCCESS_PAYMENT_ID = "standingOrder:successPaymentId"
    const val VIEW_PAYMENT_STATUS_BUTTON = "standingOrder:viewPaymentStatusButton"

    const val ERROR_STATE = "standingOrder:errorState"
    const val ERROR_SUPPORT_REFERENCE = "standingOrder:errorSupportReference"
    const val RETRY_BUTTON = "standingOrder:retryButton"
    const val REAUTHORISE_BUTTON = "standingOrder:reauthoriseButton"
    const val VIEW_CONSENTS_BUTTON = "standingOrder:viewConsentsButton"
    const val EDIT_AMOUNT_BUTTON = "standingOrder:editAmountButton"
    const val CHANGE_PAYER_BUTTON = "standingOrder:changePayerButton"
    const val CHANGE_DATE_BUTTON = "standingOrder:changeDateButton"

    // region — The execution date, the only control this feature adds over an immediate payment.
    const val FIRST_DATE_FIELD = "standingOrder:firstDateField"
    const val FINAL_DATE_FIELD = "standingOrder:finalDateField"
    const val FINAL_DATE_CLEAR = "standingOrder:finalDateClear"
    const val FREQUENCY_FIELD = "standingOrder:frequencyField"

    /** Present AND disabled on the international rail — asserting absence would pass against a hidden field. */
    const val RECURRING_AMOUNT_FIELD = "standingOrder:recurringAmountField"
    const val FINAL_AMOUNT_FIELD = "standingOrder:finalAmountField"

    /**
     * The reason lines under the three fields the international rail cannot carry.
     *
     * Tagged separately from the fields they explain: a disabled control with no reason is the
     * failure this is guarding against, and it is invisible to an assertion on the field alone.
     */
    const val RECURRING_AMOUNT_REASON = "standingOrder:recurringAmountReason"
    const val FINAL_AMOUNT_REASON = "standingOrder:finalAmountReason"
    const val REFERENCE_REASON = "standingOrder:referenceReason"
    const val IRREVERSIBLE_NOTICE = "standingOrder:irreversibleNotice"
    const val REVIEW_SCHEDULE_ROW = "standingOrder:reviewScheduleRow"
    const val DATE_PICKER = "standingOrder:datePicker"
    const val DATE_PICKER_CONFIRM = "standingOrder:datePickerConfirm"
    const val DATE_PICKER_CANCEL = "standingOrder:datePickerCancel"
    // endregion

    const val CHARGES_ROW = "standingOrder:chargesRow"
    const val REVIEW_CHARGE_ROW = "standingOrder:reviewChargeRow"
    const val REVIEW_SENT_AS = "standingOrder:reviewSentAs"
    const val REVIEW_CHARGE_CAVEAT = "standingOrder:reviewChargeCaveat"
    const val REVIEW_NOT_YET_MADE = "standingOrder:reviewNotYetMade"
    const val ABANDON_AUTHORISATION_BUTTON = "standingOrder:abandonAuthorisationButton"

    /** One rail option in the toggle. */
    fun railOption(rail: PaymentRail): String = "standingOrder:rail:" + when (rail) {
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
    fun instructedCurrencyOption(code: String): String = "standingOrder:instructedCurrency:$code"

    /** One charge-bearer option. */
    fun chargeBearerOption(bearer: ChargeBearer): String = "standingOrder:chargeBearer:" + bearer.name

    /** One debtor row, keyed by OBIE `AccountId`. */
    fun debtorRow(accountId: String): String = "standingOrder:debtorRow:$accountId"

    /** One payee row, keyed by OBIE `BeneficiaryId`. */
    fun creditorRow(beneficiaryId: String): String = "standingOrder:creditorRow:$beneficiaryId"

    fun frequencyOption(frequency: StandingOrderFrequency): String =
        "standingOrder:frequency:" + frequency.wireValue
}
