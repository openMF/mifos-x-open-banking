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

import org.mifosx.openbanking.core.model.banking.payment.PaymentReceipt
import org.mifosx.openbanking.core.model.banking.payment.ScheduledPaymentDraft
import org.mifosx.openbanking.core.model.banking.payment.StagedConsent
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult

/**
 * Stages and submits payments the bank executes on a future date.
 *
 * Deliberately **not** `ScheduledPaymentsRepository` — that name belongs to the AIS read side, which
 * lists the scheduled payments a bank already holds. This is the write path, and "Initiation" is the
 * OBIE term that separates the two throughout this codebase.
 *
 * Three methods, and the two absences are the interesting part:
 *  - **No `confirmFunds`.** OBIE defines no funds-confirmation endpoint for domestic-scheduled at
 *    all, and HSBC marks the international-scheduled one unsupported for every brand. The journey
 *    goes from authorised straight to creating the payment.
 *  - **No `paymentStatus`.** Reading a payment back belongs to [PaymentStatusRepository], which
 *    answers for every product from the type stored on the history row.
 *
 * Like the immediate write path, nothing here is cached: every method is a one-shot returning a
 * [NetworkResult], because a cached payment response is somewhere for a stale "this already
 * succeeded" to live. Duplicate protection is the idempotency key on the draft.
 */
interface ScheduledPaymentInitiationRepository {

    /**
     * Stages [draft] with the bank and builds the URL the PSU authorises it at.
     *
     * Records the consent type in the payment auth session as it goes, because the redirect that
     * comes back names no product and the session is the only thing that remembers.
     */
    suspend fun stagePayment(draft: ScheduledPaymentDraft): NetworkResult<StagedConsent, NetworkError>

    /**
     * Creates the payment against the authorised [consentId].
     *
     * Rebuilds the `Initiation` from the same draft that was staged, which is what keeps the two
     * bodies byte-identical and the bank from refusing `U008`.
     */
    suspend fun submitPayment(
        draft: ScheduledPaymentDraft,
        consentId: String,
    ): NetworkResult<PaymentReceipt, NetworkError>

    /**
     * The instruction staged for the authorisation in flight, or null when none is.
     *
     * Read from the session rather than held: the screen that built it does not survive the browser
     * hop, and only the draft that was actually staged may be submitted.
     */
    fun stagedDraft(): ScheduledPaymentDraft?
}
