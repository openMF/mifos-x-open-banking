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
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.mifosx.openbanking.core.model.user.DarkThemeConfig
import org.mifosx.openbanking.feature.settings.ui.SettingsAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Headless coverage for [SettingsScreenContent] on the desktop renderer, over the same
 * [SettingsTestTags] the Robolectric suite drives.
 *
 * Test names are camelCase — this source set compiles for Kotlin/Native, whose frontend rejects
 * punctuation inside backticked names.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsScreenUiTest {

    @Test
    fun contentRendersAllThreeSections() = runComposeUiTest {
        setContent {
            SettingsScreenContent(state = SettingsFixtures.contentState(), onAction = {})
        }

        onNodeWithTag(SettingsTestTags.CONTENT).assertExists()
        onNodeWithTag(SettingsTestTags.SECTION_APPEARANCE).assertExists()
        onNodeWithTag(SettingsTestTags.SECTION_ACCOUNT).assertExists()
        onNodeWithTag(SettingsTestTags.SECTION_ABOUT).assertExists()
        assertEquals(
            SettingsFixtures.EXPECTED_SECTION_COUNT,
            onAllNodesWithTag(SettingsTestTags.SECTION, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .size,
        )
    }

    @Test
    fun contentRendersEveryRowAndNoOthers() = runComposeUiTest {
        setContent {
            SettingsScreenContent(state = SettingsFixtures.contentState(), onAction = {})
        }

        assertEquals(
            SettingsFixtures.EXPECTED_ROW_COUNT,
            onAllNodesWithTag(SettingsTestTags.ROW, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .size,
        )
    }

    @Test
    fun everyThemeConfigGetsAPreviewCard() = runComposeUiTest {
        setContent {
            SettingsScreenContent(state = SettingsFixtures.contentState(), onAction = {})
        }

        DarkThemeConfig.entries.forEach { config ->
            onNodeWithTag(SettingsTestTags.themeCard(config)).assertExists()
        }
    }

    @Test
    fun onlyTheStoredThemeCardReadsAsSelected() = runComposeUiTest {
        setContent {
            SettingsScreenContent(
                state = SettingsFixtures.contentState(themeConfig = DarkThemeConfig.DARK),
                onAction = {},
            )
        }

        onNodeWithTag(SettingsTestTags.themeCard(DarkThemeConfig.DARK)).assertIsSelected()
        onNodeWithTag(SettingsTestTags.themeCard(DarkThemeConfig.LIGHT)).assertIsNotSelected()
        onNodeWithTag(SettingsTestTags.themeCard(DarkThemeConfig.FOLLOW_SYSTEM))
            .assertIsNotSelected()
    }

    @Test
    fun tappingAThemeCardDispatchesSelectTheme() = runComposeUiTest {
        val actions = mutableListOf<SettingsAction>()
        setContent {
            SettingsScreenContent(
                state = SettingsFixtures.contentState(),
                onAction = { actions.add(it) },
            )
        }

        onNodeWithTag(SettingsTestTags.themeCard(DarkThemeConfig.LIGHT)).performClick()

        assertEquals(
            listOf<SettingsAction>(SettingsAction.SelectTheme(DarkThemeConfig.LIGHT)),
            actions,
        )
    }

    @Test
    fun tappingLicencesRowFiresNavigateToLicencesAndKeepsItsChevron() = runComposeUiTest {
        var navigated = false
        setContent {
            SettingsScreenContent(
                state = SettingsFixtures.contentState(),
                onAction = {},
                onNavigateToLicences = { navigated = true },
            )
        }

        onNodeWithTag(SettingsTestTags.LICENCES_ROW).performClick()

        assertTrue(navigated)
        onNodeWithTag(
            SettingsTestTags.chevron(SettingsTestTags.LICENCES_ROW),
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun tappingPrivacyRowOpensTheMifosPrivacyPolicyUrl() = runComposeUiTest {
        val opened = mutableListOf<String>()
        setContent {
            SettingsScreenContent(
                state = SettingsFixtures.contentState(),
                onAction = {},
                onOpenUrl = { opened.add(it) },
            )
        }

        onNodeWithTag(SettingsTestTags.PRIVACY_ROW).performClick()

        assertEquals(listOf("https://mifos.org/privacy-policy/"), opened)
    }
}
