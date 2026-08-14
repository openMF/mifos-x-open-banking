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

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val ROBOLECTRIC_SDK = 34

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [ROBOLECTRIC_SDK])
class PaymentsHubScreenRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theHubRendersItsQuickActions() {
        composeRule.setContent { PaymentsHubContent(onNavigateToSendMoney = {}) }

        composeRule.onNodeWithTag(PaymentsHubTestTags.QUICK_ACTIONS_GRID).assertIsDisplayed()
        composeRule.onNodeWithTag(PaymentsHubTestTags.QUICK_ACTION_SEND_MONEY).assertIsDisplayed()
        composeRule.onNodeWithTag(PaymentsHubTestTags.QUICK_ACTION_VRP).performScrollTo().assertIsDisplayed()
    }

    /**
     * Nothing waits on anything.
     *
     * The hub used to open on a skeleton and leave it only when the local payment store emitted. With
     * no store to read, a shimmer here would never resolve — so its absence is the assertion.
     */
    @Test
    fun theHubRendersImmediatelyWithNoLoadingOrEmptyState() {
        composeRule.setContent { PaymentsHubContent(onNavigateToSendMoney = {}) }

        composeRule.onNodeWithText("Recent").assertDoesNotExist()
        composeRule.onNodeWithText("Quick Actions").assertDoesNotExist()
        composeRule.onNodeWithText("No payments yet").assertDoesNotExist()
    }
}
