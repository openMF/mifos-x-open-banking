/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.vrppayment

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mifosx.openbanking.core.designsystem.theme.MifosXOpenBankingTheme
import org.mifosx.openbanking.feature.vrppayment.payment.AmountProblem
import org.mifosx.openbanking.feature.vrppayment.payment.PaymentFailureKind
import org.mifosx.openbanking.feature.vrppayment.payment.SubmissionUi
import org.mifosx.openbanking.feature.vrppayment.payment.VrpPaymentScreenContent
import org.mifosx.openbanking.feature.vrppayment.payment.VrpPaymentState
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import template.core.base.designsystem.theme.KptTheme

private const val ROBOLECTRIC_SDK = 34

/** Golden images for paying under a VRP, written under `build/outputs/roborazzi/`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(manifest = Config.NONE, sdk = [ROBOLECTRIC_SDK], qualifiers = "w412dp-h892dp")
class VrpPaymentScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun amountGolden() = capture("amount", VrpPaymentFixtures.amountState())

    /** The resting state has to show something: an empty field paints nothing to tap. */
    @Test
    fun amountEmptyGolden() = capture("amount_empty", VrpPaymentFixtures.amountState(amount = ""))

    @Test
    fun amountOverPerPaymentGolden() = capture(
        "amount_over_per_payment",
        VrpPaymentFixtures.amountState("500.00", AmountProblem.OverPerPayment),
    )

    @Test
    fun amountOverRemainingGolden() = capture(
        "amount_over_remaining",
        VrpPaymentFixtures.amountState("400.00", AmountProblem.OverRemaining),
    )

    @Test
    fun amountFundsWarningGolden() =
        capture("amount_funds_warning", VrpPaymentFixtures.amountState(fundsWarning = true))

    @Test
    fun reviewGolden() = capture("review", VrpPaymentFixtures.reviewState())

    @Test
    fun sendingGolden() = capture("sending", VrpPaymentFixtures.reviewState(SubmissionUi.Sending))

    @Test
    fun sentInProgressGolden() = capture("sent_in_progress", VrpPaymentFixtures.sentState(settled = false))

    @Test
    fun sentSettledGolden() = capture("sent_settled", VrpPaymentFixtures.sentState(settled = true))

    @Test
    fun rejectedGolden() =
        capture("failed_rejected", VrpPaymentFixtures.failedState(PaymentFailureKind.Rejected))

    /** The one that must never be resent: the money may already have moved. */
    @Test
    fun unconfirmedGolden() = capture(
        "failed_unconfirmed",
        VrpPaymentFixtures.failedState(PaymentFailureKind.Unconfirmed, VrpPaymentFixtures.SUPPORT_REFERENCE),
    )

    @Test
    fun networkFailureGolden() =
        capture("failed_network", VrpPaymentFixtures.failedState(PaymentFailureKind.NetworkUnavailable))

    @Test
    fun overLimitGolden() = capture(
        "failed_over_limit",
        VrpPaymentFixtures.failedState(PaymentFailureKind.OverLimit, VrpPaymentFixtures.SUPPORT_REFERENCE),
    )

    @Test
    fun consentUnusableGolden() =
        capture("failed_consent_unusable", VrpPaymentFixtures.failedState(PaymentFailureKind.ConsentUnusable))

    @Test
    fun needsReauthorisationGolden() = capture(
        "failed_needs_reauthorisation",
        VrpPaymentFixtures.failedState(PaymentFailureKind.NeedsReauthorisation),
    )

    @Test
    fun unusableGolden() = capture("unusable", VrpPaymentFixtures.unusableState())

    @Test
    fun errorGolden() = capture("error", VrpPaymentFixtures.errorState())

    @Test
    fun loadingGolden() = capture("loading", VrpPaymentFixtures.loadingState())

    private fun capture(state: String, screenState: VrpPaymentState) {
        composeRule.setContent {
            Themed {
                VrpPaymentScreenContent(state = screenState, onAction = {}, onBack = {})
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/vrp_payment_$state.png")
    }

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        MifosXOpenBankingTheme(darkTheme = false) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = KptTheme.colorScheme.background,
                content = content,
            )
        }
    }
}
