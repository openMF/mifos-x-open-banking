/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.vrpcallback

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.mifosx.openbanking.core.data.vrp.VrpAuthRepository
import org.mifosx.openbanking.core.data.vrp.VrpAuthValidation
import org.mifosx.openbanking.core.data.vrp.VrpConsentRepository
import org.mifosx.openbanking.core.model.vrp.VrpConsent
import org.mifosx.openbanking.core.model.vrp.VrpConsentDraft
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult

/** Answers the callback's validation and exchange with whatever the test set. */
class FakeVrpAuthRepository(
    private var validation: VrpAuthValidation = VrpAuthValidation.NoPending,
    private var exchangeResult: NetworkResult<Unit, NetworkError> = NetworkResult.Success(Unit),
) : VrpAuthRepository {

    /** Every redirect validated, in order. */
    val validatedUrls = mutableListOf<String>()

    /** Every code-and-consent pair exchanged, in order. */
    val exchanges = mutableListOf<Pair<String, String>>()

    var discardCount: Int = 0
        private set

    fun validationReturns(outcome: VrpAuthValidation) {
        validation = outcome
    }

    fun exchangeReturns(result: NetworkResult<Unit, NetworkError>) {
        exchangeResult = result
    }

    override suspend fun beginAuthorisation(consentId: String): NetworkResult<String, NetworkError> =
        NetworkResult.Error(NetworkError.Client.BadRequest("not used"))

    override fun isVrpRedirect(redirectUrl: String): Boolean = true

    override fun validateCallback(redirectUrl: String): VrpAuthValidation {
        validatedUrls += redirectUrl
        return validation
    }

    override suspend fun exchangeAndPersistCredential(
        code: String,
        consentId: String,
    ): NetworkResult<Unit, NetworkError> {
        exchanges += code to consentId
        return exchangeResult
    }

    override fun pendingConsentId(): String? = null

    override fun discardAuthorisation() {
        discardCount++
    }
}

/** Answers the callback's read-back with whatever the test set. */
class FakeVrpConsentRepository(
    private var refreshResult: NetworkResult<VrpConsent, NetworkError> =
        NetworkResult.Error(NetworkError.Client.BadRequest("not set")),
) : VrpConsentRepository {

    /** Every consent a read-back was asked for, in order. */
    val refreshedIds = mutableListOf<String>()

    fun refreshReturns(result: NetworkResult<VrpConsent, NetworkError>) {
        refreshResult = result
    }

    override fun observeActive(): Flow<List<VrpConsent>> = MutableStateFlow(emptyList())

    override fun observeById(consentId: String): Flow<VrpConsent?> = MutableStateFlow(null)

    override suspend fun stageConsent(draft: VrpConsentDraft): NetworkResult<VrpConsent, NetworkError> =
        NetworkResult.Error(NetworkError.Client.BadRequest("not used"))

    override suspend fun refreshStatus(consentId: String): NetworkResult<VrpConsent, NetworkError> {
        refreshedIds += consentId
        return refreshResult
    }

    override suspend fun revoke(consentId: String): NetworkResult<Unit, NetworkError> =
        NetworkResult.Success(Unit)
}
