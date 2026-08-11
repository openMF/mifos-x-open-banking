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
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult

/**
 * Reads a submitted payment back, whatever product created it.
 *
 * It sits apart from the repositories that *make* payments because it belongs to none of them: a
 * payment id arrives from the history row or a nav argument with no memory of which product issued
 * it, and this answers for all of them. Leaving it on the single-payment repository would have made
 * that class answer for scheduled payments too, which its name denies.
 *
 * One method, and no cache. A settlement status is exactly the thing that must never be served stale.
 */
interface PaymentStatusRepository {

    /**
     * Reads a submitted payment's current status from the endpoint belonging to its own consent.
     *
     * The product cannot be told from the id, and the endpoints do not accept each other's — a
     * payment read against the wrong one answers 404 or about a different payment. The type is read
     * back from the history row written at submission.
     *
     * An id with no stored row, or one whose stored type this build does not recognise, is an
     * **error** rather than a guess: guessing would report another product's answer as this one's.
     */
    suspend fun paymentStatus(paymentId: String): NetworkResult<PaymentReceipt, NetworkError>
}
