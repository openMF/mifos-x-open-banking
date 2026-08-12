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

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import org.mifosx.openbanking.core.model.banking.payment.PaymentRail
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.SchedulePaymentErrorKind
import kotlin.test.Test

/**
 * What each state actually puts on screen, driven through the stateless content composable.
 *
 * Assertions are on test tags, never on copy — the tags are the contract and the wording is a string
 * resource that may be reworded without breaking the journey.
 *
 * The date picker is deliberately absent from this suite: it renders in its own window, where
 * tag-based assertions are unreliable. Its rules are asserted directly against
 * `SchedulePaymentDateRulesTest` instead, which is where the behaviour lives anyway.
 */
@OptIn(ExperimentalTestApi::class)
class SchedulePaymentScreenUiTest {

    /** The skeleton, not a spinner — the app's loading convention, and the mockups' one deviation. */
    @Test
    fun loadingRendersTheSkeleton() = runComposeUiTest {
        setContent {
            SchedulePaymentScreenContent(SchedulePaymentFixtures.loadingState(), {}, {})
        }

        onNodeWithTag(SchedulePaymentTestTags.SKELETON).assertIsDisplayed()
        onNodeWithTag(SchedulePaymentTestTags.DATE_FIELD).assertDoesNotExist()
    }

    @Test
    fun theFormShowsTheDateField() = runComposeUiTest {
        setContent {
            SchedulePaymentScreenContent(SchedulePaymentFixtures.formState(), {}, {})
        }

        onNodeWithTag(SchedulePaymentTestTags.DATE_FIELD).assertIsDisplayed()
    }

    @Test
    fun theFormShowsTheDateFieldOnBothRails() = runComposeUiTest {
        setContent {
            SchedulePaymentScreenContent(
                SchedulePaymentFixtures.formState(rail = PaymentRail.International),
                {},
                {},
            )
        }

        onNodeWithTag(SchedulePaymentTestTags.DATE_FIELD).assertIsDisplayed()
    }

    /**
     * The review states the date, which is what makes it a scheduled payment rather than a payment.
     */
    @Test
    fun theReviewStatesTheDate() = runComposeUiTest {
        setContent {
            SchedulePaymentScreenContent(SchedulePaymentFixtures.reviewState(), {}, {})
        }

        onNodeWithTag(SchedulePaymentTestTags.REVIEW_DATE_ROW).assertIsDisplayed()
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
            SchedulePaymentScreenContent(SchedulePaymentFixtures.reviewState(), {}, {})
        }

        onNodeWithTag(SchedulePaymentTestTags.REVIEW_NOT_YET_MADE).assertIsDisplayed()
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
            SchedulePaymentScreenContent(
                SchedulePaymentFixtures.reviewState(rail = PaymentRail.International),
                {},
                {},
            )
        }

        onNodeWithTag(SchedulePaymentTestTags.REVIEW_CHARGE_ROW).assertIsDisplayed()
        onNodeWithTag(SchedulePaymentTestTags.REVIEW_CHARGE_CAVEAT).assertIsDisplayed()
    }

    /** The caveat belongs to the international rail alone; the domestic review carries a reference. */
    @Test
    fun theDomesticReviewShowsNoChargeCaveat() = runComposeUiTest {
        setContent {
            SchedulePaymentScreenContent(SchedulePaymentFixtures.reviewState(), {}, {})
        }

        onNodeWithTag(SchedulePaymentTestTags.REVIEW_CHARGE_CAVEAT).assertDoesNotExist()
        onNodeWithTag(SchedulePaymentTestTags.REVIEW_REFERENCE).assertIsDisplayed()
    }

    /** `RemittanceInformation` is refused `U005` internationally, so there is no reference row. */
    @Test
    fun theInternationalReviewCarriesNoReference() = runComposeUiTest {
        setContent {
            SchedulePaymentScreenContent(
                SchedulePaymentFixtures.reviewState(rail = PaymentRail.International),
                {},
                {},
            )
        }

        onNodeWithTag(SchedulePaymentTestTags.REVIEW_REFERENCE).assertDoesNotExist()
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
            SchedulePaymentScreenContent(
                SchedulePaymentFixtures.submittingState(
                    stage = org.mifosx.openbanking.feature.paymentsschedulepayment.ui
                        .SchedulePaymentStage.AwaitingAuthorisation,
                ),
                {},
                {},
            )
        }

        onNodeWithTag(SchedulePaymentTestTags.ABANDON_AUTHORISATION_BUTTON).assertIsDisplayed()
    }

    @Test
    fun stagingOffersNoWayOut() = runComposeUiTest {
        setContent {
            SchedulePaymentScreenContent(
                SchedulePaymentFixtures.submittingState(
                    stage = org.mifosx.openbanking.feature.paymentsschedulepayment.ui
                        .SchedulePaymentStage.StagingConsent,
                ),
                {},
                {},
            )
        }

        onNodeWithTag(SchedulePaymentTestTags.ABANDON_AUTHORISATION_BUTTON).assertDoesNotExist()
    }

    /** A refused date offers a date change, and does not offer a Retry that could only fail again. */
    @Test
    fun aRefusedDateOffersChangingTheDate() = runComposeUiTest {
        setContent {
            SchedulePaymentScreenContent(
                SchedulePaymentFixtures.errorState(SchedulePaymentErrorKind.DateRefused),
                {},
                {},
            )
        }

        onNodeWithTag(SchedulePaymentTestTags.CHANGE_DATE_BUTTON).assertIsDisplayed()
        onNodeWithTag(SchedulePaymentTestTags.RETRY_BUTTON).assertDoesNotExist()
    }

    /** An app-side defect offers nothing at all — there is no action that would help. */
    @Test
    fun aMalformedRequestOffersNoRetry() = runComposeUiTest {
        setContent {
            SchedulePaymentScreenContent(
                SchedulePaymentFixtures.errorState(SchedulePaymentErrorKind.RequestMalformed),
                {},
                {},
            )
        }

        onNodeWithTag(SchedulePaymentTestTags.RETRY_BUTTON).assertDoesNotExist()
        onNodeWithTag(SchedulePaymentTestTags.CHANGE_DATE_BUTTON).assertDoesNotExist()
    }
}
