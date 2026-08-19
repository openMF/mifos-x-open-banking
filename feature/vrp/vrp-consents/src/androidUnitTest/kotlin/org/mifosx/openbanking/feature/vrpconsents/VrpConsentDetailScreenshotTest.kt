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
import org.mifosx.openbanking.feature.vrpconsents.consentDetail.RevokePhase
import org.mifosx.openbanking.feature.vrpconsents.consentDetail.VrpConsentDetailScreenContent
import org.mifosx.openbanking.feature.vrpconsents.consentDetail.VrpConsentDetailState
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import template.core.base.designsystem.theme.KptTheme

private const val ROBOLECTRIC_SDK = 34

/** Golden images for one standing payment, written under `build/outputs/roborazzi/`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(manifest = Config.NONE, sdk = [ROBOLECTRIC_SDK], qualifiers = "w412dp-h1800dp")
class VrpConsentDetailScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun contentGolden() = capture("content", VrpConsentsFixtures.detailContentState())

    @Test
    fun loadingGolden() = capture("loading", VrpConsentsFixtures.detailLoadingState())

    @Test
    fun noPaymentsGolden() = capture(
        "no_payments",
        VrpConsentsFixtures.detailState(VrpConsentsFixtures.detailContent(payments = emptyList())),
    )

    @Test
    fun revokeConfirmingGolden() =
        capture("revoke_confirming", VrpConsentsFixtures.detailContentState(RevokePhase.Confirming))

    @Test
    fun revokingGolden() =
        capture("revoking", VrpConsentsFixtures.detailContentState(RevokePhase.Revoking))

    @Test
    fun unusableGolden() = capture("unusable", VrpConsentsFixtures.detailUnusableState())

    @Test
    fun endedGolden() = capture("ended", VrpConsentsFixtures.detailEndedState())

    @Test
    fun notFoundGolden() = capture("not_found", VrpConsentsFixtures.detailNotFoundState())

    @Test
    fun revokeRefusedGolden() =
        capture("revoke_refused", VrpConsentsFixtures.detailRevokeRefusedState())

    private fun capture(state: String, screenState: VrpConsentDetailState) {
        composeRule.setContent {
            Themed {
                VrpConsentDetailScreenContent(
                    state = screenState,
                    onAction = {},
                    onBack = {},
                    onNavigateToPayment = {},
                )
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/vrp_consent_detail_$state.png")
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
