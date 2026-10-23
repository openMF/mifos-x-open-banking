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
import kotlin.time.Instant
import org.mifosx.openbanking.core.data.callback.impl.authorizeState
import org.mifosx.openbanking.core.network.api.Vrp
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult
import kotlin.time.Clock

/** The error code the bank returns when a consent no longer resolves. */
private const val RESOURCE_NOT_FOUND = "U011"

class VrpConsentRepositoryImpl(
    private val vrp: Vrp,
    private val dao: VrpConsentDao,
    private val session: VrpAuthSession,
) : VrpConsentRepository {

    override fun observeActive(): Flow<List<VrpConsent>> =
        dao.observeActive().map { rows -> rows.map { it.toVrpConsent() } }

    override fun observeById(consentId: String): Flow<VrpConsent?> =
        dao.observeById(consentId).map { it?.toVrpConsent() }

    /**
     * Stages the consent through the Worker and records the authorisation in flight. The Worker
     * returns only the consent id and its signed authorize URL, so the consent is described from the
     * draft the customer configured — exactly what was sent.
     */
    @Suppress("ReturnCount")
    override suspend fun stageConsent(draft: VrpConsentDraft): NetworkResult<VrpConsent, NetworkError> {
        val start = when (val result = vrp.createConsent(draft.toCreateConsent(), draft.idempotencyKey)) {
            is NetworkResult.Success -> result.data
            is NetworkResult.Error -> return result
        }
        val state = start.authorizeState()
            ?: return NetworkResult.Error(NetworkError.Client.BadRequest("authorizeUrl carries no state"))
        session.saveAuthorisationInFlight(start.consentId, state, start.flowId, start.authorizeUrl)
        return NetworkResult.Success(draft.toStagedConsent(start.consentId, Clock.System.now()))
    }

    @Suppress("ReturnCount")
    override suspend fun refreshStatus(consentId: String): NetworkResult<VrpConsent, NetworkError> {
        return when (val result = vrp.getConsent(consentId)) {
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
        val result = vrp.deleteConsent(consentId)
        forget(consentId)
        return result
    }

    /**
     * Writes [consent] once it is authorised, and keeps an already-stored one current afterwards.
     */
    private suspend fun store(consent: VrpConsent) {
        val alreadyStored = dao.findById(consent.consentId) != null
        if (alreadyStored || consent.status == ConsentStatus.Authorised) {
            dao.upsert(consent.toEntity())
        }
    }

    /** Marks the consent revoked (the Worker holds, and drops, its credential). */
    private suspend fun forget(consentId: String) {
        dao.markRevoked(consentId, revokedAt = Clock.System.now().toString())
    }
}

/** The consent as staged — awaiting approval — described from the draft that was sent. */
private fun VrpConsentDraft.toStagedConsent(consentId: String, now: Instant): VrpConsent = VrpConsent(
    consentId = consentId,
    status = ConsentStatus.AwaitingAuthorisation,
    createdAt = now,
    controlParameters = controlParameters,
    payee = payee,
    payer = payer,
    validity = validity,
    reference = reference,
    syncedAt = now,
)
