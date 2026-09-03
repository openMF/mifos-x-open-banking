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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.jetbrains.compose.resources.stringResource
import org.mifosx.openbanking.core.model.user.DarkThemeConfig
import org.mifosx.openbanking.feature.settings.components.SettingsRow
import org.mifosx.openbanking.feature.settings.components.SettingsRowChevron
import org.mifosx.openbanking.feature.settings.components.SettingsRowExternalLink
import org.mifosx.openbanking.feature.settings.components.SettingsSection
import org.mifosx.openbanking.feature.settings.components.SettingsSectionLabel
import org.mifosx.openbanking.feature.settings.components.ThemePreviewRow
import org.mifosx.openbanking.feature.settings.generated.resources.Res
import org.mifosx.openbanking.feature.settings.generated.resources.feature_settings_consents_subtitle
import org.mifosx.openbanking.feature.settings.generated.resources.feature_settings_consents_title
import org.mifosx.openbanking.feature.settings.generated.resources.feature_settings_content_accessibility
import org.mifosx.openbanking.feature.settings.generated.resources.feature_settings_licences_title
import org.mifosx.openbanking.feature.settings.generated.resources.feature_settings_opens_externally_accessibility
import org.mifosx.openbanking.feature.settings.generated.resources.feature_settings_opens_in_app_accessibility
import org.mifosx.openbanking.feature.settings.generated.resources.feature_settings_privacy_title
import org.mifosx.openbanking.feature.settings.generated.resources.feature_settings_section_about
import org.mifosx.openbanking.feature.settings.generated.resources.feature_settings_section_account
import org.mifosx.openbanking.feature.settings.generated.resources.feature_settings_section_appearance
import org.mifosx.openbanking.feature.settings.ui.SettingsAction
import org.mifosx.openbanking.feature.settings.ui.SettingsState
import template.core.base.designsystem.theme.KptTheme

/** The Mifos Initiative privacy policy page, opened in an external browser. */
private const val PRIVACY_URL = "https://mifos.org/privacy-policy/"

/**
 * The settings body: Appearance, Account, and About & Legal, in that order.
 *
 * Free of the view model so the Compose suites can drive it directly.
 */
@Composable
internal fun SettingsScreenContent(
    state: SettingsState,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToConsents: () -> Unit = {},
    onNavigateToLicences: () -> Unit = {},
    onOpenUrl: (String) -> Unit = {},
) {
    val description = stringResource(Res.string.feature_settings_content_accessibility)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = KptTheme.spacing.md, vertical = KptTheme.spacing.lg)
            .testTag(SettingsTestTags.CONTENT)
            .semantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(KptTheme.spacing.xl),
    ) {
        AppearanceSection(
            themeConfig = state.themeConfig,
            onSelectTheme = { onAction(SettingsAction.SelectTheme(it)) },
        )
        AccountSection(
            onNavigateToConsents = onNavigateToConsents,
        )
        AboutSection(
            onOpenPrivacy = { onOpenUrl(PRIVACY_URL) },
            onNavigateToLicences = onNavigateToLicences,
        )
    }
}

/**
 * The theme picker. Unlike the other sections it has no card of its own — the three preview cards
 * carry the elevation, so wrapping them would stack two shadows.
 */
@Composable
private fun AppearanceSection(
    themeConfig: DarkThemeConfig,
    onSelectTheme: (DarkThemeConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(SettingsTestTags.SECTION_APPEARANCE),
        verticalArrangement = Arrangement.spacedBy(KptTheme.spacing.sm),
    ) {
        SettingsSectionLabel(
            title = stringResource(Res.string.feature_settings_section_appearance),
        )
        ThemePreviewRow(
            selected = themeConfig,
            onSelect = onSelectTheme,
            modifier = Modifier.testTag(SettingsTestTags.SECTION),
        )
    }
}

@Composable
private fun AccountSection(
    onNavigateToConsents: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val opensInApp = stringResource(Res.string.feature_settings_opens_in_app_accessibility)
    SettingsSection(
        title = stringResource(Res.string.feature_settings_section_account),
        testTag = SettingsTestTags.SECTION_ACCOUNT,
        modifier = modifier,
    ) {
        SettingsRow(
            title = stringResource(Res.string.feature_settings_consents_title),
            testTag = SettingsTestTags.CONSENTS_ROW,
            icon = Icons.Filled.Policy,
            subtitle = stringResource(Res.string.feature_settings_consents_subtitle),
            onClick = onNavigateToConsents,
        ) {
            SettingsRowChevron(
                description = opensInApp,
                testTag = SettingsTestTags.chevron(SettingsTestTags.CONSENTS_ROW),
            )
        }
    }
}

/** Privacy, which opens a browser, and Licences, which opens in the app. */
@Composable
private fun AboutSection(
    onOpenPrivacy: () -> Unit,
    onNavigateToLicences: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val opensExternally = stringResource(Res.string.feature_settings_opens_externally_accessibility)
    val opensInApp = stringResource(Res.string.feature_settings_opens_in_app_accessibility)
    SettingsSection(
        title = stringResource(Res.string.feature_settings_section_about),
        testTag = SettingsTestTags.SECTION_ABOUT,
        modifier = modifier,
    ) {
        SettingsRow(
            title = stringResource(Res.string.feature_settings_privacy_title),
            testTag = SettingsTestTags.PRIVACY_ROW,
            icon = Icons.Filled.PrivacyTip,
            onClick = onOpenPrivacy,
        ) {
            SettingsRowExternalLink(
                description = opensExternally,
                testTag = SettingsTestTags.externalLink(SettingsTestTags.PRIVACY_ROW),
            )
        }
        HorizontalDivider(
            color = KptTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(horizontal = KptTheme.spacing.md),
        )
        SettingsRow(
            title = stringResource(Res.string.feature_settings_licences_title),
            testTag = SettingsTestTags.LICENCES_ROW,
            icon = Icons.Filled.Info,
            onClick = onNavigateToLicences,
        ) {
            SettingsRowChevron(
                description = opensInApp,
                testTag = SettingsTestTags.chevron(SettingsTestTags.LICENCES_ROW),
            )
        }
    }
}
