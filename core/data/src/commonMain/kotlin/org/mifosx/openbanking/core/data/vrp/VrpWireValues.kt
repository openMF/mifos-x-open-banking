/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.data.vrp

import kotlinx.datetime.LocalDate
import org.mifosx.openbanking.core.model.vrp.Money
import org.mifosx.openbanking.core.model.vrp.PeriodType
import kotlin.time.Instant

/** The payment type every consent this app creates is for. */
internal const val VRP_TYPE_SWEEPING = "UK.OBIE.VRPType.Sweeping"

/** The only authentication method a VRP consent accepts. */
internal const val SCA_NOT_REQUIRED = "UK.OBIE.SCANotRequired"

/** The only account identification scheme a VRP request may use. */
internal const val SCHEME_SORT_CODE_ACCOUNT_NUMBER = "UK.OBIE.SortCodeAccountNumber"

/** Limit periods run from the consent's creation rather than a calendar boundary. */
internal const val PERIOD_ALIGNMENT_CONSENT = "Consent"

/** The customer is not expected to be present when a payment is made. */
internal const val INTERACTION_OFF_SESSION = "OffSession"

/** The currency a VRP is denominated in. */
internal const val CURRENCY_GBP = "GBP"

private const val MINOR_UNITS_PER_MAJOR = 100L
private const val MINOR_UNIT_DIGITS = 2
private const val WIRE_DATE_LENGTH = 10

/** Renders minor units as the decimal string the wire carries: `1000` becomes `10.00`. */
internal fun Money.toWireAmount(): String {
    val major = minorUnits / MINOR_UNITS_PER_MAJOR
    val minor = minorUnits % MINOR_UNITS_PER_MAJOR
    return "$major.${minor.toString().padStart(MINOR_UNIT_DIGITS, '0')}"
}

/** Reads a wire amount and currency, or null when either is absent or malformed. */
@Suppress("ReturnCount")
internal fun wireMoney(amount: String?, currency: String?): Money? {
    if (amount == null || currency == null) return null
    val parts = amount.trim().split('.')
    if (parts.size > 2) return null
    val major = parts[0].toLongOrNull() ?: return null
    val minor = parts.getOrNull(1).orEmpty()
        .padEnd(MINOR_UNIT_DIGITS, '0')
        .take(MINOR_UNIT_DIGITS)
    if (minor.isEmpty() || minor.any { !it.isDigit() }) return null
    return Money(
        minorUnits = major * MINOR_UNITS_PER_MAJOR + minor.toLong(),
        currency = currency,
    )
}

/** The wire value for a period. [PeriodType.HalfYear] is sent as `Half-year`. */
internal fun PeriodType.toWireValue(): String = when (this) {
    PeriodType.Day -> "Day"
    PeriodType.Week -> "Week"
    PeriodType.Fortnight -> "Fortnight"
    PeriodType.Month -> "Month"
    PeriodType.HalfYear -> "Half-year"
    PeriodType.Year -> "Year"
}

/** Reads a wire period, or null when unrecognised. */
internal fun periodTypeFromWire(raw: String?): PeriodType? =
    PeriodType.entries.firstOrNull { it.toWireValue() == raw?.trim() }

/**
 * Renders a date as the date-time the wire requires: `2027-12-31T00:00:00+00:00`.
 *
 * An offset is mandatory. The bank keeps the date as written and discards the time.
 */
internal fun LocalDate.toWireDateTime(): String = "${this}T00:00:00+00:00"

/** Reads the date out of a wire date-time, or null when absent or malformed. */
internal fun wireDate(raw: String?): LocalDate? {
    val value = raw?.trim().orEmpty()
    if (value.length < WIRE_DATE_LENGTH) return null
    return runCatching { LocalDate.parse(value.take(WIRE_DATE_LENGTH)) }.getOrNull()
}

/** Reads a wire date-time as an instant, or null when absent or malformed. */
internal fun wireInstant(raw: String?): Instant? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null
    return runCatching { Instant.parse(value) }.getOrNull()
}
