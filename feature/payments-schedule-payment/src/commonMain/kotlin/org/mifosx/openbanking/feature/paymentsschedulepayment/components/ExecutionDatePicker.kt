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
import org.mifosx.openbanking.core.model.banking.payment.PaymentRail
import org.mifosx.openbanking.feature.paymentsschedulepayment.SchedulePaymentTestTags
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.Res
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_date_picker_cancel
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_date_picker_confirm
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_date_picker_title
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.epochMillisOf
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.isSelectableExecutionDate
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.selectableExecutionYears
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.utcDateOf

/**
 * The execution-date dialog.
 *
 * Split into a dialog wrapper and a `SelectableDates` adapter with the rule itself left in
 * `SchedulePaymentDateRules`. A dialog renders in its own window, where tag-based assertions are
 * unreliable, so the boundaries are tested against the pure rule rather than by driving this.
 *
 * The customer is never allowed to produce an invalid date: everything outside the window is
 * unselectable rather than rejected afterwards. Material3 does that silently, which is why the field
 * that opens this carries the rule as helper text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExecutionDatePickerDialog(
    today: LocalDate,
    rail: PaymentRail,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = selected?.let { epochMillisOf(it) },
        selectableDates = ExecutionSelectableDates(today, rail),
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag(SchedulePaymentTestTags.DATE_PICKER),
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onSelect(utcDateOf(it)) } },
                modifier = Modifier.testTag(SchedulePaymentTestTags.DATE_PICKER_CONFIRM),
            ) {
                Text(stringResource(Res.string.feature_payments_schedule_payment_date_picker_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(SchedulePaymentTestTags.DATE_PICKER_CANCEL),
            ) {
                Text(stringResource(Res.string.feature_payments_schedule_payment_date_picker_cancel))
            }
        },
    ) {
        DatePicker(
            state = state,
            title = {
                Text(
                    text = stringResource(Res.string.feature_payments_schedule_payment_date_picker_title),
                    modifier = Modifier.padding(TitlePadding),
                )
            },
        )
    }
}

/**
 * Material3's hook into the rule, and nothing more.
 *
 * The millis it is handed are UTC by contract, and they are read as UTC here — **not** with
 * `currentSystemDefault()`, which `feature/transactions` uses for its own range picker. That is a
 * precedent to diverge from: east of UTC it resolves to the following calendar day, which here would
 * offer a date the bank refuses as already past.
 */
private class ExecutionSelectableDates(
    private val today: LocalDate,
    private val rail: PaymentRail,
) : SelectableDates {

    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        isSelectableExecutionDate(utcDateOf(utcTimeMillis), today, rail)

    /**
     * Narrowed to the years the window actually spans.
     *
     * Left at the default, the picker lets the customer page through decades of months in which
     * every single day is disabled — which reads as a broken calendar rather than a bounded one.
     */
    override fun isSelectableYear(year: Int): Boolean = year in selectableExecutionYears(today)
}

/** Material3 lays the title out itself; this only restores the inset the dialog expects. */
private val TitlePadding = 24.dp
