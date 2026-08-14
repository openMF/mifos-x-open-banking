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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

/**
 * The on-device mirror of [PaymentsHubScreenRobolectricTest].
 *
 * Deliberately small: Robolectric covers the rendering, and this exists for what only a real device
 * disagrees about — measurement, scrolling and touch dispatch on the cards.
 */
class PaymentsHubScreenInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theQuickActionsAreReachableOnADeviceSizedScreen() {
        composeRule.setContent { PaymentsHubContent(onNavigateToSendMoney = {}) }

        composeRule.onNodeWithTag(PaymentsHubTestTags.QUICK_ACTION_SEND_MONEY).assertIsDisplayed()
        composeRule.onNodeWithTag(PaymentsHubTestTags.QUICK_ACTION_VRP)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun tappingTheStandingOrderCardNavigates() {
        var navigated = false
        composeRule.setContent {
            PaymentsHubContent(
                onNavigateToSendMoney = {},
                onNavigateToStandingOrder = { navigated = true },
            )
        }

        composeRule.onNodeWithTag(PaymentsHubTestTags.QUICK_ACTION_STANDING_ORDER)
            .performScrollTo()
            .performClick()

        assertTrue(navigated, "the standing-order card must reach its destination")
    }
}
