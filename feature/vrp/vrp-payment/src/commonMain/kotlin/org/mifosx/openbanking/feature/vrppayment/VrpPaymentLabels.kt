/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.vrppayment

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import org.mifosx.openbanking.core.common.formatMinorUnits
import org.mifosx.openbanking.core.model.vrp.Money
import org.mifosx.openbanking.core.model.vrp.PeriodType
import org.mifosx.openbanking.feature.vrppayment.generated.resources.Res
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_period_day
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_period_fortnight
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_period_half_year
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_period_month
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_period_week
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_period_year

/** An amount as this screen renders it, e.g. `£45.00`. */
internal fun formatExactAmount(amount: Money): String =
    formatMinorUnits(amount.minorUnits, amount.currency)

/** The period word, e.g. `month`. Blank when no ceiling binds. */
@Composable
internal fun periodLabel(period: PeriodType?): String = when (period) {
    PeriodType.Day -> stringResource(Res.string.feature_vrp_payment_period_day)
    PeriodType.Week -> stringResource(Res.string.feature_vrp_payment_period_week)
    PeriodType.Fortnight -> stringResource(Res.string.feature_vrp_payment_period_fortnight)
    PeriodType.Month -> stringResource(Res.string.feature_vrp_payment_period_month)
    PeriodType.HalfYear -> stringResource(Res.string.feature_vrp_payment_period_half_year)
    PeriodType.Year -> stringResource(Res.string.feature_vrp_payment_period_year)
    null -> ""
}
