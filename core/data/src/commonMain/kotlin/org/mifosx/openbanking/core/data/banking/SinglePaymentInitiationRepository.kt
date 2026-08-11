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

import org.mifosx.openbanking.core.model.banking.payment.PaymentDraft
import org.mifosx.openbanking.core.model.banking.payment.PaymentReceipt
import org.mifosx.openbanking.core.model.banking.payment.StagedConsent
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult

/**
 * The payment write path.
 *
 * Every method is a one-shot returning a [NetworkResult] rather than a `ScreenDataStream`, because
 * none of it may be cached. A cached payment response is somewhere for a stale "this already
 * succeeded" answer to live, and duplicate protection here comes from the idempotency key on the
 * draft, not from a cache.
 */
interface SinglePaymentInitiationRepository {

    /**
     * Stages [draft] with the bank and builds the URL the PSU authorises it at.
     *
     * Records the returned consent id, `state` and `nonce` so the redirect can be validated when it
     * comes back, and stores [draft] alongside them so the returning leg can submit the same
     * instruction it staged. Nothing moves until [submitPayment].
     */
    suspend fun stagePayment(draft: PaymentDraft): NetworkResult<StagedConsent, NetworkError>

    /**
     * Asks the bank whether the debtor account can cover the staged amount.
     *
     * `false` means do not submit. This is the authoritative check — the amount step's local balance
     * comparison is advisory and can pass while this refuses.
     */
    suspend fun confirmFunds(consentId: String): NetworkResult<Boolean, NetworkError>

    /**
     * Executes [draft] against the authorised [consentId].
     *
     * Rebuilds the `Initiation` from the same draft that was staged, which is what keeps it
     * byte-identical and the bank from refusing with `U008`.
     */
    suspend fun submitPayment(draft: PaymentDraft, consentId: String): NetworkResult<PaymentReceipt, NetworkError>

    /**
     * The instruction staged for the authorisation in flight, or null when none is.
     *
     * The leg that returns from the bank is not the one that built the draft, so it reads it back
     * from here rather than holding it. Returning the *staged* draft is the point: the submitted
     * `Initiation` must be byte-identical to the staged one, which a rebuilt equivalent would not be.
     */
    fun stagedDraft(): PaymentDraft?
}
