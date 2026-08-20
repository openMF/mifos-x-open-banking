/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.data.banking

import kotlinx.coroutines.flow.Flow
import org.mifosx.openbanking.core.model.banking.payment.ConsentType
import org.mifosx.openbanking.core.model.banking.payment.PaymentDraft
import org.mifosx.openbanking.core.model.banking.payment.PaymentHistoryRow
import org.mifosx.openbanking.core.model.banking.payment.PaymentReceipt
import org.mifosx.openbanking.core.model.banking.payment.PaymentStageTimestamps
import org.mifosx.openbanking.core.model.banking.payment.ScheduledPaymentDraft
import org.mifosx.openbanking.core.model.banking.payment.StandingOrderDraft

/**
 * Local store of payments this app has made, and of those that failed before reaching the bank.
 *
 * Backs each payment feature's history list, and answers the rail and stage times the status screen
 * needs.
 */
interface PaymentHistoryRepository {

    /** Persists a payment that the bank has accepted (any OBIE status, not just success). */
    suspend fun saveSubmitted(receipt: PaymentReceipt, draft: PaymentDraft)

    /** The scheduled equivalent, which also records the date the payment is due. */
    suspend fun saveSubmitted(receipt: PaymentReceipt, draft: ScheduledPaymentDraft)

    /**
     * The standing-order equivalent, which also records how often it repeats and when it ends.
     *
     * This row matters more than its siblings: a mandate cannot be found again through the AIS read
     * side, which returns no `StandingOrderId` to correlate on, so the hub is the only record the app
     * keeps that the customer ever set one up.
     */
    suspend fun saveSubmitted(receipt: PaymentReceipt, draft: StandingOrderDraft)

    /** Persists a payment that failed before reaching submission. */
    suspend fun saveFailed(draft: PaymentDraft, errorKind: String, errorDescription: String)

    /**
     * The scheduled equivalent.
     *
     * A separate overload rather than a shared supertype because the return leg must call the one
     * matching the draft it found — calling neither would leave a failed scheduled payment with no
     * history row at all, silently.
     */
    suspend fun saveFailed(draft: ScheduledPaymentDraft, errorKind: String, errorDescription: String)

    /** The standing-order equivalent, for the same reason. */
    suspend fun saveFailed(draft: StandingOrderDraft, errorKind: String, errorDescription: String)

    /**
     * Which rail a submitted payment was sent on, so its status is read from the right endpoint.
     *
     * The two rails have separate status endpoints, and an id is only valid against its own — an
     * international payment looked up as domestic answers 404. The rail is not derivable from the
     * id, so it is read back from the row written at submission.
     *
     * Returns null when nothing is stored for [paymentId], which the caller must decide about
     * rather than have guessed for it.
     */
    suspend fun consentTypeOf(paymentId: String): ConsentType?

    /**
     * The two stage times this app recorded for a submitted payment, for the detail timeline.
     *
     * The same single-row lookup [consentTypeOf] performs, and for the same reason: the bank returns one
     * `CreationDateTime` and no stage history, so approval and submission can only come from the row
     * written when they were observed.
     *
     * Null when no row exists for [paymentId] — a payment made on another device, or one made before
     * the last logout.
     */
    suspend fun stageTimestampsOf(paymentId: String): PaymentStageTimestamps?

    /**
     * The payments made on any of [types] that reached the bank, newest first, capped at [limit].
     *
     * Emits again whenever a row is written or its status refreshed.
     */
    fun observeHistory(types: Set<ConsentType>, limit: Int): Flow<List<PaymentHistoryRow>>

    /** Writes back the status [receipt] reported for [paymentId]. */
    suspend fun recordStatus(paymentId: String, receipt: PaymentReceipt)
}
