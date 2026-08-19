/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.vrpconsents

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
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

/** Serves consents from storage and records what was asked of the bank. */
class FakeVrpConsentRepository(
    active: List<VrpConsent> = emptyList(),
    byId: VrpConsent? = null,
) : VrpConsentRepository {

    private val activeConsents = MutableStateFlow(active)
    private val consent = MutableStateFlow(byId)

    /** Set to make the storage read fail rather than emit. */
    var storageFails: Boolean = false

    private var revokeResult: NetworkResult<Unit, NetworkError> = NetworkResult.Success(Unit)

    /** Every consent a status read was asked for, in order. */
    val refreshedIds = mutableListOf<String>()

    /** Every consent a removal was asked for, in order. */
    val revokedIds = mutableListOf<String>()

    fun emitActive(consents: List<VrpConsent>) {
        activeConsents.value = consents
    }

    fun emit(value: VrpConsent?) {
        consent.value = value
    }

    fun revokeReturns(result: NetworkResult<Unit, NetworkError>) {
        revokeResult = result
    }

    override fun observeActive(): Flow<List<VrpConsent>> = when {
        storageFails -> flow { error("storage unavailable") }
        else -> activeConsents
    }

    override fun observeById(consentId: String): Flow<VrpConsent?> = consent

    override suspend fun stageConsent(draft: VrpConsentDraft): NetworkResult<VrpConsent, NetworkError> =
        NetworkResult.Error(NetworkError.Client.BadRequest("not used"))

    override suspend fun refreshStatus(consentId: String): NetworkResult<VrpConsent, NetworkError> {
        refreshedIds += consentId
        return NetworkResult.Error(NetworkError.Client.BadRequest("not used"))
    }

    override suspend fun revoke(consentId: String): NetworkResult<Unit, NetworkError> {
        revokedIds += consentId
        return revokeResult
    }
}

/** Serves the payments made under a consent, and what they have consumed. */
class FakeVrpPaymentRepository(
    made: List<VrpPayment> = emptyList(),
    usage: List<PeriodUsage> = emptyList(),
) : VrpPaymentRepository {

    private val payments = MutableStateFlow(made)
    private val periodUsage = MutableStateFlow(usage)

    fun emitPayments(value: List<VrpPayment>) {
        payments.value = value
    }

    fun emitUsage(value: List<PeriodUsage>) {
        periodUsage.value = value
    }

    override fun observeForConsent(consentId: String): Flow<List<VrpPayment>> = payments

    override fun observeUsage(consentId: String): Flow<List<PeriodUsage>> = periodUsage

    override suspend fun checkFunds(
        consentId: String,
        amount: Money,
    ): NetworkResult<FundsAvailability, NetworkError> =
        NetworkResult.Error(NetworkError.Client.BadRequest("not used"))

    override suspend fun pay(
        consent: VrpConsent,
        amount: Money,
        instructionIdentification: String,
        endToEndIdentification: String,
        idempotencyKey: String,
    ): NetworkResult<VrpPayment, NetworkError> =
        NetworkResult.Error(NetworkError.Client.BadRequest("not used"))

    /** Every payment a status read was asked for, in order. */
    val refreshedPayments = mutableListOf<VrpPayment>()

    override suspend fun refreshStatus(payment: VrpPayment): NetworkResult<VrpPayment, NetworkError> {
        refreshedPayments += payment
        return NetworkResult.Error(NetworkError.Client.BadRequest("not used"))
    }
}
