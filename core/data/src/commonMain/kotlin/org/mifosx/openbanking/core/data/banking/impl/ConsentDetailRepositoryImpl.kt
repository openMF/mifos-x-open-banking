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

import kotlinx.coroutines.CoroutineScope
import org.mifosx.openbanking.core.data.banking.ConsentDetailRepository
import org.mifosx.openbanking.core.data.banking.ConsentRevokeRepository
import org.mifosx.openbanking.core.data.infra.NetworkMonitor
import org.mifosx.openbanking.core.model.banking.ConsentSummary
import org.mifosx.openbanking.core.network.api.Aisp
import org.mobilenativefoundation.store.store5.Store
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult
import template.core.base.store.infra.FetchedAtRepository
import template.core.base.store.screen.ScreenDataStream
import template.core.base.store.screen.asScreenStream

/**
 * Opens a stream over the consent-detail store, keyed and cached per consent.
 */
internal class ConsentDetailRepositoryImpl(
    private val store: Store<String, ConsentSummary>,
    private val networkMonitor: NetworkMonitor,
    private val fetchedAtRepository: FetchedAtRepository,
) : ConsentDetailRepository {

    override fun consentStream(
        consentId: String,
        scope: CoroutineScope,
    ): ScreenDataStream<ConsentSummary> =
        store.asScreenStream(
            key = consentId,
            networkMonitor = networkMonitor,
            fetchedAtRepository = fetchedAtRepository,
            cacheKey = "$CACHE_KEY:$consentId",
            scope = scope,
        )

    private companion object {
        const val CACHE_KEY = "consent:detail"
    }
}

/**
 * Revokes the sign-in consent at the bank through the Worker (`DELETE /v1/consents/ais/{ConsentId}`).
 *
 * Talks to the network directly rather than through a store: this is a mutation, and routing it
 * through a cache would only create somewhere for a stale "still authorised" answer to live.
 */
internal class ConsentRevokeRepositoryImpl(
    private val aisp: Aisp,
) : ConsentRevokeRepository {

    override suspend fun revokeConsent(consentId: String): NetworkResult<Unit, NetworkError> =
        when (val result = aisp.deleteConsent(consentId)) {
            is NetworkResult.Success -> result
            is NetworkResult.Error -> result.orSuccessIfAlreadyGone()
        }

    /**
     * Treats a `404` as a success.
     *
     * OBIE returns it when the consent no longer exists at the bank — revoked from the bank's own
     * app, or a retry of a call that already landed. Either way the PSU's intent is satisfied, so
     * reporting a failure here would leave the local token in place and strand the user on an error
     * screen for something that already happened.
     */
    private fun NetworkResult.Error<NetworkError>.orSuccessIfAlreadyGone(): NetworkResult<Unit, NetworkError> =
        if (error is NetworkError.Client.NotFound) NetworkResult.Success(Unit) else this
}
