/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.data.banking.impl

import org.mifosx.openbanking.core.data.banking.mapper.toIntlPaymentReceipt
import org.mifosx.openbanking.core.data.banking.mapper.toIntlScheduledPaymentReceipt
import org.mifosx.openbanking.core.data.banking.mapper.toPaymentReceipt
import org.mifosx.openbanking.core.data.banking.mapper.toScheduledPaymentReceipt
import org.mifosx.openbanking.core.model.banking.payment.ConsentType
import org.mifosx.openbanking.core.model.banking.payment.PaymentReceipt
import org.mifosx.openbanking.core.network.api.Pisp
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult

/**
 * Reads a submitted payment back from the endpoint belonging to its consent.
 *
 * The id alone cannot say which endpoint issued it — every product returns a bare numeric id — so the
 * caller must supply the [type] it recorded at staging. Asking the wrong endpoint does not fail
 * loudly; it answers about a different payment or reports `U011`, which is why this dispatch is
 * exhaustive over [ConsentType] with no `else` branch.
 *
 * It lives here, as one function, because two callers needed exactly this: the status read on
 * [SinglePaymentInitiationRepositoryImpl] and the refresh loop in [PaymentHistoryRepositoryImpl].
 * They had drifted to two copies of the same `when`, which is one place for each new product to be
 * added to and one place for it to be forgotten.
 */
internal suspend fun Pisp.readReceipt(
    token: String,
    paymentId: String,
    type: ConsentType,
): NetworkResult<PaymentReceipt, NetworkError> = when (type) {
    ConsentType.DomesticSinglePayment ->
        when (val result = getDomesticPayment(token, paymentId)) {
            is NetworkResult.Success -> NetworkResult.Success(result.data.toPaymentReceipt())
            is NetworkResult.Error -> result
        }

    ConsentType.InternationalSinglePayment ->
        when (val result = getInternationalPayment(token, paymentId)) {
            is NetworkResult.Success -> NetworkResult.Success(result.data.toIntlPaymentReceipt())
            is NetworkResult.Error -> result
        }

    ConsentType.DomesticScheduledPayment ->
        when (val result = getDomesticScheduledPayment(token, paymentId)) {
            is NetworkResult.Success -> NetworkResult.Success(result.data.toScheduledPaymentReceipt())
            is NetworkResult.Error -> result
        }

    ConsentType.InternationalScheduledPayment ->
        when (val result = getInternationalScheduledPayment(token, paymentId)) {
            is NetworkResult.Success -> NetworkResult.Success(result.data.toIntlScheduledPaymentReceipt())
            is NetworkResult.Error -> result
        }
}
