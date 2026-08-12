/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.paymentsschedulepayment

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mifosx.openbanking.core.model.banking.payment.PaymentRail
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.SchedulePaymentAction
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.SchedulePaymentErrorKind
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.SchedulePaymentStage
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.SchedulePaymentState
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val ROBOLECTRIC_SDK = 34

/**
 * Renders [SchedulePaymentScreenContent] under Robolectric (JVM, no device), driven through the
 * shared [SchedulePaymentTestTags]. An on-device mirror lives in
 * [SchedulePaymentScreenInstrumentedTest].
 *
 * The recurring question is what each rail asks for and what it must not: the two accept genuinely
 * different fields, and sending the wrong one is a `400` rather than a matter of taste.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [ROBOLECTRIC_SDK])
class SchedulePaymentScreenRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<SchedulePaymentAction>()

    private fun render(state: SchedulePaymentState) {
        composeRule.setContent {
            SchedulePaymentScreenContent(
                state = state,
                onAction = { actions.add(it) },
                onNavigateToConsents = {},
            )
        }
    }

    @Test
    fun theFormCarriesEveryPickerTheDateAndTheAmountOnOneScroll() {
        render(SchedulePaymentFixtures.formState())

        composeRule.onNodeWithTag(SchedulePaymentTestTags.FORM_PAGE).assertIsDisplayed()
        composeRule.onNodeWithTag(SchedulePaymentTestTags.RAIL_TOGGLE).assertIsDisplayed()
        composeRule.onNodeWithTag(SchedulePaymentTestTags.PAYER_PICKER).assertIsDisplayed()
        composeRule.onNodeWithTag(SchedulePaymentTestTags.DATE_FIELD).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(SchedulePaymentTestTags.AMOUNT_CARD).performScrollTo().assertIsDisplayed()
    }

    /** Tapping the date row is the only way to set a date — it is not a text field. */
    @Test
    fun tappingTheDateFieldAsksToOpenThePicker() {
        render(SchedulePaymentFixtures.formState())

        composeRule.onNodeWithTag(SchedulePaymentTestTags.DATE_FIELD).performScrollTo().performClick()

        assertEquals(listOf<SchedulePaymentAction>(SchedulePaymentAction.OpenDatePicker), actions)
    }

    /** `RemittanceInformation` is refused `U005` internationally, so the field must not be offered. */
    @Test
    fun theInternationalFormOffersNoReferenceField() {
        render(SchedulePaymentFixtures.formState(rail = PaymentRail.International))

        composeRule.onNodeWithTag(SchedulePaymentTestTags.REFERENCE_FIELD).assertDoesNotExist()
    }

    @Test
    fun theDomesticFormOffersAReferenceField() {
        render(SchedulePaymentFixtures.formState())

        composeRule.onNodeWithTag(SchedulePaymentTestTags.REFERENCE_FIELD).performScrollTo().assertIsDisplayed()
    }

    /** Pinned below the scroll, so the action does not need scrolling to. */
    @Test
    fun theActionBarIsReachableWithoutScrolling() {
        render(SchedulePaymentFixtures.filledFormState())

        composeRule.onNodeWithTag(SchedulePaymentTestTags.FORM_ACTIONS).assertIsDisplayed()
    }

    @Test
    fun theReviewStatesTheDateAndTheNotYetMadeNotice() {
        render(SchedulePaymentFixtures.reviewState())

        composeRule.onNodeWithTag(SchedulePaymentTestTags.REVIEW_DATE_ROW).assertIsDisplayed()
        composeRule.onNodeWithTag(SchedulePaymentTestTags.REVIEW_NOT_YET_MADE).performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun theInternationalReviewShowsTheChargeCaveat() {
        render(SchedulePaymentFixtures.reviewState(rail = PaymentRail.International))

        composeRule.onNodeWithTag(SchedulePaymentTestTags.REVIEW_CHARGE_CAVEAT).performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun confirmingOnTheReviewStagesTheConsent() {
        render(SchedulePaymentFixtures.reviewState())

        composeRule.onNodeWithTag(SchedulePaymentTestTags.CONFIRM_BUTTON).performScrollTo().performClick()

        assertTrue(SchedulePaymentAction.ConfirmAndStageConsent in actions)
    }

    /**
     * The waiting state can be left; the staging state cannot.
     *
     * A customer returning through the task switcher delivers no callback at all, and the immediate
     * rail's equivalent state waits on one forever.
     */
    @Test
    fun awaitingAuthorisationOffersAWayOutAndStagingDoesNot() {
        render(
            SchedulePaymentFixtures.submittingState(stage = SchedulePaymentStage.AwaitingAuthorisation),
        )

        composeRule.onNodeWithTag(SchedulePaymentTestTags.ABANDON_AUTHORISATION_BUTTON).assertIsDisplayed()
    }

    @Test
    fun abandoningAuthorisationDispatchesTheAction() {
        render(
            SchedulePaymentFixtures.submittingState(stage = SchedulePaymentStage.AwaitingAuthorisation),
        )

        composeRule.onNodeWithTag(SchedulePaymentTestTags.ABANDON_AUTHORISATION_BUTTON).performClick()

        assertEquals(listOf<SchedulePaymentAction>(SchedulePaymentAction.AbandonAuthorisation), actions)
    }

    @Test
    fun aRefusedDateOffersADateChangeAndNoRetry() {
        render(SchedulePaymentFixtures.errorState(SchedulePaymentErrorKind.DateRefused))

        composeRule.onNodeWithTag(SchedulePaymentTestTags.CHANGE_DATE_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithTag(SchedulePaymentTestTags.RETRY_BUTTON).assertDoesNotExist()
    }

    /** The loading convention: a shimmer skeleton, not the spinner the mockups draw. */
    @Test
    fun loadingRendersTheSkeletonRatherThanASpinner() {
        render(SchedulePaymentFixtures.loadingState())

        composeRule.onNodeWithTag(SchedulePaymentTestTags.SKELETON).assertIsDisplayed()
        composeRule.onNodeWithTag(SchedulePaymentTestTags.DATE_FIELD).assertDoesNotExist()
    }
}
