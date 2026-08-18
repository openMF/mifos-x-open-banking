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
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import kotlinx.serialization.json.JsonObject
import org.mifosx.openbanking.core.network.model.vrp.checkFunds.request.CheckFunds
import org.mifosx.openbanking.core.network.model.vrp.checkFunds.response.CheckFundsResponse
import org.mifosx.openbanking.core.network.model.vrp.createConsent.request.CreateConsent
import org.mifosx.openbanking.core.network.model.vrp.createConsent.response.CreateConsentResponse
import org.mifosx.openbanking.core.network.model.vrp.initiatePayment.request.InitiatePayment
import org.mifosx.openbanking.core.network.model.vrp.initiatePayment.response.InitiatePaymentResponse
import org.mifosx.openbanking.core.network.pisp.detachedJwsSignature
import org.mifosx.openbanking.core.network.pisp.fapiHeaders
import org.mifosx.openbanking.core.network.pisp.obieBody
import org.mifosx.openbanking.core.network.pisp.writeHeaders
import org.mifosx.openbanking.core.network.result.toNetworkResult
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult
import kotlin.time.Clock

private const val VRP = "v4.0/pisp"

/**
 * The variable recurring payments API.
 *
 * @property httpClient The sandbox client, shared with the other API families.
 * @property kid Key id of the signing certificate.
 * @property signingKeyPem Signing key, PEM encoded.
 * @property financialId The bank's Open Banking organisation id.
 * @property signingIssuer Value of the signature's issuer claim.
 */
class Vrp(
    private val httpClient: HttpClient,
    private val kid: String,
    private val signingKeyPem: String,
    private val financialId: String,
    private val signingIssuer: String,
) {

    /**
     * Creates a consent. The customer has not approved it yet.
     *
     * @param paymentsScopeToken Client-credentials token scoped to payments.
     * @param idempotencyKey Identifies this attempt; replay it to retry without staging a second
     *   consent.
     */
    suspend fun createConsent(
        paymentsScopeToken: String,
        request: CreateConsent,
        idempotencyKey: String,
    ): NetworkResult<CreateConsentResponse, NetworkError> =
        signedPost(
            path = "$VRP/domestic-vrp-consents",
            accessToken = paymentsScopeToken,
            idempotencyKey = idempotencyKey,
            body = obieBody(CreateConsent.serializer(), request),
        ).toNetworkResult()

    /**
     * Reads a consent back, including its status and the payer the customer chose.
     *
     * @param accessToken Client-credentials token scoped to payments.
     */
    suspend fun getConsent(
        accessToken: String,
        consentId: String,
    ): NetworkResult<CreateConsentResponse, NetworkError> =
        authorizedGet("$VRP/domestic-vrp-consents/$consentId", accessToken).toNetworkResult()

    /**
     * Removes a consent. Returns no body.
     *
     * @param accessToken Client-credentials token scoped to payments.
     */
    suspend fun deleteConsent(
        accessToken: String,
        consentId: String,
    ): NetworkResult<Unit, NetworkError> =
        httpClient.delete("$VRP/domestic-vrp-consents/$consentId") {
            bearerAuth(accessToken)
            fapiHeaders(financialId)
        }.toNetworkResult()

    /**
     * Asks whether an amount is currently in the account.
     *
     * @param psuAccessToken Token from the customer's authorisation of this consent.
     * @param idempotencyKey Identifies this attempt.
     */
    suspend fun checkFunds(
        psuAccessToken: String,
        consentId: String,
        request: CheckFunds,
        idempotencyKey: String,
    ): NetworkResult<CheckFundsResponse, NetworkError> =
        signedPost(
            path = "$VRP/domestic-vrp-consents/$consentId/funds-confirmation",
            accessToken = psuAccessToken,
            idempotencyKey = idempotencyKey,
            body = obieBody(CheckFunds.serializer(), request),
        ).toNetworkResult()

    /**
     * Submits a payment under an approved consent.
     *
     * @param psuAccessToken Token from the customer's authorisation of this consent.
     * @param idempotencyKey Identifies this payment; replay it to retry without paying twice.
     */
    suspend fun createPayment(
        psuAccessToken: String,
        request: InitiatePayment,
        idempotencyKey: String,
    ): NetworkResult<InitiatePaymentResponse, NetworkError> =
        signedPost(
            path = "$VRP/domestic-vrps",
            accessToken = psuAccessToken,
            idempotencyKey = idempotencyKey,
            body = obieBody(InitiatePayment.serializer(), request),
        ).toNetworkResult()

    /**
     * Reads a payment back, including its current status.
     *
     * @param accessToken Client-credentials token scoped to payments.
     */
    suspend fun getPayment(
        accessToken: String,
        domesticVrpId: String,
    ): NetworkResult<InitiatePaymentResponse, NetworkError> =
        authorizedGet("$VRP/domestic-vrps/$domesticVrpId", accessToken).toNetworkResult()

    private suspend fun authorizedGet(path: String, accessToken: String): HttpResponse =
        httpClient.get(path) {
            bearerAuth(accessToken)
            fapiHeaders(financialId)
        }

    /**
     * Posts [body] with a detached JWS over it.
     *
     * The transmitted string is the one that was signed, wrapped in [TextContent] so content
     * negotiation cannot re-serialise it.
     */
    private suspend fun signedPost(
        path: String,
        accessToken: String,
        idempotencyKey: String,
        body: JsonObject,
    ): HttpResponse {
        val payload = body.toString()
        val signature = detachedJwsSignature(
            payload = payload,
            kid = kid,
            signingKeyPem = signingKeyPem,
            issuer = signingIssuer,
            issuedAtEpochSeconds = Clock.System.now().epochSeconds,
        )
        return httpClient.post(path) {
            bearerAuth(accessToken)
            fapiHeaders(financialId)
            writeHeaders(idempotencyKey = idempotencyKey, jwsSignature = signature)
            setBody(TextContent(payload, ContentType.Application.Json))
        }
    }
}
