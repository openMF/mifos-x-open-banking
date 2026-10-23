/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.network.api

import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.mifosx.openbanking.core.network.postJson
import org.mifosx.openbanking.core.network.result.toNetworkResult
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult

/** What every consent POST returns: the pending flow, the bank's consent id and the URL to open. */
@Serializable
data class ConsentStart(val flowId: String, val consentId: String, val authorizeUrl: String)

/** `POST /v1/consents/ais`. Absent dates are defaulted by the Worker (+90 / -90 days / now). */
@Serializable
data class AisConsentRequest(
    val permissions: List<String>,
    val expirationDateTime: String? = null,
    val transactionFromDateTime: String? = null,
    val transactionToDateTime: String? = null,
)

/** `POST /v1/auth/complete` — the relay's `code` / `id_token` / `state` for one flow. */
@Serializable
data class CompleteAuthRequest(val flowId: String, val code: String, val idToken: String, val state: String)

/**
 * The opaque Worker session. [consentStatus] is `Authorised` | `AwaitingAuthorisation` | `Unknown`
 * (status read failed); a rejected consent is a `409 consent_rejected` instead. [expiresAt] is epoch ms
 * and advisory.
 */
@Serializable
data class CompletedAuth(
    val session: String,
    val expiresAt: Long,
    val leg: String,
    val consentId: String,
    val consentStatus: String,
    val consentExpiresAt: String? = null,
)

private val bodyJson = Json { explicitNulls = false }

/** Sign-in and session lifecycle — the only place a session is created or ended. */
class Session(private val httpClient: HttpClient) {

    suspend fun startAisConsent(request: AisConsentRequest): NetworkResult<ConsentStart, NetworkError> =
        httpClient.postJson("/v1/consents/ais", bodyJson.encodeToString(request), attest = true).toNetworkResult()

    /**
     * Exchanges the relay's callback for a session. A payment / VRP leg attaches to the session the
     * client presents (its `Authorization: Session` header); the AIS leg always mints a new one.
     * `400 invalid_id_token | state_mismatch | unknown_flow`, `409 consent_rejected`.
     */
    suspend fun completeAuth(request: CompleteAuthRequest): NetworkResult<CompletedAuth, NetworkError> =
        httpClient.postJson("/v1/auth/complete", bodyJson.encodeToString(request), attest = true).toNetworkResult()

    /** Wipes the Worker session; [revoke] also deletes the AIS consent at HSBC (best effort). */
    suspend fun logout(revoke: Boolean = false): NetworkResult<Unit, NetworkError> =
        httpClient.post("/v1/session/logout") {
            if (revoke) parameter("revoke", "true")
        }.toNetworkResult()
}
