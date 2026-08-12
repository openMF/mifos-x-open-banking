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

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.mifosx.openbanking.core.model.banking.payment.ChargeBearer
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.Res
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_charge_bearer_creditor
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_charge_bearer_debtor
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_charge_bearer_shared
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_currency_aed
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_currency_aud
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_currency_cad
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_currency_chf
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_currency_cny
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_currency_czk
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_currency_dkk
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_currency_eur
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_currency_gbp
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_currency_hkd
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_currency_nok
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_currency_nzd
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_currency_pln
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_currency_sar
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_currency_sek
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_currency_sgd
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_currency_thb
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_currency_usd
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_currency_zar

/**
 * Every currency this app has a name for, keyed by its ISO code.
 *
 * A map rather than a `when` chain, and keyed rather than ordered, because the previous version was
 * a `when` whose `else` branch returned the **EUR** string while its comment claimed it returned the
 * bare code. With two currencies offered that was invisible; with nineteen it would have rendered
 * sixteen of them as "Euros (EUR)".
 */
private val CURRENCY_NAMES: Map<String, StringResource> = mapOf(
    "GBP" to Res.string.feature_payments_schedule_payment_currency_gbp,
    "EUR" to Res.string.feature_payments_schedule_payment_currency_eur,
    "USD" to Res.string.feature_payments_schedule_payment_currency_usd,
    "AUD" to Res.string.feature_payments_schedule_payment_currency_aud,
    "CAD" to Res.string.feature_payments_schedule_payment_currency_cad,
    "CHF" to Res.string.feature_payments_schedule_payment_currency_chf,
    "CNY" to Res.string.feature_payments_schedule_payment_currency_cny,
    "HKD" to Res.string.feature_payments_schedule_payment_currency_hkd,
    "SGD" to Res.string.feature_payments_schedule_payment_currency_sgd,
    "NZD" to Res.string.feature_payments_schedule_payment_currency_nzd,
    "AED" to Res.string.feature_payments_schedule_payment_currency_aed,
    "CZK" to Res.string.feature_payments_schedule_payment_currency_czk,
    "DKK" to Res.string.feature_payments_schedule_payment_currency_dkk,
    "NOK" to Res.string.feature_payments_schedule_payment_currency_nok,
    "PLN" to Res.string.feature_payments_schedule_payment_currency_pln,
    "SAR" to Res.string.feature_payments_schedule_payment_currency_sar,
    "SEK" to Res.string.feature_payments_schedule_payment_currency_sek,
    "ZAR" to Res.string.feature_payments_schedule_payment_currency_zar,
    "THB" to Res.string.feature_payments_schedule_payment_currency_thb,
)

/** The readable name of a currency, or the code itself when this app has no name for it. */
@Composable
internal fun currencyName(code: String): String =
    CURRENCY_NAMES[code.uppercase()]?.let { stringResource(it) } ?: code

/**
 * Who pays the charges, in OBIE's own words.
 *
 * The one place this mapping lives. It was written twice — once in the picker and once in the review
 * — so the review could repeat a customer's choice back to them in different words than they chose
 * it in.
 *
 * The labels are the official terms with no gloss. Neither OBIE nor HSBC documents any
 * customer-facing wording for these values, so "Recipient pays" and "I pay" were this app asserting
 * a meaning nobody had defined — and "Whatever the scheme decides" described an option the bank
 * refuses outright.
 */
@Composable
internal fun chargeBearerLabel(bearer: ChargeBearer): String = when (bearer) {
    ChargeBearer.BorneByCreditor -> stringResource(Res.string.feature_payments_schedule_payment_charge_bearer_creditor)
    ChargeBearer.BorneByDebtor -> stringResource(Res.string.feature_payments_schedule_payment_charge_bearer_debtor)
    ChargeBearer.Shared -> stringResource(Res.string.feature_payments_schedule_payment_charge_bearer_shared)
    // Never offered — HSBC refuses it — so this is reachable only by a value read back from the wire
    // or from a stored draft. The OBIE term itself is the honest label; there is no customer-facing
    // wording to invent for an option the customer was never shown.
    ChargeBearer.FollowingServiceLevel -> bearer.wireValue
}
