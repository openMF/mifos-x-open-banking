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

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.mifosx.openbanking.feature.vrpsetup.epochMillisOf
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.Res
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_date_picker_cancel
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_date_picker_confirm
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_date_picker_title
import org.mifosx.openbanking.feature.vrpsetup.isSelectableEndDate
import org.mifosx.openbanking.feature.vrpsetup.selectableEndYears
import org.mifosx.openbanking.feature.vrpsetup.setup.VrpSetupTestTags
import org.mifosx.openbanking.feature.vrpsetup.utcDateOf

/**
 * Picks the day the VRP stops.
 *
 * A date under the bank's floor is unselectable rather than refused afterwards. Material3 disables it
 * silently, which is why the row that opens this states the earliest date as helper text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VrpEndDatePickerDialog(
    today: LocalDate,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = selected?.let { epochMillisOf(it) },
        selectableDates = EndDateSelectableDates(today),
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag(VrpSetupTestTags.DATE_PICKER),
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onSelect(utcDateOf(it)) } },
                modifier = Modifier.testTag(VrpSetupTestTags.DATE_PICKER_CONFIRM),
            ) {
                Text(stringResource(Res.string.feature_vrp_setup_date_picker_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(VrpSetupTestTags.DATE_PICKER_CANCEL),
            ) {
                Text(stringResource(Res.string.feature_vrp_setup_date_picker_cancel))
            }
        },
    ) {
        DatePicker(
            state = state,
            title = {
                Text(
                    text = stringResource(Res.string.feature_vrp_setup_date_picker_title),
                    modifier = Modifier.padding(TitlePadding),
                )
            },
        )
    }
}

/** Material3's hook into the rule, and nothing more. The millis are UTC by contract. */
@OptIn(ExperimentalMaterial3Api::class)
private class EndDateSelectableDates(private val today: LocalDate) : SelectableDates {

    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        isSelectableEndDate(utcDateOf(utcTimeMillis), today)

    override fun isSelectableYear(year: Int): Boolean = year in selectableEndYears(today)
}

/** Material3 lays the title out itself; this only restores the inset the dialog expects. */
private val TitlePadding = 24.dp
