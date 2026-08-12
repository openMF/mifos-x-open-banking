/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.paymentsschedulepayment.ui

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.mifosx.openbanking.core.model.banking.payment.PaymentRail
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The furthest ahead either rail will accept, measured in days from today.
 *
 * Proven at both edges rather than read off a specification: `T+365` staged `201` on the domestic and
 * the international rail, and `T+366` was refused `U002` on the domestic one. The bound is inclusive
 * because the accepted probe sat exactly on it.
 *
 * This figure is HSBC Personal and first direct only. Business, Kinetic and HSBCnet are documented at
 * 45 days, so a future multi-brand build cannot treat this as a constant of the API.
 */
internal const val MAX_EXECUTION_DAYS_AHEAD = 365

/**
 * Today, as the bank reckons it.
 *
 * **UTC, not the device's zone.** The execution date leaves as a date-only value at UTC midnight, so
 * the window it is checked against has to be measured in the same zone or the two disagree for part of
 * every day. `feature/transactions` converts picker millis with `currentSystemDefault()`; that is a
 * precedent to diverge from rather than copy — east of UTC it shifts the calendar day, which here
 * would offer a date the bank refuses `U003` as already past.
 */
internal fun todayUtc(clock: Clock): LocalDate = clock.now().toLocalDateTime(TimeZone.UTC).date

/** The same conversion for a picker's UTC-millis callback. */
internal fun utcDateOf(epochMillis: Long): LocalDate =
    Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.UTC).date

/**
 * The inverse, for seeding a picker with a date already chosen.
 *
 * UTC again, and for the same reason: round-tripping a date through a different zone than the one it
 * came back in is how a picker ends up highlighting the day either side of the one that was set.
 */
internal fun epochMillisOf(date: LocalDate): Long =
    date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

/**
 * Whether the bank will accept [date] as an execution date on [rail].
 *
 * Pure and Compose-free so the boundaries can be tested against a fixed [today] rather than against
 * whatever day the suite happens to run on.
 *
 * Three clauses, each earned:
 *  - **Strictly after today.** `T+0` is refused `U003` — "a date in the past" — so the bank counts
 *    today as past. A picker that offered it would produce a form that can only fail at staging.
 *  - **At most [MAX_EXECUTION_DAYS_AHEAD] ahead**, inclusive.
 *  - **Weekdays only on the international rail.** This one is *documented, not observed*: no weekend
 *    probe was ever run against either scheduled rail, and the corpus's only Saturday capture is a
 *    domestic `T+30` that staged `201` by accident. The Guide states a working-day rule for
 *    international, and the same Guide is wrong elsewhere in this corpus — so this clause refuses
 *    dates the bank may well accept. It is the conservative direction of the two: a refused Saturday
 *    costs the customer a different date, an accepted one that the bank then rejects costs them a
 *    completed form.
 *
 * Mid-week bank holidays are deliberately **not** excluded. No holiday calendar is consulted anywhere
 * in this app, and half a rule — weekends but not holidays — would be a promise the app cannot keep.
 */
internal fun isSelectableExecutionDate(
    date: LocalDate,
    today: LocalDate,
    rail: PaymentRail,
): Boolean = date > today &&
    date <= today.plus(MAX_EXECUTION_DAYS_AHEAD, DateTimeUnit.DAY) &&
    (rail == PaymentRail.Domestic || !date.isWeekend())

private fun LocalDate.isWeekend(): Boolean =
    dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY

/**
 * The years the window spans, so the picker does not offer one where every day is disabled.
 *
 * Material3's `SelectableDates` asks about a year before it asks about the days in it, and the
 * default answer is "yes" for every year it can render. Without narrowing, the picker lets the
 * customer page back to 1900 and forward to 2100 through months that are entirely grey.
 */
internal fun selectableExecutionYears(today: LocalDate): IntRange =
    today.year..today.plus(MAX_EXECUTION_DAYS_AHEAD, DateTimeUnit.DAY).year
