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

import kotlinx.serialization.Serializable
import org.mifosx.openbanking.core.model.banking.BankAccount

/**
 * A payment instruction the bank is to execute on a future date.
 *
 * A separate type from [PaymentDraft] rather than a nullable date on it, and the reason is not
 * tidiness. Both drafts are persisted whole into the payment auth session, whose `Json` is built with
 * `ignoreUnknownKeys = true`. A single type carrying an optional date would therefore decode
 * *successfully* as an immediate payment when read back by a code path expecting one — and the return
 * leg would submit a scheduled instruction to `domestic-payments`. Two types make that read return
 * null instead, which is the honest "cannot say" the session is designed to give.
 *
 * Everything [PaymentDraft] says about byte-identity applies here unchanged: the instruction is
 * staged once and submitted against the consent, and the submitted `Initiation` must match the
 * staged one exactly. [requestedExecutionDate] is stored, never recomputed — a draft that re-derived
 * "tomorrow" between staging and submission would send two different dates and be refused `U008`.
 *
 * @property requestedExecutionDate The date the bank is to make the payment, as a plain ISO date —
 *   `2026-08-14`, no time and no offset. The mapper appends a fixed `T00:00:00+00:00` at the wire
 *   boundary, so the consent and the submission are identical by construction and no timezone can
 *   enter. The bank normalises whatever time is sent to midnight anyway; sending no offset means the
 *   question of whether a non-UTC one shifts the calendar day never arises. Non-null because this
 *   product has no immediate path: a scheduled payment without a date is not a thing.
 * @property debtorAccount The account to pay from, or null to let the PSU choose it at the bank.
 *   Both scheduled rails accept a consent without one, and a card or Global Money wallet is refused
 *   as a named debtor but settles when the bank picks it — so null is the only route to paying from
 *   those.
 * @property reference Domestic only. International refuses `RemittanceInformation` with `U005` on
 *   this rail exactly as it does on the immediate one.
 * @property chargeBearer International only, and mandatory there — omitting it is `400 U004`.
 *   Only `BorneByCreditor` has ever been sent on a scheduled consent.
 * @property currencyOfTransfer Non-null on the international rail and the field the mapper reads to
 *   tell the two rails apart.
 */
@Serializable
data class ScheduledPaymentDraft(
    val debtorAccount: BankAccount?,
    val creditor: CreditorSelection,
    val amountMinorUnits: Long,
    val currency: String,
    val reference: String?,
    val instructionIdentification: String,
    val endToEndIdentification: String,
    val consentIdempotencyKey: String,
    val paymentIdempotencyKey: String,
    val requestedExecutionDate: String,
    val currencyOfTransfer: String? = null,
    val chargeBearer: ChargeBearer? = null,
)
