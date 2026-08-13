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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import org.mifosx.openbanking.feature.paymentshub.ui.PaymentsHubState
import org.mifosx.openbanking.feature.paymentshub.ui.PaymentsHubUiState
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PaymentsHubScreenUiTest {

    @Test
    fun loadingRendersTheSkeleton() = runComposeUiTest {
        setContent {
            PaymentsHubContent(
                state = PaymentsHubState(uiState = PaymentsHubUiState.Loading),
                onAction = {},
                onNavigateToSendMoney = {},
                onNavigateToPaymentStatus = {},
            )
        }
        onNodeWithTag(PaymentsHubTestTags.SKELETON).assertIsDisplayed()
        onNodeWithTag(PaymentsHubTestTags.QUICK_ACTIONS_GRID).assertDoesNotExist()
    }

    @Test
    fun contentRendersQuickActionsGrid() = runComposeUiTest {
        setContent {
            PaymentsHubContent(
                state = PaymentsHubState(
                    uiState = PaymentsHubUiState.Content(activityItems = emptyList()),
                ),
                onAction = {},
                onNavigateToSendMoney = {},
                onNavigateToPaymentStatus = {},
            )
        }
        onNodeWithTag(PaymentsHubTestTags.QUICK_ACTION_SEND_MONEY).assertIsDisplayed()
    }

    @Test
    fun emptyActivityShowsEmptyState() = runComposeUiTest {
        setContent {
            PaymentsHubContent(
                state = PaymentsHubState(
                    uiState = PaymentsHubUiState.Content(activityItems = emptyList()),
                ),
                onAction = {},
                onNavigateToSendMoney = {},
                onNavigateToPaymentStatus = {},
            )
        }
        onNodeWithTag(PaymentsHubTestTags.EMPTY_ACTIVITY).assertIsDisplayed()
    }

    @Test
    fun errorRendersRetryButton() = runComposeUiTest {
        setContent {
            PaymentsHubContent(
                state = PaymentsHubState(
                    uiState = PaymentsHubUiState.Error("Connection failed"),
                ),
                onAction = {},
                onNavigateToSendMoney = {},
                onNavigateToPaymentStatus = {},
            )
        }
        onNodeWithTag(PaymentsHubTestTags.ERROR_SCREEN).assertIsDisplayed()
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
                state = PaymentsHubState(
                    uiState = PaymentsHubUiState.Content(activityItems = emptyList()),
                ),
                onAction = {},
                onNavigateToSendMoney = {},
                onNavigateToPaymentStatus = {},
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
                state = PaymentsHubState(
                    uiState = PaymentsHubUiState.Content(activityItems = emptyList()),
                ),
                onAction = {},
                onNavigateToSendMoney = {},
                onNavigateToPaymentStatus = {},
                onNavigateToSchedulePayment = { navigated = true },
            )
        }

        onNodeWithTag(PaymentsHubTestTags.QUICK_ACTION_SCHEDULE).performScrollTo().performClick()

        assertTrue(navigated, "the schedule card must reach its destination")
    }
}
