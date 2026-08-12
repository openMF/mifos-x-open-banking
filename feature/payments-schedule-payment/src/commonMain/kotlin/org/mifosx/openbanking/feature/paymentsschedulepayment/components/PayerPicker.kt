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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.mifosx.openbanking.core.ui.account.accountDisplayName
import org.mifosx.openbanking.feature.paymentsschedulepayment.CardBorder
import org.mifosx.openbanking.feature.paymentsschedulepayment.CardCorner
import org.mifosx.openbanking.feature.paymentsschedulepayment.CardPadding
import org.mifosx.openbanking.feature.paymentsschedulepayment.GlyphSize
import org.mifosx.openbanking.feature.paymentsschedulepayment.HeroGap
import org.mifosx.openbanking.feature.paymentsschedulepayment.SchedulePaymentTestTags
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.Res
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_balance_available
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_balance_available_prefixed
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_payer_bank_choice
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_payer_bank_choice_supporting
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_payer_collapse_a11y
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_payer_expand_a11y
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_payer_unchosen
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_payer_unchosen_supporting
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.SchedulePaymentAccountRow
import template.core.base.designsystem.theme.KptTheme

private val RowMinHeight = 56.dp
private val RowGap = 12.dp

/**
 * The payer, as one collapsed row that opens into the alternatives.
 *
 * Every payable account used to be listed permanently. With four real accounts that filled half the
 * screen and pushed the amount below the fold, for a decision that is made once and then only looked
 * at — so the chosen account (or the invitation to choose one) stays visible and the rest folds away.
 *
 * "Choose at my bank" lives **inside** the expansion rather than beside it. It is the only route by
 * which a credit card or a Global Money wallet can fund a payment — both are filtered out of the
 * account list because the bank refuses them as a named `DebtorAccount` — so it has to stay
 * reachable, but it is an alternative to picking an account and belongs among them.
 */
@Composable
internal fun SchedulePaymentPayerPicker(
    rows: List<SchedulePaymentAccountRow>,
    selectedId: String?,
    letBankChoose: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelect: (String) -> Unit,
    onLetBankChoose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardCorner))
            .border(
                width = CardBorder,
                color = KptTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(CardCorner),
            )
            .background(KptTheme.colorScheme.surfaceContainerLowest),
    ) {
        CollapsedPayerRow(
            row = rows.firstOrNull { it.id == selectedId },
            letBankChoose = letBankChoose,
            expanded = expanded,
            onToggle = onToggle,
        )

        if (expanded) {
            HorizontalDivider(color = KptTheme.colorScheme.outlineVariant)
            Column(modifier = Modifier.testTag(SchedulePaymentTestTags.DEBTOR_LIST)) {
                rows.forEach { row ->
                    PayerOptionRow(
                        row = row,
                        selected = row.id == selectedId,
                        onClick = { onSelect(row.id) },
                        modifier = Modifier.testTag(SchedulePaymentTestTags.debtorRow(row.id)),
                    )
                }
                BankChoiceRow(selected = letBankChoose, onClick = onLetBankChoose)
            }
        }
    }
}

/**
 * What the picker says while it is shut: the chosen account, the bank choice, or an invitation.
 *
 * The unchosen case is deliberately not an empty row. The payer is not preselected — sending one
 * nobody picked was the defect that removed the preselection — so with nothing chosen this has to
 * ask for a decision rather than look like a field that failed to load.
 */
@Composable
private fun CollapsedPayerRow(
    row: SchedulePaymentAccountRow?,
    letBankChoose: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val headline = when {
        row != null -> accountDisplayName(
            nickname = row.nickname,
            accountSubType = row.accountSubType,
            accountNumber = row.accountNumber,
            rawIdentification = row.rawIdentification,
        )

        letBankChoose -> stringResource(Res.string.feature_payments_schedule_payment_payer_bank_choice)
        else -> stringResource(Res.string.feature_payments_schedule_payment_payer_unchosen)
    }
    val supporting = when {
        row != null -> headline.identifierHalf()
        letBankChoose -> stringResource(Res.string.feature_payments_schedule_payment_payer_bank_choice_supporting)
        else -> stringResource(Res.string.feature_payments_schedule_payment_payer_unchosen_supporting)
    }
    val trailing = row?.supporting
        ?.takeIf { it.isNotBlank() }
        ?.let { stringResource(Res.string.feature_payments_schedule_payment_balance_available_prefixed, it) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SchedulePaymentTestTags.PAYER_PICKER)
            .clickable(onClick = onToggle)
            .padding(CardPadding),
        horizontalArrangement = Arrangement.spacedBy(RowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PayerGlyph()
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(HeroGap),
        ) {
            Text(
                text = headline.nameHalf(),
                style = KptTheme.typography.titleSmall,
                color = KptTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (supporting.isNotBlank()) {
                Text(
                    text = supporting,
                    style = KptTheme.typography.bodySmall,
                    color = KptTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(HeroGap),
        ) {
            if (trailing != null) {
                Text(
                    text = trailing,
                    style = KptTheme.typography.bodySmall,
                    color = KptTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Icon(
                imageVector = chevronFor(expanded),
                contentDescription = stringResource(
                    if (expanded) {
                        Res.string.feature_payments_schedule_payment_payer_collapse_a11y
                    } else {
                        Res.string.feature_payments_schedule_payment_payer_expand_a11y
                    },
                ),
                tint = KptTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun chevronFor(expanded: Boolean): ImageVector =
    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore

/** The bank mark, in a tonal circle — the reference's `account_balance` in a secondary container. */
@Composable
private fun PayerGlyph() {
    Box(
        modifier = Modifier
            .size(GlyphSize)
            .background(KptTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.AccountBalance,
            contentDescription = null,
            tint = KptTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** One account inside the expansion. Compact, because the collapsed row carries the detail. */
@Composable
private fun PayerOptionRow(
    row: SchedulePaymentAccountRow,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = accountDisplayName(
        nickname = row.nickname,
        accountSubType = row.accountSubType,
        accountNumber = row.accountNumber,
        rawIdentification = row.rawIdentification,
    )
    OptionRow(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        leading = { OptionInitials(initialsOf(label), selected) },
        headline = label,
        supporting = row.supporting
            .takeIf { it.isNotBlank() }
            ?.let { stringResource(Res.string.feature_payments_schedule_payment_balance_available, it) }
            .orEmpty(),
    )
}

/** Sending no `DebtorAccount` at all: a shape the bank supports, offered as a peer of the accounts. */
@Composable
private fun BankChoiceRow(
    selected: Boolean,
    onClick: () -> Unit,
) {
    OptionRow(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.testTag(SchedulePaymentTestTags.PAYER_BANK_CHOICE),
        leading = {
            Icon(
                imageVector = Icons.Filled.AccountBalance,
                contentDescription = null,
                tint = KptTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(GlyphSize),
            )
        },
        headline = stringResource(Res.string.feature_payments_schedule_payment_payer_bank_choice),
        supporting = stringResource(Res.string.feature_payments_schedule_payment_payer_bank_choice_supporting),
    )
}

@Composable
private fun OptionRow(
    selected: Boolean,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    headline: String,
    supporting: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (selected) {
                    KptTheme.colorScheme.secondaryContainer
                } else {
                    KptTheme.colorScheme.surfaceContainerLowest
                },
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = RowMinHeight)
            .padding(horizontal = CardPadding, vertical = RowGap),
        horizontalArrangement = Arrangement.spacedBy(RowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(HeroGap),
        ) {
            Text(
                text = headline,
                style = KptTheme.typography.bodyMedium,
                color = KptTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (supporting.isNotBlank()) {
                Text(
                    text = supporting,
                    style = KptTheme.typography.bodySmall,
                    color = KptTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun OptionInitials(initials: String, selected: Boolean) {
    Box(
        modifier = Modifier
            .size(GlyphSize)
            .background(
                color = if (selected) {
                    KptTheme.colorScheme.primary
                } else {
                    KptTheme.colorScheme.surfaceContainerHigh
                },
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = KptTheme.typography.labelMedium,
            color = if (selected) {
                KptTheme.colorScheme.onPrimary
            } else {
                KptTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
