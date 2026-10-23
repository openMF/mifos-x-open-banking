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

import org.mifosx.openbanking.core.data.callback.impl.CallbackUrlParams
import org.mifosx.openbanking.core.data.callback.impl.parseCallbackUrl
import org.mifosx.openbanking.core.network.api.CompleteAuthRequest
import org.mifosx.openbanking.core.network.api.Session
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult

private const val ERROR_ACCESS_DENIED = "access_denied"

internal class VrpAuthRepositoryImpl(
    private val gateway: Session,
    private val session: VrpAuthSession,
) : VrpAuthRepository {

    /** The redirect [validateCallback] last accepted; in memory so the `id_token` is never stored. */
    private var validated: CallbackUrlParams? = null

    override suspend fun beginAuthorisation(consentId: String): NetworkResult<String, NetworkError> {
        val url = session.pendingAuthorizeUrl()?.takeIf { session.pendingConsentId() == consentId }
            ?: return NetworkResult.Error(NetworkError.Client.BadRequest("Consent $consentId was not staged"))
        return NetworkResult.Success(url)
    }

    override fun isVrpRedirect(redirectUrl: String): Boolean {
        val pending = session.pendingConsentId()
        if (pending.isNullOrBlank()) return false
        return session.matchesPendingState(parseCallbackUrl(redirectUrl).state)
    }

    /**
     * Order matters:
     *  - whether anything is in flight is asked first, and answered as [VrpAuthValidation.NoPending].
     *  - `state` is checked next. Present and wrong is the tampering signal.
     *  - the bank's own error is classified before the `id_token`, because a declined authorisation
     *    carries none; the Worker verifies the `id_token` itself in full.
     */
    @Suppress("ReturnCount")
    override fun validateCallback(redirectUrl: String): VrpAuthValidation {
        val params = parseCallbackUrl(redirectUrl)

        val consentId = session.pendingConsentId()
        if (consentId.isNullOrBlank()) return VrpAuthValidation.NoPending
        if (!session.matchesPendingState(params.state)) return VrpAuthValidation.SecurityError

        if (params.error != null) {
            return if (params.error == ERROR_ACCESS_DENIED) {
                VrpAuthValidation.AccessDenied
            } else {
                VrpAuthValidation.Error(
                    params.errorDescription ?: "HSBC reported an error: ${params.error}",
                )
            }
        }

        if (params.idToken == null) return VrpAuthValidation.SecurityError
        val code = params.code ?: return VrpAuthValidation.MissingCode
        validated = params
        return VrpAuthValidation.Valid(code = code, consentId = consentId)
    }

    @Suppress("ReturnCount")
    override suspend fun exchangeAndPersistCredential(
        code: String,
        consentId: String,
    ): NetworkResult<Unit, NetworkError> {
        val callback = validated?.takeIf { it.code == code }
            ?: return NetworkResult.Error(NetworkError.Client.BadRequest("No validated redirect for this code"))
        val flowId = session.pendingFlowId()?.takeIf { session.pendingConsentId() == consentId }
            ?: return NetworkResult.Error(NetworkError.Client.BadRequest("No VRP authorisation in flight"))
        validated = null

        val request = CompleteAuthRequest(flowId, code, callback.idToken.orEmpty(), callback.state.orEmpty())
        return when (val result = gateway.completeAuth(request)) {
            is NetworkResult.Error -> result
            is NetworkResult.Success -> {
                session.clearAuthorisationInFlight()
                NetworkResult.Success(Unit)
            }
        }
    }

    override fun pendingConsentId(): String? = session.pendingConsentId()

    override fun discardAuthorisation() = session.clearAuthorisationInFlight()
}
