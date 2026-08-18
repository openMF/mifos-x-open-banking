/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.data.vrp

import kotlinx.coroutines.flow.Flow
import org.mifosx.openbanking.core.model.vrp.FundsAvailability
import org.mifosx.openbanking.core.model.vrp.Money
import org.mifosx.openbanking.core.model.vrp.PeriodUsage
import org.mifosx.openbanking.core.model.vrp.VrpConsent
import org.mifosx.openbanking.core.model.vrp.VrpPayment
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult

/** Pays under an authorised consent and reads those payments back. */
interface VrpPaymentRepository {

    /** Payments made under [consentId], newest first. Read from storage. */
    fun observeForConsent(consentId: String): Flow<List<VrpPayment>>

    /** What each of the consent's caps has consumed, and what is left of it. */
    fun observeUsage(consentId: String): Flow<List<PeriodUsage>>

    /**
     * Asks whether [amount] can be funded under [consentId].
     *
     * Initiates nothing and may be repeated.
     */
    suspend fun checkFunds(
        consentId: String,
        amount: Money,
    ): NetworkResult<FundsAvailability, NetworkError>

    /**
     * Pays [amount] under [consent].
     *
     * The initiation is taken from [consent] rather than rebuilt, so it matches what the customer
     * approved.
     *
     * @param instructionIdentification Identifies the instruction between this app and the bank.
     * @param endToEndIdentification Travels unchanged with the payment.
     * @param idempotencyKey Reused verbatim to retry an attempt whose response never arrived,
     *   which returns the original payment rather than making a second one. A retry must repeat
     *   the two identifications as well, so that the bank sees the same instruction.
     */
    suspend fun pay(
        consent: VrpConsent,
        amount: Money,
        instructionIdentification: String,
        endToEndIdentification: String,
        idempotencyKey: String,
    ): NetworkResult<VrpPayment, NetworkError>

    /**
     * Reads [payment] back from the bank and updates what is stored.
     *
     * A payment submitted successfully reports an interim status; the settled one appears only on
     * a later read. An attempt that never reached the bank has nothing to read and is returned
     * unchanged.
     */
    suspend fun refreshStatus(payment: VrpPayment): NetworkResult<VrpPayment, NetworkError>
}
