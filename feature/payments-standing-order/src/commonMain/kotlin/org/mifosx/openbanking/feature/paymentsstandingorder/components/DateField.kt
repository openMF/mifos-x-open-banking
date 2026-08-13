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
import androidx.compose.material3.TextButton
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
import org.mifosx.openbanking.feature.paymentsstandingorder.CardBorder
import org.mifosx.openbanking.feature.paymentsstandingorder.CardCorner
import org.mifosx.openbanking.feature.paymentsstandingorder.CardPadding
import org.mifosx.openbanking.feature.paymentsstandingorder.GlyphSize
import org.mifosx.openbanking.feature.paymentsstandingorder.HeroGap
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.Res
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_clear
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_date_content_description
import template.core.base.designsystem.theme.KptTheme

/**
 * One of the two mandate dates, as a row that opens the picker.
 *
 * Not a text field. Each date has to satisfy a window the customer cannot see — and the end date's
 * window depends on the start date — so anything typed would have to be rejected after the fact,
 * which is what the picker exists to avoid. Tapping is the only way in.
 *
 * [onClear] is non-null only where clearing means something. On the final date it does: an empty
 * value is the mandate running until the customer stops it, which is the common case and needs to be
 * reachable. The first date has no such affordance — a mandate this app sends always states when it
 * starts.
 *
 * [enabled] goes false on the final date until a first date exists. Its rule cannot be evaluated
 * without one, and offering a choice that would be rejected is the thing this whole screen avoids.
 */
@Composable
internal fun DateField(
    label: String,
    dateLabel: String,
    placeholder: String,
    helper: String,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClear: (() -> Unit)? = null,
) {
    val chosen = dateLabel.isNotBlank()
    // The spoken label carries the window as well as the value. A screen-reader user gets no benefit
    // from greyed-out days, so the rule has to be in the description or it is not conveyed at all.
    val spoken = stringResource(
        Res.string.feature_payments_standing_order_date_content_description,
        "$label. ${if (chosen) dateLabel else placeholder}. $helper",
    )
    val contentColour = when {
        !enabled -> KptTheme.colorScheme.onSurfaceVariant.copy(alpha = DISABLED_ALPHA)
        chosen -> KptTheme.colorScheme.onSurface
        else -> KptTheme.colorScheme.onSurfaceVariant
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = KptTheme.typography.bodySmall,
            color = KptTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = LabelGap, start = CardPadding),
        )
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
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(CardPadding)
                .semantics { contentDescription = spoken }
                .testTag(testTag),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HeroGap),
        ) {
            Icon(
                imageVector = Icons.Filled.CalendarToday,
                contentDescription = null,
                tint = contentColour,
                modifier = Modifier.size(GlyphSize),
            )
            Text(
                text = if (chosen) dateLabel else placeholder,
                style = KptTheme.typography.titleSmall,
                color = contentColour,
                modifier = Modifier.weight(1f),
            )
            if (onClear != null && enabled) {
                TextButton(
                    onClick = onClear,
                    modifier = Modifier.testTag(
                        org.mifosx.openbanking.feature.paymentsstandingorder
                            .StandingOrderTestTags.FINAL_DATE_CLEAR,
                    ),
                ) {
                    Text(
                        text = stringResource(Res.string.feature_payments_standing_order_clear),
                        style = KptTheme.typography.labelLarge,
                        color = KptTheme.colorScheme.primary,
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = contentColour,
                    modifier = Modifier.size(GlyphSize),
                )
            }
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
private val LabelGap = 4.dp
private const val DISABLED_ALPHA = 0.5f
