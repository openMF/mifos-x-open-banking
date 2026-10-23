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
import io.ktor.client.request.delete
import io.ktor.client.request.get
import org.mifosx.openbanking.core.network.model.vrp.checkFunds.request.CheckFunds
import org.mifosx.openbanking.core.network.model.vrp.checkFunds.response.CheckFundsResponse
import org.mifosx.openbanking.core.network.model.vrp.createConsent.request.CreateConsent
import org.mifosx.openbanking.core.network.model.vrp.createConsent.response.CreateConsentResponse
import org.mifosx.openbanking.core.network.model.vrp.initiatePayment.request.InitiatePayment
import org.mifosx.openbanking.core.network.model.vrp.initiatePayment.response.InitiatePaymentResponse
import org.mifosx.openbanking.core.network.pisp.obieBody
import org.mifosx.openbanking.core.network.postJson
import org.mifosx.openbanking.core.network.result.toNetworkResult
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult

private const val CONSENTS = "/v1/consents/vrp"
private const val PAYMENTS = "/v1/vrp/payments"

/**
 * The Worker's variable-recurring-payments surface (bff/src/routes.ts). The Worker holds the
 * consent's refresh token and refreshes on 401; the app only presents its session. Writes carry the
 * caller's idempotency key and are sent as compact JSON, which the Worker signs byte-for-byte.
 */
class Vrp(
    private val httpClient: HttpClient,
) {

    /** Stages a consent (attested). The customer approves it at [ConsentStart.authorizeUrl]. */
    suspend fun createConsent(
        request: CreateConsent,
        idempotencyKey: String,
    ): NetworkResult<ConsentStart, NetworkError> =
        httpClient.postJson(
            CONSENTS,
            obieBody(CreateConsent.serializer(), request).toString(),
            attest = true,
            idempotencyKey = idempotencyKey,
        ).toNetworkResult()

    /** Reads a consent this session authorised, including its status and the payer chosen. */
    suspend fun getConsent(consentId: String): NetworkResult<CreateConsentResponse, NetworkError> =
        httpClient.get("$CONSENTS/$consentId").toNetworkResult()

    /** Removes a consent at the bank. Returns no body. */
    suspend fun deleteConsent(consentId: String): NetworkResult<Unit, NetworkError> =
        httpClient.delete("$CONSENTS/$consentId").toNetworkResult()

    /** Asks whether an amount is currently in the account (attested; the body names [consentId]). */
    suspend fun checkFunds(
        consentId: String,
        request: CheckFunds,
        idempotencyKey: String,
    ): NetworkResult<CheckFundsResponse, NetworkError> =
        httpClient.postJson(
            "$CONSENTS/$consentId/funds-confirmation",
            obieBody(CheckFunds.serializer(), request).toString(),
            attest = true,
            idempotencyKey = idempotencyKey,
        ).toNetworkResult()

    /** Submits a payment under an approved consent; replay [idempotencyKey] to retry safely. */
    suspend fun createPayment(
        request: InitiatePayment,
        idempotencyKey: String,
    ): NetworkResult<InitiatePaymentResponse, NetworkError> =
        httpClient.postJson(
            PAYMENTS,
            obieBody(InitiatePayment.serializer(), request).toString(),
            idempotencyKey = idempotencyKey,
        ).toNetworkResult()

    /** Reads back a payment this session submitted. */
    suspend fun getPayment(domesticVrpId: String): NetworkResult<InitiatePaymentResponse, NetworkError> =
        httpClient.get("$PAYMENTS/$domesticVrpId").toNetworkResult()
}
