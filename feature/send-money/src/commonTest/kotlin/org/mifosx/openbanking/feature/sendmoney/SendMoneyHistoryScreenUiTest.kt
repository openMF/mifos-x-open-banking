/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.sendmoney

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
 * The recent-payments list under the form, and the full list behind "See all".
 *
 * The status words are the point of most of these: a single payment that the bank has accepted but
 * not settled must not read as sent.
 */
@OptIn(ExperimentalTestApi::class)
class SendMoneyHistoryScreenUiTest {

    @Test
    fun theFormShowsTheRecentPayments() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(
                state = SendMoneyFixtures.formState(recentPayments = SendMoneyFixtures.paymentHistory()),
                onAction = {},
                onNavigateToConsents = {},
            )
        }

        onNodeWithTag(SendMoneyTestTags.HISTORY).performScrollTo().assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.historyRow("19901")).assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.historyRow("19902")).assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.historyRow("19903")).assertIsDisplayed()
    }

    /** Nothing sent yet is not worth a notice under a form the customer came here to fill in. */
    @Test
    fun theFormOmitsTheSectionEntirelyWithNoPayments() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(
                state = SendMoneyFixtures.formState(recentPayments = emptyList()),
                onAction = {},
                onNavigateToConsents = {},
            )
        }

        onNodeWithTag(SendMoneyTestTags.HISTORY).assertDoesNotExist()
    }

    @Test
    fun aSettledPaymentReadsAsSentAndAnAcceptedOneDoesNot() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(
                state = SendMoneyFixtures.formState(recentPayments = SendMoneyFixtures.paymentHistory()),
                onAction = {},
                onNavigateToConsents = {},
            )
        }

        onNodeWithText("Sent").performScrollTo().assertIsDisplayed()
        onNodeWithText("Sending").assertIsDisplayed()
    }

    @Test
    fun aRefusedPaymentSaysSo() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(
                state = SendMoneyFixtures.formState(recentPayments = SendMoneyFixtures.paymentHistory()),
                onAction = {},
                onNavigateToConsents = {},
            )
        }

        onNodeWithText("Your bank did not accept this payment").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun tappingARowOpensThatPayment() = runComposeUiTest {
        var opened: String? = null
        setContent {
            SendMoneyScreenContent(
                state = SendMoneyFixtures.formState(recentPayments = SendMoneyFixtures.paymentHistory()),
                onAction = {},
                onNavigateToConsents = {},
                onOpenPayment = { opened = it },
            )
        }

        onNodeWithTag(SendMoneyTestTags.historyRow("19902")).performScrollTo().performClick()

        assertEquals("19902", opened)
    }

    /** "See all" appears only when the cap actually hid something. */
    @Test
    fun seeAllIsOfferedOnlyWhenThereIsMoreToSee() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(
                state = SendMoneyFixtures.formState(
                    recentPayments = SendMoneyFixtures.paymentHistory(),
                    hasMorePayments = false,
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
            SendMoneyScreenContent(
                state = SendMoneyFixtures.formState(
                    recentPayments = SendMoneyFixtures.paymentHistory(),
                    hasMorePayments = true,
                ),
                onAction = {},
                onNavigateToConsents = {},
                onShowAllPayments = { shownAll = true },
            )
        }

        onNodeWithText("See all").performScrollTo().performClick()

        assertEquals(true, shownAll)
    }

    @Test
    fun theFullListRendersEveryPayment() = runComposeUiTest {
        setContent {
            SendMoneyHistoryScreenContent(
                payments = SendMoneyFixtures.paymentHistory(),
                onNavigateToPayment = {},
            )
        }

        onNodeWithTag(SendMoneyTestTags.historyRow("19901")).assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.historyRow("19902")).assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.historyRow("19903")).assertIsDisplayed()
    }

    @Test
    fun theFullListSaysSoWhenThereIsNothingToShow() = runComposeUiTest {
        setContent {
            SendMoneyHistoryScreenContent(payments = emptyList(), onNavigateToPayment = {})
        }

        onNodeWithText("You have not sent any payments yet.").assertIsDisplayed()
    }

    @Test
    fun tappingARowInTheFullListOpensThatPayment() = runComposeUiTest {
        var opened: String? = null
        setContent {
            SendMoneyHistoryScreenContent(
                payments = SendMoneyFixtures.paymentHistory(),
                onNavigateToPayment = { opened = it },
            )
        }

        onNodeWithTag(SendMoneyTestTags.historyRow("19903")).performClick()

        assertEquals("19903", opened)
    }
}
