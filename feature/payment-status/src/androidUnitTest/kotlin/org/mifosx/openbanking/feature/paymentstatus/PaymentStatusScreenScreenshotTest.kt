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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mifosx.openbanking.core.model.banking.payment.PaymentDisposition
import org.mifosx.openbanking.core.model.banking.payment.PaymentStatus
import org.mifosx.openbanking.feature.paymentstatus.ui.PaymentStatusErrorKind
import org.mifosx.openbanking.feature.paymentstatus.ui.PaymentStatusState
import org.mifosx.openbanking.feature.paymentstatus.ui.PaymentStepState
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import template.core.base.designsystem.KptTheme

private const val ROBOLECTRIC_SDK = 34
private const val FRAME_WIDTH = 412
private const val FRAME_HEIGHT = 1800
private const val SETTLED_AT = "4 Aug 2026, 09:00"
private const val REJECTED_AT = "3 Aug 2026, 16:40"

/**
 * Golden-image coverage for [PaymentStatusScreenContent], captured with Roborazzi under
 * Robolectric's native graphics (no device). Goldens are written under `build/outputs/roborazzi/`
 * — build output, not `src/` — and `recordRoborazziDemoDebug` writes them while
 * `verifyRoborazziDemoDebug` fails on pixel drift.
 *
 * The device qualifier is taller than the default: this screen stacks a summary card, a timeline
 * and a details list, and a shorter frame would crop the very stages these goldens exist to show. A
 * taller `Surface` alone does not change what is captured — the qualifier is what does.
 *
 * One golden per [PaymentDisposition] so the timeline's three outcomes are each reviewable, plus
 * the two states the disposition cannot express: a still-unaccepted payment (`Pending` fourth
 * stage) and a refresh that failed over live content.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(manifest = Config.NONE, sdk = [ROBOLECTRIC_SDK], qualifiers = "w412dp-h1800dp")
class PaymentStatusScreenScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun inProgressGolden() = capture("in_progress", PaymentStatusFixtures.contentState())

    @Test
    fun receivedGolden() = capture(
        "received",
        PaymentStatusFixtures.contentState(
            status = PaymentStatus.Received,
            timeline = PaymentStatusFixtures.timeline(completedState = PaymentStepState.Pending),
        ),
    )

    @Test
    fun settledGolden() = capture(
        "settled",
        PaymentStatusFixtures.contentState(
            disposition = PaymentDisposition.TerminalSuccess,
            status = PaymentStatus.AcceptedCreditSettlementCompleted,
            // Same value in both places: the view model derives the fourth stage from `settledAt`,
            // so a golden where they differ would depict a state the app cannot reach.
            settledAt = SETTLED_AT,
            timeline = PaymentStatusFixtures.timeline(
                completedState = PaymentStepState.Done,
                completedAt = SETTLED_AT,
            ),
        ),
    )

    @Test
    fun rejectedGolden() = capture(
        "rejected",
        PaymentStatusFixtures.contentState(
            disposition = PaymentDisposition.TerminalFailure,
            status = PaymentStatus.Rejected,
            // A refused payment never settles, so there is no settlement row; the fourth stage is
            // dated from the bank's own status-update time, which is what `statusChangedAt` holds.
            settledAt = "",
            statusChangedAt = REJECTED_AT,
            timeline = PaymentStatusFixtures.timeline(
                completedState = PaymentStepState.Failed,
                completedAt = REJECTED_AT,
            ),
        ),
    )

    @Test
    fun refreshFailedGolden() = capture(
        "refresh_failed",
        PaymentStatusFixtures.contentState(refreshFailure = PaymentStatusErrorKind.NetworkError),
    )

    @Test
    fun twoChargesGolden() = capture(
        "two_charges",
        PaymentStatusFixtures.contentState(charges = PaymentStatusFixtures.twoCharges()),
    )

    /**
     * A scheduled payment before its date — the state this screen had no golden for.
     *
     * Worth an image rather than an assertion because what matters is what it does NOT say. There is
     * no "Settles" row, nothing claims the money has moved, and the date shown is the one the
     * customer chose rather than the creation timestamp the bank echoes back in
     * `ExpectedSettlementDateTime`.
     */
    @Test
    fun scheduledGolden() = capture("scheduled", PaymentStatusFixtures.scheduledState())

    @Test
    fun loadingGolden() = capture("loading", PaymentStatusFixtures.loadingState())

    @Test
    fun errorGolden() =
        capture("error", PaymentStatusFixtures.errorState(PaymentStatusErrorKind.PaymentNotFound))

    private fun capture(state: String, screenState: PaymentStatusState) {
        composeRule.setContent {
            Themed {
                PaymentStatusScreenContent(
                    state = screenState,
                    onAction = {},
                    onStartNewPayment = {},
                )
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/payment_status_$state.png")
    }

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        KptTheme {
            Surface(
                modifier = Modifier
                    .width(FRAME_WIDTH.dp)
                    .height(FRAME_HEIGHT.dp)
                    .background(MaterialTheme.colorScheme.background),
                content = content,
            )
        }
    }
}
