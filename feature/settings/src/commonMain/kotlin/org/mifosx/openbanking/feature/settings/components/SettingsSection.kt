/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.settings.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.mifosx.openbanking.core.designsystem.theme.DesignToken
import org.mifosx.openbanking.feature.settings.SettingsTestTags
import template.core.base.designsystem.theme.KptTheme

/**
 * One titled group of settings rows: a label above an elevated, outlined card holding the rows.
 *
 * @param testTag Carried by the group; its card carries [SettingsTestTags.SECTION] and its label
 *   [SettingsTestTags.SECTION_TITLE].
 */
@Composable
internal fun SettingsSection(
    title: String,
    testTag: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(KptTheme.spacing.sm),
    ) {
        SettingsSectionLabel(title = title)
        Surface(
            color = KptTheme.colorScheme.surfaceContainerLowest,
            shape = KptTheme.shapes.large,
            shadowElevation = KptTheme.elevation.level1,
            border = BorderStroke(DesignToken.strokes.hairline, KptTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(SettingsTestTags.SECTION),
        ) {
            Column(content = content)
        }
    }
}

/** A section's heading, sitting above its content rather than inside it. */
@Composable
internal fun SettingsSectionLabel(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = KptTheme.typography.labelMedium,
        color = KptTheme.colorScheme.primary,
        modifier = modifier
            .padding(start = KptTheme.spacing.md)
            .testTag(SettingsTestTags.SECTION_TITLE),
    )
}
