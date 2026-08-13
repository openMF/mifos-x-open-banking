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
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The two date windows, exercised at every boundary against a fixed day.
 *
 * Pure and clock-free by design: boundary arithmetic that can only be reached through a dialog is
 * boundary arithmetic that does not get tested.
 *
 * [today] is a Wednesday, chosen so that T+1 is a weekday and both weekend days are reachable inside
 * a week — the weekend cases are the ones where this product deliberately diverges from its scheduled
 * sibling, and they need to be reachable to assert.
 */
class StandingOrderDateRulesTest {

    private val today = LocalDate(2026, 8, 12)

    private fun LocalDate.plusDays(days: Int): LocalDate = plus(days, DateTimeUnit.DAY)

    private fun firstSelectable(date: LocalDate): Boolean = isSelectableFirstPaymentDate(date, today)

    private fun finalSelectable(date: LocalDate, first: LocalDate? = today.plusDays(7)): Boolean =
        isSelectableFinalPaymentDate(date, today, first)

    // region — the first payment date

    @Test
    fun todayIsNotSelectableAsAFirstPayment() {
        assertFalse(firstSelectable(today))
    }

    @Test
    fun yesterdayIsNotSelectable() {
        assertFalse(firstSelectable(today.plusDays(-1)))
    }

    @Test
    fun tomorrowIsSelectable() {
        assertTrue(firstSelectable(today.plusDays(1)))
    }

    @Test
    fun theThreeHundredAndSixtyFifthDayAheadIsSelectable() {
        assertTrue(firstSelectable(today.plusDays(MAX_FIRST_PAYMENT_DAYS_AHEAD)))
    }

    @Test
    fun theThreeHundredAndSixtySixthDayAheadIsNotSelectable() {
        assertFalse(firstSelectable(today.plusDays(MAX_FIRST_PAYMENT_DAYS_AHEAD + 1)))
    }

    /**
     * The deliberate divergence from the scheduled module, and the observed half of these rules.
     *
     * Weekend first-payment dates were probed on both rails and accepted, every one echoed back
     * unchanged rather than shifted to the next working day.
     */
    @Test
    fun aSaturdayIsSelectable() {
        assertTrue(firstSelectable(LocalDate(2026, 8, 15)))
    }

    @Test
    fun aSundayIsSelectable() {
        assertTrue(firstSelectable(LocalDate(2026, 8, 16)))
    }

    /** No bank-holiday calendar. A midweek holiday stays selectable, as on the scheduled rails. */
    @Test
    fun aMidweekBankHolidayRemainsSelectable() {
        assertTrue(firstSelectable(LocalDate(2026, 12, 28)))
    }

    // endregion

    // region — the final payment date

    @Test
    fun aFinalDateAfterTheFirstIsSelectable() {
        assertTrue(finalSelectable(today.plusDays(30)))
    }

    /** The clause the bank names last and enforces first. */
    @Test
    fun aFinalDateEqualToTheFirstIsNotSelectable() {
        assertFalse(finalSelectable(today.plusDays(7)))
    }

    @Test
    fun aFinalDateBeforeTheFirstIsNotSelectable() {
        assertFalse(finalSelectable(today.plusDays(3)))
    }

    @Test
    fun todayAndTomorrowAreNotSelectableAsAFinalPayment() {
        assertFalse(finalSelectable(today, first = today.plusDays(-30)))
        assertFalse(finalSelectable(today.plusDays(1), first = today.plusDays(-30)))
    }

    @Test
    fun theDayAfterTomorrowIsSelectableWhenTheMandateStartedEarlier() {
        assertTrue(finalSelectable(today.plusDays(2), first = today.plusDays(-30)))
    }

    @Test
    fun twelveMonthsAheadIsSelectable() {
        assertTrue(finalSelectable(today.plus(MAX_FINAL_PAYMENT_MONTHS_AHEAD, DateTimeUnit.MONTH)))
    }

    @Test
    fun aDayBeyondTwelveMonthsIsNotSelectable() {
        val justOver = today.plus(MAX_FINAL_PAYMENT_MONTHS_AHEAD, DateTimeUnit.MONTH).plusDays(1)

        assertFalse(finalSelectable(justOver))
    }

    /**
     * Nothing is selectable before a start date is chosen.
     *
     * The clause that matters most — after the first payment — cannot be checked without one, and
     * offering a date anyway would mean accepting a pair the bank will refuse.
     */
    @Test
    fun noFinalDateIsSelectableUntilTheFirstIsChosen() {
        assertFalse(finalSelectable(today.plusDays(30), first = null))
    }

    // endregion

    // region — the shared entry point and the picker window

    @Test
    fun theRoleSelectsTheRule() {
        val date = today.plusDays(7)

        assertTrue(isSelectableDate(date, today, StandingOrderDateRole.First, firstPaymentDate = null))
        assertFalse(isSelectableDate(date, today, StandingOrderDateRole.Final, firstPaymentDate = null))
    }

    @Test
    fun theFirstDatePickerSpansOnlyItsWindow() {
        assertEquals(2026..2027, selectableYears(today, StandingOrderDateRole.First))
    }

    @Test
    fun theFinalDatePickerSpansOnlyItsWindow() {
        assertEquals(2026..2027, selectableYears(today, StandingOrderDateRole.Final))
    }

    @Test
    fun millisAreReadAsUtcDates() {
        val date = LocalDate(2026, 8, 20)

        assertEquals(date, utcDateOf(epochMillisOf(date)))
    }

    // endregion
}
