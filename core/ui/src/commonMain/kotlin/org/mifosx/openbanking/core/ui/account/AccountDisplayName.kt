/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.ui.account

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.mifosx.openbanking.core.common.maskCardNumber
import org.mifosx.openbanking.core.ui.generated.resources.Res
import org.mifosx.openbanking.core.ui.generated.resources.core_ui_account_type_credit
import org.mifosx.openbanking.core.ui.generated.resources.core_ui_account_type_current
import org.mifosx.openbanking.core.ui.generated.resources.core_ui_account_type_global_money
import org.mifosx.openbanking.core.ui.generated.resources.core_ui_account_type_global_wallet
import org.mifosx.openbanking.core.ui.generated.resources.core_ui_account_type_other
import org.mifosx.openbanking.core.ui.generated.resources.core_ui_account_type_savings

private const val LAST_DIGITS = 4
private const val TYPE_NUMBER_SEPARATOR = " ·· "
private const val TYPE_CARD_SEPARATOR = " "
private const val MASK_CHARACTER = "X"
private val CREDIT_CARD_SUBTYPES = setOf("creditcard", "credit", "card", "ccrd")

/**
 * The account's number with every character but the last four masked, e.g. `XXXX3349`.
 *
 * A credit card flattens a full PAN rather than a UK account number, so it takes [maskCardNumber]'s
 * grouped form instead. Blank when the bank supplied no identifier at all.
 */
fun maskedAccountNumber(
    accountSubType: String,
    accountNumber: String,
    rawIdentification: String = "",
): String {
    if (isCreditCardSubType(accountSubType)) {
        return if (rawIdentification.isNotBlank()) maskCardNumber(rawIdentification) else ""
    }
    val digits = accountNumber.ifBlank { rawIdentification }.filter { it.isLetterOrDigit() }
    return if (digits.length <= LAST_DIGITS) {
        digits
    } else {
        MASK_CHARACTER.repeat(digits.length - LAST_DIGITS) + digits.takeLast(LAST_DIGITS)
    }
}

/** The localized account-type label on its own, e.g. `Current account`. */
@Composable
fun accountTypeLabel(accountSubType: String): String =
    stringResource(accountTypeLabelRes(accountSubType))

/**
 * The name to show for an account.
 *
 * A bank-provided `Nickname`/`Name` is used as-is. HSBC's sandbox provides neither (its top-level
 * `Description` is free text and the nested `Account[].Name` is the account holder, not the account),
 * so when [nickname] is blank this falls back to a localized account-type label plus an identifier —
 * e.g. "Current account ·· 3349". Shared so Home, Accounts and Account-detail render the same label
 * from the same localized strings.
 *
 * @param accountNumber the flattened UK account number; [rawIdentification] (e.g. a card number or
 *   IBAN) is used for the last-four when the account number is absent, as it is for cards.
 */
@Composable
fun accountDisplayName(
    nickname: String,
    accountSubType: String,
    accountNumber: String,
    rawIdentification: String = "",
): String {
    if (nickname.isNotBlank()) return nickname
    val typeLabel = stringResource(accountTypeLabelRes(accountSubType))
    return accountFallbackLabel(typeLabel, accountSubType, accountNumber, rawIdentification)
}

/**
 * Builds the "type + identifier" fallback shown when the bank supplied no nickname.
 *
 * A credit card flattens a full PAN rather than a UK account number, so the mid-PAN [accountNumber]
 * slice a generic "type ·· last 4" would take is meaningless; for cards this shows the real masked last
 * four via [maskCardNumber] instead — e.g. "Credit card •••• 7654", matching the Accounts list. Every
 * other product keeps the "type ·· last 4" form — e.g. "Current account ·· 3349".
 *
 * Non-composable so it is unit-testable on the JVM without a Compose runtime.
 */
internal fun accountFallbackLabel(
    typeLabel: String,
    accountSubType: String,
    accountNumber: String,
    rawIdentification: String,
): String {
    if (isCreditCardSubType(accountSubType)) {
        return if (rawIdentification.isNotBlank()) {
            "$typeLabel$TYPE_CARD_SEPARATOR${maskCardNumber(rawIdentification)}"
        } else {
            typeLabel
        }
    }
    val lastDigits = accountNumber.ifBlank { rawIdentification }.takeLast(LAST_DIGITS)
    return if (lastDigits.isNotBlank()) "$typeLabel$TYPE_NUMBER_SEPARATOR$lastDigits" else typeLabel
}

/** True for the OBIE credit-card subtype tokens the label resolver treats as a credit card. */
internal fun isCreditCardSubType(accountSubType: String): Boolean =
    accountSubType.lowercase() in CREDIT_CARD_SUBTYPES

/**
 * Maps an OBIE `AccountSubType` to its display label, accepting the enum case-insensitively and the
 * common ISO-20022 cash-account codes (`CACC`, `SVGS`, `CCRD`) so it resolves whether the bank
 * populates `AccountSubType` or only `AccountTypeCode`.
 */
private fun accountTypeLabelRes(accountSubType: String): StringResource = when (accountSubType.lowercase()) {
    "currentaccount", "current", "cacc" -> Res.string.core_ui_account_type_current
    "savings", "svgs" -> Res.string.core_ui_account_type_savings
    "creditcard", "credit", "card", "ccrd" -> Res.string.core_ui_account_type_credit
    "globalmoney" -> Res.string.core_ui_account_type_global_money
    "globalwallet" -> Res.string.core_ui_account_type_global_wallet
    else -> Res.string.core_ui_account_type_other
}
