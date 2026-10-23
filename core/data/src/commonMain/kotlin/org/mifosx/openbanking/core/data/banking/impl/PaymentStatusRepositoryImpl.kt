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
import org.mifosx.openbanking.core.network.api.Pisp
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult

/**
 * Reads a payment back through the Worker, which uses its client-credentials token — so a status read
 * keeps working after the PSU token has expired, as it will when the customer opens the status screen
 * days later. Only payments this session submitted can be read (`403 payment_not_in_session`).
 */
internal class PaymentStatusRepositoryImpl(
    private val pisp: Pisp,
    private val paymentHistoryRepository: PaymentHistoryRepository,
) : PaymentStatusRepository {

    override suspend fun paymentStatus(paymentId: String): NetworkResult<PaymentReceipt, NetworkError> {
        val type = paymentHistoryRepository.consentTypeOf(paymentId)
            ?: return NetworkResult.Error(
                NetworkError.Client.BadRequest("Unknown consent type — cannot choose an endpoint"),
            )
        return pisp.readReceipt(paymentId, type)
    }
}
