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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.mifosx.openbanking.core.model.banking.payment.ConsentType
import org.mifosx.openbanking.core.model.banking.payment.PaymentDisposition
import org.mifosx.openbanking.core.model.banking.payment.PaymentHistoryRow
import org.mifosx.openbanking.core.model.banking.payment.dispositionFor
import template.core.base.network.NetworkResult

/**
 * A payment feature's history, read back from the bank as it is first shown.
 *
 * Holds which payments have already been read this visit, so one instance belongs to one screen —
 * construct it in a ViewModel, never as a shared singleton.
 */
class PaymentHistoryFeed(
    private val history: PaymentHistoryRepository,
    private val status: PaymentStatusRepository,
) {

    private val readBack = mutableSetOf<String>()

    /**
     * The stored payments of [types], newest first, capped at [limit].
     *
     * Each unsettled payment is read back from the bank once per instance. The read writes to
     * storage, which emits back into this flow, so repeating on every emission would not stop.
     */
    fun rows(
        types: Set<ConsentType>,
        limit: Int,
        scope: CoroutineScope,
    ): Flow<List<PaymentHistoryRow>> =
        history.observeHistory(types, limit).onEach { rows -> readBackUnsettled(rows, scope) }

    private fun readBackUnsettled(rows: List<PaymentHistoryRow>, scope: CoroutineScope) {
        rows.filter { it.status.dispositionFor(it.consentType) == PaymentDisposition.InProgress }
            .filter { readBack.add(it.paymentId) }
            .forEach { row -> scope.launch { readBack(row.paymentId) } }
    }

    /** A read that fails leaves the stored status alone; what is on screen is still the bank's. */
    private suspend fun readBack(paymentId: String) {
        when (val result = status.paymentStatus(paymentId)) {
            is NetworkResult.Success -> history.recordStatus(paymentId, result.data)
            is NetworkResult.Error -> Unit
        }
    }
}
