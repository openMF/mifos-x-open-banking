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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import org.jetbrains.compose.resources.stringResource
import org.mifosx.openbanking.core.designsystem.theme.DesignToken
import org.mifosx.openbanking.core.designsystem.theme.darkScheme
import org.mifosx.openbanking.core.designsystem.theme.lightScheme
import org.mifosx.openbanking.core.model.user.DarkThemeConfig
import org.mifosx.openbanking.feature.settings.SettingsTestTags
import org.mifosx.openbanking.feature.settings.generated.resources.Res
import org.mifosx.openbanking.feature.settings.generated.resources.feature_settings_theme_option_accessibility
import org.mifosx.openbanking.feature.settings.generated.resources.feature_settings_theme_sample
import org.mifosx.openbanking.feature.settings.ui.themeLabel
import template.core.base.designsystem.theme.KptTheme

/** Width of a preview card relative to its height. */
private const val SWATCH_ASPECT_RATIO = 1.30f

/** Corner rounding of the inner mini-card, as a percentage of its top-start corner. */
private const val MINI_CARD_PERCENT = 18

/**
 * The three theme options as preview cards, each a miniature of the app in that theme.
 *
 * Ordered Light, Dark, System rather than by declaration: [displayOrder] is an exhaustive `when`,
 * so a config added to [DarkThemeConfig] fails to compile until it is placed.
 */
@Composable
internal fun ThemePreviewRow(
    selected: DarkThemeConfig,
    onSelect: (DarkThemeConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KptTheme.spacing.md),
    ) {
        DarkThemeConfig.entries.sortedBy { it.displayOrder }.forEach { config ->
            ThemePreviewCard(
                config = config,
                isSelected = config == selected,
                onSelect = { onSelect(config) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Where a config sits in the picker, left to right. */
private val DarkThemeConfig.displayOrder: Int
    get() = when (this) {
        DarkThemeConfig.LIGHT -> 0
        DarkThemeConfig.DARK -> 1
        DarkThemeConfig.FOLLOW_SYSTEM -> 2
    }

/** One theme option: its preview above its name, bordered and badged when selected. */
@Composable
private fun ThemePreviewCard(
    config: DarkThemeConfig,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(config.themeLabel())
    val description = stringResource(
        Res.string.feature_settings_theme_option_accessibility,
        label,
    )
    Column(
        modifier = modifier
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onSelect)
            .testTag(SettingsTestTags.themeCard(config))
            .semantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(KptTheme.spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            color = KptTheme.colorScheme.surfaceContainerLowest,
            shape = KptTheme.shapes.large,
            shadowElevation = KptTheme.elevation.level1,
            border = selectionBorder(isSelected),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(SWATCH_ASPECT_RATIO),
        ) {
            ThemeSwatch(config = config, modifier = Modifier.fillMaxSize())
        }
        Text(
            text = label,
            style = KptTheme.typography.bodyMedium,
            color = KptTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

/** The card outline: primary and thicker while selected, a hairline otherwise. */
@Composable
private fun selectionBorder(isSelected: Boolean): BorderStroke = if (isSelected) {
    BorderStroke(DesignToken.strokes.thick, KptTheme.colorScheme.primary)
} else {
    BorderStroke(DesignToken.strokes.hairline, KptTheme.colorScheme.outlineVariant)
}

/**
 * The miniature for one config, its mini-card painted from that theme's own palette.
 *
 * [DarkThemeConfig.FOLLOW_SYSTEM] shows both halves, dark then light, each laid out exactly as it
 * is in its own card.
 */
@Composable
private fun ThemeSwatch(config: DarkThemeConfig, modifier: Modifier = Modifier) {
    when (config) {
        DarkThemeConfig.LIGHT -> SchemeSwatch(scheme = lightScheme, modifier = modifier)
        DarkThemeConfig.DARK -> SchemeSwatch(scheme = darkScheme, modifier = modifier)
        DarkThemeConfig.FOLLOW_SYSTEM -> Row(modifier = modifier) {
            SchemeSwatch(
                scheme = darkScheme,
                innerCardPadding = PaddingValues(
                    start = KptTheme.spacing.md,
                    top = KptTheme.spacing.md,
                ),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            SchemeSwatch(
                scheme = lightScheme,
                innerCardPadding = PaddingValues(
                    start = KptTheme.spacing.md,
                    top = KptTheme.spacing.md,
                ),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

/**
 * A window mock in [scheme]: that theme's screen background behind a mini-card filled with its
 * primary, carrying sample text in its on-primary.
 */
@Composable
private fun SchemeSwatch(
    scheme: ColorScheme,
    modifier: Modifier = Modifier,
    innerCardPadding: PaddingValues = PaddingValues(
        start = KptTheme.spacing.lg,
        top = KptTheme.spacing.md,
    ),
) {
    val miniCardShape = RoundedCornerShape(topStartPercent = MINI_CARD_PERCENT)
    Box(modifier = modifier.background(scheme.background)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerCardPadding),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(miniCardShape)
                    .background(scheme.primary)
                    .border(
                        width = DesignToken.strokes.hairline,
                        color = scheme.outline,
                        shape = miniCardShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.feature_settings_theme_sample),
                    style = KptTheme.typography.titleMedium,
                    color = scheme.onPrimary,
                )
            }
        }
    }
}
