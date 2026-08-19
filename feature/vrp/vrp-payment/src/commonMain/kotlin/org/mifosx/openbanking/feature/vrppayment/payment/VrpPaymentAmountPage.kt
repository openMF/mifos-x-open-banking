/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.vrppayment.payment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import org.jetbrains.compose.resources.stringResource
import org.mifosx.openbanking.feature.vrppayment.generated.resources.Res
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_amount_error_decimals
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_amount_error_min
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_amount_error_missing
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_amount_error_per_payment
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_amount_error_remaining
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_amount_label
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_check_funds
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_from
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_funds_warning
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_per_payment_limit
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_remaining
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_remaining_note
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_to
import org.mifosx.openbanking.feature.vrppayment.periodLabel
import template.core.base.designsystem.theme.KptTheme

/** The entry phase: how much, against the two ceilings. */
@Composable
internal fun VrpPaymentAmountPage(
    form: PaymentFormUi,
    onAction: (VrpPaymentAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(KptTheme.spacing.md)
            .testTag(VrpPaymentTestTags.AMOUNT_PAGE),
        verticalArrangement = Arrangement.spacedBy(KptTheme.spacing.md),
    ) {
        Text(
            text = stringResource(Res.string.feature_vrp_payment_to, form.payeeName),
            style = KptTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = KptTheme.colorScheme.onSurface,
        )
        if (form.payerName.isNotBlank()) {
            Text(
                text = stringResource(Res.string.feature_vrp_payment_from, form.payerName),
                style = KptTheme.typography.bodyMedium,
                color = KptTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedTextField(
            value = form.amount,
            onValueChange = { onAction(VrpPaymentAction.AmountChanged(it)) },
            label = { Text(stringResource(Res.string.feature_vrp_payment_amount_label)) },
            prefix = { Text(text = "£", style = KptTheme.typography.titleMedium) },
            isError = form.problem != null,
            singleLine = true,
            shape = KptTheme.shapes.small,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().testTag(VrpPaymentTestTags.AMOUNT_FIELD),
        )

        form.errorMessage()?.let {
            Text(
                text = it,
                style = KptTheme.typography.bodySmall,
                color = KptTheme.colorScheme.error,
                modifier = Modifier.testTag(VrpPaymentTestTags.AMOUNT_ERROR),
            )
        }

        Text(
            text = stringResource(
                Res.string.feature_vrp_payment_per_payment_limit,
                form.perPaymentCeilingAmount,
            ),
            style = KptTheme.typography.bodyMedium,
            color = KptTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(VrpPaymentTestTags.PER_PAYMENT_LIMIT),
        )

        if (form.remainingAmount.isNotBlank()) {
            Text(
                text = stringResource(
                    Res.string.feature_vrp_payment_remaining,
                    form.remainingAmount,
                    periodLabel(form.periodType),
                ),
                style = KptTheme.typography.bodyMedium,
                color = KptTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(VrpPaymentTestTags.REMAINING),
            )
            AdvisoryNote()
        }

        TextButton(
            onClick = { onAction(VrpPaymentAction.CheckFunds) },
            enabled = form.isUsable,
            modifier = Modifier.testTag(VrpPaymentTestTags.CHECK_FUNDS_BUTTON),
        ) {
            Text(stringResource(Res.string.feature_vrp_payment_check_funds))
        }

        if (form.fundsWarning) {
            FundsWarning()
        }
    }
}

/** A remaining figure is this app's own count, so it never appears without saying so. */
@Composable
private fun AdvisoryNote() {
    Surface(
        shape = KptTheme.shapes.medium,
        color = KptTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(KptTheme.spacing.md),
            horizontalArrangement = Arrangement.spacedBy(KptTheme.spacing.sm),
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = KptTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = stringResource(Res.string.feature_vrp_payment_remaining_note),
                style = KptTheme.typography.bodyMedium,
                color = KptTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.testTag(VrpPaymentTestTags.REMAINING_NOTE),
            )
        }
    }
}

/** Shown only on a shortfall: an available answer ignores the limits and promises nothing. */
@Composable
private fun FundsWarning() {
    Row(
        modifier = Modifier.fillMaxWidth().testTag(VrpPaymentTestTags.FUNDS_WARNING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KptTheme.spacing.sm),
    ) {
        Icon(
            imageVector = Icons.Filled.WarningAmber,
            contentDescription = null,
            tint = KptTheme.colorScheme.error,
        )
        Text(
            text = stringResource(Res.string.feature_vrp_payment_funds_warning),
            style = KptTheme.typography.bodyMedium,
            color = KptTheme.colorScheme.error,
        )
    }
}

@Composable
private fun PaymentFormUi.errorMessage(): String? = when (problem) {
    null -> null
    AmountProblem.Missing -> stringResource(Res.string.feature_vrp_payment_amount_error_missing)
    AmountProblem.NotANumber -> stringResource(Res.string.feature_vrp_payment_amount_error_decimals)
    AmountProblem.BelowMinimum -> stringResource(Res.string.feature_vrp_payment_amount_error_min)

    AmountProblem.OverPerPayment -> stringResource(
        Res.string.feature_vrp_payment_amount_error_per_payment,
        perPaymentCeilingAmount,
    )

    AmountProblem.OverRemaining -> stringResource(
        Res.string.feature_vrp_payment_amount_error_remaining,
        remainingAmount,
        periodLabel(periodType),
    )
}
