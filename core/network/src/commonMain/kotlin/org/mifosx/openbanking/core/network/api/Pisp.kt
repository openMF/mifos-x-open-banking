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
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import kotlinx.serialization.json.JsonObject
import org.mifosx.openbanking.core.network.model.pisp.domesticPayment.request.DomesticPaymentConsentRequest
import org.mifosx.openbanking.core.network.model.pisp.domesticPayment.request.DomesticPaymentRequest
import org.mifosx.openbanking.core.network.model.pisp.domesticPayment.response.DomesticPaymentConsentResponse
import org.mifosx.openbanking.core.network.model.pisp.domesticPayment.response.DomesticPaymentResponse
import org.mifosx.openbanking.core.network.model.pisp.domesticScheduledPayment.request.DomesticScheduledPaymentConsentRequest
import org.mifosx.openbanking.core.network.model.pisp.domesticScheduledPayment.request.DomesticScheduledPaymentRequest
import org.mifosx.openbanking.core.network.model.pisp.domesticScheduledPayment.response.DomesticScheduledPaymentConsentResponse
import org.mifosx.openbanking.core.network.model.pisp.domesticScheduledPayment.response.DomesticScheduledPaymentResponse
import org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.request.DomesticStandingOrderConsentRequest
import org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.request.DomesticStandingOrderRequest
import org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.response.DomesticStandingOrderConsentResponse
import org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.response.DomesticStandingOrderResponse
import org.mifosx.openbanking.core.network.model.pisp.fundsConfirmation.response.FundsConfirmationResponse
import org.mifosx.openbanking.core.network.model.pisp.internationalPayment.request.InternationalPaymentConsentRequest
import org.mifosx.openbanking.core.network.model.pisp.internationalPayment.request.InternationalPaymentRequest
import org.mifosx.openbanking.core.network.model.pisp.internationalPayment.response.InternationalPaymentConsentResponse
import org.mifosx.openbanking.core.network.model.pisp.internationalPayment.response.InternationalPaymentResponse
import org.mifosx.openbanking.core.network.model.pisp.internationalScheduledPayment.request.InternationalScheduledPaymentConsentRequest
import org.mifosx.openbanking.core.network.model.pisp.internationalScheduledPayment.request.InternationalScheduledPaymentRequest
import org.mifosx.openbanking.core.network.model.pisp.internationalScheduledPayment.response.InternationalScheduledPaymentConsentResponse
import org.mifosx.openbanking.core.network.model.pisp.internationalScheduledPayment.response.InternationalScheduledPaymentResponse
import org.mifosx.openbanking.core.network.model.pisp.internationalStandingOrder.request.InternationalStandingOrderConsentRequest
import org.mifosx.openbanking.core.network.model.pisp.internationalStandingOrder.request.InternationalStandingOrderRequest
import org.mifosx.openbanking.core.network.model.pisp.internationalStandingOrder.response.InternationalStandingOrderConsentResponse
import org.mifosx.openbanking.core.network.model.pisp.internationalStandingOrder.response.InternationalStandingOrderResponse
import org.mifosx.openbanking.core.network.pisp.detachedJwsSignature
import org.mifosx.openbanking.core.network.pisp.fapiHeaders
import org.mifosx.openbanking.core.network.pisp.obieBody
import org.mifosx.openbanking.core.network.pisp.writeHeaders
import org.mifosx.openbanking.core.network.result.toNetworkResult
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult
import kotlin.time.Clock

private const val PIS = "v4.0/pisp"

/**
 * HSBC OBIE PISP endpoints — the app's only write surface. Paths are relative and resolve against
 * the client's `defaultRequest` base URL. Every call returns a [NetworkResult].
 *
 * Unlike the AIS reads, **no call here is authenticated by the client's bearer interceptor**: each
 * takes its token explicitly, because the payment journey uses two credentials that are both
 * distinct from the AIS PSU bearer the rest of the app runs on. Staging is authorised by a
 * client-credentials token minted with `scope=payments`; funds confirmation, submission and status
 * are authorised by the PSU token from the payments authorisation leg. `isAisResourceRequest`
 * excludes this whole path prefix so the interceptor cannot overwrite the header set here.
 *
 * The two write calls additionally carry a detached JWS over the request body and an idempotency
 * key. The key is a parameter rather than something this class generates: it is staged once per
 * payment and replayed unchanged on every retry.
 *
 * The function count is over detekt's threshold and suppressed deliberately: this class is an
 * endpoint catalogue, four calls per payment product, and splitting it by product would put the
 * shared signing and header logic behind an internal seam for no gain in readability.
 */
@Suppress("TooManyFunctions")
class Pisp(
    private val httpClient: HttpClient,
    private val kid: String,
    private val signingKeyPem: String,
    private val financialId: String,
    private val signingIssuer: String,
) {

    /**
     * Stages a payment. The bank returns a `ConsentId` with `Status` `AWAU`, awaiting the PSU's
     * authorisation; nothing moves until [createDomesticPayment] is called against it.
     *
     * [paymentsScopeToken] must be a client-credentials token minted with `scope=payments` — the AIS
     * client-credentials token fails here on scope, and the failure reads as an auth problem.
     */
    suspend fun createDomesticPaymentConsent(
        paymentsScopeToken: String,
        request: DomesticPaymentConsentRequest,
        idempotencyKey: String,
    ): NetworkResult<DomesticPaymentConsentResponse, NetworkError> =
        signedPost(
            path = "$PIS/domestic-payment-consents",
            accessToken = paymentsScopeToken,
            idempotencyKey = idempotencyKey,
            body = obieBody(DomesticPaymentConsentRequest.serializer(), request),
        ).toNetworkResult()

    /** Reads a staged consent back, principally to observe its `Status` after authorisation. */
    suspend fun getDomesticPaymentConsent(
        accessToken: String,
        consentId: String,
    ): NetworkResult<DomesticPaymentConsentResponse, NetworkError> =
        authorizedGet("$PIS/domestic-payment-consents/$consentId", accessToken).toNetworkResult()

    /**
     * Asks whether the debtor account can cover the staged amount. Callable only once the consent is
     * authorised, since it needs the PSU token.
     *
     * Optional in the protocol, but binding when made: a `false` result must stop the submission.
     * This is the authoritative check — the amount screen's balance comparison is advisory and can
     * pass while this refuses.
     */
    suspend fun getFundsConfirmation(
        psuAccessToken: String,
        consentId: String,
    ): NetworkResult<FundsConfirmationResponse, NetworkError> =
        authorizedGet(
            "$PIS/domestic-payment-consents/$consentId/funds-confirmation",
            psuAccessToken,
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
        psuAccessToken: String,
        request: DomesticPaymentRequest,
        idempotencyKey: String,
    ): NetworkResult<DomesticPaymentResponse, NetworkError> =
        signedPost(
            path = "$PIS/domestic-payments",
            accessToken = psuAccessToken,
            idempotencyKey = idempotencyKey,
            body = obieBody(DomesticPaymentRequest.serializer(), request),
        ).toNetworkResult()

    /**
     * Reads a submitted payment's settlement status.
     *
     * A pure read — no JWS, no idempotency key — so it survives expiry of the single-payment PSU
     * token on a client-credentials `payments` token.
     */
    suspend fun getDomesticPayment(
        accessToken: String,
        domesticPaymentId: String,
    ): NetworkResult<DomesticPaymentResponse, NetworkError> =
        authorizedGet("$PIS/domestic-payments/$domesticPaymentId", accessToken).toNetworkResult()

    suspend fun createInternationalPaymentConsent(
        paymentsScopeToken: String,
        request: InternationalPaymentConsentRequest,
        idempotencyKey: String,
    ): NetworkResult<InternationalPaymentConsentResponse, NetworkError> =
        signedPost(
            path = "$PIS/international-payment-consents",
            accessToken = paymentsScopeToken,
            idempotencyKey = idempotencyKey,
            body = obieBody(InternationalPaymentConsentRequest.serializer(), request),
        ).toNetworkResult()

    suspend fun getInternationalPaymentConsent(
        accessToken: String,
        consentId: String,
    ): NetworkResult<InternationalPaymentConsentResponse, NetworkError> =
        authorizedGet("$PIS/international-payment-consents/$consentId", accessToken).toNetworkResult()

    suspend fun getInternationalFundsConfirmation(
        psuAccessToken: String,
        consentId: String,
    ): NetworkResult<FundsConfirmationResponse, NetworkError> =
        authorizedGet(
            "$PIS/international-payment-consents/$consentId/funds-confirmation",
            psuAccessToken,
        ).toNetworkResult()

    suspend fun createInternationalPayment(
        psuAccessToken: String,
        request: InternationalPaymentRequest,
        idempotencyKey: String,
    ): NetworkResult<InternationalPaymentResponse, NetworkError> =
        signedPost(
            path = "$PIS/international-payments",
            accessToken = psuAccessToken,
            idempotencyKey = idempotencyKey,
            body = obieBody(InternationalPaymentRequest.serializer(), request),
        ).toNetworkResult()

    suspend fun getInternationalPayment(
        accessToken: String,
        internationalPaymentId: String,
    ): NetworkResult<InternationalPaymentResponse, NetworkError> =
        authorizedGet(
            "$PIS/international-payments/$internationalPaymentId",
            accessToken,
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
        paymentsScopeToken: String,
        request: DomesticScheduledPaymentConsentRequest,
        idempotencyKey: String,
    ): NetworkResult<DomesticScheduledPaymentConsentResponse, NetworkError> =
        signedPost(
            path = "$PIS/domestic-scheduled-payment-consents",
            accessToken = paymentsScopeToken,
            idempotencyKey = idempotencyKey,
            body = obieBody(DomesticScheduledPaymentConsentRequest.serializer(), request),
        ).toNetworkResult()

    suspend fun getDomesticScheduledPaymentConsent(
        accessToken: String,
        consentId: String,
    ): NetworkResult<DomesticScheduledPaymentConsentResponse, NetworkError> =
        authorizedGet("$PIS/domestic-scheduled-payment-consents/$consentId", accessToken).toNetworkResult()

    suspend fun createDomesticScheduledPayment(
        psuAccessToken: String,
        request: DomesticScheduledPaymentRequest,
        idempotencyKey: String,
    ): NetworkResult<DomesticScheduledPaymentResponse, NetworkError> =
        signedPost(
            path = "$PIS/domestic-scheduled-payments",
            accessToken = psuAccessToken,
            idempotencyKey = idempotencyKey,
            body = obieBody(DomesticScheduledPaymentRequest.serializer(), request),
        ).toNetworkResult()

    suspend fun getDomesticScheduledPayment(
        accessToken: String,
        domesticScheduledPaymentId: String,
    ): NetworkResult<DomesticScheduledPaymentResponse, NetworkError> =
        authorizedGet(
            "$PIS/domestic-scheduled-payments/$domesticScheduledPaymentId",
            accessToken,
        ).toNetworkResult()

    suspend fun createInternationalScheduledPaymentConsent(
        paymentsScopeToken: String,
        request: InternationalScheduledPaymentConsentRequest,
        idempotencyKey: String,
    ): NetworkResult<InternationalScheduledPaymentConsentResponse, NetworkError> =
        signedPost(
            path = "$PIS/international-scheduled-payment-consents",
            accessToken = paymentsScopeToken,
            idempotencyKey = idempotencyKey,
            body = obieBody(InternationalScheduledPaymentConsentRequest.serializer(), request),
        ).toNetworkResult()

    suspend fun getInternationalScheduledPaymentConsent(
        accessToken: String,
        consentId: String,
    ): NetworkResult<InternationalScheduledPaymentConsentResponse, NetworkError> =
        authorizedGet(
            "$PIS/international-scheduled-payment-consents/$consentId",
            accessToken,
        ).toNetworkResult()

    suspend fun createInternationalScheduledPayment(
        psuAccessToken: String,
        request: InternationalScheduledPaymentRequest,
        idempotencyKey: String,
    ): NetworkResult<InternationalScheduledPaymentResponse, NetworkError> =
        signedPost(
            path = "$PIS/international-scheduled-payments",
            accessToken = psuAccessToken,
            idempotencyKey = idempotencyKey,
            body = obieBody(InternationalScheduledPaymentRequest.serializer(), request),
        ).toNetworkResult()

    suspend fun getInternationalScheduledPayment(
        accessToken: String,
        internationalScheduledPaymentId: String,
    ): NetworkResult<InternationalScheduledPaymentResponse, NetworkError> =
        authorizedGet(
            "$PIS/international-scheduled-payments/$internationalScheduledPaymentId",
            accessToken,
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
        paymentsScopeToken: String,
        request: DomesticStandingOrderConsentRequest,
        idempotencyKey: String,
    ): NetworkResult<DomesticStandingOrderConsentResponse, NetworkError> =
        signedPost(
            path = "$PIS/domestic-standing-order-consents",
            accessToken = paymentsScopeToken,
            idempotencyKey = idempotencyKey,
            body = obieBody(DomesticStandingOrderConsentRequest.serializer(), request),
        ).toNetworkResult()

    suspend fun getDomesticStandingOrderConsent(
        accessToken: String,
        consentId: String,
    ): NetworkResult<DomesticStandingOrderConsentResponse, NetworkError> =
        authorizedGet(
            "$PIS/domestic-standing-order-consents/$consentId",
            accessToken,
        ).toNetworkResult()

    suspend fun createDomesticStandingOrder(
        psuAccessToken: String,
        request: DomesticStandingOrderRequest,
        idempotencyKey: String,
    ): NetworkResult<DomesticStandingOrderResponse, NetworkError> =
        signedPost(
            path = "$PIS/domestic-standing-orders",
            accessToken = psuAccessToken,
            idempotencyKey = idempotencyKey,
            body = obieBody(DomesticStandingOrderRequest.serializer(), request),
        ).toNetworkResult()

    suspend fun getDomesticStandingOrder(
        accessToken: String,
        domesticStandingOrderId: String,
    ): NetworkResult<DomesticStandingOrderResponse, NetworkError> =
        authorizedGet(
            "$PIS/domestic-standing-orders/$domesticStandingOrderId",
            accessToken,
        ).toNetworkResult()

    suspend fun createInternationalStandingOrderConsent(
        paymentsScopeToken: String,
        request: InternationalStandingOrderConsentRequest,
        idempotencyKey: String,
    ): NetworkResult<InternationalStandingOrderConsentResponse, NetworkError> =
        signedPost(
            path = "$PIS/international-standing-order-consents",
            accessToken = paymentsScopeToken,
            idempotencyKey = idempotencyKey,
            body = obieBody(InternationalStandingOrderConsentRequest.serializer(), request),
        ).toNetworkResult()

    suspend fun getInternationalStandingOrderConsent(
        accessToken: String,
        consentId: String,
    ): NetworkResult<InternationalStandingOrderConsentResponse, NetworkError> =
        authorizedGet(
            "$PIS/international-standing-order-consents/$consentId",
            accessToken,
        ).toNetworkResult()

    suspend fun createInternationalStandingOrder(
        psuAccessToken: String,
        request: InternationalStandingOrderRequest,
        idempotencyKey: String,
    ): NetworkResult<InternationalStandingOrderResponse, NetworkError> =
        signedPost(
            path = "$PIS/international-standing-orders",
            accessToken = psuAccessToken,
            idempotencyKey = idempotencyKey,
            body = obieBody(InternationalStandingOrderRequest.serializer(), request),
        ).toNetworkResult()

    suspend fun getInternationalStandingOrder(
        accessToken: String,
        internationalStandingOrderId: String,
    ): NetworkResult<InternationalStandingOrderResponse, NetworkError> =
        authorizedGet(
            "$PIS/international-standing-orders/$internationalStandingOrderId",
            accessToken,
        ).toNetworkResult()

    // endregion

    private suspend fun authorizedGet(path: String, accessToken: String): HttpResponse =
        httpClient.get(path) {
            bearerAuth(accessToken)
            fapiHeaders(financialId)
        }

    /**
     * Posts [body] with a detached JWS over it.
     *
     * The body is transmitted as the same string that was signed, wrapped in [TextContent] so
     * content negotiation cannot re-serialise it into different bytes and invalidate the signature.
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
