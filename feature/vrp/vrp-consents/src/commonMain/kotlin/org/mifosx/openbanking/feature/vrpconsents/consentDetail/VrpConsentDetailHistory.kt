/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.vrpconsents.consentDetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.jetbrains.compose.resources.stringResource
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.Res
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_detail_history_empty
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_detail_history_title
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_detail_payment_failed
import org.mifosx.openbanking.feature.vrpconsents.paymentStatusLabel
import template.core.base.designsystem.theme.KptTheme

/** Every payment attempted under the consent, newest first. */
@Composable
internal fun PaymentHistory(payments: List<PaymentRowUi>) {
    Column(modifier = Modifier.fillMaxWidth().testTag(VrpConsentDetailTestTags.HISTORY)) {
        SectionHeading(stringResource(Res.string.feature_vrp_consents_detail_history_title))

        if (payments.isEmpty()) {
            Text(
                text = stringResource(Res.string.feature_vrp_consents_detail_history_empty),
                style = KptTheme.typography.bodyMedium,
                color = KptTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = KptTheme.spacing.sm)
                    .testTag(VrpConsentDetailTestTags.HISTORY_EMPTY),
            )
            return@Column
        }

        // Flat: the history is a list to read, and a raised surface reads as something to tap.
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = KptTheme.spacing.sm),
            shape = KptTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = KptTheme.colorScheme.surfaceContainerLowest),
            elevation = CardDefaults.cardElevation(defaultElevation = KptTheme.elevation.level0),
        ) {
            payments.forEachIndexed { index, payment ->
                if (index > 0) HorizontalDivider()
                PaymentRow(payment)
            }
        }
    }
}

@Composable
private fun PaymentRow(payment: PaymentRowUi) {
    val failed = payment.hasFailed
    val tone = if (failed) KptTheme.colorScheme.error else KptTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(KptTheme.spacing.md)
            .testTag(VrpConsentDetailTestTags.paymentRow(payment.localId)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = payment.sentAmount,
                style = KptTheme.typography.titleMedium,
                color = tone,
                modifier = Modifier.testTag(VrpConsentDetailTestTags.paymentAmount(payment.localId)),
            )
            if (failed) {
                Text(
                    text = stringResource(Res.string.feature_vrp_consents_detail_payment_failed),
                    style = KptTheme.typography.bodySmall,
                    color = KptTheme.colorScheme.error,
                )
            }
            Text(
                text = payment.sentOn,
                style = KptTheme.typography.bodySmall,
                color = KptTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KptTheme.spacing.xs),
            modifier = Modifier.testTag(VrpConsentDetailTestTags.paymentStatus(payment.localId)),
        ) {
            Icon(
                imageVector = if (failed) Icons.Filled.ErrorOutline else Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = if (failed) KptTheme.colorScheme.error else KptTheme.colorScheme.outline,
            )
            if (!failed) {
                Text(
                    text = paymentStatusLabel(payment.status),
                    style = KptTheme.typography.bodyMedium,
                    color = KptTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
