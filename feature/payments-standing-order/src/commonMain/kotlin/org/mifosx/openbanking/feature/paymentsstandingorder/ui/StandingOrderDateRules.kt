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

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The furthest ahead a mandate may start, measured in days from today.
 *
 * **Carried over from scheduled payments rather than observed here.** The T+1 floor and T+365 ceiling
 * were established against `RequestedExecutionDateTime` on a different product; every standing-order
 * probe sent a first payment date between T+7 and T+20, so neither bound was ever tested on this
 * field. Applying them is the conservative direction — it can only refuse dates the bank might have
 * taken — but it is an assumption, and worth revisiting if a boundary probe is ever run.
 */
internal const val MAX_FIRST_PAYMENT_DAYS_AHEAD = 365

/**
 * The furthest ahead a mandate may end.
 *
 * Twelve months, and unlike the window above this one comes from the bank's own words: its `U003`
 * message states that the final transfer date "cannot be today or tomorrow, and must be within 12
 * months. Also the final transfer date cannot be less than or equal to FirstPaymentDateTime". All
 * three clauses arrive in that single message whichever was actually broken, which is why they are
 * enforced separately here — the customer has two dates and only one of them is wrong.
 */
internal const val MAX_FINAL_PAYMENT_MONTHS_AHEAD = 12

/** Which of the two dates a picker is editing. */
enum class StandingOrderDateRole { First, Final }

/** Today, as the bank reckons it. UTC, not the device's zone. */
internal fun todayUtc(clock: Clock): LocalDate = clock.now().toLocalDateTime(TimeZone.UTC).date

/** The same conversion for a picker's UTC-millis callback. */
internal fun utcDateOf(epochMillis: Long): LocalDate =
    Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.UTC).date

/** The inverse, for seeding a picker with a date already chosen. */
internal fun epochMillisOf(date: LocalDate): Long =
    date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

/**
 * Whether the bank will accept [date] as the first payment.
 *
 * Two clauses: strictly after today, and at most [MAX_FIRST_PAYMENT_DAYS_AHEAD] ahead, inclusive.
 *
 * **Weekends are selectable, on both rails.** This is the deliberate divergence from the scheduled
 * module, whose international rail refuses them — and it is the part of these rules that was actually
 * observed rather than carried over. Saturday and Sunday first-payment dates were probed on both
 * rails, all accepted, and every one echoed back unchanged rather than shifted to the next working
 * day. Refusing them here would deny a date the bank takes.
 */
internal fun isSelectableFirstPaymentDate(date: LocalDate, today: LocalDate): Boolean =
    date > today && date <= today.plus(MAX_FIRST_PAYMENT_DAYS_AHEAD, DateTimeUnit.DAY)

/**
 * Whether the bank will accept [date] as the final payment.
 *
 * Three clauses, matching the three the bank recites: strictly after [firstPaymentDate], not today or
 * tomorrow, and within twelve months of today.
 *
 * [firstPaymentDate] is null while the customer has not chosen a start. Nothing is selectable then —
 * an end date without a beginning cannot be checked against the clause that matters most, and
 * offering one would mean accepting a pair the bank will refuse.
 */
internal fun isSelectableFinalPaymentDate(
    date: LocalDate,
    today: LocalDate,
    firstPaymentDate: LocalDate?,
): Boolean {
    if (firstPaymentDate == null) return false
    return date > firstPaymentDate &&
        date > today.plus(1, DateTimeUnit.DAY) &&
        date <= today.plus(MAX_FINAL_PAYMENT_MONTHS_AHEAD, DateTimeUnit.MONTH)
}

/** Dispatches to the rule for [role], so the picker and the draft builder cannot apply different ones. */
internal fun isSelectableDate(
    date: LocalDate,
    today: LocalDate,
    role: StandingOrderDateRole,
    firstPaymentDate: LocalDate?,
): Boolean = when (role) {
    StandingOrderDateRole.First -> isSelectableFirstPaymentDate(date, today)
    StandingOrderDateRole.Final -> isSelectableFinalPaymentDate(date, today, firstPaymentDate)
}

/** The years a picker in [role] may offer, so it never opens on a month where every day is disabled. */
internal fun selectableYears(today: LocalDate, role: StandingOrderDateRole): IntRange = when (role) {
    StandingOrderDateRole.First ->
        today.year..today.plus(MAX_FIRST_PAYMENT_DAYS_AHEAD, DateTimeUnit.DAY).year

    StandingOrderDateRole.Final ->
        today.year..today.plus(MAX_FINAL_PAYMENT_MONTHS_AHEAD, DateTimeUnit.MONTH).year
}
