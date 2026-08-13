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
import org.mifosx.openbanking.core.model.banking.payment.StagedConsent
import org.mifosx.openbanking.core.model.banking.payment.StandingOrderDraft
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult

/**
 * Stages and submits recurring mandates.
 *
 * Deliberately **not** `StandingOrdersRepository` — that name belongs to the AIS read side, which
 * lists the standing orders a bank already holds. This is the write path, and "Initiation" is the
 * OBIE term that separates the two throughout this codebase. The two are otherwise unconnected: a
 * mandate created here cannot be found again through that list, which returns no `StandingOrderId`
 * to correlate on.
 *
 * Three methods, and the absences are the same as the scheduled rails', one of them more absolute:
 *  - **No `confirmFunds`.** OBIE defines no funds-confirmation sub-resource for either standing-order
 *    consent, so unlike the scheduled rails there is not even an unsupported endpoint to decline.
 *  - **No `paymentStatus`.** Reading a mandate back belongs to [PaymentStatusRepository], which
 *    answers for every product from the type stored on the history row.
 *  - **No cancel or amend.** `DELETE` on a standing-order consent answers `405` and leaves it intact,
 *    and no amendment endpoint exists. Offering either here would be offering a call that cannot
 *    succeed.
 *
 * Nothing is cached: every method is a one-shot returning a [NetworkResult], because a cached
 * response is somewhere for a stale "this already succeeded" to live. Duplicate protection is the
 * idempotency key on the draft.
 */
interface StandingOrderInitiationRepository {

    /**
     * Stages [draft] with the bank and builds the URL the customer authorises it at.
     *
     * Records the consent type in the payment auth session as it goes, because the redirect that
     * comes back names no product and the session is the only thing that remembers.
     */
    suspend fun stageStandingOrder(draft: StandingOrderDraft): NetworkResult<StagedConsent, NetworkError>

    /**
     * Creates the standing order against the authorised [consentId].
     *
     * Rebuilds the `Initiation` from the same draft that was staged, which is what keeps the two
     * bodies byte-identical and the bank from refusing `U008`.
     */
    suspend fun submitStandingOrder(
        draft: StandingOrderDraft,
        consentId: String,
    ): NetworkResult<PaymentReceipt, NetworkError>

    /**
     * The mandate staged for the authorisation in flight, or null when none is.
     *
     * Read from the session rather than held: the screen that built it does not survive the browser
     * hop, and only the draft that was actually staged may be submitted.
     */
    fun stagedDraft(): StandingOrderDraft?
}
