/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.ui.payment

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.jetbrains.compose.resources.stringResource
import org.mifosx.openbanking.core.ui.components.MifosSectionHeading
import org.mifosx.openbanking.core.ui.generated.resources.Res
import org.mifosx.openbanking.core.ui.generated.resources.core_ui_payment_history_empty
import org.mifosx.openbanking.core.ui.generated.resources.core_ui_payment_history_failed
import org.mifosx.openbanking.core.ui.generated.resources.core_ui_payment_history_open
import org.mifosx.openbanking.core.ui.generated.resources.core_ui_payment_history_see_all
import org.mifosx.openbanking.core.ui.generated.resources.core_ui_payment_history_title
import template.core.base.designsystem.theme.KptTheme

/**
 * A list of payments, newest first.
 *
 * @param onPaymentClick Opens one payment, or null to leave the rows inert.
 * @param onSeeAll Opens the full list, or null to omit the affordance.
 */
@Composable
fun MifosPaymentHistoryList(
    payments: List<MifosPaymentRowUi>,
    modifier: Modifier = Modifier,
    title: String = stringResource(Res.string.core_ui_payment_history_title),
    emptyLabel: String = stringResource(Res.string.core_ui_payment_history_empty),
    onPaymentClick: ((String) -> Unit)? = null,
    onSeeAll: (() -> Unit)? = null,
    testTag: String? = null,
    emptyTestTag: String? = null,
    rowTestTag: (String) -> String = { "paymentHistory:row:$it" },
) {
    Column(modifier = modifier.fillMaxWidth().thenTestTag(testTag)) {
        MifosSectionHeading(title)

        if (payments.isEmpty()) {
            Text(
                text = emptyLabel,
                style = KptTheme.typography.bodyMedium,
                color = KptTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = KptTheme.spacing.sm)
                    .thenTestTag(emptyTestTag),
            )
            return@Column
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(top = KptTheme.spacing.sm),
            shape = KptTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = KptTheme.colorScheme.surfaceContainerLowest,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = KptTheme.elevation.level0),
        ) {
            payments.forEachIndexed { index, payment ->
                if (index > 0) HorizontalDivider()
                MifosPaymentRow(
                    payment = payment,
                    onClick = onPaymentClick,
                    testTag = rowTestTag(payment.id),
                )
            }
        }

        if (onSeeAll != null) {
            TextButton(onClick = onSeeAll, modifier = Modifier.padding(top = KptTheme.spacing.xs)) {
                Text(
                    text = stringResource(Res.string.core_ui_payment_history_see_all),
                    color = KptTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun MifosPaymentRow(
    payment: MifosPaymentRowUi,
    onClick: ((String) -> Unit)?,
    testTag: String,
) {
    val failed = payment.failed
    val tone = if (failed) KptTheme.colorScheme.error else KptTheme.colorScheme.onSurface
    val openLabel = stringResource(Res.string.core_ui_payment_history_open)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier
                        .clickable { onClick(payment.id) }
                        .semantics { contentDescription = openLabel }
                },
            )
            .padding(KptTheme.spacing.md)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = payment.amountLabel,
                style = KptTheme.typography.titleMedium,
                color = tone,
            )
            if (failed) {
                Text(
                    text = stringResource(Res.string.core_ui_payment_history_failed),
                    style = KptTheme.typography.bodySmall,
                    color = KptTheme.colorScheme.error,
                )
            }
            Text(
                text = payment.dateLabel,
                style = KptTheme.typography.bodySmall,
                color = KptTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KptTheme.spacing.xs),
        ) {
            Icon(
                imageVector = if (failed) Icons.Filled.ErrorOutline else Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = if (failed) KptTheme.colorScheme.error else KptTheme.colorScheme.outline,
            )
            if (!failed) {
                Text(
                    text = payment.statusLabel,
                    style = KptTheme.typography.bodyMedium,
                    color = KptTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onClick != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = KptTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun Modifier.thenTestTag(tag: String?): Modifier =
    if (tag == null) this else testTag(tag)
