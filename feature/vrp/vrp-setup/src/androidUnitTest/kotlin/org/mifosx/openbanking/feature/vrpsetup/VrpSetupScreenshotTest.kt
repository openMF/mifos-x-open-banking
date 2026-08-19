/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.vrpsetup

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
import org.mifosx.openbanking.feature.vrpsetup.setup.StagingFailure
import org.mifosx.openbanking.feature.vrpsetup.setup.StagingUi
import org.mifosx.openbanking.feature.vrpsetup.setup.VrpSetupScreenContent
import org.mifosx.openbanking.feature.vrpsetup.setup.VrpSetupState
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import template.core.base.designsystem.theme.KptTheme

private const val ROBOLECTRIC_SDK = 34

/** Golden images for VRP setup, written under `build/outputs/roborazzi/`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(manifest = Config.NONE, sdk = [ROBOLECTRIC_SDK], qualifiers = "w412dp-h1400dp")
class VrpSetupScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun formSavedPayeeGolden() = capture("form_saved_payee", VrpSetupFixtures.formState())

    @Test
    fun formEmptyGolden() =
        capture("form_empty", VrpSetupFixtures.formState(form = VrpSetupFixtures.emptyForm()))

    @Test
    fun formPayNewGolden() = capture(
        "form_pay_new",
        VrpSetupFixtures.formState(form = VrpSetupFixtures.filledForm(payNewSelected = true)),
    )

    @Test
    fun formPayerExpandedGolden() = capture(
        "form_payer_expanded",
        VrpSetupFixtures.formState(form = VrpSetupFixtures.filledForm(payerExpanded = true)),
    )

    @Test
    fun reviewGolden() = capture("review", VrpSetupFixtures.reviewState())

    @Test
    fun reviewStagingFailedGolden() = capture(
        "review_staging_failed",
        VrpSetupFixtures.reviewState(StagingUi.Failed(StagingFailure.NetworkUnavailable)),
    )

    @Test
    fun loadingGolden() = capture("loading", VrpSetupFixtures.loadingState())

    @Test
    fun noAccountsGolden() = capture("no_accounts", VrpSetupFixtures.noAccountsState())

    @Test
    fun errorGolden() = capture("error", VrpSetupFixtures.errorState())

    private fun capture(state: String, screenState: VrpSetupState) {
        composeRule.setContent {
            Themed {
                VrpSetupScreenContent(
                    state = screenState,
                    onAction = {},
                    onBack = {},
                )
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/vrp_setup_$state.png")
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
