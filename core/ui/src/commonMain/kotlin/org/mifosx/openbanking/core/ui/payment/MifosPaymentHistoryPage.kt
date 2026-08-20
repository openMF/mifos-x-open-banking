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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.mifosx.openbanking.core.ui.generated.resources.Res
import org.mifosx.openbanking.core.ui.generated.resources.core_ui_payment_history_failed
import template.core.base.designsystem.theme.KptTheme

private val PagePadding = 16.dp

/**
 * Every payment a feature has recorded, newest first.
 *
 * Lazy and one card per row, unlike the capped list under a form: this one has no upper bound.
 *
 * @param onPaymentClick Opens one payment, or null to leave the rows inert.
 */
@Composable
fun MifosPaymentHistoryPage(
    payments: List<MifosPaymentRowUi>,
    emptyLabel: String,
    modifier: Modifier = Modifier,
    onPaymentClick: ((String) -> Unit)? = null,
    testTag: String? = null,
    rowTestTag: (String) -> String = { "paymentHistory:row:$it" },
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(if (testTag == null) Modifier else Modifier.testTag(testTag)),
        contentAlignment = Alignment.Center,
    ) {
        if (payments.isEmpty()) {
            Text(
                text = emptyLabel,
                style = KptTheme.typography.bodyMedium,
                color = KptTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(PagePadding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(PagePadding),
                verticalArrangement = Arrangement.spacedBy(KptTheme.spacing.sm),
            ) {
                items(payments, key = { it.id }) { payment ->
                    PaymentCard(
                        payment = payment,
                        onClick = onPaymentClick,
                        testTag = rowTestTag(payment.id),
                    )
                }
            }
        }
    }
}

@Composable
private fun PaymentCard(
    payment: MifosPaymentRowUi,
    onClick: ((String) -> Unit)?,
    testTag: String,
) {
    val failed = payment.failed
    val tone = if (failed) KptTheme.colorScheme.error else KptTheme.colorScheme.onSurface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick == null) Modifier else Modifier.clickable { onClick(payment.id) })
            .testTag(testTag),
        shape = KptTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = KptTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = KptTheme.elevation.level0),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(KptTheme.spacing.md),
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
            }
        }
    }
}
