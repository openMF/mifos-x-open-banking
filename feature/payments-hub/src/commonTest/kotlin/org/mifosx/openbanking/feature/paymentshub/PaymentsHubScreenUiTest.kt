/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.paymentshub

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The hub is four cards and nothing else.
 *
 * There is no state to drive it any more, so these cases take no fixtures: the Recent list, the
 * loading skeleton and the error screen were all removed with the local payment store's only reader.
 * What is left is worth testing for one reason — a card that renders but does not navigate is a
 * defect this suite has already caught once.
 */
@OptIn(ExperimentalTestApi::class)
class PaymentsHubScreenUiTest {

    @Test
    fun theHubRendersItsFourQuickActions() = runComposeUiTest {
        setContent { PaymentsHubContent(onNavigateToSendMoney = {}) }

        onNodeWithTag(PaymentsHubTestTags.QUICK_ACTIONS_GRID).assertIsDisplayed()
        onNodeWithTag(PaymentsHubTestTags.QUICK_ACTION_SEND_MONEY).assertIsDisplayed()
        onNodeWithTag(PaymentsHubTestTags.QUICK_ACTION_SCHEDULE).assertIsDisplayed()
        onNodeWithTag(PaymentsHubTestTags.QUICK_ACTION_STANDING_ORDER).performScrollTo().assertIsDisplayed()
        onNodeWithTag(PaymentsHubTestTags.QUICK_ACTION_VRP).performScrollTo().assertIsDisplayed()
    }

    /**
     * Neither heading survives.
     *
     * "Recent" went with the list it introduced; "Quick Actions" went because with one section left
     * there is nothing to tell it apart from.
     */
    @Test
    fun theHubCarriesNoSectionHeadings() = runComposeUiTest {
        setContent { PaymentsHubContent(onNavigateToSendMoney = {}) }

        onNodeWithText("Recent").assertDoesNotExist()
        onNodeWithText("Quick Actions").assertDoesNotExist()
        onNodeWithText("No payments yet").assertDoesNotExist()
    }

    /**
     * The standing-order card actually navigates.
     *
     * This card sat inert for months — it rendered, it had a test tag, and it had no `onClick` — so a
     * test that only asserts it is displayed proves nothing about it working. That is the failure this
     * case exists for, and it is not hypothetical: the lambda was accepted by this composable and not
     * passed down, which no other test noticed and detekt caught as an unused parameter.
     */
    @Test
    fun theStandingOrderCardNavigates() = runComposeUiTest {
        var navigated = false
        setContent {
            PaymentsHubContent(
                onNavigateToSendMoney = {},
                onNavigateToStandingOrder = { navigated = true },
            )
        }

        onNodeWithTag(PaymentsHubTestTags.QUICK_ACTION_STANDING_ORDER)
            .performScrollTo()
            .performClick()

        assertTrue(navigated, "the standing-order card must reach its destination")
    }

    /** The schedule card, for the same reason and by the same route. */
    @Test
    fun theScheduleCardNavigates() = runComposeUiTest {
        var navigated = false
        setContent {
            PaymentsHubContent(
                onNavigateToSendMoney = {},
                onNavigateToSchedulePayment = { navigated = true },
            )
        }

        onNodeWithTag(PaymentsHubTestTags.QUICK_ACTION_SCHEDULE).performScrollTo().performClick()

        assertTrue(navigated, "the schedule card must reach its destination")
    }

    /** And send-money, which was the only card that ever worked. */
    @Test
    fun theSendMoneyCardNavigates() = runComposeUiTest {
        var navigated = false
        setContent { PaymentsHubContent(onNavigateToSendMoney = { navigated = true }) }

        onNodeWithTag(PaymentsHubTestTags.QUICK_ACTION_SEND_MONEY).performScrollTo().performClick()

        assertTrue(navigated, "the send-money card must reach its destination")
    }
}
