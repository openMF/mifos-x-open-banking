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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import org.mifosx.openbanking.feature.sendmoney.ui.SendMoneyAction
import org.mifosx.openbanking.feature.sendmoney.ui.SendMoneyErrorKind
import org.mifosx.openbanking.feature.sendmoney.ui.SendMoneyState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * One case per interactive surface: every control this screen renders must dispatch the action its
 * `on_click` contract declares, or navigate. A control that renders but dispatches nothing is the
 * defect this suite exists to catch.
 */
@OptIn(ExperimentalTestApi::class)
class SendMoneyActionTest {

    /**
     * [scroll] is opt-in because only the form states sit in a scroller — the submitting, success
     * and error states fill the viewport, and `performScrollTo` fails without a scrollable ancestor.
     */
    private fun captureActions(
        state: SendMoneyState,
        tag: String,
        scroll: Boolean = false,
    ): List<SendMoneyAction> {
        val actions = mutableListOf<SendMoneyAction>()
        runComposeUiTest {
            setContent {
                SendMoneyScreenContent(
                    state = state,
                    onAction = { actions += it },
                    onNavigateToConsents = {},
                )
            }
            val node = onNodeWithTag(tag)
            if (scroll) node.performScrollTo()
            node.performClick()
        }
        return actions
    }

    /** The collapsed summary is the toggle. Nothing else on it is tappable. */
    @Test
    fun tappingTheCollapsedPayerOpensThePicker() {
        val actions = captureActions(SendMoneyFixtures.formState(), SendMoneyTestTags.PAYER_PICKER)

        assertEquals(listOf(SendMoneyAction.TogglePayerPicker), actions)
    }

    /** And again while open — one action, toggling both ways, rather than two that can disagree. */
    @Test
    fun tappingTheExpandedPayerClosesThePicker() {
        val actions = captureActions(
            SendMoneyFixtures.formState(payerPickerExpanded = true),
            SendMoneyTestTags.PAYER_HEADER,
        )

        assertEquals(listOf(SendMoneyAction.TogglePayerPicker), actions)
    }

    @Test
    fun tappingAnAccountRowSelectsThatPayer() {
        val actions = captureActions(
            SendMoneyFixtures.formState(payerPickerExpanded = true),
            SendMoneyTestTags.debtorRow(SendMoneyFixtures.SAVINGS_ACCOUNT_ID),
        )

        assertEquals(
            listOf(SendMoneyAction.SelectDebtorAccount(SendMoneyFixtures.SAVINGS_ACCOUNT_ID)),
            actions,
        )
    }

    /**
     * The bank choice moved inside the picker, and must still dispatch from there.
     *
     * It is the only way a credit card or a Global Money wallet can fund a payment — neither is
     * offered as a named payer, because the bank refuses both — so losing it is losing a capability,
     * not a control.
     */
    @Test
    fun tappingChooseAtMyBankInsideThePickerDispatchesTheBankChoice() {
        val actions = captureActions(
            SendMoneyFixtures.formState(debtorAccountId = null, payerPickerExpanded = true),
            SendMoneyTestTags.PAYER_BANK_CHOICE,
        )

        assertEquals(listOf(SendMoneyAction.LetBankChoosePayer), actions)
    }

    @Test
    fun tappingAPayeeRowSelectsThatCreditor() {
        val actions = captureActions(
            SendMoneyFixtures.formState(),
            SendMoneyTestTags.creditorRow(SendMoneyFixtures.JAMESON_ID),
        )

        assertEquals(listOf(SendMoneyAction.SelectCreditor(SendMoneyFixtures.JAMESON_ID)), actions)
    }

    /** The "Pay new" avatar, which replaced the text button and kept its contract. */
    @Test
    fun tappingPayNewOpensTheManualFields() {
        val actions = captureActions(
            SendMoneyFixtures.formState(),
            SendMoneyTestTags.MANUAL_ENTRY_BUTTON,
        )

        assertEquals(listOf(SendMoneyAction.ShowManualCreditorEntry), actions)
    }

    /** Pay new is reachable with no payees at all — that is the case it exists for. */
    @Test
    fun payNewIsStillTappableWithNoSavedPayees() {
        val actions = captureActions(
            SendMoneyFixtures.formState(beneficiaries = emptyList()),
            SendMoneyTestTags.MANUAL_ENTRY_BUTTON,
        )

        assertEquals(listOf(SendMoneyAction.ShowManualCreditorEntry), actions)
    }

    @Test
    fun tappingUseTheseDetailsConfirmsTheManualPayee() {
        val actions = captureActions(
            SendMoneyFixtures.formState(manualEntryVisible = true),
            SendMoneyTestTags.MANUAL_CONFIRM,
            scroll = true,
        )

        assertEquals(listOf(SendMoneyAction.ConfirmManualCreditor), actions)
    }

    /**
     * No scroll: the action bar is pinned below the form, not the last thing in it.
     *
     * That is the point of pinning it — the merged page is long enough that a button at the end of
     * the scroll would sit off screen on a phone.
     */
    @Test
    fun tappingReviewMovesToTheReviewPage() {
        val actions = captureActions(SendMoneyFixtures.filledFormState(), SendMoneyTestTags.REVIEW_BUTTON)

        assertEquals(listOf(SendMoneyAction.ReviewPayment), actions)
    }

    @Test
    fun tappingConfirmStagesTheConsent() {
        val actions = captureActions(SendMoneyFixtures.reviewState(), SendMoneyTestTags.CONFIRM_BUTTON, scroll = true)

        assertEquals(listOf(SendMoneyAction.ConfirmAndStageConsent), actions)
    }

    @Test
    fun tappingEditPaymentGoesBackToTheForm() {
        val actions = captureActions(
            SendMoneyFixtures.reviewState(),
            SendMoneyTestTags.EDIT_PAYMENT_BUTTON,
            scroll = true,
        )

        assertEquals(listOf(SendMoneyAction.BackStep), actions)
    }

    @Test
    fun tappingRetryStagesTheConsentAgain() {
        val actions = captureActions(
            SendMoneyFixtures.errorState(kind = SendMoneyErrorKind.NetworkError),
            SendMoneyTestTags.RETRY_BUTTON,
        )

        assertEquals(listOf(SendMoneyAction.RetryStaging), actions)
    }

    @Test
    fun tappingChangeAmountGoesBackAStep() {
        val actions = captureActions(
            SendMoneyFixtures.errorState(kind = SendMoneyErrorKind.OutsideControlParameters),
            SendMoneyTestTags.EDIT_AMOUNT_BUTTON,
        )

        assertEquals(listOf(SendMoneyAction.BackStep), actions)
    }

    @Test
    fun tappingAuthoriseAgainRestagesTheConsent() {
        val actions = captureActions(
            SendMoneyFixtures.errorState(kind = SendMoneyErrorKind.ConsentNotAuthorised),
            SendMoneyTestTags.REAUTHORISE_BUTTON,
        )

        assertEquals(listOf(SendMoneyAction.ConfirmAndStageConsent), actions)
    }

    /** The navigating control is a callback, not an action — the host owns the route table. */
    @Test
    fun viewConsentsNavigatesRatherThanDispatching() {
        val actions = mutableListOf<SendMoneyAction>()
        var wentToConsents = false
        runComposeUiTest {
            setContent {
                SendMoneyScreenContent(
                    state = SendMoneyFixtures.errorState(kind = SendMoneyErrorKind.ConsentRevoked),
                    onAction = { actions += it },
                    onNavigateToConsents = { wentToConsents = true },
                )
            }
            onNodeWithTag(SendMoneyTestTags.VIEW_CONSENTS_BUTTON).performClick()
        }

        assertTrue(wentToConsents)
        assertTrue(actions.isEmpty())
    }
}
