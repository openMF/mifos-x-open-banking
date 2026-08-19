/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.vrpsetup.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.mifosx.openbanking.core.model.vrp.PeriodType
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.Res
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_valid_to_clear
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_valid_to_none
import org.mifosx.openbanking.feature.vrpsetup.periodLabel
import org.mifosx.openbanking.feature.vrpsetup.setup.VrpSetupTestTags
import template.core.base.designsystem.theme.KptTheme

private val FieldMinHeight = 56.dp
private val FieldBorder = 1.dp

/** An outlined amount entry with the currency mark as its prefix. */
@Composable
internal fun VrpAmountField(
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    testTag: String,
    errorTestTag: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().testTag(testTag),
            prefix = { Text(text = "£", style = KptTheme.typography.titleMedium) },
            isError = error != null,
            singleLine = true,
            shape = KptTheme.shapes.small,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        error?.let { FieldError(it, errorTestTag) }
    }
}

/** An outlined text entry for one part of a new payee. */
@Composable
internal fun VrpTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    testTag: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth().testTag(testTag),
            isError = error != null,
            singleLine = true,
            shape = KptTheme.shapes.small,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        )
        error?.let { FieldError(it, testTag + "Error") }
    }
}

/** The window the periodic ceiling covers. */
@Composable
internal fun VrpPeriodDropdown(
    selected: PeriodType,
    onSelect: (PeriodType) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedField(
            onClick = { expanded = true },
            testTag = VrpSetupTestTags.PERIOD_DROPDOWN,
        ) {
            Text(
                text = periodLabel(selected),
                style = KptTheme.typography.bodyLarge,
                color = KptTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = KptTheme.colorScheme.onSurfaceVariant,
            )
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PeriodType.entries.forEach { period ->
                DropdownMenuItem(
                    text = { Text(periodLabel(period)) },
                    onClick = {
                        expanded = false
                        onSelect(period)
                    },
                    modifier = Modifier.testTag(VrpSetupTestTags.periodOption(period.name)),
                )
            }
        }
    }
}

/** The optional end date: a picker row, never a free-text field. */
@Composable
internal fun VrpEndDateRow(
    date: String?,
    onOpen: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedField(
        onClick = onOpen,
        testTag = VrpSetupTestTags.VALID_TO_ROW,
        modifier = modifier,
    ) {
        Icon(
            imageVector = Icons.Filled.CalendarMonth,
            contentDescription = null,
            tint = KptTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = date ?: stringResource(Res.string.feature_vrp_setup_valid_to_none),
            style = KptTheme.typography.bodyLarge,
            color = KptTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(start = KptTheme.spacing.sm),
        )
        if (date != null) {
            TextButton(
                onClick = onClear,
                modifier = Modifier.testTag(VrpSetupTestTags.VALID_TO_CLEAR),
            ) {
                Text(
                    text = stringResource(Res.string.feature_vrp_setup_valid_to_clear).uppercase(),
                    color = KptTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** A read-only control that opens something: the same outline the text fields carry. */
@Composable
private fun OutlinedField(
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = FieldMinHeight)
            .border(FieldBorder, KptTheme.colorScheme.outline, KptTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = KptTheme.spacing.md)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun FieldError(message: String, testTag: String) {
    Text(
        text = message,
        style = KptTheme.typography.bodySmall,
        color = KptTheme.colorScheme.error,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .padding(top = KptTheme.spacing.xs)
            .testTag(testTag),
    )
}
