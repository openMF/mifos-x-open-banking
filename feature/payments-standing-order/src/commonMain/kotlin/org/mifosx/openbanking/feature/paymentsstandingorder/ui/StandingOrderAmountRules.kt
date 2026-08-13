/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.paymentsstandingorder.ui

import org.mifosx.openbanking.core.common.parseMinorUnits

/** What an amount may carry after the point. More than this is refused, never rounded. */
private const val MAX_DECIMAL_PLACES = 2

/** The decimal places typed, or `null` when there is no point at all. */
private fun String.decimalPlaces(): Int? =
    substringAfter('.', missingDelimiterValue = "").takeIf { '.' in this }?.length

/**
 * The typed major-unit amount as minor units, or `null` when it is not an amount at all.
 *
 * A negative figure is not rejected here — `parseMinorUnits` accepts a leading `-` — because the
 * positivity rung of [amountProblemOf] says so with a message about the amount rather than its shape.
 */
internal fun amountMinorUnits(amount: String): Long? =
    amount.takeIf { it.isNotBlank() }?.let(::parseMinorUnits)

/**
 * The validation ladder, in order: parseable, two decimal places at most, positive, within the
 * balance available to compare against.
 *
 * The amount is read in MAJOR units — `250` and `250.00` both mean £250 — and converted once, here.
 * It used to be parsed as minor units behind a field labelled "Amount in pence", so someone typing
 * 250 for £250 sent £2.50. `ScheduledPaymentDraft.amountMinorUnits` is unchanged: the wire still carries
 * pence, and [amountMinorUnits] is the only place that conversion happens.
 *
 * The decimal-places rung comes before the sign check because `parseMinorUnits` truncates rather
 * than refusing — `250.999` would otherwise become £250.99, an amount nobody typed.
 *
 * @param comparableBalanceMinorUnits The payer's available balance **only when it is denominated in
 *   the same currency as the amount**; `null` otherwise, which skips the rung entirely. Comparing
 *   250 USD against a sterling balance is not a weaker check than none, it is a wrong one — it
 *   refuses affordable payments and passes unaffordable ones depending on which way the rate runs.
 *   The bank's funds confirmation remains the binding check either way; this rung was always
 *   advisory.
 */
internal fun amountProblemOf(
    amount: String,
    comparableBalanceMinorUnits: Long?,
): StandingOrderAmountProblem? {
    val places = amount.decimalPlaces()
    val parsed = amountMinorUnits(amount)
    return when {
        amount.isBlank() -> null
        parsed == null -> StandingOrderAmountProblem.NotANumber
        places != null && places > MAX_DECIMAL_PLACES -> StandingOrderAmountProblem.TooManyDecimals
        parsed <= 0L -> StandingOrderAmountProblem.NotPositive
        comparableBalanceMinorUnits != null && parsed > comparableBalanceMinorUnits ->
            StandingOrderAmountProblem.ExceedsAvailableBalance

        else -> null
    }
}
