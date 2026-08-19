/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.vrpsetup

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The rules the bank enforces, checked against a fixed day.
 *
 * The end-date floor is the one the bank misreports: it refuses a date under two days ahead with
 * "Invalid date format", which points at the one thing that is not wrong.
 */
class VrpSetupRulesTest {

    private val today = LocalDate(2026, 8, 19)

    @Test
    fun theEarliestEndDateIsTwoDaysAhead() {
        assertEquals(LocalDate(2026, 8, 21), earliestEndDate(today))
    }

    @Test
    fun todayAndTomorrowAreRefused() {
        assertFalse(isSelectableEndDate(today, today))
        assertFalse(isSelectableEndDate(LocalDate(2026, 8, 20), today))
    }

    @Test
    fun theDayAfterTomorrowOnwardsIsAccepted() {
        assertTrue(isSelectableEndDate(LocalDate(2026, 8, 21), today))
        assertTrue(isSelectableEndDate(LocalDate(2027, 3, 18), today))
    }

    /**
     * The bank drops a validity date's offset without converting it, so the calendar date written is
     * the one it uses. Reading the device zone would offer a date it treats as already past.
     */
    @Test
    fun todayIsReadInUtcNotTheDeviceZone() {
        val lateInLondon = Instant.parse("2026-08-19T23:30:00Z")

        assertEquals(LocalDate(2026, 8, 19), todayUtc(lateInLondon))
    }

    /** The picker hands out UTC millis; reading them east of UTC lands on the following day. */
    @Test
    fun pickerMillisAreReadInUtc() {
        assertEquals(LocalDate(2026, 8, 21), utcDateOf(1_787_270_400_000L))
    }

    @Test
    fun aDateSurvivesTheRoundTripThroughMillis() {
        val date = LocalDate(2026, 8, 21)

        assertEquals(date, utcDateOf(epochMillisOf(date)))
    }

    @Test
    fun theCalendarOffersTenYearsAhead() {
        assertEquals(2026..2036, selectableEndYears(today))
    }

    @Test
    fun aBlankAmountIsMissing() {
        assertEquals(AmountProblem.Missing, checkAmount(""))
    }

    @Test
    fun anUnreadableAmountIsRejected() {
        assertEquals(AmountProblem.NotANumber, checkAmount("ten pounds"))
    }

    @Test
    fun anAmountBelowAPennyIsRejected() {
        assertEquals(AmountProblem.BelowMinimum, checkAmount("0.00"))
    }

    @Test
    fun aPennyIsAccepted() {
        assertNull(checkAmount("0.01"))
    }

    @Test
    fun thePerPaymentCeilingMustBeBelowThePeriodicOne() {
        assertEquals(AmountProblem.NotBelowPeriodic, checkOrdering("500.00", "500.00"))
        assertEquals(AmountProblem.NotBelowPeriodic, checkOrdering("600.00", "500.00"))
        assertNull(checkOrdering("200.00", "500.00"))
    }

    /** Either half being unreadable is that field's own problem, not an ordering breach. */
    @Test
    fun orderingIsNotJudgedWhenEitherAmountIsUnreadable() {
        assertNull(checkOrdering("", "500.00"))
        assertNull(checkOrdering("200.00", ""))
    }

    @Test
    fun aSortCodeMustBeSixDigits() {
        assertEquals(PayeeProblem.SortCodeIncomplete, checkSortCode("4047"))
        assertNull(checkSortCode("404784"))
        assertNull(checkSortCode("40-47-84"))
    }

    @Test
    fun anAccountNumberMustBeEightDigits() {
        assertEquals(PayeeProblem.AccountNumberIncomplete, checkAccountNumber("1234"))
        assertNull(checkAccountNumber("12345678"))
    }

    @Test
    fun aPayeeNeedsAName() {
        assertEquals(PayeeProblem.NameMissing, checkPayeeName("   "))
        assertNull(checkPayeeName("Sarah Chen"))
    }

    @Test
    fun payingTheAccountYouArePayingFromIsRefused() {
        assertEquals(
            PayeeProblem.SameAsPayer,
            checkPayeeDiffersFromPayer("40478412345678", "40-47-84 12345678"),
        )
        assertNull(checkPayeeDiffersFromPayer("40478412345678", "80200110204021"))
    }

    /** A payer chosen at the bank is unknown here, so the breach cannot be judged. */
    @Test
    fun sameAsPayerIsNotJudgedWhenThePayerWillBeChosenAtTheBank() {
        assertNull(checkPayeeDiffersFromPayer("40478412345678", null))
    }

    @Test
    fun anIdentificationIsTheSortCodeAndAccountNumberWithoutSeparators() {
        assertEquals("40478412345678", combinedIdentification("40-47-84", "12345678"))
    }
}
