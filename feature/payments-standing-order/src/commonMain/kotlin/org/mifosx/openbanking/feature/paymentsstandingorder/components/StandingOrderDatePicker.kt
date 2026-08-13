/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.paymentsstandingorder.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.stringResource
import org.mifosx.openbanking.feature.paymentsstandingorder.StandingOrderTestTags
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.Res
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_date_picker_cancel
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_date_picker_confirm
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_date_picker_window
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_final_date_picker_title
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_first_date_picker_title
import org.mifosx.openbanking.feature.paymentsstandingorder.ui.MAX_FINAL_PAYMENT_MONTHS_AHEAD
import org.mifosx.openbanking.feature.paymentsstandingorder.ui.MAX_FIRST_PAYMENT_DAYS_AHEAD
import org.mifosx.openbanking.feature.paymentsstandingorder.ui.StandingOrderDateRole
import org.mifosx.openbanking.feature.paymentsstandingorder.ui.epochMillisOf
import org.mifosx.openbanking.feature.paymentsstandingorder.ui.formatStandingOrderDate
import org.mifosx.openbanking.feature.paymentsstandingorder.ui.isSelectableDate
import org.mifosx.openbanking.feature.paymentsstandingorder.ui.selectableYears
import org.mifosx.openbanking.feature.paymentsstandingorder.ui.utcDateOf

/**
 * One dialog serving both mandate dates.
 *
 * Parameterised by [role] rather than duplicated, so the picker and the draft builder cannot end up
 * applying different rules to the same date — they both go through `isSelectableDate`.
 *
 * The window is stated in words above the grid as well as enforced within it. Material3 greys
 * unavailable days silently, and a calendar where most of the month is dim with no explanation reads
 * as broken rather than as constrained — and a screen reader conveys none of the greying at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StandingOrderDatePickerDialog(
    today: LocalDate,
    role: StandingOrderDateRole,
    firstPaymentDate: LocalDate?,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectable = remember(today, role, firstPaymentDate) {
        MandateSelectableDates(today = today, role = role, firstPaymentDate = firstPaymentDate)
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = selected?.let(::epochMillisOf),
        yearRange = selectableYears(today, role),
        selectableDates = selectable,
    )

    val earliest = when (role) {
        StandingOrderDateRole.First -> today.plus(1, DateTimeUnit.DAY)
        // The later of "after the first payment" and "not today or tomorrow": both apply, and which
        // one binds depends on how soon the mandate starts.
        StandingOrderDateRole.Final -> maxOf(
            firstPaymentDate?.plus(1, DateTimeUnit.DAY) ?: today.plus(2, DateTimeUnit.DAY),
            today.plus(2, DateTimeUnit.DAY),
        )
    }
    val latest = when (role) {
        StandingOrderDateRole.First -> today.plus(MAX_FIRST_PAYMENT_DAYS_AHEAD, DateTimeUnit.DAY)
        StandingOrderDateRole.Final -> today.plus(MAX_FINAL_PAYMENT_MONTHS_AHEAD, DateTimeUnit.MONTH)
    }

    DatePickerDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag(StandingOrderTestTags.DATE_PICKER),
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { onSelect(utcDateOf(it)) } ?: onDismiss()
                },
                modifier = Modifier.testTag(StandingOrderTestTags.DATE_PICKER_CONFIRM),
            ) {
                Text(stringResource(Res.string.feature_payments_standing_order_date_picker_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(StandingOrderTestTags.DATE_PICKER_CANCEL),
            ) {
                Text(stringResource(Res.string.feature_payments_standing_order_date_picker_cancel))
            }
        },
    ) {
        DatePicker(
            state = state,
            title = {
                Text(
                    text = when (role) {
                        StandingOrderDateRole.First ->
                            stringResource(Res.string.feature_payments_standing_order_first_date_picker_title)

                        StandingOrderDateRole.Final ->
                            stringResource(Res.string.feature_payments_standing_order_final_date_picker_title)
                    },
                    modifier = Modifier.padding(TitlePadding),
                )
            },
            headline = {
                Text(
                    text = stringResource(
                        Res.string.feature_payments_standing_order_date_picker_window,
                        formatStandingOrderDate(earliest),
                        formatStandingOrderDate(latest),
                    ),
                    modifier = Modifier.padding(TitlePadding),
                )
            },
            colors = DatePickerDefaults.colors(),
        )
    }
}

/**
 * The window, expressed the way Material3 asks for it.
 *
 * Delegates to the same predicate the draft builder uses, so a date the picker offers is one the
 * builder will accept — the two diverging is exactly how a refused date reaches the bank.
 */
@OptIn(ExperimentalMaterial3Api::class)
private class MandateSelectableDates(
    private val today: LocalDate,
    private val role: StandingOrderDateRole,
    private val firstPaymentDate: LocalDate?,
) : SelectableDates {

    override fun isSelectableDate(utcTimeMillis: Long): Boolean = isSelectableDate(
        date = utcDateOf(utcTimeMillis),
        today = today,
        role = role,
        firstPaymentDate = firstPaymentDate,
    )

    override fun isSelectableYear(year: Int): Boolean = year in selectableYears(today, role)
}

private val TitlePadding = 24.dp
