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

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mifosx.openbanking.core.model.user.DarkThemeConfig
import org.mifosx.openbanking.feature.settings.ui.SettingsAction
import org.mifosx.openbanking.feature.settings.ui.SettingsState
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val ROBOLECTRIC_SDK = 34

/** Renders [SettingsScreenContent] and [LicencesScreenContent] under Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [ROBOLECTRIC_SDK])
class SettingsScreenRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<SettingsAction>()

    private fun render(state: SettingsState) {
        composeRule.setContent {
            SettingsScreenContent(state = state, onAction = { actions.add(it) })
        }
    }

    private fun countOf(tag: String): Int =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun topOf(tag: String): Float = composeRule
        .onNodeWithTag(tag, useUnmergedTree = true)
        .fetchSemanticsNode()
        .positionInRoot
        .y

    private fun leftOf(tag: String): Float = composeRule
        .onNodeWithTag(tag, useUnmergedTree = true)
        .fetchSemanticsNode()
        .positionInRoot
        .x

    @Test
    fun allThreeSectionsRenderInThePreviewOrder() {
        render(SettingsFixtures.contentState())

        composeRule.onNodeWithTag(SettingsTestTags.CONTENT).assertExists()
        assertTrue(topOf(SettingsTestTags.SECTION_APPEARANCE) < topOf(SettingsTestTags.SECTION_ACCOUNT))
        assertTrue(topOf(SettingsTestTags.SECTION_ACCOUNT) < topOf(SettingsTestTags.SECTION_ABOUT))
    }

    @Test
    fun everyThemeConfigGetsAPreviewCard() {
        render(SettingsFixtures.contentState())

        DarkThemeConfig.entries.forEach { config ->
            composeRule.onNodeWithTag(SettingsTestTags.themeCard(config)).assertExists()
        }
    }

    /** Light, Dark, System — the picked design's order, not the enum's declaration order. */
    @Test
    fun themeCardsRunLightThenDarkThenSystem() {
        render(SettingsFixtures.contentState())

        val light = leftOf(SettingsTestTags.themeCard(DarkThemeConfig.LIGHT))
        val dark = leftOf(SettingsTestTags.themeCard(DarkThemeConfig.DARK))
        val system = leftOf(SettingsTestTags.themeCard(DarkThemeConfig.FOLLOW_SYSTEM))
        assertTrue(light < dark)
        assertTrue(dark < system)
    }

    @Test
    fun onlyTheStoredThemeCardReadsAsSelected() {
        render(SettingsFixtures.contentState(themeConfig = DarkThemeConfig.DARK))

        composeRule.onNodeWithTag(SettingsTestTags.themeCard(DarkThemeConfig.DARK))
            .assertIsSelected()
        composeRule.onNodeWithTag(SettingsTestTags.themeCard(DarkThemeConfig.LIGHT))
            .assertIsNotSelected()
    }

    @Test
    fun tappingLightDispatchesSelectThemeWithLight() {
        render(SettingsFixtures.contentState())

        composeRule.onNodeWithTag(SettingsTestTags.themeCard(DarkThemeConfig.LIGHT)).performClick()

        assertEquals(
            listOf<SettingsAction>(SettingsAction.SelectTheme(DarkThemeConfig.LIGHT)),
            actions,
        )
    }

    @Test
    fun accountSectionRendersConsentsWithAChevron() {
        render(SettingsFixtures.contentState())

        composeRule.onNodeWithTag(SettingsTestTags.CONSENTS_ROW).assertExists()
        composeRule.onNodeWithTag(
            SettingsTestTags.chevron(SettingsTestTags.CONSENTS_ROW),
            useUnmergedTree = true,
        ).assertExists()
    }

    /** Privacy leaves the app; Licences stays inside it. The glyphs must say so. */
    @Test
    fun aboutRowsCarryTheTrailingIconTheirDestinationImplies() {
        render(SettingsFixtures.contentState())

        composeRule.onNodeWithTag(
            SettingsTestTags.externalLink(SettingsTestTags.PRIVACY_ROW),
            useUnmergedTree = true,
        ).assertExists()
        composeRule.onNodeWithTag(
            SettingsTestTags.chevron(SettingsTestTags.LICENCES_ROW),
            useUnmergedTree = true,
        ).assertExists()
        composeRule.onNodeWithTag(
            SettingsTestTags.externalLink(SettingsTestTags.LICENCES_ROW),
            useUnmergedTree = true,
        ).assertDoesNotExist()
    }

    @Test
    fun noSecurityOrNotificationsSectionIsRendered() {
        render(SettingsFixtures.contentState())

        assertEquals(SettingsFixtures.EXPECTED_SECTION_COUNT, countOf(SettingsTestTags.SECTION))
        composeRule.onNodeWithTag(SettingsTestTags.SECTION_APPEARANCE).assertExists()
        composeRule.onNodeWithTag(SettingsTestTags.SECTION_ACCOUNT).assertExists()
        composeRule.onNodeWithTag(SettingsTestTags.SECTION_ABOUT).assertExists()
    }

    @Test
    fun noClearLocalDataRowIsRendered() {
        render(SettingsFixtures.contentState())

        assertEquals(SettingsFixtures.EXPECTED_ROW_COUNT, countOf(SettingsTestTags.ROW))
    }

    @Test
    fun licencesScreenRendersItsScaffoldAndLicenceBox() {
        composeRule.setContent {
            LicencesScreenContent(state = LicencesFixtures.contentState(), onAction = {})
        }

        composeRule.onNodeWithTag(SettingsTestTags.LICENCES_SCREEN).assertExists()
        composeRule.onNodeWithTag(SettingsTestTags.LICENCES_LIST, useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun licencesScreenOmitsTheLicenceBoxWhileLoading() {
        composeRule.setContent {
            LicencesScreenContent(state = LicencesFixtures.loadingState(), onAction = {})
        }

        composeRule.onNodeWithTag(SettingsTestTags.LICENCES_SCREEN).assertExists()
        composeRule.onNodeWithTag(SettingsTestTags.LICENCES_LIST, useUnmergedTree = true)
            .assertDoesNotExist()
    }
}
