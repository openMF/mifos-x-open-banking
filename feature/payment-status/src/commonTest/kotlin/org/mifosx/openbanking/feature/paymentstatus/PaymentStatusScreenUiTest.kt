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

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import org.mifosx.openbanking.core.model.banking.payment.PaymentDisposition
import org.mifosx.openbanking.feature.paymentstatus.ui.PaymentStatusAction
import org.mifosx.openbanking.feature.paymentstatus.ui.PaymentStatusErrorKind
import org.mifosx.openbanking.feature.paymentstatus.ui.PaymentStepState
import org.mifosx.openbanking.feature.paymentstatus.ui.PaymentTimelineStep
import kotlin.test.Test
import kotlin.test.assertEquals

// Kept identical to the field the view model derives the fourth stage from, so no fixture here
// depicts a timeline the app could not actually produce.
private const val SETTLED_AT = "4 Aug 2026, 09:00"
private const val REJECTED_AT = "3 Aug 2026, 16:40"

@OptIn(ExperimentalTestApi::class)
class PaymentStatusScreenUiTest {

    @Test
    fun loadingRendersTheSkeleton() = runComposeUiTest {
        setContent {
            PaymentStatusScreenContent(PaymentStatusFixtures.loadingState(), {})
        }
        onNodeWithTag(PaymentStatusTestTags.SKELETON).assertIsDisplayed()
        onNodeWithTag(PaymentStatusTestTags.SUMMARY_CARD).assertDoesNotExist()
    }

    @Test
    fun contentRendersTheSummaryAndEveryDetailRow() = runComposeUiTest {
        setContent {
            PaymentStatusScreenContent(PaymentStatusFixtures.contentState(), {})
        }
        onNodeWithTag(PaymentStatusTestTags.SUMMARY_CARD).assertIsDisplayed()
        onNodeWithTag(PaymentStatusTestTags.AMOUNT).assertIsDisplayed()
        onNodeWithTag(PaymentStatusTestTags.STATUS_CHIP).assertIsDisplayed()
        onNodeWithTag(PaymentStatusTestTags.DETAIL_REFERENCE).performScrollTo().assertIsDisplayed()
        onNodeWithTag(PaymentStatusTestTags.DETAIL_FROM).performScrollTo().assertIsDisplayed()
        onNodeWithTag(PaymentStatusTestTags.DETAIL_SUBMITTED).performScrollTo().assertIsDisplayed()
        onNodeWithTag(PaymentStatusTestTags.DETAIL_PAYMENT_ID).performScrollTo().assertIsDisplayed()
    }

    /** The note is the whole reason an in-flight payment does not read as a failure. */
    @Test
    fun anInFlightPaymentExplainsItself() = runComposeUiTest {
        setContent {
            PaymentStatusScreenContent(PaymentStatusFixtures.contentState(), {})
        }
        onNodeWithTag(PaymentStatusTestTags.IN_PROGRESS_NOTE).assertIsDisplayed()
    }

    @Test
    fun aSettledPaymentDropsTheInFlightNote() = runComposeUiTest {
        setContent {
            PaymentStatusScreenContent(
                PaymentStatusFixtures.contentState(disposition = PaymentDisposition.TerminalSuccess),
                {},
            )
        }
        onNodeWithTag(PaymentStatusTestTags.IN_PROGRESS_NOTE).assertDoesNotExist()
    }

    /** A second refresh while one is running would be a duplicate read for no benefit. */
    @Test
    fun refreshIsDisabledWhileARefreshIsRunning() = runComposeUiTest {
        setContent {
            PaymentStatusScreenContent(PaymentStatusFixtures.contentState(refreshing = true), {})
        }
        onNodeWithTag(PaymentStatusTestTags.REFRESH_BUTTON).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun tappingRefreshRereadsTheStatus() {
        val actions = mutableListOf<PaymentStatusAction>()
        runComposeUiTest {
            setContent {
                PaymentStatusScreenContent(
                    state = PaymentStatusFixtures.contentState(),
                    onAction = { actions += it },
                )
            }
            onNodeWithTag(PaymentStatusTestTags.REFRESH_BUTTON).performScrollTo().performClick()
        }

        assertEquals<List<PaymentStatusAction>>(listOf(PaymentStatusAction.RefreshStatus), actions)
    }

    @Test
    fun errorRendersRetry() = runComposeUiTest {
        setContent {
            PaymentStatusScreenContent(PaymentStatusFixtures.errorState(), {})
        }
        onNodeWithTag(PaymentStatusTestTags.ERROR_STATE).assertIsDisplayed()
        onNodeWithTag(PaymentStatusTestTags.RETRY_BUTTON).assertIsDisplayed()
    }

    @Test
    fun tappingRetryRereadsTheStatus() {
        val actions = mutableListOf<PaymentStatusAction>()
        runComposeUiTest {
            setContent {
                PaymentStatusScreenContent(
                    state = PaymentStatusFixtures.errorState(),
                    onAction = { actions += it },
                )
            }
            onNodeWithTag(PaymentStatusTestTags.RETRY_BUTTON).performClick()
        }

        assertEquals<List<PaymentStatusAction>>(listOf(PaymentStatusAction.RefreshStatus), actions)
    }

    /**
     * One tag across every charge row made `onNodeWithTag` ambiguous the moment a payment carried
     * two — which the single-charge fixture never showed and a real international payment would.
     */
    @Test
    fun everyChargeGetsItsOwnAddressableRow() = runComposeUiTest {
        setContent {
            PaymentStatusScreenContent(
                PaymentStatusFixtures.contentState(charges = PaymentStatusFixtures.twoCharges()),
                {},
            )
        }
        onNodeWithTag(PaymentStatusTestTags.detailFee(0)).performScrollTo().assertIsDisplayed()
        onNodeWithTag(PaymentStatusTestTags.detailFee(1)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aPaymentWithNoChargesDrawsNoFeeRow() = runComposeUiTest {
        setContent {
            PaymentStatusScreenContent(
                PaymentStatusFixtures.contentState(charges = emptyList()),
                {},
            )
        }
        onNodeWithTag(PaymentStatusTestTags.detailFee(0)).assertDoesNotExist()
    }

    // region — the four-stage timeline

    @Test
    fun contentRendersEveryTimelineStage() = runComposeUiTest {
        setContent {
            PaymentStatusScreenContent(PaymentStatusFixtures.contentState(), {})
        }
        onNodeWithTag(PaymentStatusTestTags.TIMELINE).performScrollTo().assertIsDisplayed()
        PaymentTimelineStep.entries.forEach { step ->
            onNodeWithTag(PaymentStatusTestTags.timelineStep(step)).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun anInFlightPaymentMarksItsFinalStageCurrentAndTheRestDone() = runComposeUiTest {
        setContent {
            PaymentStatusScreenContent(PaymentStatusFixtures.contentState(), {})
        }
        assertStageState(PaymentTimelineStep.RequestCreated, PaymentStepState.Done)
        assertStageState(PaymentTimelineStep.ApprovedAtBank, PaymentStepState.Done)
        assertStageState(PaymentTimelineStep.Submitted, PaymentStepState.Done)
        assertStageState(PaymentTimelineStep.Completed, PaymentStepState.Current)
    }

    @Test
    fun aPaymentTheBankHasOnlyReceivedMarksItsFinalStagePending() = runComposeUiTest {
        setContent {
            PaymentStatusScreenContent(
                PaymentStatusFixtures.contentState(
                    timeline = PaymentStatusFixtures.timeline(completedState = PaymentStepState.Pending),
                ),
                {},
            )
        }
        assertStageState(PaymentTimelineStep.Completed, PaymentStepState.Pending)
    }

    @Test
    fun aSettledPaymentMarksEveryStageDone() = runComposeUiTest {
        setContent {
            PaymentStatusScreenContent(
                PaymentStatusFixtures.contentState(
                    disposition = PaymentDisposition.TerminalSuccess,
                    settledAt = SETTLED_AT,
                    timeline = PaymentStatusFixtures.timeline(
                        completedState = PaymentStepState.Done,
                        completedAt = SETTLED_AT,
                    ),
                ),
                {},
            )
        }
        PaymentTimelineStep.entries.forEach { assertStageState(it, PaymentStepState.Done) }
    }

    /** Everything up to the bank's refusal did happen, so only the last stage reads as failed. */
    @Test
    fun aRejectedPaymentMarksOnlyItsFinalStageFailed() = runComposeUiTest {
        setContent {
            PaymentStatusScreenContent(
                PaymentStatusFixtures.contentState(
                    disposition = PaymentDisposition.TerminalFailure,
                    settledAt = "",
                    statusChangedAt = REJECTED_AT,
                    timeline = PaymentStatusFixtures.timeline(
                        completedState = PaymentStepState.Failed,
                        completedAt = REJECTED_AT,
                    ),
                ),
                {},
            )
        }
        assertStageState(PaymentTimelineStep.RequestCreated, PaymentStepState.Done)
        assertStageState(PaymentTimelineStep.Submitted, PaymentStepState.Done)
        assertStageState(PaymentTimelineStep.Completed, PaymentStepState.Failed)
    }

    // endregion

    /** A failed refresh must not take the answer already on screen with it. */
    @Test
    fun aFailedRefreshShowsANoticeBesideTheStatusRatherThanReplacingIt() = runComposeUiTest {
        setContent {
            PaymentStatusScreenContent(
                PaymentStatusFixtures.contentState(
                    refreshFailure = PaymentStatusErrorKind.NetworkError,
                ),
                {},
            )
        }
        onNodeWithTag(PaymentStatusTestTags.REFRESH_FAILURE).performScrollTo().assertIsDisplayed()
        onNodeWithTag(PaymentStatusTestTags.SUMMARY_CARD).assertIsDisplayed()
        onNodeWithTag(PaymentStatusTestTags.ERROR_STATE).assertDoesNotExist()
    }

    @Test
    fun aSuccessfulReadShowsNoRefreshNotice() = runComposeUiTest {
        setContent {
            PaymentStatusScreenContent(PaymentStatusFixtures.contentState(), {})
        }
        onNodeWithTag(PaymentStatusTestTags.REFRESH_FAILURE).assertDoesNotExist()
    }

    // region standing orders

    /** Without these rows a mandate is indistinguishable from a payment that happened once. */
    @Test
    fun aStandingOrderStatesHowOftenItRepeatsAndWhenItEnds() = runComposeUiTest {
        setContent {
            PaymentStatusScreenContent(PaymentStatusFixtures.standingOrderState(), {})
        }

        onNodeWithTag(PaymentStatusTestTags.DETAIL_REPEATS).performScrollTo().assertIsDisplayed()
        onNodeWithText("Every week").assertIsDisplayed()
        onNodeWithTag(PaymentStatusTestTags.DETAIL_RECURRING_AMOUNT)
            .performScrollTo()
            .assertIsDisplayed()
        onNodeWithTag(PaymentStatusTestTags.DETAIL_FINAL_PAYMENT)
            .performScrollTo()
            .assertIsDisplayed()
    }

    /** A mandate with no end date runs until it is stopped, which is an answer, not a gap. */
    @Test
    fun aStandingOrderWithNoEndDateDrawsNoLastPaymentRow() = runComposeUiTest {
        setContent {
            PaymentStatusScreenContent(
                PaymentStatusFixtures.standingOrderState(finalPaymentAt = ""),
                {},
            )
        }

        onNodeWithTag(PaymentStatusTestTags.DETAIL_REPEATS).performScrollTo().assertIsDisplayed()
        onNodeWithTag(PaymentStatusTestTags.DETAIL_FINAL_PAYMENT).assertDoesNotExist()
    }

    /** The date on a mandate is its first payment, never the only one. */
    @Test
    fun aStandingOrderLabelsItsDateAsTheFirstPayment() = runComposeUiTest {
        setContent {
            PaymentStatusScreenContent(PaymentStatusFixtures.standingOrderState(), {})
        }

        onNodeWithText("First payment").performScrollTo().assertIsDisplayed()
        onNodeWithText("Scheduled for").assertDoesNotExist()
    }

    /** A settled mandate is set up. Reading it as sent would claim money has moved. */
    @Test
    fun aSettledStandingOrderReadsAsSetUp() = runComposeUiTest {
        setContent {
            PaymentStatusScreenContent(PaymentStatusFixtures.standingOrderState(), {})
        }

        onNodeWithTag(PaymentStatusTestTags.STATUS_CHIP).assertIsDisplayed()
        onNodeWithText("Set up").assertIsDisplayed()
        onNodeWithText("Completed").assertDoesNotExist()
    }

    @Test
    fun aBookedScheduledPaymentReadsAsScheduled() = runComposeUiTest {
        setContent {
            PaymentStatusScreenContent(PaymentStatusFixtures.scheduledState(), {})
        }

        onNodeWithText("Scheduled").assertIsDisplayed()
        onNodeWithText("Completed").assertDoesNotExist()
    }

    /** A single payment has no mandate, so none of those rows may appear. */
    @Test
    fun aSinglePaymentDrawsNoMandateRows() = runComposeUiTest {
        setContent {
            PaymentStatusScreenContent(PaymentStatusFixtures.contentState(), {})
        }

        onNodeWithTag(PaymentStatusTestTags.DETAIL_REPEATS).assertDoesNotExist()
        onNodeWithTag(PaymentStatusTestTags.DETAIL_FINAL_PAYMENT).assertDoesNotExist()
        onNodeWithTag(PaymentStatusTestTags.DETAIL_RECURRING_AMOUNT).assertDoesNotExist()
    }

    // endregion
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.assertStageState(step: PaymentTimelineStep, state: PaymentStepState) {
    onNodeWithTag(PaymentStatusTestTags.timelineState(step, state))
        .performScrollTo()
        .assertIsDisplayed()
}
