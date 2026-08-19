/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.vrpconsents

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
import org.mifosx.openbanking.feature.vrpconsents.consentList.VrpConsentListScreenContent
import org.mifosx.openbanking.feature.vrpconsents.consentList.VrpConsentListState
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import template.core.base.designsystem.theme.KptTheme

private const val ROBOLECTRIC_SDK = 34

/** Golden images for the standing-payment list, written under `build/outputs/roborazzi/`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(manifest = Config.NONE, sdk = [ROBOLECTRIC_SDK], qualifiers = "w412dp-h892dp")
class VrpConsentListScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun contentGolden() = capture("content", VrpConsentsFixtures.listContentState())

    @Test
    fun loadingGolden() = capture("loading", VrpConsentsFixtures.listLoadingState())

    @Test
    fun emptyGolden() = capture("empty", VrpConsentsFixtures.listEmptyState())

    @Test
    fun errorGolden() = capture("error", VrpConsentsFixtures.listErrorState())

    private fun capture(state: String, screenState: VrpConsentListState) {
        composeRule.setContent {
            Themed {
                VrpConsentListScreenContent(
                    state = screenState,
                    onAction = {},
                    onBack = {},
                    onOpenConsent = {},
                    onNavigateToSetup = {},
                )
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/vrp_consent_list_$state.png")
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
