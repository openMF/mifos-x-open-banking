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

import org.mifosx.openbanking.core.data.callback.impl.idTokenNonceMismatch
import org.mifosx.openbanking.core.data.callback.impl.parseCallbackUrl
import org.mifosx.openbanking.core.model.vrp.VrpConsent
import org.mifosx.openbanking.core.network.api.ConsentCreationScope
import org.mifosx.openbanking.core.network.api.OAuth
import org.mifosx.openbanking.core.network.authorize.generateConsentAuthorizationUrl
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult
import kotlin.time.Clock

private const val AUTHORIZE_PATH = "/obie/open-banking/v1.1/oauth2/authorize"
private const val RESPONSE_TYPE = "code id_token"
private const val ERROR_ACCESS_DENIED = "access_denied"

internal class VrpAuthRepositoryImpl(
    private val oauth: OAuth,
    private val session: VrpAuthSession,
    private val consents: VrpConsentRepository,
    private val signingKeyPem: String,
    private val clientId: String,
    private val kid: String,
    private val bankHost: String,
    private val authorizeHost: String,
    private val redirectUri: String,
) : VrpAuthRepository {

    override suspend fun beginAuthorisation(consentId: String): NetworkResult<String, NetworkError> {
        val auth = generateConsentAuthorizationUrl(
            audience = "https://$bankHost",
            authorizeUrl = "https://$authorizeHost$AUTHORIZE_PATH",
            clientId = clientId,
            kid = kid,
            scope = ConsentCreationScope.PAYMENTS,
            responseType = RESPONSE_TYPE,
            redirectUri = redirectUri,
            consentId = consentId,
            signingKeyPem = signingKeyPem,
            nowEpochSeconds = Clock.System.now().epochSeconds,
        )

        session.saveAuthorisationInFlight(
            consentId = consentId,
            state = auth.state,
            nonce = auth.nonce,
        )
        return NetworkResult.Success(auth.authorizationUrl)
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
     *  - the bank's own error is classified before the nonce, because a declined authorisation
     *    carries no `id_token` and checking the nonce first would report a refusal as tampering.
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

        if (idTokenNonceMismatch(params.idToken, session.pendingNonce().orEmpty())) {
            return VrpAuthValidation.SecurityError
        }

        val code = params.code ?: return VrpAuthValidation.MissingCode
        return VrpAuthValidation.Valid(code = code, consentId = consentId)
    }

    @Suppress("ReturnCount")
    override suspend fun completeAuthorisation(
        code: String,
        consentId: String,
    ): NetworkResult<VrpConsent, NetworkError> {
        val tokens = when (val result = oauth.exchangeAuthorizationCode(code, redirectUri)) {
            is NetworkResult.Success -> result.data
            is NetworkResult.Error -> return result
        }

        val refreshToken = tokens.refreshtoken?.takeIf { it.isNotBlank() }
            ?: return NetworkResult.Error(
                NetworkError.Client.BadRequest("Authorisation returned no refresh token"),
            )

        session.saveRefreshToken(consentId, refreshToken)
        session.clearAuthorisationInFlight()

        return consents.refreshStatus(consentId)
    }

    override fun pendingConsentId(): String? = session.pendingConsentId()

    override fun discardAuthorisation() = session.clearAuthorisationInFlight()
}
