/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.vrppayment

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.mifosx.openbanking.core.data.vrp.VrpConsentRepository
import org.mifosx.openbanking.core.data.vrp.VrpPaymentRepository
import org.mifosx.openbanking.core.model.vrp.FundsAvailability
import org.mifosx.openbanking.core.model.vrp.Money
import org.mifosx.openbanking.core.model.vrp.PeriodUsage
import org.mifosx.openbanking.core.model.vrp.VrpConsent
import org.mifosx.openbanking.core.model.vrp.VrpConsentDraft
import org.mifosx.openbanking.core.model.vrp.VrpPayment
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult

/** Serves the VRP being paid under. */
class FakeVrpConsentRepository(byId: VrpConsent? = null) : VrpConsentRepository {

    private val consent = MutableStateFlow(byId)

    fun emit(value: VrpConsent?) {
        consent.value = value
    }

    override fun observeActive(): Flow<List<VrpConsent>> = MutableStateFlow(emptyList())

    override fun observeById(consentId: String): Flow<VrpConsent?> = consent

    override suspend fun stageConsent(draft: VrpConsentDraft): NetworkResult<VrpConsent, NetworkError> =
        NetworkResult.Error(NetworkError.Client.BadRequest("not used"))

    override suspend fun refreshStatus(consentId: String): NetworkResult<VrpConsent, NetworkError> =
        NetworkResult.Error(NetworkError.Client.BadRequest("not used"))

    override suspend fun revoke(consentId: String): NetworkResult<Unit, NetworkError> =
        NetworkResult.Success(Unit)
}

/** One attempt to pay, as it was submitted. */
data class PayCall(
    val amount: Money,
    val instructionIdentification: String,
    val endToEndIdentification: String,
    val idempotencyKey: String,
)

/** Records every submission and answers with whatever the test set. */
class FakeVrpPaymentRepository(
    usage: List<PeriodUsage> = emptyList(),
) : VrpPaymentRepository {

    private val periodUsage = MutableStateFlow(usage)

    private var fundsResult: NetworkResult<FundsAvailability, NetworkError> =
        NetworkResult.Error(NetworkError.Client.BadRequest("not set"))

    private var payResult: NetworkResult<VrpPayment, NetworkError> =
        NetworkResult.Error(NetworkError.Client.BadRequest("not set"))

    private var refreshResult: NetworkResult<VrpPayment, NetworkError> =
        NetworkResult.Error(NetworkError.Client.BadRequest("not set"))

    /** Every submission, in order. */
    val payCalls = mutableListOf<PayCall>()

    /** Every payment a status read was asked for, in order. */
    val refreshedPayments = mutableListOf<VrpPayment>()

    /** Every amount a funds check was asked about, in order. */
    val fundsChecks = mutableListOf<Money>()

    fun emitUsage(value: List<PeriodUsage>) {
        periodUsage.value = value
    }

    fun fundsReturns(result: NetworkResult<FundsAvailability, NetworkError>) {
        fundsResult = result
    }

    fun payReturns(result: NetworkResult<VrpPayment, NetworkError>) {
        payResult = result
    }

    fun refreshReturns(result: NetworkResult<VrpPayment, NetworkError>) {
        refreshResult = result
    }

    override fun observeForConsent(consentId: String): Flow<List<VrpPayment>> =
        MutableStateFlow(emptyList())

    override fun observeUsage(consentId: String): Flow<List<PeriodUsage>> = periodUsage

    override suspend fun checkFunds(
        consentId: String,
        amount: Money,
    ): NetworkResult<FundsAvailability, NetworkError> {
        fundsChecks += amount
        return fundsResult
    }

    override suspend fun pay(
        consent: VrpConsent,
        amount: Money,
        instructionIdentification: String,
        endToEndIdentification: String,
        idempotencyKey: String,
    ): NetworkResult<VrpPayment, NetworkError> {
        payCalls += PayCall(amount, instructionIdentification, endToEndIdentification, idempotencyKey)
        return payResult
    }

    override suspend fun refreshStatus(payment: VrpPayment): NetworkResult<VrpPayment, NetworkError> {
        refreshedPayments += payment
        return refreshResult
    }
}
