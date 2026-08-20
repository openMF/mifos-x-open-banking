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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.mifosx.openbanking.core.model.banking.payment.PaymentRail
import org.mifosx.openbanking.core.ui.generated.resources.Res
import org.mifosx.openbanking.core.ui.generated.resources.core_ui_rail_domestic
import org.mifosx.openbanking.core.ui.generated.resources.core_ui_rail_international
import template.core.base.designsystem.theme.KptTheme

private val TrackCorner = RoundedCornerShape(8.dp)
private val OptionCorner = RoundedCornerShape(6.dp)
private val OptionHeight = 40.dp
private val TrackPadding = 4.dp

/**
 * Which rail the payment goes out on.
 *
 * The two rails accept genuinely different fields, so this is not a cosmetic filter — switching it
 * changes what the form asks for. It renders on the form page only: the rail must not be able to
 * change while a review of a specific payment is on screen.
 *
 * @param optionTestTag Applied per rail, so a caller's own suite can address one option.
 */
@Composable
fun MifosRailToggle(
    rail: PaymentRail,
    onSelect: (PaymentRail) -> Unit,
    modifier: Modifier = Modifier,
    optionTestTag: (PaymentRail) -> String = ::railOptionTag,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(KptTheme.colorScheme.surfaceContainer, TrackCorner)
            .padding(TrackPadding),
        horizontalArrangement = Arrangement.spacedBy(TrackPadding),
    ) {
        listOf(PaymentRail.Domestic, PaymentRail.International).forEach { option ->
            val selected = option == rail
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(OptionHeight)
                    .testTag(optionTestTag(option))
                    .background(
                        if (selected) {
                            KptTheme.colorScheme.primary
                        } else {
                            KptTheme.colorScheme.surfaceContainer
                        },
                        OptionCorner,
                    )
                    .selectable(selected = selected, role = Role.RadioButton) { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(option.labelResource()),
                    style = KptTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) {
                        KptTheme.colorScheme.onPrimary
                    } else {
                        KptTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

private fun PaymentRail.labelResource(): StringResource = when (this) {
    PaymentRail.Domestic -> Res.string.core_ui_rail_domestic
    PaymentRail.International -> Res.string.core_ui_rail_international
}

/** The default per-rail tag. */
fun railOptionTag(rail: PaymentRail): String = when (rail) {
    PaymentRail.Domestic -> "mifosRailToggle:option:Domestic"
    PaymentRail.International -> "mifosRailToggle:option:International"
}
