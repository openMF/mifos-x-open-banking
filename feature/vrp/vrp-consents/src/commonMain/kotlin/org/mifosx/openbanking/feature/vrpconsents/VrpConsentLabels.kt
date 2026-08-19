/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.vrpconsents

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import org.mifosx.openbanking.core.common.formatMinorUnits
import org.mifosx.openbanking.core.model.banking.payment.PaymentDisposition
import org.mifosx.openbanking.core.model.banking.payment.PaymentStatus
import org.mifosx.openbanking.core.model.callback.ConsentStatus
import org.mifosx.openbanking.core.model.vrp.Money
import org.mifosx.openbanking.core.model.vrp.PeriodType
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.Res
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_payment_failed
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_payment_pending
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_payment_sent
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_period_day
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_period_fortnight
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_period_half_year
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_period_month
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_period_week
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_period_year
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_status_active
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_status_awaiting
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_status_cancelled
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_status_consumed
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_status_expired
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_status_rejected
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_status_revoked

private const val WHOLE_AMOUNT_SUFFIX = ".00"

/**
 * A ceiling as the list renders it, e.g. `£500`.
 *
 * Drops the pence on a whole figure, which the detail screen deliberately does not — the list is a
 * glanceable summary and its figure is set large, where trailing zeros read as noise.
 */
internal fun formatLimitAmount(amount: Money): String =
    formatMinorUnits(amount.minorUnits, amount.currency).removeSuffix(WHOLE_AMOUNT_SUFFIX)

/** A ceiling as the detail screen renders it, e.g. `£500.00`. */
internal fun formatExactAmount(amount: Money): String =
    formatMinorUnits(amount.minorUnits, amount.currency)

/** The period word, e.g. `month`. Blank when the consent carries no periodic ceiling. */
@Composable
internal fun periodLabel(period: PeriodType?): String = when (period) {
    PeriodType.Day -> stringResource(Res.string.feature_vrp_consents_period_day)
    PeriodType.Week -> stringResource(Res.string.feature_vrp_consents_period_week)
    PeriodType.Fortnight -> stringResource(Res.string.feature_vrp_consents_period_fortnight)
    PeriodType.Month -> stringResource(Res.string.feature_vrp_consents_period_month)
    PeriodType.HalfYear -> stringResource(Res.string.feature_vrp_consents_period_half_year)
    PeriodType.Year -> stringResource(Res.string.feature_vrp_consents_period_year)
    null -> ""
}

/** The status word shown on a standing payment, e.g. `Active`. */
@Composable
internal fun consentStatusLabel(status: ConsentStatus): String = when (status) {
    ConsentStatus.AwaitingAuthorisation -> stringResource(Res.string.feature_vrp_consents_status_awaiting)
    ConsentStatus.Authorised -> stringResource(Res.string.feature_vrp_consents_status_active)
    ConsentStatus.Rejected -> stringResource(Res.string.feature_vrp_consents_status_rejected)
    ConsentStatus.Revoked -> stringResource(Res.string.feature_vrp_consents_status_revoked)
    ConsentStatus.Cancelled -> stringResource(Res.string.feature_vrp_consents_status_cancelled)
    ConsentStatus.Expired -> stringResource(Res.string.feature_vrp_consents_status_expired)
    ConsentStatus.Consumed -> stringResource(Res.string.feature_vrp_consents_status_consumed)
}

/**
 * The status word shown on one payment, e.g. `Sent`.
 *
 * Read from the disposition rather than the code, so both spellings the bank uses for a settled
 * payment render alike and an unrecognised value never reads as settled.
 */
@Composable
internal fun paymentStatusLabel(status: PaymentStatus): String = when (status.disposition) {
    PaymentDisposition.TerminalSuccess -> stringResource(Res.string.feature_vrp_consents_payment_sent)
    PaymentDisposition.TerminalFailure -> stringResource(Res.string.feature_vrp_consents_payment_failed)
    PaymentDisposition.InProgress -> stringResource(Res.string.feature_vrp_consents_payment_pending)
}
