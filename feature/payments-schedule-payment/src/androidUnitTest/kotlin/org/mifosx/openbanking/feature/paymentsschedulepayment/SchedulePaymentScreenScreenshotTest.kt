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
import org.mifosx.openbanking.core.designsystem.theme.MifosXOpenBankingTheme
import org.mifosx.openbanking.core.model.banking.payment.PaymentRail
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.SchedulePaymentErrorKind
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.SchedulePaymentStage
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.SchedulePaymentState
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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
 * Golden-image coverage for [SchedulePaymentScreenContent].
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
class SchedulePaymentScreenScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun domesticFormGolden() = capture("form_domestic", SchedulePaymentFixtures.formState())

    /** The recent scheduled payments under the form — booked, still booking, and refused. */
    @Test
    fun recentScheduledPaymentsGolden() = capture(
        "form_recent_scheduled_payments",
        SchedulePaymentFixtures.formState(
            recentPayments = SchedulePaymentFixtures.paymentHistory(),
        ),
    )

    /**
     * The international form, which is not a styling variation on the one above it.
     *
     * It carries no reference field — `RemittanceInformation` is refused `U005` on this rail — and a
     * different date helper, because weekends are excluded here. The difference between the two is
     * the thing most worth being able to see.
     */
    @Test
    fun internationalFormGolden() =
        capture("form_international", SchedulePaymentFixtures.formState(rail = PaymentRail.International))

    /** The date field before anything is chosen: placeholder text, muted, with the rule underneath. */
    @Test
    fun emptyDateFieldGolden() =
        capture("form_date_empty", SchedulePaymentFixtures.formState(executionDate = null))

    /** And with a date set, which is the state the review is reachable from. */
    @Test
    fun filledDateFieldGolden() = capture("form_date_filled", SchedulePaymentFixtures.filledFormState())

    /**
     * The domestic review, whose hero leads with the date rather than the amount.
     *
     * The order is the whole difference from the immediate rail's review, and it is a layout fact no
     * assertion describes.
     */
    @Test
    fun domesticReviewGolden() = capture("review_domestic", SchedulePaymentFixtures.reviewState())

    /**
     * The international review, which must show a charge bearer and no figure.
     *
     * The absence is the point, and an image is the only way to confirm the caveat reads as an
     * explanation rather than as a missing value.
     */
    @Test
    fun internationalReviewGolden() =
        capture("review_international", SchedulePaymentFixtures.reviewState(rail = PaymentRail.International))

    /** The waiting state, which carries a way out the immediate rail's equivalent does not. */
    @Test
    fun awaitingAuthorisationGolden() = capture(
        "awaiting_authorisation",
        SchedulePaymentFixtures.submittingState(stage = SchedulePaymentStage.AwaitingAuthorisation),
    )

    @Test
    fun stagingGolden() = capture(
        "staging",
        SchedulePaymentFixtures.submittingState(stage = SchedulePaymentStage.StagingConsent),
    )

    /** A refused date, offering a date change and no retry. */
    @Test
    fun dateRefusedGolden() =
        capture("error_date_refused", SchedulePaymentFixtures.errorState(SchedulePaymentErrorKind.DateRefused))

    /** An app-side defect, which offers nothing — the hardest error state to word well. */
    @Test
    fun malformedRequestGolden() = capture(
        "error_request_malformed",
        SchedulePaymentFixtures.errorState(SchedulePaymentErrorKind.RequestMalformed),
    )

    @Test
    fun skeletonGolden() = capture("skeleton", SchedulePaymentFixtures.loadingState())

    private fun capture(state: String, screenState: SchedulePaymentState) {
        composeRule.setContent {
            // The app's theme, not a bare KptTheme: that leaves MaterialTheme at its own defaults
            // and every golden renders in Material's baseline purple rather than the app palette.
            MifosXOpenBankingTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    SchedulePaymentScreenContent(
                        state = screenState,
                        onAction = {},
                        onNavigateToConsents = {},
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/schedule_payment_$state.png")
    }
}
