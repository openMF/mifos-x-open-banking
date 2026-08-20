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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import template.core.base.designsystem.theme.KptTheme

/**
 * A text entry carrying the form's own skin.
 *
 * Material's defaults are a 4dp corner and no container, which is neither of the things the controls
 * beside it do — the account picker, the dropdowns and the amount card all sit on
 * `surfaceContainerLowest` at [KptTheme.shapes]`.medium`. A bare `OutlinedTextField` in one of these
 * forms therefore reads as a different kind of control rather than the same one asking a different
 * question, which is why the skin belongs here rather than at eighteen call sites.
 *
 * @param label Floats into the border when the field has focus or a value, as Material's does.
 * @param error Turns the border and the label red.
 * @param supportingText Sits under the field, for a helper line or the message that goes with
 *   [error]. A slot rather than a string because several callers tag or condition their own.
 */
@Composable
fun MifosOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    prefix: String? = null,
    error: Boolean = false,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    testTag: String? = null,
    supportingText: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .then(testTag?.let { Modifier.testTag(it) } ?: Modifier),
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        prefix = prefix?.let { { Text(text = it, style = KptTheme.typography.titleMedium) } },
        supportingText = supportingText,
        isError = error,
        singleLine = singleLine,
        shape = KptTheme.shapes.medium,
        colors = mifosOutlinedTextFieldColors(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}

/**
 * The form's field colours: a primary border at rest as well as focused, on the same ground the
 * cards use.
 *
 * The error colours are left at their defaults on purpose — a refused value has to be able to turn
 * the border red, and naming primary for every state would bury it.
 */
@Composable
fun mifosOutlinedTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = KptTheme.colorScheme.primary,
    unfocusedBorderColor = KptTheme.colorScheme.primary,
    focusedContainerColor = KptTheme.colorScheme.surfaceContainerLowest,
    unfocusedContainerColor = KptTheme.colorScheme.surfaceContainerLowest,
)
