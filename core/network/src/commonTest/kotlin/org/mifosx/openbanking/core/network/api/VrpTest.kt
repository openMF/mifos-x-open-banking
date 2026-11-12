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

import com.russhwolf.settings.MapSettings
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.mifosx.openbanking.core.network.FakeAttestation
import org.mifosx.openbanking.core.network.HEADER_ATTESTATION
import org.mifosx.openbanking.core.network.HEADER_IDEMPOTENCY_KEY
import org.mifosx.openbanking.core.network.Recorder
import org.mifosx.openbanking.core.network.SESSION_KEY
import org.mifosx.openbanking.core.network.TEST_SESSION
import org.mifosx.openbanking.core.network.gatewayHttpClient
import org.mifosx.openbanking.core.network.model.vrp.checkFunds.request.CheckFunds
import org.mifosx.openbanking.core.network.model.vrp.createConsent.request.CreateConsent
import org.mifosx.openbanking.core.network.model.vrp.initiatePayment.request.InitiatePayment
import org.mifosx.openbanking.core.network.pisp.obieBody
import org.mifosx.openbanking.core.network.pisp.obieJson
import template.core.base.network.NetworkResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

private const val START =
    """{"flowId":"f","consentId":"vrp-1","authorizeUrl":"https://sandbox.ob.hsbc.co.uk/a?state=s"}"""

private val CONSENT_BODY = obieJson.decodeFromString(
    CreateConsent.serializer(),
    """{"Data":{"ControlParameters":{"MaximumIndividualAmount":{"Amount":"10.00","Currency":"GBP"},""" +
        """"PSUAuthenticationMethods":["UK.OBIE.SCANotRequired"],""" +
        """"PeriodicLimits":[{"Amount":"100.00","Currency":"GBP",""" +
        """"PeriodAlignment":"Consent","PeriodType":"Month"}],"VRPType":["UK.OBIE.VRPType.Sweeping"]},""" +
        """"Initiation":{"CreditorAccount":{"SchemeName":"UK.OBIE.SortCodeAccountNumber",""" +
        """"Identification":"40120965872310",""" +
        """"Name":"Jameson Lettings"}}},"Risk":{}}""",
)
private val FUNDS_BODY = obieJson.decodeFromString(
    CheckFunds.serializer(),
    """{"Data":{"ConsentId":"vrp-1","InstructedAmount":{"Amount":"5.00","Currency":"GBP"}}}""",
)
private val PAYMENT_BODY = obieJson.decodeFromString(
    InitiatePayment.serializer(),
    """{"Data":{"ConsentId":"vrp-1","Initiation":{},"Instruction":{"CreditorAccount":{""" +
        """"SchemeName":"UK.OBIE.SortCodeAccountNumber",""" +
        """"Identification":"40120965872310","Name":"Jameson Lettings"},""" +
        """"EndToEndIdentification":"e2e","InstructedAmount":""" +
        """{"Amount":"5.00","Currency":"GBP"},"InstructionIdentification":"i1"},""" +
        """"PSUAuthenticationMethod":"UK.OBIE.SCANotRequired",""" +
        """"VRPType":"UK.OBIE.VRPType.Sweeping"},"Risk":{}}""",
)

class VrpTest {

    private val rec = Recorder { path ->
        if (path == "/v1/consents/vrp") HttpStatusCode.OK to START else HttpStatusCode.OK to "{}"
    }
    private val vrp = Vrp(
        gatewayHttpClient(
            MapSettings(SESSION_KEY to TEST_SESSION),
            FakeAttestation("ios", answer = "ios k.a"),
            rec.engine,
        ),
    )

    @Test
    fun createsAConsentThroughTheWorkerWithItsKeyAndAttestation() = runTest {
        val body = CONSENT_BODY

        val result = vrp.createConsent(body, "idem-1")

        assertEquals("vrp-1", assertIs<NetworkResult.Success<ConsentStart>>(result).data.consentId)
        val (req, sent) = rec.last("/v1/consents/vrp")
        assertEquals(HttpMethod.Post, req.method)
        assertEquals("idem-1", req.headers[HEADER_IDEMPOTENCY_KEY])
        assertEquals("ios k.a", req.headers[HEADER_ATTESTATION])
        assertEquals(obieBody(CreateConsent.serializer(), body).toString(), sent)
    }

    @Test
    fun theFundsCheckIsAnAttestedSignedPostOnTheConsent() = runTest {
        vrp.checkFunds("vrp-1", FUNDS_BODY, "funds-1")

        val (req, _) = rec.last("/v1/consents/vrp/vrp-1/funds-confirmation")
        assertEquals(HttpMethod.Post, req.method)
        assertEquals("funds-1", req.headers[HEADER_IDEMPOTENCY_KEY])
        assertEquals("ios k.a", req.headers[HEADER_ATTESTATION])
    }

    @Test
    fun aPaymentIsAPlainSessionPost() = runTest {
        vrp.createPayment(PAYMENT_BODY, "pay-1")

        val (req, _) = rec.last("/v1/vrp/payments")
        assertEquals("pay-1", req.headers[HEADER_IDEMPOTENCY_KEY])
        assertEquals("Session $TEST_SESSION", req.headers[HttpHeaders.Authorization])
        assertNull(req.headers[HEADER_ATTESTATION])
    }

    @Test
    fun readsAndRevocationAreSessionScoped() = runTest {
        val lastMethodAndPath = { rec.requests.last().first.let { it.method to it.url.encodedPath } }
        vrp.getConsent("vrp-1")
        assertEquals(HttpMethod.Get to "/v1/consents/vrp/vrp-1", lastMethodAndPath())
        vrp.deleteConsent("vrp-1")
        assertEquals(HttpMethod.Delete to "/v1/consents/vrp/vrp-1", lastMethodAndPath())
        vrp.getPayment("p-9")
        assertEquals(HttpMethod.Get to "/v1/vrp/payments/p-9", lastMethodAndPath())
    }
}
