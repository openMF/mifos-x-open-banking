/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.vrpsetup

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import org.mifosx.openbanking.core.model.vrp.PeriodType
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.Res
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_amount_error_decimals
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_amount_error_min
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_amount_error_missing
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_amount_error_ordering
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_payee_error_account_number
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_payee_error_name
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_payee_error_same
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_payee_error_sort_code
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_period_day
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_period_fortnight
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_period_half_year
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_period_month
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_period_week
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_period_year

/** The period word, e.g. `month`. */
@Composable
internal fun periodLabel(period: PeriodType): String = when (period) {
    PeriodType.Day -> stringResource(Res.string.feature_vrp_setup_period_day)
    PeriodType.Week -> stringResource(Res.string.feature_vrp_setup_period_week)
    PeriodType.Fortnight -> stringResource(Res.string.feature_vrp_setup_period_fortnight)
    PeriodType.Month -> stringResource(Res.string.feature_vrp_setup_period_month)
    PeriodType.HalfYear -> stringResource(Res.string.feature_vrp_setup_period_half_year)
    PeriodType.Year -> stringResource(Res.string.feature_vrp_setup_period_year)
}

/** What the customer is told about an amount that cannot be used. */
@Composable
internal fun amountErrorLabel(problem: AmountProblem?): String? = when (problem) {
    null -> null
    AmountProblem.Missing -> stringResource(Res.string.feature_vrp_setup_amount_error_missing)
    AmountProblem.NotANumber -> stringResource(Res.string.feature_vrp_setup_amount_error_decimals)
    AmountProblem.BelowMinimum -> stringResource(Res.string.feature_vrp_setup_amount_error_min)
    AmountProblem.NotBelowPeriodic -> stringResource(Res.string.feature_vrp_setup_amount_error_ordering)
}

/** What the customer is told about a payee that cannot be used. */
@Composable
internal fun payeeErrorLabel(problem: PayeeProblem?): String? = when (problem) {
    null -> null
    PayeeProblem.NameMissing -> stringResource(Res.string.feature_vrp_setup_payee_error_name)
    PayeeProblem.SortCodeIncomplete -> stringResource(Res.string.feature_vrp_setup_payee_error_sort_code)
    PayeeProblem.AccountNumberIncomplete ->
        stringResource(Res.string.feature_vrp_setup_payee_error_account_number)

    PayeeProblem.SameAsPayer -> stringResource(Res.string.feature_vrp_setup_payee_error_same)
}
