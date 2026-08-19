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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import org.jetbrains.compose.resources.stringResource
import org.mifosx.openbanking.feature.vrppayment.generated.resources.Res
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_amount_error_decimals
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_amount_error_min
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_amount_error_per_payment
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_amount_error_remaining
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_check_funds
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_from
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_funds_warning
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_per_payment_limit
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_remaining
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_remaining_note
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_to
import org.mifosx.openbanking.feature.vrppayment.periodLabel
import template.core.base.designsystem.theme.KptTheme

/**
 * The entry phase: how much, against the two ceilings.
 *
 * The figure carries no currency mark. Sterling is already stated as a fact directly beneath it, and
 * a mark inside the field states the unit twice.
 */
@Composable
internal fun VrpPaymentAmountPage(
    form: PaymentFormUi,
    onAction: (VrpPaymentAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(KptTheme.spacing.md)
            .testTag(VrpPaymentTestTags.AMOUNT_PAGE),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.feature_vrp_payment_to, form.payeeName),
            style = KptTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = KptTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (form.payerName.isNotBlank()) {
            Text(
                text = stringResource(Res.string.feature_vrp_payment_from, form.payerName),
                style = KptTheme.typography.bodyLarge,
                color = KptTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = KptTheme.spacing.xs),
            )
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AmountEntry(form.amount) { onAction(VrpPaymentAction.AmountChanged(it)) }

            form.errorMessage()?.let {
                Text(
                    text = it,
                    style = KptTheme.typography.bodyMedium,
                    color = KptTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = KptTheme.spacing.md)
                        .testTag(VrpPaymentTestTags.AMOUNT_ERROR),
                )
            }

            Text(
                text = stringResource(
                    Res.string.feature_vrp_payment_per_payment_limit,
                    form.perPaymentCeilingAmount,
                ),
                style = KptTheme.typography.bodyLarge,
                color = KptTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = KptTheme.spacing.lg)
                    .testTag(VrpPaymentTestTags.PER_PAYMENT_LIMIT),
            )

            if (form.remainingAmount.isNotBlank()) {
                Text(
                    text = stringResource(
                        Res.string.feature_vrp_payment_remaining,
                        form.remainingAmount,
                        periodLabel(form.periodType),
                    ),
                    style = KptTheme.typography.bodyLarge,
                    color = KptTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = KptTheme.spacing.xs)
                        .testTag(VrpPaymentTestTags.REMAINING),
                )
                Text(
                    text = stringResource(Res.string.feature_vrp_payment_remaining_note),
                    style = KptTheme.typography.bodyMedium,
                    color = KptTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = KptTheme.spacing.sm)
                        .testTag(VrpPaymentTestTags.REMAINING_NOTE),
                )
            }

            if (form.fundsWarning) {
                FundsWarning()
            }
        }

        OutlinedButton(
            onClick = { onAction(VrpPaymentAction.CheckFunds) },
            enabled = form.isUsable,
            modifier = Modifier
                .padding(bottom = KptTheme.spacing.md)
                .testTag(VrpPaymentTestTags.CHECK_FUNDS_BUTTON),
        ) {
            Text(stringResource(Res.string.feature_vrp_payment_check_funds))
        }
    }
}

/** The figure, set large and centred, with the currency stated beneath rather than inside it. */
@Composable
private fun AmountEntry(
    value: String,
    onValueChange: (String) -> Unit,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = KptTheme.typography.displayLarge.copy(
            color = KptTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Light,
        ),
        cursorBrush = SolidColor(KptTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().testTag(VrpPaymentTestTags.AMOUNT_FIELD),
    )
}

/** Shown only on a shortfall: an available answer ignores the limits and promises nothing. */
@Composable
private fun FundsWarning() {
    Row(
        modifier = Modifier
            .padding(top = KptTheme.spacing.md)
            .testTag(VrpPaymentTestTags.FUNDS_WARNING),
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
