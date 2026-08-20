/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.paymentsschedulepayment.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.mifosx.openbanking.core.data.banking.PaymentHistoryFeed
import org.mifosx.openbanking.core.data.banking.PaymentHistoryRepository
import org.mifosx.openbanking.core.data.banking.PaymentStatusRepository
import org.mifosx.openbanking.core.model.banking.payment.ConsentType
import org.mifosx.openbanking.core.ui.payment.PaymentHistoryEntry
import org.mifosx.openbanking.core.ui.payment.toHistoryEntry

/** Every payment scheduled from this feature, on either rail. */
private val SCHEDULED_PAYMENT_TYPES = setOf(
    ConsentType.DomesticScheduledPayment,
    ConsentType.InternationalScheduledPayment,
)

/**
 * Well past what anyone scrolls, and the table is emptied at logout, so this is a guard rather than
 * a page size.
 */
private const val HISTORY_CAP = 200

class SchedulePaymentHistoryViewModel(
    paymentHistoryRepository: PaymentHistoryRepository,
    paymentStatusRepository: PaymentStatusRepository,
) : ViewModel() {

    private val feed = PaymentHistoryFeed(paymentHistoryRepository, paymentStatusRepository)

    private val _payments = MutableStateFlow<List<PaymentHistoryEntry>>(emptyList())
    val payments: StateFlow<List<PaymentHistoryEntry>> = _payments.asStateFlow()

    init {
        feed.rows(SCHEDULED_PAYMENT_TYPES, HISTORY_CAP, viewModelScope)
            .onEach { rows -> _payments.value = rows.map { it.toHistoryEntry() } }
            .launchIn(viewModelScope)
    }
}
