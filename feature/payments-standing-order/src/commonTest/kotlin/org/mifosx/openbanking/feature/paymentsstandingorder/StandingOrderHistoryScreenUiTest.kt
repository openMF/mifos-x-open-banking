/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.paymentsstandingorder

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
 * The recent standing orders under the form, and the full list behind "See all".
 *
 * The wording is what these guard. A mandate the bank has accepted is set up, not sent — the money
 * has not moved and will not for some time.
 */
@OptIn(ExperimentalTestApi::class)
class StandingOrderHistoryScreenUiTest {

    @Test
    fun theFormShowsTheRecentStandingOrders() = runComposeUiTest {
        setContent {
            StandingOrderScreenContent(
                state = StandingOrderFixtures.formState()
                    .withPayments(StandingOrderFixtures.paymentHistory()),
                onAction = {},
                onNavigateToConsents = {},
            )
        }

        onNodeWithTag(StandingOrderTestTags.HISTORY).performScrollTo().assertIsDisplayed()
        onNodeWithTag(StandingOrderTestTags.historyRow("19916")).assertIsDisplayed()
        onNodeWithTag(StandingOrderTestTags.historyRow("19917")).assertIsDisplayed()
        onNodeWithTag(StandingOrderTestTags.historyRow("19918")).assertIsDisplayed()
    }

    @Test
    fun theFormOmitsTheSectionEntirelyWithNoStandingOrders() = runComposeUiTest {
        setContent {
            StandingOrderScreenContent(
                state = StandingOrderFixtures.formState().withPayments(emptyList()),
                onAction = {},
                onNavigateToConsents = {},
            )
        }

        onNodeWithTag(StandingOrderTestTags.HISTORY).assertDoesNotExist()
    }

    /** The rail-aware wording: a settled mandate must never read as money already sent. */
    @Test
    fun anAcceptedMandateReadsAsSetUpRatherThanSent() = runComposeUiTest {
        setContent {
            StandingOrderScreenContent(
                state = StandingOrderFixtures.formState()
                    .withPayments(StandingOrderFixtures.paymentHistory()),
                onAction = {},
                onNavigateToConsents = {},
            )
        }

        onNodeWithText("Set up").performScrollTo().assertIsDisplayed()
        onNodeWithText("Sent").assertDoesNotExist()
        onNodeWithText("Sending").assertDoesNotExist()
    }

    @Test
    fun aMandateStillBeingSetUpSaysSo() = runComposeUiTest {
        setContent {
            StandingOrderScreenContent(
                state = StandingOrderFixtures.formState()
                    .withPayments(StandingOrderFixtures.paymentHistory()),
                onAction = {},
                onNavigateToConsents = {},
            )
        }

        onNodeWithText("Setting up").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun tappingARowOpensThatStandingOrder() = runComposeUiTest {
        var opened: String? = null
        setContent {
            StandingOrderScreenContent(
                state = StandingOrderFixtures.formState()
                    .withPayments(StandingOrderFixtures.paymentHistory()),
                onAction = {},
                onNavigateToConsents = {},
                onOpenPayment = { opened = it },
            )
        }

        onNodeWithTag(StandingOrderTestTags.historyRow("19917")).performScrollTo().performClick()

        assertEquals("19917", opened)
    }

    @Test
    fun seeAllIsOfferedOnlyWhenThereIsMoreToSee() = runComposeUiTest {
        setContent {
            StandingOrderScreenContent(
                state = StandingOrderFixtures.formState()
                    .withPayments(StandingOrderFixtures.paymentHistory()),
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
            StandingOrderScreenContent(
                state = StandingOrderFixtures.formState()
                    .withPayments(StandingOrderFixtures.paymentHistory())
                    .withMorePayments(),
                onAction = {},
                onNavigateToConsents = {},
                onShowAllPayments = { shownAll = true },
            )
        }

        onNodeWithText("See all").performScrollTo().performClick()

        assertEquals(true, shownAll)
    }

    @Test
    fun theFullListRendersEveryStandingOrder() = runComposeUiTest {
        setContent {
            StandingOrderHistoryScreenContent(
                payments = StandingOrderFixtures.paymentHistory(),
                onNavigateToPayment = {},
            )
        }

        onNodeWithTag(StandingOrderTestTags.historyRow("19916")).assertIsDisplayed()
        onNodeWithTag(StandingOrderTestTags.historyRow("19917")).assertIsDisplayed()
        onNodeWithTag(StandingOrderTestTags.historyRow("19918")).assertIsDisplayed()
    }

    @Test
    fun theFullListSaysSoWhenThereIsNothingToShow() = runComposeUiTest {
        setContent {
            StandingOrderHistoryScreenContent(payments = emptyList(), onNavigateToPayment = {})
        }

        onNodeWithText("You have not set up any standing orders yet.").assertIsDisplayed()
    }
}
