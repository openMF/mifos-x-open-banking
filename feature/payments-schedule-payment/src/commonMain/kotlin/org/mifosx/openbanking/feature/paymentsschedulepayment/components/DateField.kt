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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.mifosx.openbanking.core.model.banking.payment.PaymentRail
import org.mifosx.openbanking.feature.paymentsschedulepayment.CardBorder
import org.mifosx.openbanking.feature.paymentsschedulepayment.CardCorner
import org.mifosx.openbanking.feature.paymentsschedulepayment.CardPadding
import org.mifosx.openbanking.feature.paymentsschedulepayment.GlyphSize
import org.mifosx.openbanking.feature.paymentsschedulepayment.HeroGap
import org.mifosx.openbanking.feature.paymentsschedulepayment.SchedulePaymentTestTags
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.Res
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_date_content_description
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_date_helper_domestic
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_date_helper_international
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_date_placeholder
import template.core.base.designsystem.theme.KptTheme

/**
 * The execution date, as a row that opens the picker.
 *
 * Not a text field. The date has to satisfy a window the customer cannot see and, on the
 * international rail, a working-day rule as well — so anything typed would have to be rejected after
 * the fact, which is the thing the picker exists to avoid. Tapping is the only way in.
 *
 * The helper text underneath is rail-specific because the accepted window is. It states the rule
 * rather than waiting for the customer to hit it: Material3 greys out unavailable days silently, and
 * a calendar where half the month is dim with no explanation reads as broken rather than as
 * constrained.
 */
@Composable
internal fun DateField(
    dateLabel: String,
    rail: PaymentRail,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val placeholder = stringResource(Res.string.feature_payments_schedule_payment_date_placeholder)
    val helper = when (rail) {
        PaymentRail.Domestic -> stringResource(Res.string.feature_payments_schedule_payment_date_helper_domestic)
        PaymentRail.International ->
            stringResource(Res.string.feature_payments_schedule_payment_date_helper_international)
    }
    val chosen = dateLabel.isNotBlank()
    // The spoken label carries the window as well as the date. A screen-reader user gets no benefit
    // from greyed-out days, so the rule has to be in the description or it is not conveyed at all.
    val spoken = stringResource(
        Res.string.feature_payments_schedule_payment_date_content_description,
        "${if (chosen) dateLabel else placeholder}. $helper",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CardCorner))
                .border(
                    width = CardBorder,
                    color = KptTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(CardCorner),
                )
                .background(KptTheme.colorScheme.surfaceContainerLowest)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(CardPadding)
                .semantics { contentDescription = spoken }
                .testTag(SchedulePaymentTestTags.DATE_FIELD),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HeroGap),
        ) {
            Icon(
                imageVector = Icons.Filled.CalendarToday,
                contentDescription = null,
                tint = KptTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(GlyphSize),
            )
            Text(
                text = if (chosen) dateLabel else placeholder,
                style = KptTheme.typography.titleSmall,
                // Muted until a date exists, so an unfilled mandatory field does not read as filled.
                color = if (chosen) {
                    KptTheme.colorScheme.onSurface
                } else {
                    KptTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = KptTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(GlyphSize),
            )
        }

        Text(
            text = helper,
            style = KptTheme.typography.bodySmall,
            color = KptTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = HelperTopGap, start = CardPadding),
        )
    }
}

private val HelperTopGap = 6.dp
