/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.ui.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import org.jetbrains.compose.resources.stringResource
import org.mifosx.openbanking.core.ui.generated.resources.Res
import org.mifosx.openbanking.core.ui.generated.resources.core_ui_account_picker_bank_choice
import org.mifosx.openbanking.core.ui.generated.resources.core_ui_account_picker_bank_choice_supporting
import template.core.base.designsystem.theme.KptTheme

/**
 * The one payer alternative that is not an account: let the customer pick at the bank.
 *
 * Written for [MifosAccountPicker]'s `extraOptions` slot, which is why it draws its own divider —
 * it is the last row of the expansion, not a control in its own right.
 */
@Composable
fun MifosBankChoiceRow(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = BANK_CHOICE_TAG,
) {
    HorizontalDivider(color = KptTheme.colorScheme.outlineVariant)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(KptTheme.spacing.md)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.AccountBalance,
            contentDescription = null,
            tint = KptTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.padding(start = KptTheme.spacing.md)) {
            Text(
                text = stringResource(Res.string.core_ui_account_picker_bank_choice),
                style = KptTheme.typography.titleMedium,
                color = if (selected) KptTheme.colorScheme.primary else KptTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(Res.string.core_ui_account_picker_bank_choice_supporting),
                style = KptTheme.typography.bodyMedium,
                color = KptTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val BANK_CHOICE_TAG = "mifosAccountPicker:bankChoice"
