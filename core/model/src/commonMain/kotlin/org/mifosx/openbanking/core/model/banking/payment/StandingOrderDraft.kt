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
 * A recurring mandate the bank executes on a schedule, rather than one instruction it executes once.
 *
 * A **third** draft type beside [PaymentDraft] and [ScheduledPaymentDraft], for the reason the
 * scheduled one gives: all three are persisted whole into the payment auth session, whose `Json` is
 * built with `ignoreUnknownKeys = true`. One type carrying optional schedule fields would therefore
 * decode *successfully* as an immediate payment when read back by a code path expecting one, and the
 * return leg would submit a mandate to `domestic-payments`. Three types make that read return null
 * instead, which is the honest "cannot say" the session is designed to give.
 *
 * Everything the other two say about byte-identity applies unchanged: the mandate is staged once and
 * submitted against the consent, and the submitted `Initiation` must match the staged one exactly.
 * Every field here is stored, never recomputed.
 *
 * Two fields the other drafts carry are **absent**, and their absence is the shape of the product:
 * `instructionIdentification` and `endToEndIdentification` identify a single payment, and a mandate
 * is not one. `LocalInstrument` is absent for the same reason the scheduled rails omit it — a mandate
 * does not pick a rail, the bank picks one per instalment.
 *
 * @property frequency How often it repeats. A closed enum, because `MONT` is refused and is one
 *   character from the code that means monthly.
 * @property firstPaymentDate When the first payment is due, as a plain ISO date — `2026-08-20`, no
 *   time and no offset. The mapper appends a fixed `T00:00:00+00:00` at the wire boundary, so the
 *   consent and the submission are identical by construction and no timezone can enter. Non-null:
 *   the API treats it as optional, but a mandate whose start the customer cannot see is not one this
 *   app will create.
 * @property finalPaymentDate When it stops, or null to run until the customer stops it. Both rails
 *   accept a consent without one. This is the app's only termination control — `CountPerPeriod` is
 *   never sent.
 * @property firstPaymentAmountMinorUnits The amount of the first payment. On the domestic rail this
 *   is `FirstPaymentAmount`; on the international rail it is the single `InstructedAmount`.
 * @property recurringPaymentAmountMinorUnits An ongoing amount differing from the first, or null.
 *   **Domestic only** — the international initiation defines no such member, and the form disables
 *   the field rather than hiding it.
 * @property finalPaymentAmountMinorUnits A closing amount differing from the rest, or null. Domestic
 *   only, as above.
 * @property reference Domestic only. International refuses `RemittanceInformation` with `U005`.
 *   It does **not** go in `MandateRelatedInformation.Reference` — that is not a v4.0 field at all,
 *   and sending it is refused.
 * @property chargeBearer International only, and mandatory there — omitting it is `400 U004`.
 * @property currencyOfTransfer Non-null on the international rail and the field the mapper reads to
 *   tell the two rails apart, matching both sibling drafts.
 */
@Serializable
data class StandingOrderDraft(
    val debtorAccount: BankAccount?,
    val creditor: CreditorSelection,
    val frequency: StandingOrderFrequency,
    val firstPaymentDate: String,
    val finalPaymentDate: String? = null,
    val firstPaymentAmountMinorUnits: Long,
    val recurringPaymentAmountMinorUnits: Long? = null,
    val finalPaymentAmountMinorUnits: Long? = null,
    val currency: String,
    val reference: String? = null,
    val consentIdempotencyKey: String,
    val paymentIdempotencyKey: String,
    val currencyOfTransfer: String? = null,
    val chargeBearer: ChargeBearer? = null,
)
