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
import kotlinx.coroutines.flow.map
import org.mifosx.openbanking.core.data.util.obieErrorCode
import org.mifosx.openbanking.core.database.vrp.dao.VrpConsentDao
import org.mifosx.openbanking.core.model.callback.ConsentStatus
import org.mifosx.openbanking.core.model.vrp.VrpConsent
import org.mifosx.openbanking.core.model.vrp.VrpConsentDraft
import org.mifosx.openbanking.core.network.api.ConsentCreationScope
import org.mifosx.openbanking.core.network.api.OAuth
import org.mifosx.openbanking.core.network.api.Vrp
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult
import kotlin.time.Clock

/** The error code the bank returns when a consent no longer resolves. */
private const val RESOURCE_NOT_FOUND = "U011"

class VrpConsentRepositoryImpl(
    private val vrp: Vrp,
    private val oauth: OAuth,
    private val dao: VrpConsentDao,
    private val session: VrpAuthSession,
    private val tokens: VrpTokenProvider,
) : VrpConsentRepository {

    override fun observeActive(): Flow<List<VrpConsent>> =
        dao.observeActive().map { rows -> rows.map { it.toVrpConsent() } }

    override fun observeById(consentId: String): Flow<VrpConsent?> =
        dao.observeById(consentId).map { it?.toVrpConsent() }

    @Suppress("ReturnCount")
    override suspend fun stageConsent(draft: VrpConsentDraft): NetworkResult<VrpConsent, NetworkError> {
        val token = when (val result = oauth.clientCredentialsToken(ConsentCreationScope.PAYMENTS)) {
            is NetworkResult.Success -> result.data.accessToken
            is NetworkResult.Error -> return result
        }

        return when (
            val result = vrp.createConsent(
                paymentsScopeToken = token,
                request = draft.toCreateConsent(),
                idempotencyKey = draft.idempotencyKey,
            )
        ) {
            is NetworkResult.Error -> result

            is NetworkResult.Success -> {
                val consent = result.data.toVrpConsent(Clock.System.now())
                    ?: return NetworkResult.Error(
                        NetworkError.Serialization(IllegalStateException("Consent response was unusable")),
                    )
                NetworkResult.Success(consent)
            }
        }
    }

    @Suppress("ReturnCount")
    override suspend fun refreshStatus(consentId: String): NetworkResult<VrpConsent, NetworkError> {
        val token = when (val result = oauth.clientCredentialsToken(ConsentCreationScope.PAYMENTS)) {
            is NetworkResult.Success -> result.data.accessToken
            is NetworkResult.Error -> return result
        }

        return when (val result = vrp.getConsent(token, consentId)) {
            is NetworkResult.Error -> {
                if (result.error.obieErrorCode() == RESOURCE_NOT_FOUND) {
                    forget(consentId)
                }
                result
            }

            is NetworkResult.Success -> {
                val consent = result.data.toVrpConsent(Clock.System.now())
                    ?: return NetworkResult.Error(
                        NetworkError.Serialization(IllegalStateException("Consent response was unusable")),
                    )
                store(consent)
                NetworkResult.Success(consent)
            }
        }
    }

    override suspend fun revoke(consentId: String): NetworkResult<Unit, NetworkError> {
        val token = when (val result = oauth.clientCredentialsToken(ConsentCreationScope.PAYMENTS)) {
            is NetworkResult.Success -> result.data.accessToken
            is NetworkResult.Error -> {
                forget(consentId)
                return result
            }
        }

        val result = vrp.deleteConsent(token, consentId)
        forget(consentId)
        return result
    }

    /**
     * Writes [consent] once it is authorised, and keeps an already-stored one current afterwards.
     *
     * A consent the customer never approved is not stored: it can never be paid under, and the
     * identifier needed to resume it is held in the session for the round trip.
     */
    private suspend fun store(consent: VrpConsent) {
        val alreadyStored = dao.findById(consent.consentId) != null
        if (alreadyStored || consent.status == ConsentStatus.Authorised) {
            dao.upsert(consent.toEntity())
        }
    }

    /** Drops the credential and marks the consent revoked. */
    private suspend fun forget(consentId: String) {
        session.removeRefreshToken(consentId)
        tokens.invalidateAccessToken(consentId)
        dao.markRevoked(consentId, revokedAt = Clock.System.now().toString())
    }
}
