/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.sendmoney

import org.mifosx.openbanking.core.model.banking.payment.ChargeBearer
import org.mifosx.openbanking.core.model.banking.payment.PaymentRail

/**
 * Stable tags the three UI suites drive this screen by. Append-only: a renamed tag silently drops
 * whichever assertions referenced it.
 */
internal object SendMoneyTestTags {
    const val SKELETON = "sendMoney:skeleton"

    const val FORM_PAGE = "sendMoney:formPage"
    const val REVIEW_PAGE = "sendMoney:reviewPage"
    const val RAIL_TOGGLE = "sendMoney:railToggle"
    const val FORM_ACTIONS = "sendMoney:formActions"
    const val FORM_TRUST_NOTE = "sendMoney:formTrustNote"

    /** The always-present collapsed summary. Tapping it toggles [DEBTOR_LIST] in and out. */
    const val PAYER_PICKER = "sendMoney:payerPicker"

    /** The expanded half of the picker. Absent entirely while it is collapsed. */
    const val DEBTOR_LIST = "sendMoney:debtorList"
    const val PAYER_BANK_CHOICE = "sendMoney:payerBankChoice"
    const val AMOUNT_CARD = "sendMoney:amountCard"
    const val AMOUNT_BALANCE = "sendMoney:amountBalance"
    const val NON_GBP_NOTICE = "sendMoney:nonGbpNotice"
    const val CREDITOR_LIST = "sendMoney:creditorList"

    /** The payee read is still in flight, and the row is showing placeholders in its place. */
    const val PAYEES_LOADING = "sendMoney:payeesLoading"
    const val MANUAL_ENTRY_BUTTON = "sendMoney:manualEntryButton"
    const val MANUAL_SORT_CODE = "sendMoney:manualSortCode"
    const val MANUAL_ACCOUNT_NUMBER = "sendMoney:manualAccountNumber"
    const val MANUAL_NAME = "sendMoney:manualName"
    const val MANUAL_CONFIRM = "sendMoney:manualConfirm"

    const val AMOUNT_FIELD = "sendMoney:amountField"
    const val AMOUNT_ERROR = "sendMoney:amountError"
    const val REFERENCE_FIELD = "sendMoney:referenceField"
    const val MANUAL_IBAN = "sendMoney:manualIban"
    const val MANUAL_IBAN_ERROR = "sendMoney:manualIbanError"
    const val CHARGE_BEARER_PICKER = "sendMoney:chargeBearerPicker"

    /**
     * The currency, in the box at the LEADING edge of the amount row. International only.
     *
     * The form's only currency control. `CURRENCY_MISMATCH` and the separate transfer-currency
     * picker are gone with the second selector — a tag with nothing behind it would send the next
     * reader looking for a field that no longer exists.
     */
    const val INSTRUCTED_CURRENCY_PICKER = "sendMoney:instructedCurrencyPicker"

    /**
     * The domestic rail's `£`, in the same box and at the same place, with nothing to open.
     *
     * Its own tag rather than sharing [INSTRUCTED_CURRENCY_PICKER], because the two are the same
     * shape and the difference between them is exactly what wants asserting: the domestic rail must
     * name its currency AND must not offer a menu, and one tag could not say both.
     */
    const val STATIC_CURRENCY_BOX = "sendMoney:staticCurrencyBox"
    const val REVIEW_BUTTON = "sendMoney:reviewButton"

    const val REVIEW_LEAD = "sendMoney:reviewLead"
    const val REVIEW_SUMMARY = "sendMoney:reviewSummary"
    const val REVIEW_FROM = "sendMoney:reviewFrom"
    const val REVIEW_TO = "sendMoney:reviewTo"
    const val REVIEW_AMOUNT = "sendMoney:reviewAmount"
    const val REVIEW_REFERENCE = "sendMoney:reviewReference"
    const val CONFIRM_BUTTON = "sendMoney:confirmButton"
    const val CANCEL_BUTTON = "sendMoney:cancelButton"
    const val REVIEW_HERO = "sendMoney:reviewHero"
    const val REVIEW_PAYEE_CHIP = "sendMoney:reviewPayeeChip"
    const val REVIEW_SENT_VIA = "sendMoney:reviewSentVia"
    const val REVIEW_CHARGE_BEARER = "sendMoney:reviewChargeBearer"
    const val REVIEW_TOTAL = "sendMoney:reviewTotal"
    const val REVIEW_AUTH_NOTICE = "sendMoney:reviewAuthNotice"
    const val EDIT_PAYMENT_BUTTON = "sendMoney:editPaymentButton"

    const val SUBMITTING_INDICATOR = "sendMoney:submittingIndicator"
    const val SUBMITTING_AMOUNT = "sendMoney:submittingAmount"
    const val SUBMITTING_LOCK_NOTE = "sendMoney:submittingLockNote"

    const val SUCCESS_STATE = "sendMoney:successState"
    const val SUCCESS_AMOUNT = "sendMoney:successAmount"
    const val SUCCESS_STATUS_CHIP = "sendMoney:successStatusChip"
    const val SUCCESS_PAYMENT_ID = "sendMoney:successPaymentId"
    const val VIEW_PAYMENT_STATUS_BUTTON = "sendMoney:viewPaymentStatusButton"

    const val ERROR_STATE = "sendMoney:errorState"
    const val ERROR_SUPPORT_REFERENCE = "sendMoney:errorSupportReference"
    const val RETRY_BUTTON = "sendMoney:retryButton"
    const val REAUTHORISE_BUTTON = "sendMoney:reauthoriseButton"
    const val VIEW_CONSENTS_BUTTON = "sendMoney:viewConsentsButton"
    const val EDIT_AMOUNT_BUTTON = "sendMoney:editAmountButton"
    const val CHANGE_PAYER_BUTTON = "sendMoney:changePayerButton"

    /** One rail option in the toggle. */
    fun railOption(rail: PaymentRail): String = "sendMoney:rail:" + when (rail) {
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
    fun instructedCurrencyOption(code: String): String = "sendMoney:instructedCurrency:$code"

    /** One charge-bearer option. */
    fun chargeBearerOption(bearer: ChargeBearer): String = "sendMoney:chargeBearer:" + bearer.name

    /** One debtor row, keyed by OBIE `AccountId`. */
    fun debtorRow(accountId: String): String = "sendMoney:debtorRow:$accountId"

    /** One payee row, keyed by OBIE `BeneficiaryId`. */
    fun creditorRow(beneficiaryId: String): String = "sendMoney:creditorRow:$beneficiaryId"
}
