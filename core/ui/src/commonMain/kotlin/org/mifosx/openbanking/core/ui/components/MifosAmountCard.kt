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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.mifosx.openbanking.core.ui.generated.resources.Res
import org.mifosx.openbanking.core.ui.generated.resources.core_ui_amount_available
import org.mifosx.openbanking.core.ui.generated.resources.core_ui_amount_label
import org.mifosx.openbanking.core.ui.generated.resources.core_ui_amount_placeholder
import template.core.base.designsystem.theme.KptTheme

/** Keeps the figure's row a stable height, and is the height the currency box is cut to. */
private val FigureRowMinHeight = 48.dp

private val CardCorner = 12.dp
private val CardBorder = 1.dp
private val CardPadding = 16.dp
private val RowGap = 12.dp
private val SectionGap = 8.dp

/**
 * How the row is divided between the currency and the figure.
 *
 * Weights rather than a fixed width for the box: the proportion then holds on a phone, on a desktop
 * window several times its width, and at a form's max width in between. They are declared here, in
 * the one component that lays the row out, rather than passed in — that is what makes the figure
 * start at the same x-position whichever control the caller supplies.
 */
private const val CURRENCY_WEIGHT = 0.22f
private const val FIGURE_WEIGHT = 0.78f

/**
 * The amount, as a card: the figure, a hairline, and the available balance beneath.
 *
 * The figure is a [BasicTextField] rather than a label over an `OutlinedTextField`. What is being
 * asked for is one number, and a bordered box with a floating label around a large figure reads as
 * a form field among form fields — the point of the card is that this is the payment, not a detail
 * of it.
 *
 * **No currency mark inside the field.** The unit is said once, by the [leading] control; the
 * balance line beneath keeps its own symbol because it reads in the *account's* currency, which is
 * the one place the two can differ.
 *
 * [balanceLabel] is blank until a payer is chosen, which is what hides the balance line: with no
 * account there is no balance to state, and a printed £0.00 would be a claim about one. An
 * [errorMessage] takes that line rather than being added below it, so the card cannot grow taller
 * as the customer types.
 *
 * [leading] is the currency, and it is the row's FIRST child — a control that names the unit of the
 * figure reads as an afterthought at the trailing edge. It has no default and every caller must
 * supply one: the slot's width is fixed here, so passing nothing would leave a fifth of the row
 * blank and only appear to hold the figure's position.
 */
@Composable
fun MifosAmountCard(
    amount: String,
    balanceLabel: String,
    errorMessage: String?,
    onAmountChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    fieldTestTag: String = AMOUNT_FIELD_TAG,
    errorTestTag: String = AMOUNT_ERROR_TAG,
    balanceTestTag: String = AMOUNT_BALANCE_TAG,
    leading: @Composable () -> Unit,
) {
    val amountLabel = stringResource(Res.string.core_ui_amount_label)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardCorner))
            .border(
                width = CardBorder,
                color = if (errorMessage != null) {
                    KptTheme.colorScheme.error
                } else {
                    KptTheme.colorScheme.primary
                },
                shape = RoundedCornerShape(CardCorner),
            )
            .background(KptTheme.colorScheme.surfaceContainerLowest)
            .padding(CardPadding),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(SectionGap),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = FigureRowMinHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RowGap),
        ) {
            // A definite height rather than fillMaxHeight: this row sits inside a vertical scroll,
            // so its own height constraint is unbounded and fillMaxHeight would silently do
            // nothing. Cutting the box to the figure row's height is what makes the two look like
            // one control split in half rather than a small thing parked beside a big one.
            Box(
                modifier = Modifier.weight(CURRENCY_WEIGHT).height(FigureRowMinHeight),
                contentAlignment = Alignment.Center,
            ) {
                leading()
            }
            AmountField(
                amount = amount,
                contentDescription = amountLabel,
                onAmountChange = onAmountChange,
                testTag = fieldTestTag,
                modifier = Modifier.weight(FIGURE_WEIGHT),
            )
        }

        // Only when there is something under it. With no payer there is no balance to state and no
        // problem to report, and a rule with nothing beneath it reads as a card that failed to load.
        if (errorMessage != null || balanceLabel.isNotBlank()) {
            HorizontalDivider(color = KptTheme.colorScheme.outlineVariant)
            AmountFooter(
                balanceLabel = balanceLabel,
                errorMessage = errorMessage,
                errorTestTag = errorTestTag,
                balanceTestTag = balanceTestTag,
            )
        }
    }
}

@Composable
private fun AmountFooter(
    balanceLabel: String,
    errorMessage: String?,
    errorTestTag: String,
    balanceTestTag: String,
) {
    when {
        errorMessage != null -> Text(
            text = errorMessage,
            style = KptTheme.typography.bodySmall,
            color = KptTheme.colorScheme.error,
            modifier = Modifier.testTag(errorTestTag),
        )

        balanceLabel.isNotBlank() -> Text(
            text = stringResource(Res.string.core_ui_amount_available, balanceLabel),
            style = KptTheme.typography.bodySmall,
            color = KptTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(balanceTestTag),
        )
    }
}

/**
 * The figure itself.
 *
 * The placeholder is drawn behind the field rather than substituted into it, so an empty card still
 * shows the shape of what is wanted without anyone having to delete a zero to type over it.
 *
 * Left-aligned and weighted rather than centred inside an intrinsic width. Centring it would leave
 * a gap between the currency box and the figure it qualifies, and would start the figure somewhere
 * other than where the balance line beneath it starts.
 */
@Composable
private fun AmountField(
    amount: String,
    contentDescription: String,
    onAmountChange: (String) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val figureStyle = KptTheme.typography.displaySmall.copy(
        color = KptTheme.colorScheme.onSurface,
        textAlign = TextAlign.Start,
    )
    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart,
    ) {
        if (amount.isEmpty()) {
            Text(
                text = stringResource(Res.string.core_ui_amount_placeholder),
                style = figureStyle,
                color = KptTheme.colorScheme.outlineVariant,
            )
        }
        BasicTextField(
            value = amount,
            onValueChange = onAmountChange,
            textStyle = figureStyle,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            cursorBrush = SolidColor(KptTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
                .semantics { this.contentDescription = contentDescription },
        )
    }
}

private const val AMOUNT_FIELD_TAG = "mifosAmountCard:field"
private const val AMOUNT_ERROR_TAG = "mifosAmountCard:error"
private const val AMOUNT_BALANCE_TAG = "mifosAmountCard:balance"
