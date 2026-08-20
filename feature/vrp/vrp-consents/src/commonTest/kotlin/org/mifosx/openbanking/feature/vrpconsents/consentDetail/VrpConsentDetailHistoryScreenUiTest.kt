/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.vrpconsents.consentDetail

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import org.mifosx.openbanking.feature.vrpconsents.VrpConsentsFixtures
import kotlin.test.Test

/**
 * Renders the consent detail's payment history, which is the shared list component in VRP's clothes.
 *
 * Guards the two things the migration to `MifosPaymentHistoryList` could quietly change: that VRP's
 * own test tags still reach the same nodes, and that its rows stay inert.
 */
@OptIn(ExperimentalTestApi::class)
class VrpConsentDetailHistoryScreenUiTest {

    @Test
    fun rendersOneRowPerPayment() = runComposeUiTest {
        setContent { PaymentHistory(VrpConsentsFixtures.paymentRows()) }

        onNodeWithTag(VrpConsentDetailTestTags.HISTORY).assertIsDisplayed()
        listOf("pay-1", "pay-2", "pay-3", "pay-4").forEach { localId ->
            onNodeWithTag(VrpConsentDetailTestTags.paymentRow(localId)).assertIsDisplayed()
        }
        onNodeWithTag(VrpConsentDetailTestTags.HISTORY_EMPTY).assertDoesNotExist()
    }

    @Test
    fun rendersTheAmountAndDateOfEachPayment() = runComposeUiTest {
        setContent { PaymentHistory(VrpConsentsFixtures.paymentRows().take(1)) }

        onNodeWithText("£45.00").assertIsDisplayed()
        onNodeWithText("14 Aug 2026").assertIsDisplayed()
    }

    @Test
    fun emptyHistoryRendersTheNotice() = runComposeUiTest {
        setContent { PaymentHistory(emptyList()) }

        onNodeWithTag(VrpConsentDetailTestTags.HISTORY_EMPTY).assertIsDisplayed()
        onNodeWithTag(VrpConsentDetailTestTags.paymentRow("pay-1")).assertDoesNotExist()
    }

    /** VRP keeps no per-payment screen, so a row must not offer to open one. */
    @Test
    fun rowsAreNotClickable() = runComposeUiTest {
        setContent { PaymentHistory(VrpConsentsFixtures.paymentRows()) }

        onNodeWithContentDescription("Show this payment").assertDoesNotExist()
    }

    /** A refused payment says so in words, and drops the status word beside the icon. */
    @Test
    fun aRefusedPaymentSaysSo() = runComposeUiTest {
        val refused = VrpConsentsFixtures.paymentRows().filter { it.localId == "pay-3" }
        setContent { PaymentHistory(refused) }

        onNodeWithText("Your bank did not accept this payment").assertIsDisplayed()
        onNodeWithText("Not sent").assertDoesNotExist()
    }

    @Test
    fun aSettledPaymentShowsTheStatusWord() = runComposeUiTest {
        val settled = VrpConsentsFixtures.paymentRows().filter { it.localId == "pay-1" }
        setContent { PaymentHistory(settled) }

        onNodeWithText("Sent").assertIsDisplayed()
    }
}
