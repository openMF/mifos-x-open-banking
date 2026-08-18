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

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.mifosx.openbanking.core.network.api.OAuth
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Seconds of remaining life below which a held token is treated as spent. */
private const val EXPIRY_MARGIN_SECONDS = 10

/**
 * Holds each consent's access token in memory for its lifetime.
 *
 * Access tokens are not persisted; after process death the first call redeems a new one. The stored
 * refresh token is read but never rewritten.
 */
class VrpTokenProviderImpl(
    private val oauth: OAuth,
    private val session: VrpAuthSession,
    private val clock: Clock,
) : VrpTokenProvider {

    private data class HeldToken(val accessToken: String, val expiresAt: Instant)

    private val held = mutableMapOf<String, HeldToken>()
    private val mutex = Mutex()

    override suspend fun accessToken(consentId: String): NetworkResult<String, NetworkError> =
        mutex.withLock {
            val current = held[consentId]
            if (current != null && clock.now() < current.expiresAt) {
                NetworkResult.Success(current.accessToken)
            } else {
                redeem(consentId)
            }
        }

    override suspend fun refreshAccessToken(consentId: String): NetworkResult<String, NetworkError> =
        mutex.withLock {
            held.remove(consentId)
            redeem(consentId)
        }

    override fun invalidateAccessToken(consentId: String) {
        held.remove(consentId)
    }

    private suspend fun redeem(consentId: String): NetworkResult<String, NetworkError> {
        val refreshToken = session.refreshToken(consentId)
            ?: return NetworkResult.Error(
                NetworkError.Client.Unauthorized("No stored credential for consent $consentId"),
            )

        return when (val result = oauth.refreshToken(refreshToken)) {
            is NetworkResult.Error -> result
            is NetworkResult.Success -> {
                val accessToken = result.data.accessToken
                if (accessToken.isNullOrBlank()) {
                    NetworkResult.Error(
                        NetworkError.Client.Unauthorized("Refresh returned no access token"),
                    )
                } else {
                    val lifetime = result.data.expiresIn ?: 0
                    held[consentId] = HeldToken(
                        accessToken = accessToken,
                        expiresAt = clock.now() + (lifetime - EXPIRY_MARGIN_SECONDS).seconds,
                    )
                    NetworkResult.Success(accessToken)
                }
            }
        }
    }
}
