/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.paymentsschedulepayment.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mifosx.openbanking.feature.paymentsschedulepayment.CardBorder
import org.mifosx.openbanking.feature.paymentsschedulepayment.CardCorner
import org.mifosx.openbanking.feature.paymentsschedulepayment.CardPadding
import org.mifosx.openbanking.feature.paymentsschedulepayment.HeroGap
import org.mifosx.openbanking.feature.paymentsschedulepayment.RowGap
import template.core.base.designsystem.theme.KptTheme

/** Matches the payer picker's rows, so the two controls do not disagree about how tall a field is. */
private val FieldMinHeight = 56.dp
private val ChipIconSize = 18.dp

/**
 * A value and its alternatives, as one bordered field that opens a menu.
 *
 * Built here rather than reused: `ExposedDropdownMenuBox` and `SegmentedButton` appear nowhere in
 * this repo and `core/ui` carries no selection control at all. The only other menu is settings'
 * `ThemeDropdownRow`, which is private to that feature and hardcoded to `DarkThemeConfig` — so this
 * follows its shape (an anchor, `Icons.Filled.ArrowDropDown`, a `DropdownMenu` of
 * `DropdownMenuItem`s) without depending on it. Promote it to `core/ui` as a `Mifos*` when a second
 * feature needs one, the same reasoning `AccountSelectorSheet` records for the codebase's one sheet.
 *
 * It replaces two `FilterChip` rows. Nineteen currencies cannot be chips, and the four charge chips
 * wrapped and left "Whatever the scheme decides" orphaned on a row of its own.
 *
 * **Expansion is local `remember`, not hoisted.** `PayerPicker` hoists its expansion because the
 * ViewModel has to collapse it on a selection made elsewhere; nothing collapses these but their own
 * menu, so putting them on the state would be presentation state the form has no rule about.
 *
 * @param label What the anchor reads. Separate from [optionLabel] because the two are not always the
 *   same string: the amount card's control shows the bare code beside the figure, while its menu
 *   spells the currency out.
 * @param selected Marked in the menu's semantics, so a screen reader says which of the options is
 *   the current one rather than reading nineteen equal-sounding rows.
 */
@Composable
internal fun <T> SchedulePaymentDropdownField(
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
        modifier = modifier.fillMaxWidth(),
    ) { onOpen ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CardCorner))
                .border(
                    width = CardBorder,
                    color = KptTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(CardCorner),
                )
                .background(KptTheme.colorScheme.surfaceContainerLowest)
                .clickable(role = Role.DropdownList, onClick = onOpen)
                .heightIn(min = FieldMinHeight)
                .padding(horizontal = CardPadding, vertical = RowGap),
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

/**
 * The same control, sized to sit BESIDE the amount rather than under it.
 *
 * It belongs to the figure it qualifies — the currency the amount is instructed in is part of the
 * amount — so a full-width row of its own would read as a fourth question rather than as the unit
 * on the one already asked.
 *
 * **This is the adapted one, and [SchedulePaymentDropdownField] is not.** Both were candidates and only
 * one could take a caller-supplied width: the field hard-codes `fillMaxWidth()` onto the host and
 * pads its anchor by `CardPadding` on each side, which is right for a control that owns a row and
 * leaves a fifth-width box with nothing left for "GBP" and a caret. This one passes its [modifier]
 * straight through, which is what lets the amount card hand it a weight. What changed is only the
 * anchor's skin: it was a pill in `surfaceContainerHighest`, sized by its own text, and it is now
 * the field's corner, border, background and height, filling whatever box it is given. A pill
 * floating beside a bordered figure read as a tag ON the amount rather than as half of it.
 */
@Composable
internal fun <T> SchedulePaymentDropdownBox(
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
                .clip(RoundedCornerShape(CardCorner))
                .border(
                    width = CardBorder,
                    color = KptTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(CardCorner),
                )
                .background(KptTheme.colorScheme.surfaceContainerLowest)
                .clickable(role = Role.DropdownList, onClick = onOpen)
                // Narrower than the field's CardPadding on purpose: this box is a fifth of the row,
                // and 16dp a side would leave a three-letter code ellipsised on a small phone.
                .padding(horizontal = HeroGap * 2),
            horizontalArrangement = Arrangement.spacedBy(HeroGap, Alignment.CenterHorizontally),
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
