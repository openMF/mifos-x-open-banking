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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import org.mifosx.openbanking.core.model.banking.payment.PaymentRail
import org.mifosx.openbanking.feature.paymentsstandingorder.ui.StandingOrderErrorKind
import kotlin.test.Test

/**
 * What each state actually puts on screen, driven through the stateless content composable.
 *
 * Assertions are on test tags, never on copy — the tags are the contract and the wording is a string
 * resource that may be reworded without breaking the journey.
 *
 * The date picker is deliberately absent from this suite: it renders in its own window, where
 * tag-based assertions are unreliable. Its rules are asserted directly against
 * `StandingOrderDateRulesTest` instead, which is where the behaviour lives anyway.
 */
@OptIn(ExperimentalTestApi::class)
class StandingOrderScreenUiTest {

    /** The skeleton, not a spinner — the app's loading convention, and the mockups' one deviation. */
    @Test
    fun loadingRendersTheSkeleton() = runComposeUiTest {
        setContent {
            StandingOrderScreenContent(StandingOrderFixtures.loadingState(), {}, {})
        }

        onNodeWithTag(StandingOrderTestTags.SKELETON).assertIsDisplayed()
        onNodeWithTag(StandingOrderTestTags.FIRST_DATE_FIELD).assertDoesNotExist()
    }

    @Test
    fun theFormShowsTheDateField() = runComposeUiTest {
        setContent {
            StandingOrderScreenContent(StandingOrderFixtures.formState(), {}, {})
        }

        onNodeWithTag(StandingOrderTestTags.FIRST_DATE_FIELD).assertIsDisplayed()
    }

    @Test
    fun theFormShowsTheDateFieldOnBothRails() = runComposeUiTest {
        setContent {
            StandingOrderScreenContent(
                StandingOrderFixtures.formState(rail = PaymentRail.International),
                {},
                {},
            )
        }

        onNodeWithTag(StandingOrderTestTags.FIRST_DATE_FIELD).assertIsDisplayed()
    }

    /**
     * The review states the date, which is what makes it a scheduled payment rather than a payment.
     */
    @Test
    fun theReviewStatesTheSchedule() = runComposeUiTest {
        setContent {
            StandingOrderScreenContent(StandingOrderFixtures.reviewState(), {}, {})
        }

        onNodeWithTag(StandingOrderTestTags.REVIEW_SCHEDULE_ROW).assertIsDisplayed()
    }

    /**
     * The notice is present before the customer can commit, not only after.
     *
     * Telling someone a mandate is irreversible once it already is satisfies the letter of the
     * obligation and misses its point.
     */
    @Test
    fun theReviewCarriesTheIrreversibilityNotice() = runComposeUiTest {
        setContent {
            StandingOrderScreenContent(StandingOrderFixtures.reviewState(), {}, {})
        }

        onNodeWithTag(StandingOrderTestTags.IRREVERSIBLE_NOTICE).assertIsDisplayed()
        onNodeWithTag(StandingOrderTestTags.CONFIRM_BUTTON).assertIsDisplayed()
    }

    /**
     * The panel that refuses to overstate what has happened.
     *
     * Present on every review, both rails: nothing has moved, the money will not move until the date,
     * and the app cannot cancel it once approved.
     */
    @Test
    fun theReviewCarriesTheNotYetMadeNotice() = runComposeUiTest {
        setContent {
            StandingOrderScreenContent(StandingOrderFixtures.reviewState(), {}, {})
        }

        onNodeWithTag(StandingOrderTestTags.REVIEW_NOT_YET_MADE).assertIsDisplayed()
    }

    /**
     * The international review names the charge bearer and no figure.
     *
     * This rail declares its charge only when the payment resource is created, which is after the
     * customer has authorised — so a number here could only be invented.
     */
    @Test
    fun theInternationalReviewShowsTheChargeCaveatAndNoFigure() = runComposeUiTest {
        setContent {
            StandingOrderScreenContent(
                StandingOrderFixtures.reviewState(rail = PaymentRail.International),
                {},
                {},
            )
        }

        onNodeWithTag(StandingOrderTestTags.REVIEW_CHARGE_ROW).assertIsDisplayed()
        onNodeWithTag(StandingOrderTestTags.REVIEW_CHARGE_CAVEAT).assertIsDisplayed()
    }

    /**
     * The domestic review states a charge too — the caveat sentence is what belongs to the
     * international rail alone.
     *
     * This rail is the one HSBC quotes a real per-payment fee on, returned on the consent before
     * authorisation. Showing nothing here would read as "no charge" on the rail that has one, which
     * is why the row is asserted rather than its absence.
     */
    @Test
    fun theDomesticReviewShowsAChargeRowWithoutTheInternationalCaveat() = runComposeUiTest {
        setContent {
            StandingOrderScreenContent(StandingOrderFixtures.reviewState(), {}, {})
        }

        onNodeWithTag(StandingOrderTestTags.REVIEW_CHARGE_ROW).assertIsDisplayed()
        onNodeWithTag(StandingOrderTestTags.REVIEW_CHARGE_CAVEAT).assertDoesNotExist()
        onNodeWithTag(StandingOrderTestTags.REVIEW_REFERENCE).assertIsDisplayed()
    }

    /** `RemittanceInformation` is refused `U005` internationally, so there is no reference row. */
    @Test
    fun theInternationalReviewCarriesNoReference() = runComposeUiTest {
        setContent {
            StandingOrderScreenContent(
                StandingOrderFixtures.reviewState(rail = PaymentRail.International),
                {},
                {},
            )
        }

        onNodeWithTag(StandingOrderTestTags.REVIEW_REFERENCE).assertDoesNotExist()
    }

    /**
     * The waiting state offers a way out, and the staging state does not.
     *
     * Staging is brief and cannot be interrupted — a consent that reached the bank cannot be
     * withdrawn. Waiting can strand the screen forever, which is the defect the immediate rail has.
     */
    @Test
    fun awaitingAuthorisationOffersAWayOut() = runComposeUiTest {
        setContent {
            StandingOrderScreenContent(
                StandingOrderFixtures.submittingState(
                    stage = org.mifosx.openbanking.feature.paymentsstandingorder.ui
                        .StandingOrderStage.AwaitingAuthorisation,
                ),
                {},
                {},
            )
        }

        onNodeWithTag(StandingOrderTestTags.ABANDON_AUTHORISATION_BUTTON).assertIsDisplayed()
    }

    @Test
    fun stagingOffersNoWayOut() = runComposeUiTest {
        setContent {
            StandingOrderScreenContent(
                StandingOrderFixtures.submittingState(
                    stage = org.mifosx.openbanking.feature.paymentsstandingorder.ui
                        .StandingOrderStage.StagingConsent,
                ),
                {},
                {},
            )
        }

        onNodeWithTag(StandingOrderTestTags.ABANDON_AUTHORISATION_BUTTON).assertDoesNotExist()
    }

    /** A refused date offers a date change, and does not offer a Retry that could only fail again. */
    @Test
    fun aRefusedDateOffersChangingTheDate() = runComposeUiTest {
        setContent {
            StandingOrderScreenContent(
                StandingOrderFixtures.errorState(StandingOrderErrorKind.FirstDateRefused),
                {},
                {},
            )
        }

        onNodeWithTag(StandingOrderTestTags.CHANGE_DATE_BUTTON).assertIsDisplayed()
        onNodeWithTag(StandingOrderTestTags.RETRY_BUTTON).assertDoesNotExist()
    }

    /** An app-side defect offers nothing at all — there is no action that would help. */
    @Test
    fun aMalformedRequestOffersNoRetry() = runComposeUiTest {
        setContent {
            StandingOrderScreenContent(
                StandingOrderFixtures.errorState(StandingOrderErrorKind.RequestMalformed),
                {},
                {},
            )
        }

        onNodeWithTag(StandingOrderTestTags.RETRY_BUTTON).assertDoesNotExist()
        onNodeWithTag(StandingOrderTestTags.CHANGE_DATE_BUTTON).assertDoesNotExist()
    }
}
