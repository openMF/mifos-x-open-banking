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
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.mifosx.openbanking.core.network.TestSigningKey
import org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.request.DomesticStandingOrderConsentRequest
import org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.request.DomesticStandingOrderRequest
import org.mifosx.openbanking.core.network.model.pisp.internationalStandingOrder.request.InternationalStandingOrderConsentRequest
import org.mifosx.openbanking.core.network.model.pisp.internationalStandingOrder.request.InternationalStandingOrderRequest
import org.mifosx.openbanking.core.network.pisp.HEADER_FAPI_FINANCIAL_ID
import org.mifosx.openbanking.core.network.pisp.HEADER_FAPI_INTERACTION_ID
import org.mifosx.openbanking.core.network.pisp.HEADER_IDEMPOTENCY_KEY
import org.mifosx.openbanking.core.network.pisp.HEADER_JWS_SIGNATURE
import org.mifosx.openbanking.core.network.pisp.obieBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.request.CreditorAccount as SoCreditorAccount
import org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.request.Data as SoData
import org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.request.FirstPaymentAmount as SoFirstPaymentAmount
import org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.request.Frequency as SoFrequency
import org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.request.Initiation as SoInitiation
import org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.request.MandateRelatedInformation as SoMandate
import org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.request.Risk as SoRisk
import org.mifosx.openbanking.core.network.model.pisp.internationalStandingOrder.request.CreditorAccount as IntlSoCreditorAccount
import org.mifosx.openbanking.core.network.model.pisp.internationalStandingOrder.request.Data as IntlSoData
import org.mifosx.openbanking.core.network.model.pisp.internationalStandingOrder.request.Frequency as IntlSoFrequency
import org.mifosx.openbanking.core.network.model.pisp.internationalStandingOrder.request.Initiation as IntlSoInitiation
import org.mifosx.openbanking.core.network.model.pisp.internationalStandingOrder.request.InstructedAmount as IntlSoInstructedAmount
import org.mifosx.openbanking.core.network.model.pisp.internationalStandingOrder.request.MandateRelatedInformation as IntlSoMandate
import org.mifosx.openbanking.core.network.model.pisp.internationalStandingOrder.request.Risk as IntlSoRisk

private const val KID = "test-kid-1"
private const val SIGNING_ISSUER = "mifos_init_00000/0000000000000000000000"
private const val IDEMPOTENCY_KEY = "MFX-20260805-0001"
private const val CONSENT_ID = "812774903"

/**
 * [Pisp]'s standing-order surface at the wire.
 *
 * Split from [PispTest] rather than added to it: that class already sits at detekt's `LargeClass`
 * bound, and `payment-consent` set the precedent of giving a third product its own file for the same
 * reason. The infrastructure below is duplicated because test source sets do not cross classes here
 * and the originals are private — the same trade the other suites make.
 */
class PispStandingOrderTest {

    private val captured = mutableListOf<RecordedRequest>()

    private data class RecordedRequest(
        val method: HttpMethod,
        val path: String,
        val headers: Map<String, String?>,
        val body: String,
    )

    // region — standing orders

    private fun soConsentRequest(): DomesticStandingOrderConsentRequest =
        DomesticStandingOrderConsentRequest(
            data = SoData(
                permission = "Create",
                initiation = SoInitiation(
                    mandateRelatedInformation = SoMandate(
                        frequency = SoFrequency(type = "MNTH"),
                        firstPaymentDateTime = "2026-08-20T00:00:00+00:00",
                        finalPaymentDateTime = "2026-12-11T00:00:00+00:00",
                    ),
                    firstPaymentAmount = SoFirstPaymentAmount(amount = "250.00", currency = "GBP"),
                    creditorAccount = SoCreditorAccount(
                        schemeName = "UK.OBIE.SortCodeAccountNumber",
                        identification = "80200110203350",
                        name = "Mr Dharani C",
                    ),
                ),
            ),
            risk = SoRisk,
        )

    private fun intlSoConsentRequest(): InternationalStandingOrderConsentRequest =
        InternationalStandingOrderConsentRequest(
            data = IntlSoData(
                permission = "Create",
                initiation = IntlSoInitiation(
                    mandateRelatedInformation = IntlSoMandate(
                        frequency = IntlSoFrequency(type = "MNTH"),
                        firstPaymentDateTime = "2026-08-20T00:00:00+00:00",
                    ),
                    instructedAmount = IntlSoInstructedAmount(amount = "250.00", currency = "GBP"),
                    currencyOfTransfer = "USD",
                    chargeBearer = "BorneByCreditor",
                    creditorAccount = IntlSoCreditorAccount(
                        schemeName = "UK.OBIE.IBAN",
                        identification = "DE89370400440532013000",
                        name = "Klara Weiss",
                    ),
                ),
            ),
            risk = IntlSoRisk,
        )

    @Test
    fun `stages a domestic standing-order consent on the payments-scope token`() = runTest {
        pisp().createDomesticStandingOrderConsent("cc-payments-token", soConsentRequest(), IDEMPOTENCY_KEY)

        val request = captured.single()
        assertEquals("/v4.0/pisp/domestic-standing-order-consents", request.path)
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("Bearer cc-payments-token", request.headers[HttpHeaders.Authorization])
        assertEquals(IDEMPOTENCY_KEY, request.headers[HEADER_IDEMPOTENCY_KEY])
        assertNotNull(request.headers[HEADER_JWS_SIGNATURE])
    }

    @Test
    fun `stages an international standing-order consent on its own path`() = runTest {
        pisp().createInternationalStandingOrderConsent(
            "cc-payments-token",
            intlSoConsentRequest(),
            IDEMPOTENCY_KEY,
        )

        assertEquals("/v4.0/pisp/international-standing-order-consents", captured.single().path)
    }

    @Test
    fun `creates a domestic standing order on the psu token`() = runTest {
        pisp().createDomesticStandingOrder(
            "psu-payments-token",
            DomesticStandingOrderRequest(
                data = SoData(consentId = CONSENT_ID, initiation = soConsentRequest().data?.initiation),
                risk = SoRisk,
            ),
            IDEMPOTENCY_KEY,
        )

        val request = captured.single()
        assertEquals("/v4.0/pisp/domestic-standing-orders", request.path)
        assertEquals("Bearer psu-payments-token", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `creates an international standing order on the psu token`() = runTest {
        pisp().createInternationalStandingOrder(
            "psu-payments-token",
            InternationalStandingOrderRequest(
                data = IntlSoData(consentId = CONSENT_ID, initiation = intlSoConsentRequest().data?.initiation),
                risk = IntlSoRisk,
            ),
            IDEMPOTENCY_KEY,
        )

        assertEquals("/v4.0/pisp/international-standing-orders", captured.single().path)
    }

    /**
     * The four reads are pure GETs.
     *
     * Asserting the **absence** of both write headers is the point: a JWS over an empty body, or a
     * replayed idempotency key on a read, would both be accepted by the client and meaningless to the
     * bank.
     */
    @Test
    fun `standing-order reads carry neither an idempotency key nor a signature`() = runTest {
        val api = pisp()
        api.getDomesticStandingOrderConsent("cc-token", CONSENT_ID)
        api.getDomesticStandingOrder("cc-token", "19916")
        api.getInternationalStandingOrderConsent("cc-token", CONSENT_ID)
        api.getInternationalStandingOrder("cc-token", "19917")

        assertEquals(
            listOf(
                "/v4.0/pisp/domestic-standing-order-consents/$CONSENT_ID",
                "/v4.0/pisp/domestic-standing-orders/19916",
                "/v4.0/pisp/international-standing-order-consents/$CONSENT_ID",
                "/v4.0/pisp/international-standing-orders/19917",
            ),
            captured.map { it.path },
        )
        captured.forEach {
            assertEquals(HttpMethod.Get, it.method)
            assertNull(it.headers[HEADER_IDEMPOTENCY_KEY])
            assertNull(it.headers[HEADER_JWS_SIGNATURE])
        }
    }

    @Test
    fun `transmits the exact standing-order bytes that were signed`() = runTest {
        val request = soConsentRequest()

        pisp().createDomesticStandingOrderConsent("cc-token", request, IDEMPOTENCY_KEY)

        assertEquals(
            obieBody(DomesticStandingOrderConsentRequest.serializer(), request).toString(),
            captured.single().body,
        )
    }

    /**
     * `Permission` is mandatory on a standing-order consent and refused `400 U004` when missing.
     *
     * The immediate rails have no such field, so it is easy to omit by copying them.
     */
    @Test
    fun `the standing-order consent body carries the create permission`() = runTest {
        pisp().createDomesticStandingOrderConsent("cc-token", soConsentRequest(), IDEMPOTENCY_KEY)

        val data = Json.parseToJsonElement(captured.single().body).jsonObject.getValue("Data").jsonObject
        assertEquals("Create", data.getValue("Permission").jsonPrimitive.content)
    }

    /**
     * The frequency reaches the wire as an object carrying a four-letter code.
     *
     * Not the OBIE v3 interval string, which v4.0 does not accept — and not `MONT`, which is not an
     * OBIE code at all, is refused `U002`, and is one character from the one that means monthly.
     */
    @Test
    fun `the standing-order body sends the frequency as an object`() = runTest {
        pisp().createDomesticStandingOrderConsent("cc-token", soConsentRequest(), IDEMPOTENCY_KEY)

        val initiation = Json.parseToJsonElement(captured.single().body)
            .jsonObject.getValue("Data").jsonObject.getValue("Initiation").jsonObject
        val frequency = initiation.getValue("MandateRelatedInformation").jsonObject.getValue("Frequency")

        assertEquals("MNTH", frequency.jsonObject.getValue("Type").jsonPrimitive.content)
    }

    /**
     * The empty `Risk` reaches the wire as `{}`, and the merchant fields never appear.
     *
     * This product has no `PaymentContextCode` to derive; HSBC's own collection sends an empty object
     * and the bank echoes one back whatever is supplied.
     */
    @Test
    fun `the standing-order body sends an empty risk block`() = runTest {
        pisp().createDomesticStandingOrderConsent("cc-token", soConsentRequest(), IDEMPOTENCY_KEY)

        val risk = Json.parseToJsonElement(captured.single().body).jsonObject.getValue("Risk").jsonObject
        assertTrue(risk.isEmpty(), "actual: $risk")
    }

    /**
     * Neither standing-order rail sends `LocalInstrument` or the single-payment identifiers.
     *
     * A mandate does not pick a rail — the bank picks one per instalment — and it is not one payment,
     * so it carries neither an instruction id nor an end-to-end id.
     */
    @Test
    fun `the standing-order body sends no local instrument or payment identifiers`() = runTest {
        pisp().createDomesticStandingOrderConsent("cc-token", soConsentRequest(), IDEMPOTENCY_KEY)

        val initiation = Json.parseToJsonElement(captured.single().body)
            .jsonObject.getValue("Data").jsonObject.getValue("Initiation").jsonObject

        assertNull(initiation["LocalInstrument"])
        assertNull(initiation["InstructionIdentification"])
        assertNull(initiation["EndToEndIdentification"])
        assertNull(initiation["InstructedAmount"], "this rail's amount is FirstPaymentAmount")
    }

    // endregion

    private suspend fun pisp(
        financialId: String = "",
        responseBody: String = "{}",
        status: HttpStatusCode = HttpStatusCode.Created,
    ): Pisp {
        val client = HttpClient(
            MockEngine { request ->
                captured += RecordedRequest(
                    method = request.method,
                    path = request.url.encodedPath,
                    headers = listOf(
                        HttpHeaders.Authorization,
                        HEADER_IDEMPOTENCY_KEY,
                        HEADER_JWS_SIGNATURE,
                        HEADER_FAPI_INTERACTION_ID,
                        HEADER_FAPI_FINANCIAL_ID,
                    ).associateWith { request.headers[it] },
                    body = request.body.toByteArray().decodeToString(),
                )
                respond(
                    content = responseBody,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return Pisp(
            httpClient = client,
            kid = KID,
            signingKeyPem = TestSigningKey.pem(),
            financialId = financialId,
            signingIssuer = SIGNING_ISSUER,
        )
    }
}
