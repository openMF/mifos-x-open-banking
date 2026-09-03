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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import org.mifosx.openbanking.core.designsystem.theme.DesignToken
import org.mifosx.openbanking.feature.settings.SettingsTestTags
import template.core.base.designsystem.theme.KptTheme

/**
 * One settings row: a leading icon, a title over an optional subtitle, and an optional trailing
 * slot.
 *
 * @param testTag Carried by the row itself; its title carries [SettingsTestTags.ROW].
 * @param onClick `null` for a row that only reports a value — it then takes no click and shows no
 *   ripple.
 */
@Composable
internal fun SettingsRow(
    title: String,
    testTag: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleTestTag: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    val clickable = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = DesignToken.sizes.rowMin)
            .then(clickable)
            .testTag(testTag)
            .padding(KptTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KptTheme.spacing.md),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = KptTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(DesignToken.sizes.iconMedium),
        )
        RowText(
            title = title,
            subtitle = subtitle,
            subtitleTestTag = subtitleTestTag,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke(this)
    }
}

/** A row's title over its optional subtitle. */
@Composable
private fun RowText(
    title: String,
    subtitle: String?,
    subtitleTestTag: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(KptTheme.spacing.xs),
    ) {
        Text(
            text = title,
            style = KptTheme.typography.bodyLarge,
            color = KptTheme.colorScheme.onSurface,
            modifier = Modifier.testTag(SettingsTestTags.ROW),
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = KptTheme.typography.bodyMedium,
                color = KptTheme.colorScheme.onSurfaceVariant,
                modifier = subtitleTestTag?.let { Modifier.testTag(it) } ?: Modifier,
            )
        }
    }
}

/** Trailing chevron: this row opens a destination inside the app. */
@Composable
internal fun SettingsRowChevron(
    description: String,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = description,
        tint = KptTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .size(DesignToken.sizes.iconSmall)
            .testTag(testTag),
    )
}

/** Trailing external-link glyph: this row leaves the app for a browser. */
@Composable
internal fun SettingsRowExternalLink(
    description: String,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
        contentDescription = description,
        tint = KptTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .size(DesignToken.sizes.iconSmall)
            .testTag(testTag),
    )
}
