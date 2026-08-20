/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import template.core.base.designsystem.theme.KptTheme

/** Matches the account picker's rows, so the two controls do not disagree about how tall a field is. */
private val FieldMinHeight = 56.dp
private val ChipIconSize = 18.dp
private val FieldCorner = 12.dp
private val FieldBorder = 1.dp
private val FieldPadding = 16.dp
private val RowGap = 12.dp
private val BoxGap = 4.dp
private val CaptionGap = 4.dp

/**
 * A value and its alternatives, as one bordered field that opens a menu.
 *
 * Built here rather than reused: `ExposedDropdownMenuBox` and `SegmentedButton` appear nowhere in
 * this repo. It follows the shape of settings' private `ThemeDropdownRow` — an anchor,
 * `Icons.Filled.ArrowDropDown`, a `DropdownMenu` of `DropdownMenuItem`s — without depending on it.
 *
 * **Expansion is local `remember`, not hoisted.** [MifosAccountPicker] hoists its expansion because
 * a view model has to collapse it on a selection made elsewhere; nothing collapses these but their
 * own menu, so putting them on the state would be presentation state no form has a rule about.
 *
 * @param label What the anchor reads. Separate from [optionLabel] because the two are not always the
 *   same string: the amount card's control shows the bare code beside the figure, while its menu
 *   spells the currency out.
 * @param caption Drawn above the field, for a control whose anchor shows the chosen value and so
 *   has nowhere to say which question it answers. Omitted where the anchor names itself.
 * @param selected Marked in the menu's semantics, so a screen reader says which of the options is
 *   the current one rather than reading nineteen equal-sounding rows.
 */
@Composable
fun <T> MifosDropdownField(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    optionTestTag: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (caption != null) {
            Text(
                text = caption,
                style = KptTheme.typography.bodySmall,
                color = KptTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = CaptionGap, start = FieldPadding),
            )
        }
        DropdownHost(
            selected = selected,
            options = options,
            optionLabel = optionLabel,
            optionTestTag = optionTestTag,
            onSelect = onSelect,
            modifier = Modifier.fillMaxWidth(),
        ) { onOpen ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .anchorSkin(
                    border = KptTheme.colorScheme.primary,
                    background = KptTheme.colorScheme.surfaceContainerLowest,
                )
                    .clickable(role = Role.DropdownList, onClick = onOpen)
                    .heightIn(min = FieldMinHeight)
                    .padding(horizontal = FieldPadding, vertical = RowGap),
                horizontalArrangement = Arrangement.spacedBy(RowGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = KptTheme.typography.bodyLarge,
                    color = KptTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = KptTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The same control, sized to sit BESIDE the amount rather than under it.
 *
 * It belongs to the figure it qualifies — the currency the amount is instructed in is part of the
 * amount — so a full-width row of its own would read as another question rather than as the unit on
 * the one already asked. Unlike [MifosDropdownField] it passes its [modifier] straight through,
 * which is what lets an amount card hand it a weight.
 */
@Composable
fun <T> MifosDropdownBox(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    optionTestTag: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownHost(
        selected = selected,
        options = options,
        optionLabel = optionLabel,
        optionTestTag = optionTestTag,
        onSelect = onSelect,
        modifier = modifier,
    ) { onOpen ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .anchorSkin(
                    border = KptTheme.colorScheme.primary,
                    background = KptTheme.colorScheme.surfaceContainerLowest,
                )
                .clickable(role = Role.DropdownList, onClick = onOpen)
                // Narrower than the field's padding on purpose: this box is a fifth of the row, and
                // 16dp a side would leave a three-letter code ellipsised on a small phone.
                .padding(horizontal = BoxGap * 2),
            horizontalArrangement = Arrangement.spacedBy(BoxGap, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = KptTheme.typography.labelLarge,
                color = KptTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = KptTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(ChipIconSize),
            )
        }
    }
}

/**
 * The corner, border and ground both anchors carry, so only their padding differs.
 *
 * The colours are passed rather than read from the theme here: a `@Composable` modifier builder
 * recomposes more than it needs to, and the callers are already in a composable scope.
 */
private fun Modifier.anchorSkin(border: Color, background: Color): Modifier = this
    .clip(RoundedCornerShape(FieldCorner))
    .border(width = FieldBorder, color = border, shape = RoundedCornerShape(FieldCorner))
    .background(background)

/** The menu and the expansion both anchors share; only the anchor's own shape differs. */
@Composable
private fun <T> DropdownHost(
    selected: T,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    optionTestTag: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    anchor: @Composable (onOpen: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        anchor { expanded = true }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                DropdownMenuItem(
                    text = { Text(text = optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                    modifier = Modifier
                        .testTag(optionTestTag(option))
                        .semantics { this.selected = isSelected },
                )
            }
        }
    }
}
