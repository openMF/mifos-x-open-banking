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
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mifosx.openbanking.core.model.banking.payment.PaymentCharge
import org.mifosx.openbanking.core.model.banking.payment.PaymentDisposition
import org.mifosx.openbanking.core.model.banking.payment.PaymentStatus
import org.mifosx.openbanking.feature.paymentstatus.ui.PaymentStatusAction
import org.mifosx.openbanking.feature.paymentstatus.ui.PaymentStatusErrorKind
import org.mifosx.openbanking.feature.paymentstatus.ui.PaymentStatusState
import org.mifosx.openbanking.feature.paymentstatus.ui.PaymentStatusUiState
import org.mifosx.openbanking.feature.paymentstatus.ui.PaymentStepState
import org.mifosx.openbanking.feature.paymentstatus.ui.PaymentTimelineEntry
import org.mifosx.openbanking.feature.paymentstatus.ui.PaymentTimelineStep
import kotlin.test.assertEquals

private const val PAYMENT_ID = "PMT-812774903-01"

// Kept identical to the field the view model derives the fourth stage from, so no fixture here
// depicts a timeline the app could not actually produce.
private const val SETTLED_AT = "4 Aug 2026, 09:00"
private const val REJECTED_AT = "3 Aug 2026, 16:40"

/**
 * androidInstrumentedTest does not see commonTest, so the state fixtures are inlined here rather
 * than shared with [PaymentStatusFixtures].
 */
private fun timeline(
    completedState: PaymentStepState = PaymentStepState.Current,
    completedAt: String = "",
): List<PaymentTimelineEntry> = listOf(
    PaymentTimelineEntry(PaymentTimelineStep.Completed, completedState, completedAt),
    PaymentTimelineEntry(PaymentTimelineStep.Submitted, PaymentStepState.Done, "3 Aug 2026, 14:22"),
    PaymentTimelineEntry(PaymentTimelineStep.ApprovedAtBank, PaymentStepState.Done, "3 Aug 2026, 14:20"),
    // Undated on purpose: nothing records when the consent was staged, and the receipt's
    // CreationDateTime means submission, not request.
    PaymentTimelineEntry(PaymentTimelineStep.RequestCreated, PaymentStepState.Done),
)

private fun charges(): List<PaymentCharge> = listOf(
    PaymentCharge(bearer = "BorneByDebtor", typeLabel = "UK.OBIE.CHAPSOut", amountLabel = "£0.05"),
    PaymentCharge(bearer = "BorneByDebtor", typeLabel = "UK.OBIE.FX", amountLabel = "£1.20"),
)

private fun contentState(
    disposition: PaymentDisposition = PaymentDisposition.InProgress,
    status: PaymentStatus = PaymentStatus.AcceptedSettlementInProcess,
    charges: List<PaymentCharge> = charges(),
    refreshFailure: PaymentStatusErrorKind? = null,
    settledAt: String = "3 Aug 2026, 14:22",
    statusChangedAt: String = "3 Aug 2026, 14:22",
    timeline: List<PaymentTimelineEntry> = timeline(),
): PaymentStatusState = PaymentStatusState(
    paymentId = PAYMENT_ID,
    uiState = PaymentStatusUiState.Content(
        paymentId = PAYMENT_ID,
        status = status,
        disposition = disposition,
        amountLabel = "£850.00",
        creditorName = "Jameson Lettings",
        reference = "RENT-FLAT12",
        debtorLabel = "40-05-15 12345678",
        submittedAt = "3 Aug 2026, 14:22",
        settledAt = settledAt,
        statusChangedAt = statusChangedAt,
        charges = charges,
        lastCheckedAt = "14:25",
        refreshFailure = refreshFailure,
        timeline = timeline,
    ),
)

private fun loadingState(): PaymentStatusState =
    PaymentStatusState(paymentId = PAYMENT_ID, uiState = PaymentStatusUiState.Loading)

private fun errorState(
    kind: PaymentStatusErrorKind = PaymentStatusErrorKind.NetworkError,
): PaymentStatusState =
    PaymentStatusState(paymentId = PAYMENT_ID, uiState = PaymentStatusUiState.Error(kind))

/**
 * On-device mirror of [PaymentStatusScreenRobolectricTest], driving the same
 * [PaymentStatusTestTags] so a divergence between the JVM and device renderers is visible.
 */
@RunWith(AndroidJUnit4::class)
class PaymentStatusScreenInstrumentedTest {

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
        render(loadingState())

        composeRule.onNodeWithTag(PaymentStatusTestTags.SKELETON).assertExists()
        composeRule.onNodeWithTag(PaymentStatusTestTags.SUMMARY_CARD).assertDoesNotExist()
    }

    @Test
    fun contentRendersTheSummaryAndEveryDetailRow() {
        render(contentState())

        composeRule.onNodeWithTag(PaymentStatusTestTags.SUMMARY_CARD).assertIsDisplayed()
        composeRule.onNodeWithTag(PaymentStatusTestTags.AMOUNT).assertIsDisplayed()
        composeRule.onNodeWithTag(PaymentStatusTestTags.STATUS_CHIP).assertIsDisplayed()
        composeRule.onNodeWithTag(PaymentStatusTestTags.DETAIL_REFERENCE).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(PaymentStatusTestTags.DETAIL_FROM).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(PaymentStatusTestTags.DETAIL_SUBMITTED).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(PaymentStatusTestTags.DETAIL_PAYMENT_ID).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun everyChargeGetsItsOwnAddressableRow() {
        render(contentState())

        composeRule.onNodeWithTag(PaymentStatusTestTags.detailFee(0)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(PaymentStatusTestTags.detailFee(1)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun contentRendersEveryTimelineStage() {
        render(contentState())

        composeRule.onNodeWithTag(PaymentStatusTestTags.TIMELINE).performScrollTo().assertIsDisplayed()
        PaymentTimelineStep.entries.forEach { step ->
            composeRule.onNodeWithTag(PaymentStatusTestTags.timelineStep(step)).assertExists()
        }
    }

    @Test
    fun anInFlightPaymentMarksItsFinalStageCurrent() {
        render(contentState())

        assertStage(PaymentTimelineStep.Submitted, PaymentStepState.Done)
        assertStage(PaymentTimelineStep.Completed, PaymentStepState.Current)
    }

    @Test
    fun aPaymentTheBankHasOnlyReceivedMarksItsFinalStagePending() {
        render(contentState(timeline = timeline(completedState = PaymentStepState.Pending)))

        assertStage(PaymentTimelineStep.Completed, PaymentStepState.Pending)
    }

    @Test
    fun aSettledPaymentMarksEveryStageDone() {
        render(
            contentState(
                disposition = PaymentDisposition.TerminalSuccess,
                status = PaymentStatus.AcceptedCreditSettlementCompleted,
                settledAt = SETTLED_AT,
                timeline = timeline(
                    completedState = PaymentStepState.Done,
                    completedAt = SETTLED_AT,
                ),
            ),
        )

        PaymentTimelineStep.entries.forEach { assertStage(it, PaymentStepState.Done) }
        composeRule.onNodeWithTag(PaymentStatusTestTags.IN_PROGRESS_NOTE).assertDoesNotExist()
    }

    @Test
    fun aRejectedPaymentMarksOnlyItsFinalStageFailed() {
        render(
            contentState(
                disposition = PaymentDisposition.TerminalFailure,
                status = PaymentStatus.Rejected,
                settledAt = "",
                statusChangedAt = REJECTED_AT,
                timeline = timeline(
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
    fun aFailedRefreshShowsANoticeBesideTheStatusRatherThanReplacingIt() {
        render(contentState(refreshFailure = PaymentStatusErrorKind.NetworkError))

        composeRule.onNodeWithTag(PaymentStatusTestTags.REFRESH_FAILURE).assertExists()
        composeRule.onNodeWithTag(PaymentStatusTestTags.SUMMARY_CARD).assertIsDisplayed()
        composeRule.onNodeWithTag(PaymentStatusTestTags.ERROR_STATE).assertDoesNotExist()
    }

    @Test
    fun aSuccessfulReadShowsNoRefreshNotice() {
        render(contentState())

        composeRule.onNodeWithTag(PaymentStatusTestTags.REFRESH_FAILURE).assertDoesNotExist()
    }

    @Test
    fun tappingRefreshRereadsTheStatus() {
        render(contentState())

        composeRule.onNodeWithTag(PaymentStatusTestTags.REFRESH_BUTTON).performScrollTo().performClick()

        assertEquals(listOf<PaymentStatusAction>(PaymentStatusAction.RefreshStatus), actions)
    }

    @Test
    fun errorRendersRetryAndDispatchesTheSameRead() {
        render(errorState())

        composeRule.onNodeWithTag(PaymentStatusTestTags.ERROR_STATE).assertExists()
        composeRule.onNodeWithTag(PaymentStatusTestTags.RETRY_BUTTON).performClick()

        assertEquals(listOf<PaymentStatusAction>(PaymentStatusAction.RefreshStatus), actions)
    }

    private fun assertStage(step: PaymentTimelineStep, state: PaymentStepState) {
        composeRule.onNodeWithTag(PaymentStatusTestTags.timelineState(step, state)).assertExists()
    }
}
