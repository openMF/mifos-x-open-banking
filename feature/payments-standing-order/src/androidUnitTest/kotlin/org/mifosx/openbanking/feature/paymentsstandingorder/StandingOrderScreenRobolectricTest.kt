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

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mifosx.openbanking.core.model.banking.payment.PaymentRail
import org.mifosx.openbanking.feature.paymentsstandingorder.ui.StandingOrderAction
import org.mifosx.openbanking.feature.paymentsstandingorder.ui.StandingOrderDateRole
import org.mifosx.openbanking.feature.paymentsstandingorder.ui.StandingOrderErrorKind
import org.mifosx.openbanking.feature.paymentsstandingorder.ui.StandingOrderStage
import org.mifosx.openbanking.feature.paymentsstandingorder.ui.StandingOrderState
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val ROBOLECTRIC_SDK = 34

/**
 * Renders [StandingOrderScreenContent] under Robolectric (JVM, no device), driven through the
 * shared [StandingOrderTestTags]. An on-device mirror lives in
 * [StandingOrderScreenInstrumentedTest].
 *
 * The recurring question is what each rail asks for and what it must not: the two accept genuinely
 * different fields, and sending the wrong one is a `400` rather than a matter of taste.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [ROBOLECTRIC_SDK])
class StandingOrderScreenRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<StandingOrderAction>()

    private fun render(state: StandingOrderState) {
        composeRule.setContent {
            StandingOrderScreenContent(
                state = state,
                onAction = { actions.add(it) },
                onNavigateToConsents = {},
            )
        }
    }

    @Test
    fun theFormCarriesEveryPickerTheDateAndTheAmountOnOneScroll() {
        render(StandingOrderFixtures.formState())

        composeRule.onNodeWithTag(StandingOrderTestTags.FORM_PAGE).assertIsDisplayed()
        composeRule.onNodeWithTag(StandingOrderTestTags.RAIL_TOGGLE).assertIsDisplayed()
        composeRule.onNodeWithTag(StandingOrderTestTags.PAYER_PICKER).assertIsDisplayed()
        composeRule.onNodeWithTag(StandingOrderTestTags.FIRST_DATE_FIELD).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(StandingOrderTestTags.AMOUNT_CARD).performScrollTo().assertIsDisplayed()
    }

    /** Tapping the date row is the only way to set a date — it is not a text field. */
    @Test
    fun tappingTheDateFieldAsksToOpenThePicker() {
        render(StandingOrderFixtures.formState())

        composeRule.onNodeWithTag(StandingOrderTestTags.FIRST_DATE_FIELD).performScrollTo().performClick()

        assertEquals(
            listOf<StandingOrderAction>(StandingOrderAction.OpenDatePicker(StandingOrderDateRole.First)),
            actions,
        )
    }

    /**
     * `RemittanceInformation` is refused `U005` internationally — so the field is **disabled, not
     * removed**, with a reason beneath it.
     *
     * The assertion is deliberately `assertIsNotEnabled` and not `assertDoesNotExist`. Absence is
     * what this test used to check, and absence passes just as happily when the field was never
     * built at all — which is precisely what had happened to the two amount overrides beside it.
     */
    @Test
    fun theInternationalFormDisablesTheReferenceFieldAndSaysWhy() {
        render(StandingOrderFixtures.formState(rail = PaymentRail.International))

        composeRule.onNodeWithTag(StandingOrderTestTags.REFERENCE_FIELD)
            .performScrollTo()
            .assertIsNotEnabled()
        composeRule.onNodeWithTag(StandingOrderTestTags.REFERENCE_REASON, useUnmergedTree = true).assertExists()
    }

    /** The same treatment for the two amount overrides that rail has no wire member for. */
    @Test
    fun theInternationalFormDisablesBothAmountOverridesAndSaysWhy() {
        render(StandingOrderFixtures.formState(rail = PaymentRail.International))

        composeRule.onNodeWithTag(StandingOrderTestTags.RECURRING_AMOUNT_FIELD)
            .performScrollTo()
            .assertIsNotEnabled()
        composeRule.onNodeWithTag(StandingOrderTestTags.FINAL_AMOUNT_FIELD)
            .performScrollTo()
            .assertIsNotEnabled()
        composeRule.onNodeWithTag(StandingOrderTestTags.RECURRING_AMOUNT_REASON, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(StandingOrderTestTags.FINAL_AMOUNT_REASON, useUnmergedTree = true).assertExists()
    }

    /** And on the rail that can carry them, they are offered and usable. */
    @Test
    fun theDomesticFormOffersBothAmountOverridesEnabled() {
        render(StandingOrderFixtures.formState())

        composeRule.onNodeWithTag(StandingOrderTestTags.RECURRING_AMOUNT_FIELD)
            .performScrollTo()
            .assertIsEnabled()
        composeRule.onNodeWithTag(StandingOrderTestTags.FINAL_AMOUNT_FIELD)
            .performScrollTo()
            .assertIsEnabled()
        composeRule.onNodeWithTag(StandingOrderTestTags.RECURRING_AMOUNT_REASON, useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun theDomesticFormOffersAReferenceField() {
        render(StandingOrderFixtures.formState())

        composeRule.onNodeWithTag(StandingOrderTestTags.REFERENCE_FIELD).performScrollTo().assertIsDisplayed()
    }

    /** Pinned below the scroll, so the action does not need scrolling to. */
    @Test
    fun theActionBarIsReachableWithoutScrolling() {
        render(StandingOrderFixtures.filledFormState())

        composeRule.onNodeWithTag(StandingOrderTestTags.FORM_ACTIONS).assertIsDisplayed()
    }

    @Test
    fun theReviewStatesTheDateAndTheNotYetMadeNotice() {
        render(StandingOrderFixtures.reviewState())

        composeRule.onNodeWithTag(StandingOrderTestTags.REVIEW_SCHEDULE_ROW).assertIsDisplayed()
        composeRule.onNodeWithTag(StandingOrderTestTags.REVIEW_NOT_YET_MADE).performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun theInternationalReviewShowsTheChargeCaveat() {
        render(StandingOrderFixtures.reviewState(rail = PaymentRail.International))

        composeRule.onNodeWithTag(StandingOrderTestTags.REVIEW_CHARGE_CAVEAT).performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun confirmingOnTheReviewStagesTheConsent() {
        render(StandingOrderFixtures.reviewState())

        composeRule.onNodeWithTag(StandingOrderTestTags.CONFIRM_BUTTON).performScrollTo().performClick()

        assertTrue(StandingOrderAction.ConfirmAndStageConsent in actions)
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
            StandingOrderFixtures.submittingState(stage = StandingOrderStage.AwaitingAuthorisation),
        )

        composeRule.onNodeWithTag(StandingOrderTestTags.ABANDON_AUTHORISATION_BUTTON).assertIsDisplayed()
    }

    @Test
    fun abandoningAuthorisationDispatchesTheAction() {
        render(
            StandingOrderFixtures.submittingState(stage = StandingOrderStage.AwaitingAuthorisation),
        )

        composeRule.onNodeWithTag(StandingOrderTestTags.ABANDON_AUTHORISATION_BUTTON).performClick()

        assertEquals(listOf<StandingOrderAction>(StandingOrderAction.AbandonAuthorisation), actions)
    }

    @Test
    fun aRefusedDateOffersADateChangeAndNoRetry() {
        render(StandingOrderFixtures.errorState(StandingOrderErrorKind.FirstDateRefused))

        composeRule.onNodeWithTag(StandingOrderTestTags.CHANGE_DATE_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithTag(StandingOrderTestTags.RETRY_BUTTON).assertDoesNotExist()
    }

    /** The loading convention: a shimmer skeleton, not the spinner the mockups draw. */
    @Test
    fun loadingRendersTheSkeletonRatherThanASpinner() {
        render(StandingOrderFixtures.loadingState())

        composeRule.onNodeWithTag(StandingOrderTestTags.SKELETON).assertIsDisplayed()
        composeRule.onNodeWithTag(StandingOrderTestTags.FIRST_DATE_FIELD).assertDoesNotExist()
    }
}
