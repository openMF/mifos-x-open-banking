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
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.mifosx.openbanking.core.network.FakeAttestation
import org.mifosx.openbanking.core.network.HEADER_ATTESTATION
import org.mifosx.openbanking.core.network.Recorder
import org.mifosx.openbanking.core.network.gatewayHttpClient
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private const val START =
    """{"flowId":"f-1","consentId":"c-1","authorizeUrl":"https://sandbox.ob.hsbc.co.uk/authorize?state=s-1"}"""
private const val COMPLETE =
    """{"session":"sess","expiresAt":1767225600000,"leg":"ais","consentId":"c-1",""" +
        """"consentStatus":"Authorised","consentExpiresAt":"2027-01-01T00:00:00Z"}"""

class SessionTest {

    private fun session(rec: Recorder, platform: String = "android") =
        Session(gatewayHttpClient(MapSettings(), FakeAttestation(platform, answer = "play x.y"), rec.engine))

    @Test
    fun startsAnAisConsentAndReturnsTheWorkerSignedUrlAsIs() = runTest {
        val rec = Recorder { path ->
            if (path == "/v1/consents/ais") HttpStatusCode.OK to START else HttpStatusCode.OK to "{}"
        }

        val result = session(rec).startAisConsent(AisConsentRequest(permissions = listOf("ReadAccountsDetail")))

        val start = assertIs<NetworkResult.Success<ConsentStart>>(result).data
        assertEquals(ConsentStart("f-1", "c-1", "https://sandbox.ob.hsbc.co.uk/authorize?state=s-1"), start)
        val (req, body) = rec.last("/v1/consents/ais")
        assertEquals(HttpMethod.Post, req.method)
        assertEquals("play x.y", req.headers[HEADER_ATTESTATION])
        val json = Json.parseToJsonElement(body).jsonObject
        assertEquals("ReadAccountsDetail", json["permissions"]!!.jsonArray.single().jsonPrimitive.content)
        assertEquals(setOf("permissions"), json.keys) // absent dates are left for the Worker to default
    }

    @Test
    fun completesAuthWithFlowCodeIdTokenAndState() = runTest {
        val rec = Recorder { path ->
            if (path == "/v1/auth/complete") HttpStatusCode.OK to COMPLETE else HttpStatusCode.OK to "{}"
        }

        val result = session(rec).completeAuth(CompleteAuthRequest("f-1", "code", "id.tok.en", "s-1"))

        val done = assertIs<NetworkResult.Success<CompletedAuth>>(result).data
        assertEquals("sess", done.session)
        assertEquals("Authorised", done.consentStatus)
        assertEquals("2027-01-01T00:00:00Z", done.consentExpiresAt)
        val (req, body) = rec.last("/v1/auth/complete")
        assertEquals("play x.y", req.headers[HEADER_ATTESTATION])
        assertEquals("""{"flowId":"f-1","code":"code","idToken":"id.tok.en","state":"s-1"}""", body)
    }

    @Test
    fun aRejectedConsentIsAConflict() = runTest {
        val rec = Recorder { HttpStatusCode.Conflict to """{"error":"consent_rejected"}""" }

        val result = session(rec).completeAuth(CompleteAuthRequest("f", "c", "i", "s"))

        val error = assertIs<NetworkResult.Error<NetworkError>>(result).error
        assertIs<NetworkError.Client.Other>(error)
        assertEquals(409, error.statusCode)
    }

    @Test
    fun logsOutAndOptionallyRevokesAtTheBank() = runTest {
        val rec = Recorder { HttpStatusCode.NoContent to "" }
        val session = session(rec, platform = "desktop")

        assertIs<NetworkResult.Success<Unit>>(session.logout(revoke = true))
        assertEquals("revoke=true", rec.requests.last().first.url.encodedQuery)
        session.logout()
        assertEquals("/v1/session/logout", rec.requests.last().first.url.encodedPath)
        assertEquals("", rec.requests.last().first.url.encodedQuery)
    }
}
