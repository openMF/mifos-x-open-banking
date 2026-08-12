/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.paymentsschedulepayment.ui

import org.mifosx.openbanking.core.model.banking.payment.ChargeBearer

/**
 * The currencies the amount's control offers: HSBC's own routing list, nineteen of them.
 *
 * Not a Global Money allowlist — the two-value `USD`/`EUR` list this replaced was one, and it was
 * wrong twice over. `CurrencyOfTransfer: GBP` stages `201`/`AWAU` (INT-04), and no allowlist is
 * applied at the consent stage at all: even `JPY`, which is not on this list, stages `201` (INT-14).
 * So the list is a statement about what HSBC documents itself as routing, not about what the consent
 * endpoint will accept.
 *
 * GBP leads because it is the default on both rails and the only currency proven end to end.
 * Three of the nineteen — GBP, USD, EUR — are exercised against this sandbox; the other sixteen come
 * from the implementation guide and are unverified here.
 */
internal val OFFERED_CURRENCIES: List<String> = listOf(
    "GBP", "EUR", "USD", "AUD", "CAD", "CHF", "CNY", "HKD", "SGD", "NZD",
    "AED", "CZK", "DKK", "NOK", "PLN", "SAR", "SEK", "ZAR", "THB",
)

/**
 * The charge bearers the PSU may choose — three of the four OBIE defines.
 *
 * [ChargeBearer.FollowingServiceLevel] is deliberately absent. HSBC's implementation guide restricts
 * the field to *"BorneByCreditor, BorneByDebtor, Shared"*, and sending the fourth is refused with
 * `400 UK.OBIE.Field.Invalid`. It stays in the enum because the enum is the OBIE codeset and
 * `ChargeBearer.fromWire` must still parse a value read back from the bank or from storage; what it
 * must not do is offer it.
 *
 * Only [ChargeBearer.BorneByCreditor] has settled a payment against this sandbox. `Shared` and
 * `BorneByDebtor` both staged `201` (INT-11, INT-12) but neither was carried through to submission.
 */
internal val OFFERED_CHARGE_BEARERS: List<ChargeBearer> = listOf(
    ChargeBearer.BorneByCreditor,
    ChargeBearer.BorneByDebtor,
    ChargeBearer.Shared,
)
