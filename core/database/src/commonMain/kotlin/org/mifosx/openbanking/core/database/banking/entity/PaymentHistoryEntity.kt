/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.database.banking.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * Local snapshot of a submitted or failed payment — at most 5 rows, ordered by recency.
 *
 * Submitted payments carry the bank's [paymentId] and [status]; pre-submission failures carry an
 * [errorKind] and [errorDescription] instead. Only in-flight submitted payments are refreshed via
 * the API; everything else is terminal.
 *
 * This table is the only record of a failed payment: one that never reached the bank exists nowhere
 * else, which is why the stage timestamps below are stored rather than derived. OBIE returns a
 * single `CreationDateTime` and no per-stage history, so a timeline can only be built from what this
 * app observed as it happened.
 *
 * @property paymentId The bank's id, or null when the payment never reached the bank. Carries both
 *   rails; it was called `domesticPaymentId` until v5, which was never true of an international row.
 * @property errorKind The failure reason label (e.g. "InsufficientFunds"), or null on success.
 * @property errorDescription Human-readable failure message.
 * @property status The OBIE status code (ACSP, ACSC, RJCT…), or null for pre-submission failures.
 * @property approvedAt When the PSU's authorisation came back and the consent read `AUTH`.
 * @property submittedAt When the payment POST succeeded.
 * @property chargeBearer International only; null on a domestic row.
 * @property currencyOfTransfer International only; the currency the recipient receives.
 * @property syncedAt When the status was last refreshed from the API; null for failures.
 */
@Entity(tableName = "payment_history")
data class PaymentHistoryEntity(
    @PrimaryKey val id: String,
    val paymentId: String?,
    val errorKind: String?,
    val errorDescription: String?,
    val status: String?,
    val debtorAccountId: String,
    val debtorName: String,
    val debtorIdentification: String,
    val creditorName: String,
    val creditorIdentification: String,
    val amountMinorUnits: Long,
    val currency: String,
    val reference: String?,
    val creationDateTime: String,
    val approvedAt: String? = null,
    val submittedAt: String? = null,
    val settlementDateTime: String?,
    val chargeBearer: String? = null,
    val currencyOfTransfer: String? = null,
    /**
     * The date a scheduled payment is due, or null on an immediate one.
     *
     * Nullable rather than defaulted to the creation date: an immediate payment has no such date,
     * and writing one would make the hub claim a payment is due later than it was made.
     */
    val requestedExecutionDateTime: String? = null,
    /**
     * How often a standing order repeats, as the OBIE code — `MNTH`, `WEEK` — or null on any product
     * that runs once.
     *
     * Stored because it is the difference between a mandate and a one-off, and the hub row is the
     * only record the app keeps: a standing order cannot be found again through the AIS read side,
     * which returns no `StandingOrderId` to correlate on. Without this column the row would show an
     * amount and a payee and be indistinguishable from a scheduled payment.
     */
    val frequency: String? = null,
    /**
     * When a standing order stops, or null — which on this product means two different things and
     * both are correct: the row is not a standing order, or it is one that runs until the customer
     * stops it. [frequency] is what tells them apart.
     */
    val finalPaymentDateTime: String? = null,
    val paymentType: String,
    val syncedAt: String?,
)
