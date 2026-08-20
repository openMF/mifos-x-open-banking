/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.paymentsschedulepayment

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The recent scheduled payments under the form, and the full list behind "See all".
 *
 * A payment booked for a future date is scheduled, not sent — the wording is what these guard.
 */
@OptIn(ExperimentalTestApi::class)
class SchedulePaymentHistoryScreenUiTest {

    @Test
    fun theFormShowsTheRecentScheduledPayments() = runComposeUiTest {
        setContent {
            SchedulePaymentScreenContent(
                state = SchedulePaymentFixtures.formState(
                    recentPayments = SchedulePaymentFixtures.paymentHistory(),
                ),
                onAction = {},
                onNavigateToConsents = {},
            )
        }

        onNodeWithTag(SchedulePaymentTestTags.HISTORY).performScrollTo().assertIsDisplayed()
        onNodeWithTag(SchedulePaymentTestTags.historyRow("19919")).assertIsDisplayed()
        onNodeWithTag(SchedulePaymentTestTags.historyRow("19920")).assertIsDisplayed()
        onNodeWithTag(SchedulePaymentTestTags.historyRow("19921")).assertIsDisplayed()
    }

    @Test
    fun theFormOmitsTheSectionEntirelyWithNoScheduledPayments() = runComposeUiTest {
        setContent {
            SchedulePaymentScreenContent(
                state = SchedulePaymentFixtures.formState(recentPayments = emptyList()),
                onAction = {},
                onNavigateToConsents = {},
            )
        }

        onNodeWithTag(SchedulePaymentTestTags.HISTORY).assertDoesNotExist()
    }

    /** The rail-aware wording: a booked payment must never read as money already sent. */
    @Test
    fun aBookedPaymentReadsAsScheduledRatherThanSent() = runComposeUiTest {
        setContent {
            SchedulePaymentScreenContent(
                state = SchedulePaymentFixtures.formState(
                    recentPayments = SchedulePaymentFixtures.paymentHistory(),
                ),
                onAction = {},
                onNavigateToConsents = {},
            )
        }

        onNodeWithText("Scheduled").performScrollTo().assertIsDisplayed()
        onNodeWithText("Sent").assertDoesNotExist()
        onNodeWithText("Sending").assertDoesNotExist()
    }

    @Test
    fun aPaymentStillBeingBookedSaysSo() = runComposeUiTest {
        setContent {
            SchedulePaymentScreenContent(
                state = SchedulePaymentFixtures.formState(
                    recentPayments = SchedulePaymentFixtures.paymentHistory(),
                ),
                onAction = {},
                onNavigateToConsents = {},
            )
        }

        onNodeWithText("Scheduling").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun tappingARowOpensThatPayment() = runComposeUiTest {
        var opened: String? = null
        setContent {
            SchedulePaymentScreenContent(
                state = SchedulePaymentFixtures.formState(
                    recentPayments = SchedulePaymentFixtures.paymentHistory(),
                ),
                onAction = {},
                onNavigateToConsents = {},
                onOpenPayment = { opened = it },
            )
        }

        onNodeWithTag(SchedulePaymentTestTags.historyRow("19920")).performScrollTo().performClick()

        assertEquals("19920", opened)
    }

    @Test
    fun seeAllIsOfferedOnlyWhenThereIsMoreToSee() = runComposeUiTest {
        setContent {
            SchedulePaymentScreenContent(
                state = SchedulePaymentFixtures.formState(
                    recentPayments = SchedulePaymentFixtures.paymentHistory(),
                ),
                onAction = {},
                onNavigateToConsents = {},
            )
        }

        onNodeWithText("See all").assertDoesNotExist()
    }

    @Test
    fun seeAllOpensTheFullList() = runComposeUiTest {
        var shownAll = false
        setContent {
            SchedulePaymentScreenContent(
                state = SchedulePaymentFixtures.formState(
                    recentPayments = SchedulePaymentFixtures.paymentHistory(),
                ).withMorePayments(),
                onAction = {},
                onNavigateToConsents = {},
                onShowAllPayments = { shownAll = true },
            )
        }

        onNodeWithText("See all").performScrollTo().performClick()

        assertEquals(true, shownAll)
    }

    @Test
    fun theFullListRendersEveryScheduledPayment() = runComposeUiTest {
        setContent {
            SchedulePaymentHistoryScreenContent(
                payments = SchedulePaymentFixtures.paymentHistory(),
                onNavigateToPayment = {},
            )
        }

        onNodeWithTag(SchedulePaymentTestTags.historyRow("19919")).assertIsDisplayed()
        onNodeWithTag(SchedulePaymentTestTags.historyRow("19920")).assertIsDisplayed()
        onNodeWithTag(SchedulePaymentTestTags.historyRow("19921")).assertIsDisplayed()
    }

    @Test
    fun theFullListSaysSoWhenThereIsNothingToShow() = runComposeUiTest {
        setContent {
            SchedulePaymentHistoryScreenContent(payments = emptyList(), onNavigateToPayment = {})
        }

        onNodeWithText("You have not scheduled any payments yet.").assertIsDisplayed()
    }
}
