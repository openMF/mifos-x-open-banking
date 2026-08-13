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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mifosx.openbanking.core.model.banking.payment.PaymentRail
import org.mifosx.openbanking.feature.paymentsstandingorder.ui.StandingOrderErrorKind
import org.mifosx.openbanking.feature.paymentsstandingorder.ui.StandingOrderStage
import org.mifosx.openbanking.feature.paymentsstandingorder.ui.StandingOrderState
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import template.core.base.designsystem.KptTheme

private const val ROBOLECTRIC_SDK = 34

/**
 * A tall device, because the form scrolls well past a phone screen.
 *
 * Same reasoning as send-money's: Roborazzi captures the composition root, which Robolectric sizes
 * from the device, so constraining a Surface changes nothing about what lands in the image. At the
 * default height the capture stops inside the payer list — above the date field, which is the one
 * control this feature adds and the only thing worth looking at.
 */
private const val TALL_DEVICE = "w412dp-h1800dp"

/**
 * Golden-image coverage for [StandingOrderScreenContent].
 *
 * Goldens are **not tracked**: `recordRoborazziDemoDebug` writes them under
 * `build/outputs/roborazzi/` for local review, so this suite fails on a composition crash and lets a
 * change be eyeballed, but `verifyRoborazziDemoDebug` has no baseline in a clean checkout.
 *
 * These are also the only check on the templated strings. A literal `%%` renders as `%%` under
 * Compose Multiplatform resources, and no tag or count assertion catches it — which matters here
 * because the not-yet-made notice interpolates the date into its body.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(manifest = Config.NONE, sdk = [ROBOLECTRIC_SDK], qualifiers = TALL_DEVICE)
class StandingOrderScreenScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun domesticFormGolden() = capture("form_domestic", StandingOrderFixtures.formState())

    /**
     * The international form, which is not a styling variation on the one above it.
     *
     * It carries no reference field — `RemittanceInformation` is refused `U005` on this rail — and a
     * different date helper, because weekends are excluded here. The difference between the two is
     * the thing most worth being able to see.
     */
    @Test
    fun internationalFormGolden() =
        capture("form_international", StandingOrderFixtures.formState(rail = PaymentRail.International))

    /** The date field before anything is chosen: placeholder text, muted, with the rule underneath. */
    @Test
    fun emptyDateFieldGolden() =
        capture("form_date_empty", StandingOrderFixtures.formState(firstPaymentDate = null))

    /** And with a date set, which is the state the review is reachable from. */
    @Test
    fun filledDateFieldGolden() = capture("form_date_filled", StandingOrderFixtures.filledFormState())

    /**
     * The domestic review, whose hero leads with the date rather than the amount.
     *
     * The order is the whole difference from the immediate rail's review, and it is a layout fact no
     * assertion describes.
     */
    @Test
    fun domesticReviewGolden() = capture("review_domestic", StandingOrderFixtures.reviewState())

    /**
     * The international review, which must show a charge bearer and no figure.
     *
     * The absence is the point, and an image is the only way to confirm the caveat reads as an
     * explanation rather than as a missing value.
     */
    @Test
    fun internationalReviewGolden() =
        capture("review_international", StandingOrderFixtures.reviewState(rail = PaymentRail.International))

    /** The waiting state, which carries a way out the immediate rail's equivalent does not. */
    @Test
    fun awaitingAuthorisationGolden() = capture(
        "awaiting_authorisation",
        StandingOrderFixtures.submittingState(stage = StandingOrderStage.AwaitingAuthorisation),
    )

    @Test
    fun stagingGolden() = capture(
        "staging",
        StandingOrderFixtures.submittingState(stage = StandingOrderStage.StagingConsent),
    )

    /** A refused date, offering a date change and no retry. */
    @Test
    fun dateRefusedGolden() =
        capture("error_date_refused", StandingOrderFixtures.errorState(StandingOrderErrorKind.FirstDateRefused))

    /** An app-side defect, which offers nothing — the hardest error state to word well. */
    @Test
    fun malformedRequestGolden() = capture(
        "error_request_malformed",
        StandingOrderFixtures.errorState(StandingOrderErrorKind.RequestMalformed),
    )

    @Test
    fun skeletonGolden() = capture("skeleton", StandingOrderFixtures.loadingState())

    private fun capture(state: String, screenState: StandingOrderState) {
        composeRule.setContent {
            KptTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    StandingOrderScreenContent(
                        state = screenState,
                        onAction = {},
                        onNavigateToConsents = {},
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/standing_order_$state.png")
    }
}
