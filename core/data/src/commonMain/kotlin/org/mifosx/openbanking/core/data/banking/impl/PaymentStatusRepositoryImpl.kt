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

import org.mifosx.openbanking.core.data.banking.PaymentHistoryRepository
import org.mifosx.openbanking.core.data.banking.PaymentStatusRepository
import org.mifosx.openbanking.core.model.banking.payment.PaymentReceipt
import org.mifosx.openbanking.core.network.api.ConsentCreationScope
import org.mifosx.openbanking.core.network.api.OAuth
import org.mifosx.openbanking.core.network.api.Pisp
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult

/**
 * Reads a payment back on a client-credentials token.
 *
 * Client credentials rather than the PSU token on purpose: a status read must keep working after the
 * PSU token has expired, which it will, since a customer may open the status screen days later.
 */
internal class PaymentStatusRepositoryImpl(
    private val pisp: Pisp,
    private val oauth: OAuth,
    private val paymentHistoryRepository: PaymentHistoryRepository,
) : PaymentStatusRepository {

    @Suppress("ReturnCount")
    override suspend fun paymentStatus(paymentId: String): NetworkResult<PaymentReceipt, NetworkError> {
        val token = when (val result = oauth.clientCredentialsToken(ConsentCreationScope.PAYMENTS)) {
            is NetworkResult.Success -> result.data.accessToken
            is NetworkResult.Error -> return result
        }
        val type = paymentHistoryRepository.consentTypeOf(paymentId)
            ?: return NetworkResult.Error(
                NetworkError.Client.BadRequest("Unknown consent type — cannot choose an endpoint"),
            )
        return pisp.readReceipt(token, paymentId, type)
    }
}
