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
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import org.jetbrains.compose.resources.stringResource
import org.mifosx.openbanking.feature.vrppayment.generated.resources.Res
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_amount_error_decimals
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_amount_resting
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_currency_sign
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

/**
 * The figure, set large and centred, with the sign carried in front of it.
 *
 * The sign is a visual transformation rather than part of the value, so it travels with the number
 * as it grows and cannot be deleted. The resting `0.00` matters as much: an empty [BasicTextField]
 * paints nothing at all — no border, no baseline, no hint — leaving nowhere visible to tap.
 */
@Composable
private fun AmountEntry(
    value: String,
    onValueChange: (String) -> Unit,
) {
    val amountStyle = KptTheme.typography.displayLarge.copy(
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.Light,
    )
    val sign = stringResource(Res.string.feature_vrp_payment_currency_sign)
    val signTransformation = remember(sign) { CurrencySignTransformation(sign) }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = amountStyle.copy(color = KptTheme.colorScheme.primary),
        cursorBrush = SolidColor(KptTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        // Skipped while empty, or the lone sign would be painted over the resting figure.
        visualTransformation = if (value.isEmpty()) VisualTransformation.None else signTransformation,
        modifier = Modifier.fillMaxWidth().testTag(VrpPaymentTestTags.AMOUNT_FIELD),
        decorationBox = { field ->
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (value.isEmpty()) {
                    Text(
                        text = sign + stringResource(Res.string.feature_vrp_payment_amount_resting),
                        style = amountStyle,
                        color = KptTheme.colorScheme.outline,
                    )
                }
                field()
            }
        },
    )
}

/** Shows [sign] before the amount without putting it in the value the customer edits. */
private class CurrencySignTransformation(private val sign: String) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText = TransformedText(
        text = AnnotatedString(sign + text.text),
        offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = offset + sign.length
            override fun transformedToOriginal(offset: Int): Int =
                (offset - sign.length).coerceIn(0, text.length)
        },
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
