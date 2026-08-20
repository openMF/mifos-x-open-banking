/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.paymentstatus

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mifosx.openbanking.core.model.banking.payment.PaymentDisposition
import org.mifosx.openbanking.feature.paymentstatus.ui.PaymentStatusAction
import org.mifosx.openbanking.feature.paymentstatus.ui.PaymentStatusErrorKind
import org.mifosx.openbanking.feature.paymentstatus.ui.PaymentStatusState
import org.mifosx.openbanking.feature.paymentstatus.ui.PaymentStepState
import org.mifosx.openbanking.feature.paymentstatus.ui.PaymentTimelineStep
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

private const val ROBOLECTRIC_SDK = 34

// Kept identical to the field the view model derives the fourth stage from, so no fixture here
// depicts a timeline the app could not actually produce.
private const val SETTLED_AT = "4 Aug 2026, 09:00"
private const val REJECTED_AT = "3 Aug 2026, 16:40"

/**
 * Renders [PaymentStatusScreenContent] across its states under Robolectric (JVM, no device) and
 * drives it through the shared [PaymentStatusTestTags]. A verbatim on-device mirror lives in
 * [PaymentStatusScreenInstrumentedTest].
 *
 * Deliberately not named `*ScreenUiTest`: the module's JVM unit-test tasks exclude that pattern,
 * because the commonTest Compose classes compile into this source set with no runner to stand up a
 * composition. This class has one, so it must sit outside the filter to run at all.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [ROBOLECTRIC_SDK], qualifiers = "w412dp-h1800dp")
class PaymentStatusScreenRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<PaymentStatusAction>()
    private fun render(state: PaymentStatusState) {
        composeRule.setContent {
            PaymentStatusScreenContent(
                state = state,
                onAction = { actions.add(it) },
            )
        }
    }

    @Test
    fun loadingRendersTheSkeleton() {
        render(PaymentStatusFixtures.loadingState())

        composeRule.onNodeWithTag(PaymentStatusTestTags.SKELETON).assertExists()
        composeRule.onNodeWithTag(PaymentStatusTestTags.SUMMARY_CARD).assertDoesNotExist()
    }

    @Test
    fun contentRendersTheSummaryAndEveryDetailRow() {
        render(PaymentStatusFixtures.contentState())

        composeRule.onNodeWithTag(PaymentStatusTestTags.SUMMARY_CARD).assertIsDisplayed()
        composeRule.onNodeWithTag(PaymentStatusTestTags.AMOUNT).assertIsDisplayed()
        composeRule.onNodeWithTag(PaymentStatusTestTags.STATUS_CHIP).assertIsDisplayed()
        composeRule.onNodeWithTag(PaymentStatusTestTags.DETAIL_REFERENCE).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(PaymentStatusTestTags.DETAIL_FROM).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(PaymentStatusTestTags.DETAIL_SUBMITTED).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(PaymentStatusTestTags.DETAIL_SETTLED).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(PaymentStatusTestTags.DETAIL_STATUS_CHANGED).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(PaymentStatusTestTags.DETAIL_PAYMENT_ID).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun everyChargeGetsItsOwnAddressableRow() {
        render(PaymentStatusFixtures.contentState(charges = PaymentStatusFixtures.twoCharges()))

        composeRule.onNodeWithTag(PaymentStatusTestTags.detailFee(0)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(PaymentStatusTestTags.detailFee(1)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun contentRendersEveryTimelineStage() {
        render(PaymentStatusFixtures.contentState())

        composeRule.onNodeWithTag(PaymentStatusTestTags.TIMELINE).performScrollTo().assertIsDisplayed()
        PaymentTimelineStep.entries.forEach { step ->
            composeRule.onNodeWithTag(PaymentStatusTestTags.timelineStep(step)).assertExists()
        }
    }

    @Test
    fun anInFlightPaymentMarksItsFinalStageCurrent() {
        render(PaymentStatusFixtures.contentState())

        assertStage(PaymentTimelineStep.Submitted, PaymentStepState.Done)
        assertStage(PaymentTimelineStep.Completed, PaymentStepState.Current)
    }

    @Test
    fun aPaymentTheBankHasOnlyReceivedMarksItsFinalStagePending() {
        render(
            PaymentStatusFixtures.contentState(
                timeline = PaymentStatusFixtures.timeline(completedState = PaymentStepState.Pending),
            ),
        )

        assertStage(PaymentTimelineStep.Completed, PaymentStepState.Pending)
    }

    @Test
    fun aSettledPaymentMarksEveryStageDone() {
        render(
            PaymentStatusFixtures.contentState(
                disposition = PaymentDisposition.TerminalSuccess,
                settledAt = SETTLED_AT,
                timeline = PaymentStatusFixtures.timeline(
                    completedState = PaymentStepState.Done,
                    completedAt = SETTLED_AT,
                ),
            ),
        )

        PaymentTimelineStep.entries.forEach { assertStage(it, PaymentStepState.Done) }
    }

    @Test
    fun aRejectedPaymentMarksOnlyItsFinalStageFailed() {
        render(
            PaymentStatusFixtures.contentState(
                disposition = PaymentDisposition.TerminalFailure,
                settledAt = "",
                statusChangedAt = REJECTED_AT,
                timeline = PaymentStatusFixtures.timeline(
                    completedState = PaymentStepState.Failed,
                    completedAt = REJECTED_AT,
                ),
            ),
        )

        assertStage(PaymentTimelineStep.RequestCreated, PaymentStepState.Done)
        assertStage(PaymentTimelineStep.ApprovedAtBank, PaymentStepState.Done)
        assertStage(PaymentTimelineStep.Submitted, PaymentStepState.Done)
        assertStage(PaymentTimelineStep.Completed, PaymentStepState.Failed)
    }

    @Test
    fun aSettledPaymentDropsTheInFlightNote() {
        render(PaymentStatusFixtures.contentState(disposition = PaymentDisposition.TerminalSuccess))

        composeRule.onNodeWithTag(PaymentStatusTestTags.IN_PROGRESS_NOTE).assertDoesNotExist()
    }

    /** A failed refresh must not take the answer already on screen with it. */
    @Test
    fun aFailedRefreshShowsANoticeBesideTheStatusRatherThanReplacingIt() {
        render(PaymentStatusFixtures.contentState(refreshFailure = PaymentStatusErrorKind.NetworkError))

        composeRule.onNodeWithTag(PaymentStatusTestTags.REFRESH_FAILURE).assertExists()
        composeRule.onNodeWithTag(PaymentStatusTestTags.SUMMARY_CARD).assertIsDisplayed()
        composeRule.onNodeWithTag(PaymentStatusTestTags.ERROR_STATE).assertDoesNotExist()
    }

    @Test
    fun aSuccessfulReadShowsNoRefreshNotice() {
        render(PaymentStatusFixtures.contentState())

        composeRule.onNodeWithTag(PaymentStatusTestTags.REFRESH_FAILURE).assertDoesNotExist()
    }

    /** The button stays alongside the gesture: pull-to-refresh is invisible on desktop and web. */
    @Test
    fun tappingRefreshRereadsTheStatus() {
        render(PaymentStatusFixtures.contentState())

        composeRule.onNodeWithTag(PaymentStatusTestTags.REFRESH_BUTTON).performScrollTo().performClick()

        assertEquals(listOf<PaymentStatusAction>(PaymentStatusAction.RefreshStatus), actions)
    }

    @Test
    fun errorRendersRetryAndDispatchesTheSameRead() {
        render(PaymentStatusFixtures.errorState())

        composeRule.onNodeWithTag(PaymentStatusTestTags.ERROR_STATE).assertExists()
        composeRule.onNodeWithTag(PaymentStatusTestTags.RETRY_BUTTON).performClick()

        assertEquals(listOf<PaymentStatusAction>(PaymentStatusAction.RefreshStatus), actions)
    }

    private fun assertStage(step: PaymentTimelineStep, state: PaymentStepState) {
        composeRule.onNodeWithTag(PaymentStatusTestTags.timelineState(step, state)).assertExists()
    }
}
