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

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.jetbrains.compose.resources.stringResource
import org.mifosx.openbanking.core.model.banking.payment.StandingOrderFrequency
import org.mifosx.openbanking.feature.paymentsstandingorder.StandingOrderTestTags
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.Res
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_frequency_fortnightly
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_frequency_label
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_frequency_monthly
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_frequency_quarterly
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_frequency_weekly
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_frequency_yearly

/**
 * How often the mandate repeats.
 *
 * Backed by [StandingOrderFrequency], which carries only the five codes HSBC accepts of OBIE's nine.
 * A closed enum rather than a string is what makes `MONT` unrepresentable — it is not an OBIE code,
 * it is refused `U002`, and it is one character from the code that means monthly.
 *
 * The customer sees words; the four-letter code never reaches the screen.
 */
@Composable
internal fun FrequencyField(
    selected: StandingOrderFrequency,
    onSelect: (StandingOrderFrequency) -> Unit,
    modifier: Modifier = Modifier,
) {
    StandingOrderDropdownField(
        label = stringResource(Res.string.feature_payments_standing_order_frequency_label),
        selected = selected,
        options = StandingOrderFrequency.entries,
        optionLabel = { frequencyLabel(it) },
        optionTestTag = { StandingOrderTestTags.frequencyOption(it) },
        onSelect = onSelect,
        modifier = modifier.testTag(StandingOrderTestTags.FREQUENCY_FIELD),
    )
}

@Composable
internal fun frequencyLabel(frequency: StandingOrderFrequency): String = when (frequency) {
    StandingOrderFrequency.Weekly -> stringResource(Res.string.feature_payments_standing_order_frequency_weekly)
    StandingOrderFrequency.Fortnightly ->
        stringResource(Res.string.feature_payments_standing_order_frequency_fortnightly)
    StandingOrderFrequency.Monthly -> stringResource(Res.string.feature_payments_standing_order_frequency_monthly)
    StandingOrderFrequency.Quarterly ->
        stringResource(Res.string.feature_payments_standing_order_frequency_quarterly)
    StandingOrderFrequency.Yearly -> stringResource(Res.string.feature_payments_standing_order_frequency_yearly)
}
