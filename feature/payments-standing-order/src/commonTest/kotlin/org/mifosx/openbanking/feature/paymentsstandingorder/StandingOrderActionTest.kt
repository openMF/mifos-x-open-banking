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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import org.mifosx.openbanking.core.model.banking.payment.PaymentRail
import org.mifosx.openbanking.feature.paymentsstandingorder.ui.StandingOrderAction
import org.mifosx.openbanking.feature.paymentsstandingorder.ui.StandingOrderDateRole
import org.mifosx.openbanking.feature.paymentsstandingorder.ui.StandingOrderErrorKind
import org.mifosx.openbanking.feature.paymentsstandingorder.ui.StandingOrderStage
import org.mifosx.openbanking.feature.paymentsstandingorder.ui.StandingOrderState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * One case per interactive surface: tapping it dispatches that action and no other.
 *
 * These are cheap and catch a specific class of mistake the state tests cannot — a control wired to
 * the wrong action, or to none at all. The Payments Hub's Schedule card sat inert for exactly that
 * reason: it rendered, it had a test tag, and it had no `onClick`.
 */
@OptIn(ExperimentalTestApi::class)
class StandingOrderActionTest {

    private fun captureActions(
        state: StandingOrderState,
        tag: String,
        scroll: Boolean = true,
    ): List<StandingOrderAction> {
        val actions = mutableListOf<StandingOrderAction>()
        runComposeUiTest {
            setContent {
                StandingOrderScreenContent(
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

    /** The date row is the only way into the picker — the field itself is not editable. */
    @Test
    fun tappingTheDateFieldOpensThePicker() {
        val actions = captureActions(StandingOrderFixtures.formState(), StandingOrderTestTags.FIRST_DATE_FIELD)

        assertEquals(listOf(StandingOrderAction.OpenDatePicker(StandingOrderDateRole.First)), actions)
    }

    @Test
    fun tappingTheCollapsedPayerOpensThePicker() {
        val actions = captureActions(
            StandingOrderFixtures.formState(),
            StandingOrderTestTags.PAYER_PICKER,
        )

        assertEquals(listOf(StandingOrderAction.TogglePayerPicker), actions)
    }

    @Test
    fun confirmingOnTheReviewStagesTheConsent() {
        val actions = captureActions(
            StandingOrderFixtures.reviewState(),
            StandingOrderTestTags.CONFIRM_BUTTON,
        )

        assertEquals(listOf(StandingOrderAction.ConfirmAndStageConsent), actions)
    }

    /** Editing goes back to the form, which drops the staged draft — a different payment needs new keys. */
    @Test
    fun editingOnTheReviewGoesBackToTheForm() {
        val actions = captureActions(
            StandingOrderFixtures.reviewState(),
            StandingOrderTestTags.EDIT_PAYMENT_BUTTON,
        )

        assertEquals(listOf(StandingOrderAction.BackStep), actions)
    }

    @Test
    fun abandoningAuthorisationReleasesTheScreen() {
        val actions = captureActions(
            StandingOrderFixtures.submittingState(stage = StandingOrderStage.AwaitingAuthorisation),
            StandingOrderTestTags.ABANDON_AUTHORISATION_BUTTON,
            scroll = false,
        )

        assertEquals(listOf(StandingOrderAction.AbandonAuthorisation), actions)
    }

    /** A refused date sends the customer back to the form, where the date control lives. */
    @Test
    fun changingTheDateAfterARefusalReturnsToTheForm() {
        val actions = captureActions(
            StandingOrderFixtures.errorState(StandingOrderErrorKind.FirstDateRefused),
            StandingOrderTestTags.CHANGE_DATE_BUTTON,
            scroll = false,
        )

        assertEquals(listOf(StandingOrderAction.BackStep), actions)
    }

    @Test
    fun retryingAfterANetworkFailureRestages() {
        val actions = captureActions(
            StandingOrderFixtures.errorState(StandingOrderErrorKind.NetworkError),
            StandingOrderTestTags.RETRY_BUTTON,
            scroll = false,
        )

        assertEquals(listOf(StandingOrderAction.RetryStaging), actions)
    }

    @Test
    fun changingPayerAfterARefusedAccountReturnsToTheForm() {
        val actions = captureActions(
            StandingOrderFixtures.errorState(StandingOrderErrorKind.PayerNotSupported),
            StandingOrderTestTags.CHANGE_PAYER_BUTTON,
            scroll = false,
        )

        assertEquals(listOf(StandingOrderAction.ChangePayer), actions)
    }

    /** Switching to the international segment, which clears the payee and re-checks the date. */
    @Test
    fun tappingTheInternationalSegmentSwitchesRail() {
        val actions = captureActions(
            StandingOrderFixtures.formState(),
            StandingOrderTestTags.railOption(PaymentRail.International),
            scroll = false,
        )

        assertEquals(listOf(StandingOrderAction.SelectRail(PaymentRail.International)), actions)
    }
}
