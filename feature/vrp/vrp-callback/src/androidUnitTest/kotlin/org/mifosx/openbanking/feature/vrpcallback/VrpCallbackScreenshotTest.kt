/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.vrpcallback

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
import org.mifosx.openbanking.feature.vrpcallback.callback.CallbackErrorKind
import org.mifosx.openbanking.feature.vrpcallback.callback.CallbackStage
import org.mifosx.openbanking.feature.vrpcallback.callback.VrpCallbackScreenContent
import org.mifosx.openbanking.feature.vrpcallback.callback.VrpCallbackState
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import template.core.base.designsystem.theme.KptTheme

private const val ROBOLECTRIC_SDK = 34

/** Golden images for the return from the bank, written under `build/outputs/roborazzi/`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(manifest = Config.NONE, sdk = [ROBOLECTRIC_SDK], qualifiers = "w412dp-h892dp")
class VrpCallbackScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun validatingGolden() =
        capture("validating", VrpCallbackFixtures.workingState(CallbackStage.Validating))

    @Test
    fun exchangingGolden() =
        capture("exchanging", VrpCallbackFixtures.workingState(CallbackStage.Exchanging))

    @Test
    fun savingGolden() = capture("saving", VrpCallbackFixtures.workingState(CallbackStage.Saving))

    @Test
    fun confirmingGolden() =
        capture("confirming", VrpCallbackFixtures.workingState(CallbackStage.Confirming))

    @Test
    fun successGolden() = capture("success", VrpCallbackFixtures.successState())

    @Test
    fun unusableGolden() = capture("unusable", VrpCallbackFixtures.unusableState())

    @Test
    fun callbackInvalidGolden() =
        capture("failed_invalid", VrpCallbackFixtures.failedState(CallbackErrorKind.CallbackInvalid))

    @Test
    fun codeExpiredGolden() =
        capture("failed_expired", VrpCallbackFixtures.failedState(CallbackErrorKind.CodeExpiredOrUsed))

    /** The dangerous one: the bank has a live consent and this app stored nothing. */
    @Test
    fun authorityNotSavedGolden() = capture(
        "failed_not_saved",
        VrpCallbackFixtures.failedState(
            CallbackErrorKind.AuthorityNotSaved,
            VrpCallbackFixtures.SUPPORT_REFERENCE,
        ),
    )

    /** The mildest: the VRP is set up and only the read-back failed. */
    @Test
    fun confirmationFailedGolden() = capture(
        "failed_confirmation",
        VrpCallbackFixtures.failedState(CallbackErrorKind.ConfirmationFailed),
    )

    @Test
    fun networkUnavailableGolden() =
        capture("failed_network", VrpCallbackFixtures.failedState(CallbackErrorKind.NetworkUnavailable))

    private fun capture(state: String, screenState: VrpCallbackState) {
        composeRule.setContent {
            Themed {
                VrpCallbackScreenContent(state = screenState, onAction = {})
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/vrp_callback_$state.png")
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
