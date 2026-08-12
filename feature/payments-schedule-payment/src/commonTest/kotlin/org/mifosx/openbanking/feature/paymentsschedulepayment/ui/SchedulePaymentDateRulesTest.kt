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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.mifosx.openbanking.core.model.banking.payment.PaymentRail
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The execution-date window, asserted against a fixed today rather than the real calendar.
 *
 * Every boundary here corresponds to a capture against the sandbox, and each one is a `400` the
 * customer would otherwise meet only after filling the whole form: `T+0` and `T-1` are refused
 * `U003`, `T+366` is refused `U002`, and `T+1` and `T+365` both staged `201`.
 *
 * These run against the pure rule rather than through the picker, because a dialog renders in its own
 * window where tag-based assertions are unreliable — and because the rule is what the ViewModel
 * re-checks before staging, so it is the thing that must be right.
 */
class SchedulePaymentDateRulesTest {

    /** A Wednesday, chosen so `T+1` is a weekday and the weekend cases are reachable within a week. */
    private val today = LocalDate(2026, 8, 12)

    private fun LocalDate.plusDays(days: Int): LocalDate = plus(days, DateTimeUnit.DAY)

    private fun selectable(date: LocalDate, rail: PaymentRail = PaymentRail.Domestic): Boolean =
        isSelectableExecutionDate(date, today, rail)

    /**
     * Today is not selectable, which is the boundary most likely to be got wrong.
     *
     * "Schedule a payment for today" reads as reasonable and the bank refuses it outright — `U003`,
     * "you cannot trigger a Payment request for a date in the past". A picker that offered it would
     * produce a form that can only fail at staging.
     */
    @Test
    fun todayIsNotSelectable() {
        assertFalse(selectable(today))
    }

    @Test
    fun yesterdayIsNotSelectable() {
        assertFalse(selectable(today.plusDays(-1)))
    }

    @Test
    fun tomorrowIsSelectable() {
        assertTrue(selectable(today.plusDays(1)))
    }

    /** The far edge, proven accepted at exactly this offset on both rails. */
    @Test
    fun theThreeHundredAndSixtyFifthDayAheadIsSelectable() {
        assertTrue(selectable(today.plusDays(365)))
    }

    /** One day past it, proven refused `U002`. The bound is inclusive, so this is the first miss. */
    @Test
    fun theThreeHundredAndSixtySixthDayAheadIsNotSelectable() {
        assertFalse(selectable(today.plusDays(366)))
    }

    /**
     * The domestic rail takes weekends.
     *
     * Not an omission: the OBIE guidance states no working-day rule for domestic scheduled payments,
     * and the corpus's one Saturday probe staged `201`. Refusing them here would take away dates the
     * bank accepts.
     */
    @Test
    fun aSaturdayIsSelectableOnTheDomesticRail() {
        val saturday = LocalDate(2026, 8, 15)

        assertTrue(selectable(saturday))
    }

    @Test
    fun aSundayIsSelectableOnTheDomesticRail() {
        assertTrue(selectable(LocalDate(2026, 8, 16)))
    }

    /**
     * The international rail does not.
     *
     * This clause is documented rather than observed — no weekend was ever probed on either scheduled
     * rail — so it refuses dates the bank may well accept. That is the conservative direction: a
     * refused Saturday costs a different date, an accepted one the bank then rejects costs the whole
     * form after the customer has already authorised.
     */
    @Test
    fun aSaturdayIsNotSelectableOnTheInternationalRail() {
        assertFalse(selectable(LocalDate(2026, 8, 15), PaymentRail.International))
    }

    @Test
    fun aSundayIsNotSelectableOnTheInternationalRail() {
        assertFalse(selectable(LocalDate(2026, 8, 16), PaymentRail.International))
    }

    @Test
    fun aWeekdayIsSelectableOnBothRails() {
        val friday = LocalDate(2026, 8, 14)

        assertTrue(selectable(friday))
        assertTrue(selectable(friday, PaymentRail.International))
    }

    /**
     * A mid-week bank holiday stays selectable.
     *
     * No holiday calendar is consulted anywhere in this app. Excluding weekends but not holidays
     * would be half a rule, and the half that is missing is the one that would matter on the day.
     * Boxing Day 2026 falls on a Saturday; this uses the substitute Monday, a working day by the
     * weekday rule and a bank holiday in fact.
     */
    @Test
    fun aMidweekBankHolidayRemainsSelectable() {
        assertTrue(selectable(LocalDate(2026, 12, 28), PaymentRail.International))
    }

    /**
     * The window moves with the day.
     *
     * The same date is inside the window on one day and outside it the next — which is what the
     * ViewModel restamps for on every picker open, and re-checks for before staging.
     */
    @Test
    fun aDateValidTodayIsRefusedOnceItBecomesToday() {
        val tomorrow = today.plusDays(1)

        assertTrue(isSelectableExecutionDate(tomorrow, today, PaymentRail.Domestic))
        assertFalse(isSelectableExecutionDate(tomorrow, tomorrow, PaymentRail.Domestic))
    }

    /**
     * The picker's year range is narrowed to the years the window touches.
     *
     * Left at Material3's default it offers every year it can render, so the customer can page
     * through decades of months in which every day is disabled.
     */
    @Test
    fun theSelectableYearsSpanOnlyTheWindow() {
        assertTrue(selectableExecutionYears(today) == 2026..2027)
    }

    /** UTC, not the device zone — the same conversion the wire format uses. */
    @Test
    fun millisAreReadAsUtcDates() {
        val millis = epochMillisOf(LocalDate(2026, 8, 14))

        assertTrue(utcDateOf(millis) == LocalDate(2026, 8, 14))
    }
}
