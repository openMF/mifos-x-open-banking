/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.mifosx.openbanking.feature.settings.ui.LicencesAction
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Headless coverage for [LicencesScreenContent] on the desktop renderer.
 *
 * Test names are camelCase — this source set compiles for Kotlin/Native, whose frontend rejects
 * punctuation inside backticked names.
 */
@OptIn(ExperimentalTestApi::class)
class LicencesScreenUiTest {

    @Test
    fun contentRendersTheLicenceBox() = runComposeUiTest {
        setContent {
            LicencesScreenContent(state = LicencesFixtures.contentState(), onAction = {})
        }

        onNodeWithTag(SettingsTestTags.LICENCES_SCREEN).assertExists()
        onNodeWithTag(SettingsTestTags.LICENCES_LIST, useUnmergedTree = true).assertExists()
    }

    @Test
    fun loadingRendersNoLicenceBox() = runComposeUiTest {
        setContent {
            LicencesScreenContent(state = LicencesFixtures.loadingState(), onAction = {})
        }

        onNodeWithTag(SettingsTestTags.LICENCES_SCREEN).assertExists()
        onNodeWithTag(SettingsTestTags.LICENCES_LIST, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun anErrorRendersItsMessageAndNoLicenceBox() = runComposeUiTest {
        setContent {
            LicencesScreenContent(
                state = LicencesFixtures.errorState(message = "licence unavailable"),
                onAction = {},
            )
        }

        onNodeWithText("licence unavailable").assertExists()
        onNodeWithTag(SettingsTestTags.LICENCES_LIST, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun tappingRetryDispatchesRetryLoad() = runComposeUiTest {
        val actions = mutableListOf<LicencesAction>()
        setContent {
            LicencesScreenContent(
                state = LicencesFixtures.errorState(),
                onAction = { actions.add(it) },
            )
        }

        onNodeWithText("Retry").performClick()

        assertEquals(listOf<LicencesAction>(LicencesAction.RetryLoad), actions)
    }
}
