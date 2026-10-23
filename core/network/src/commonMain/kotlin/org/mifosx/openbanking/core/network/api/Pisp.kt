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
import io.ktor.client.request.get
import org.mifosx.openbanking.core.network.model.pisp.domesticPayment.request.DomesticPaymentConsentRequest
import org.mifosx.openbanking.core.network.model.pisp.domesticPayment.request.DomesticPaymentRequest
import org.mifosx.openbanking.core.network.model.pisp.domesticPayment.response.DomesticPaymentResponse
import org.mifosx.openbanking.core.network.model.pisp.domesticScheduledPayment.request.DomesticScheduledPaymentConsentRequest
import org.mifosx.openbanking.core.network.model.pisp.domesticScheduledPayment.request.DomesticScheduledPaymentRequest
import org.mifosx.openbanking.core.network.model.pisp.domesticScheduledPayment.response.DomesticScheduledPaymentResponse
import org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.request.DomesticStandingOrderConsentRequest
import org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.request.DomesticStandingOrderRequest
import org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.response.DomesticStandingOrderResponse
import org.mifosx.openbanking.core.network.model.pisp.fundsConfirmation.response.FundsConfirmationResponse
import org.mifosx.openbanking.core.network.model.pisp.internationalPayment.request.InternationalPaymentConsentRequest
import org.mifosx.openbanking.core.network.model.pisp.internationalPayment.request.InternationalPaymentRequest
import org.mifosx.openbanking.core.network.model.pisp.internationalPayment.response.InternationalPaymentResponse
import org.mifosx.openbanking.core.network.model.pisp.internationalScheduledPayment.request.InternationalScheduledPaymentConsentRequest
import org.mifosx.openbanking.core.network.model.pisp.internationalScheduledPayment.request.InternationalScheduledPaymentRequest
import org.mifosx.openbanking.core.network.model.pisp.internationalScheduledPayment.response.InternationalScheduledPaymentResponse
import org.mifosx.openbanking.core.network.model.pisp.internationalStandingOrder.request.InternationalStandingOrderConsentRequest
import org.mifosx.openbanking.core.network.model.pisp.internationalStandingOrder.request.InternationalStandingOrderRequest
import org.mifosx.openbanking.core.network.model.pisp.internationalStandingOrder.response.InternationalStandingOrderResponse
import org.mifosx.openbanking.core.network.model.pisp.domesticPayment.response.DomesticPaymentConsentResponse
import org.mifosx.openbanking.core.network.model.pisp.internationalPayment.response.InternationalPaymentConsentResponse
import org.mifosx.openbanking.core.network.model.pisp.domesticScheduledPayment.response.DomesticScheduledPaymentConsentResponse
import org.mifosx.openbanking.core.network.model.pisp.internationalScheduledPayment.response.InternationalScheduledPaymentConsentResponse
import org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.response.DomesticStandingOrderConsentResponse
import org.mifosx.openbanking.core.network.model.pisp.internationalStandingOrder.response.InternationalStandingOrderConsentResponse
import org.mifosx.openbanking.core.network.pisp.obieBody
import org.mifosx.openbanking.core.network.postJson
import org.mifosx.openbanking.core.network.result.toNetworkResult
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult

/**
 * The Worker's PIS surface (bff/src/routes.ts) — the app's only payment write path. Every call is
 * session-gated except consent creation, which runs before the PSU authorises and returns the
 * Worker-signed authorize URL ([ConsentStart]). The Worker mints both bank credentials, signs the
 * detached JWS over the exact bytes sent here, and refuses a non-compact body.
 *
 * Writes carry the caller's idempotency key — staged once per payment and replayed on every retry, so
 * a retry can never become a second instruction. The function count is an endpoint catalogue (four
 * calls per product), not tangled logic.
 */
@Suppress("TooManyFunctions")
class Pisp(
    private val httpClient: HttpClient,
) {

    /**
     * Stages a payment. The bank returns a `ConsentId` with `Status` `AWAU`, awaiting the PSU's
     * authorisation; nothing moves until [createDomesticPayment] is called against it. Returns the
     * Worker's pending flow and the authorize URL the PSU opens.
     */
    suspend fun createDomesticPaymentConsent(
        request: DomesticPaymentConsentRequest,
        idempotencyKey: String,
    ): NetworkResult<ConsentStart, NetworkError> =
        signedPost(
            attest = true,
            path = "/v1/consents/payment/domestic",
            idempotencyKey = idempotencyKey,
            body = obieBody(DomesticPaymentConsentRequest.serializer(), request),
        ).toNetworkResult()

    /** Reads a staged consent back, principally to observe its `Status` after authorisation. */
    suspend fun getDomesticPaymentConsent(
        consentId: String,
    ): NetworkResult<DomesticPaymentConsentResponse, NetworkError> =
        httpClient.get("/v1/consents/payment/domestic/$consentId").toNetworkResult()

    /**
     * Asks whether the debtor account can cover the staged amount. Callable only once the consent is
     * authorised (the Worker holds the PSU payment token on this session).
     *
     * Optional in the protocol, but binding when made: a `false` result must stop the submission.
     * This is the authoritative check — the amount screen's balance comparison is advisory and can
     * pass while this refuses.
     */
    suspend fun getFundsConfirmation(
        consentId: String,
    ): NetworkResult<FundsConfirmationResponse, NetworkError> =
        httpClient.get(
            "/v1/consents/payment/domestic/$consentId/funds-confirmation"
        ).toNetworkResult()

    /**
     * Executes the payment against an authorised consent.
     *
     * `Data.ConsentId` must match the staged consent and `Data.Initiation` must be byte-identical to
     * the one that was staged, or the bank refuses with `U008`. Pass the same `Initiation` instance
     * that was staged rather than rebuilding it — serialization is deterministic, reconstruction is
     * not.
     */
    suspend fun createDomesticPayment(
        request: DomesticPaymentRequest,
        idempotencyKey: String,
    ): NetworkResult<DomesticPaymentResponse, NetworkError> =
        signedPost(
            path = "/v1/payments/domestic",
            idempotencyKey = idempotencyKey,
            body = obieBody(DomesticPaymentRequest.serializer(), request),
        ).toNetworkResult()

    /**
     * Reads a submitted payment's settlement status.
     *
     * A pure read on the Worker's client-credentials token; only payments this session submitted.
     */
    suspend fun getDomesticPayment(
        domesticPaymentId: String,
    ): NetworkResult<DomesticPaymentResponse, NetworkError> =
        httpClient.get("/v1/payments/domestic/$domesticPaymentId").toNetworkResult()

    suspend fun createInternationalPaymentConsent(
        request: InternationalPaymentConsentRequest,
        idempotencyKey: String,
    ): NetworkResult<ConsentStart, NetworkError> =
        signedPost(
            attest = true,
            path = "/v1/consents/payment/international",
            idempotencyKey = idempotencyKey,
            body = obieBody(InternationalPaymentConsentRequest.serializer(), request),
        ).toNetworkResult()

    suspend fun getInternationalPaymentConsent(
        consentId: String,
    ): NetworkResult<InternationalPaymentConsentResponse, NetworkError> =
        httpClient.get("/v1/consents/payment/international/$consentId").toNetworkResult()

    suspend fun getInternationalFundsConfirmation(
        consentId: String,
    ): NetworkResult<FundsConfirmationResponse, NetworkError> =
        httpClient.get(
            "/v1/consents/payment/international/$consentId/funds-confirmation"
        ).toNetworkResult()

    suspend fun createInternationalPayment(
        request: InternationalPaymentRequest,
        idempotencyKey: String,
    ): NetworkResult<InternationalPaymentResponse, NetworkError> =
        signedPost(
            path = "/v1/payments/international",
            idempotencyKey = idempotencyKey,
            body = obieBody(InternationalPaymentRequest.serializer(), request),
        ).toNetworkResult()

    suspend fun getInternationalPayment(
        internationalPaymentId: String,
    ): NetworkResult<InternationalPaymentResponse, NetworkError> =
        httpClient.get(
            "/v1/payments/international/$internationalPaymentId"
        ).toNetworkResult()

    // region — Scheduled payments
    //
    // The same four calls per rail as an immediate payment, with two absences worth stating.
    //
    // There is no funds-confirmation call. OBIE defines one for international-scheduled consents and
    // none at all for domestic-scheduled; HSBC marks the international one unsupported for every
    // brand, and no capture has ever exercised it. Asking would be inventing an endpoint.
    //
    // The consent create carries `Data.Permission = "Create"`, which the immediate rails have no
    // field for — the mapper sets it, and omitting it is refused `400 U004`.

    suspend fun createDomesticScheduledPaymentConsent(
        request: DomesticScheduledPaymentConsentRequest,
        idempotencyKey: String,
    ): NetworkResult<ConsentStart, NetworkError> =
        signedPost(
            attest = true,
            path = "/v1/consents/payment/domestic-scheduled",
            idempotencyKey = idempotencyKey,
            body = obieBody(DomesticScheduledPaymentConsentRequest.serializer(), request),
        ).toNetworkResult()

    suspend fun getDomesticScheduledPaymentConsent(
        consentId: String,
    ): NetworkResult<DomesticScheduledPaymentConsentResponse, NetworkError> =
        httpClient.get("/v1/consents/payment/domestic-scheduled/$consentId").toNetworkResult()

    suspend fun createDomesticScheduledPayment(
        request: DomesticScheduledPaymentRequest,
        idempotencyKey: String,
    ): NetworkResult<DomesticScheduledPaymentResponse, NetworkError> =
        signedPost(
            path = "/v1/payments/domestic-scheduled",
            idempotencyKey = idempotencyKey,
            body = obieBody(DomesticScheduledPaymentRequest.serializer(), request),
        ).toNetworkResult()

    suspend fun getDomesticScheduledPayment(
        domesticScheduledPaymentId: String,
    ): NetworkResult<DomesticScheduledPaymentResponse, NetworkError> =
        httpClient.get(
            "/v1/payments/domestic-scheduled/$domesticScheduledPaymentId"
        ).toNetworkResult()

    suspend fun createInternationalScheduledPaymentConsent(
        request: InternationalScheduledPaymentConsentRequest,
        idempotencyKey: String,
    ): NetworkResult<ConsentStart, NetworkError> =
        signedPost(
            attest = true,
            path = "/v1/consents/payment/international-scheduled",
            idempotencyKey = idempotencyKey,
            body = obieBody(InternationalScheduledPaymentConsentRequest.serializer(), request),
        ).toNetworkResult()

    suspend fun getInternationalScheduledPaymentConsent(
        consentId: String,
    ): NetworkResult<InternationalScheduledPaymentConsentResponse, NetworkError> =
        httpClient.get(
            "/v1/consents/payment/international-scheduled/$consentId"
        ).toNetworkResult()

    suspend fun createInternationalScheduledPayment(
        request: InternationalScheduledPaymentRequest,
        idempotencyKey: String,
    ): NetworkResult<InternationalScheduledPaymentResponse, NetworkError> =
        signedPost(
            path = "/v1/payments/international-scheduled",
            idempotencyKey = idempotencyKey,
            body = obieBody(InternationalScheduledPaymentRequest.serializer(), request),
        ).toNetworkResult()

    suspend fun getInternationalScheduledPayment(
        internationalScheduledPaymentId: String,
    ): NetworkResult<InternationalScheduledPaymentResponse, NetworkError> =
        httpClient.get(
            "/v1/payments/international-scheduled/$internationalScheduledPaymentId"
        ).toNetworkResult()

    // endregion

    // region — Standing orders
    //
    // The same four calls per rail again, for a recurring mandate rather than an instruction.
    //
    // No funds confirmation, and here the absence is total: OBIE defines no such sub-resource for
    // either standing-order consent, so there is nothing to call and nothing to skip conditionally.
    //
    // `Data.Permission = "Create"` is mandatory on the consent, as on the scheduled rails.
    //
    // One sub-resource is defined and deliberately not exposed: `…/payment-details`. No capture has
    // ever called it, and OBIE describes it as detail about the submission rather than about
    // individual instalments — so adding it would be offering an answer nobody has heard.

    suspend fun createDomesticStandingOrderConsent(
        request: DomesticStandingOrderConsentRequest,
        idempotencyKey: String,
    ): NetworkResult<ConsentStart, NetworkError> =
        signedPost(
            attest = true,
            path = "/v1/consents/payment/domestic-standing-order",
            idempotencyKey = idempotencyKey,
            body = obieBody(DomesticStandingOrderConsentRequest.serializer(), request),
        ).toNetworkResult()

    suspend fun getDomesticStandingOrderConsent(
        consentId: String,
    ): NetworkResult<DomesticStandingOrderConsentResponse, NetworkError> =
        httpClient.get(
            "/v1/consents/payment/domestic-standing-order/$consentId"
        ).toNetworkResult()

    suspend fun createDomesticStandingOrder(
        request: DomesticStandingOrderRequest,
        idempotencyKey: String,
    ): NetworkResult<DomesticStandingOrderResponse, NetworkError> =
        signedPost(
            path = "/v1/payments/domestic-standing-order",
            idempotencyKey = idempotencyKey,
            body = obieBody(DomesticStandingOrderRequest.serializer(), request),
        ).toNetworkResult()

    suspend fun getDomesticStandingOrder(
        domesticStandingOrderId: String,
    ): NetworkResult<DomesticStandingOrderResponse, NetworkError> =
        httpClient.get(
            "/v1/payments/domestic-standing-order/$domesticStandingOrderId"
        ).toNetworkResult()

    suspend fun createInternationalStandingOrderConsent(
        request: InternationalStandingOrderConsentRequest,
        idempotencyKey: String,
    ): NetworkResult<ConsentStart, NetworkError> =
        signedPost(
            attest = true,
            path = "/v1/consents/payment/international-standing-order",
            idempotencyKey = idempotencyKey,
            body = obieBody(InternationalStandingOrderConsentRequest.serializer(), request),
        ).toNetworkResult()

    suspend fun getInternationalStandingOrderConsent(
        consentId: String,
    ): NetworkResult<InternationalStandingOrderConsentResponse, NetworkError> =
        httpClient.get(
            "/v1/consents/payment/international-standing-order/$consentId"
        ).toNetworkResult()

    suspend fun createInternationalStandingOrder(
        request: InternationalStandingOrderRequest,
        idempotencyKey: String,
    ): NetworkResult<InternationalStandingOrderResponse, NetworkError> =
        signedPost(
            path = "/v1/payments/international-standing-order",
            idempotencyKey = idempotencyKey,
            body = obieBody(InternationalStandingOrderRequest.serializer(), request),
        ).toNetworkResult()

    suspend fun getInternationalStandingOrder(
        internationalStandingOrderId: String,
    ): NetworkResult<InternationalStandingOrderResponse, NetworkError> =
        httpClient.get(
            "/v1/payments/international-standing-order/$internationalStandingOrderId"
        ).toNetworkResult()

    // endregion

    /**
     * Posts the compact canonical form of [body]. Consent creates are attested (the Worker's
     * `attest: true` rows); payment submissions run on the session alone.
     */
    private suspend fun signedPost(
        path: String,
        idempotencyKey: String,
        body: kotlinx.serialization.json.JsonObject,
        attest: Boolean = false,
    ) = httpClient.postJson(path, body.toString(), attest = attest, idempotencyKey = idempotencyKey)
}
