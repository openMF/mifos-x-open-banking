/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.model.banking.payment

/**
 * One payment that reached the bank, as a history list shows it.
 *
 * @property paymentId The bank's id for the payment, e.g. `19916`.
 * @property consentType The product and rail the payment was made on.
 * @property status The last status the app knows of.
 * @property amountMinorUnits The amount in minor units, e.g. `2599` for £25.99.
 * @property currency ISO 4217 code of [amountMinorUnits], e.g. `GBP`.
 * @property creditorName Who was paid.
 * @property submittedAt ISO-8601 instant the payment POST succeeded.
 * @property reference The customer's reference, or null when they gave none.
 * @property requestedExecutionDateTime ISO-8601 date a scheduled payment is due, or null.
 * @property frequency How often a standing order repeats, as the OBIE code — `MNTH`, `WEEK` — or
 *   null on a product that runs once.
 * @property finalPaymentDateTime ISO-8601 date a standing order stops, or null.
 */
data class PaymentHistoryRow(
    val paymentId: String,
    val consentType: ConsentType,
    val status: PaymentStatus,
    val amountMinorUnits: Long,
    val currency: String,
    val creditorName: String,
    val submittedAt: String,
    val reference: String? = null,
    val requestedExecutionDateTime: String? = null,
    val frequency: String? = null,
    val finalPaymentDateTime: String? = null,
)
