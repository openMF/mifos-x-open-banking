/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.network

import com.russhwolf.settings.MapSettings
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.mifosx.openbanking.core.network.attest.AppAttestation
import org.mifosx.openbanking.core.network.attest.AttestationBff
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A 43-char base64url id, the shape the Worker mints (32 random bytes). */
internal const val TEST_SESSION = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

/** An attestation double: records what it was asked to sign and answers with a fixed header. */
internal class FakeAttestation(override val platform: String, private val answer: String? = null) : AppAttestation {
    val signed = mutableListOf<String>()
    var challenge: String? = null

    override suspend fun header(clientData: ByteArray, bff: AttestationBff): String? {
        signed += clientData.decodeToString()
        challenge = bff.challenge()
        return answer
    }
}

/** Records every request the client sends, answering each with [respondWith] (path → body). */
internal class Recorder(private val respondWith: (String) -> Pair<HttpStatusCode, String> = { HttpStatusCode.OK to "{}" }) {
    val requests = mutableListOf<Pair<HttpRequestData, String>>()
    val engine = MockEngine { req ->
        requests += req to req.body.toByteArray().decodeToString()
        // The Worker's single-use challenge, which every attested request fetches first.
        val (status, body) = if (req.url.encodedPath == "/v1/attest/challenge") {
            HttpStatusCode.OK to """{"challenge":"ch-0","expiresIn":300}"""
        } else {
            respondWith(req.url.encodedPath)
        }
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
    }
    fun last(path: String): Pair<HttpRequestData, String> = requests.last { it.first.url.encodedPath == path }
}

class BffHttpClientTest {

    @Test
    fun sendsSessionPlatformAndInstanceHeadersToTheWorker() = runTest {
        val settings = MapSettings(SESSION_KEY to TEST_SESSION)
        val rec = Recorder()
        bffHttpClient(settings, FakeAttestation("desktop"), rec.engine).get("/v1/ais/accounts")

        val (req, _) = rec.last("/v1/ais/accounts")
        assertEquals("Session $TEST_SESSION", req.headers[HttpHeaders.Authorization])
        assertEquals("desktop", req.headers[HEADER_CLIENT_PLATFORM])
        assertNotNull(req.headers[HEADER_CLIENT_INSTANCE])
        assertTrue(req.url.toString().startsWith(org.mifosx.openbanking.core.network.config.BffConfig.BFF_BASE_URL))
    }

    @Test
    fun readsTheSessionFreshOnEveryRequest() = runTest {
        val settings = MapSettings()
        val rec = Recorder()
        val client = bffHttpClient(settings, FakeAttestation("android"), rec.engine)

        client.get("/v1/health")
        assertNull(rec.requests.last().first.headers[HttpHeaders.Authorization])

        settings.putString(SESSION_KEY, TEST_SESSION)
        client.get("/v1/health")
        assertEquals("Session $TEST_SESSION", rec.requests.last().first.headers[HttpHeaders.Authorization])
    }

    @Test
    fun theInstanceIdIsInstallScopedAndStable() = runTest {
        val settings = MapSettings()
        val rec = Recorder()
        bffHttpClient(settings, FakeAttestation("ios"), rec.engine).get("/v1/health")
        bffHttpClient(settings, FakeAttestation("ios"), rec.engine).get("/v1/health")

        val ids = rec.requests.map { it.first.headers[HEADER_CLIENT_INSTANCE] }
        assertEquals(1, ids.toSet().size)
        assertEquals(ids.first(), settings.getStringOrNull(INSTANCE_KEY))
    }

    @Test
    fun anAttestedPostCarriesTheAttestationOverMethodPathAndExactBody() = runTest {
        val rec = Recorder()
        val attestation = FakeAttestation("android", answer = "play ch-0.tok")
        val client = bffHttpClient(MapSettings(), attestation, rec.engine)

        client.postJson("/v1/consents/ais", """{"permissions":["ReadAccountsDetail"]}""", attest = true)

        assertEquals(listOf("POST /v1/consents/ais\n{\"permissions\":[\"ReadAccountsDetail\"]}"), attestation.signed)
        assertEquals("ch-0", attestation.challenge)
        val (req, body) = rec.last("/v1/consents/ais")
        assertEquals("play ch-0.tok", req.headers[HEADER_ATTESTATION])
        assertEquals("""{"permissions":["ReadAccountsDetail"]}""", body)
    }

    @Test
    fun webAndDesktopNeverAskForAChallenge() = runTest {
        val rec = Recorder()
        val attestation = FakeAttestation("web", answer = "never")
        bffHttpClient(MapSettings(), attestation, rec.engine).postJson("/v1/consents/ais", "{}", attest = true)

        assertTrue(attestation.signed.isEmpty())
        assertFalse(rec.requests.any { it.first.url.encodedPath == "/v1/attest/challenge" })
        assertNull(rec.last("/v1/consents/ais").first.headers[HEADER_ATTESTATION])
    }

    @Test
    fun aFailedAttestationStillSendsTheRequestWithoutTheHeader() = runTest {
        val rec = Recorder()
        bffHttpClient(MapSettings(), FakeAttestation("ios", answer = null), rec.engine)
            .postJson("/v1/auth/complete", "{}", attest = true)

        assertNull(rec.last("/v1/auth/complete").first.headers[HEADER_ATTESTATION])
    }

    @Test
    fun aWriteCarriesTheIdempotencyKeyAndRefusesOneOverTheObieLimit() = runTest {
        val rec = Recorder()
        val client = bffHttpClient(MapSettings(), FakeAttestation("desktop"), rec.engine)

        client.postJson("/v1/payments/domestic", "{}", idempotencyKey = "k-1")
        assertEquals("k-1", rec.last("/v1/payments/domestic").first.headers[HEADER_IDEMPOTENCY_KEY])

        val tooLong = "x".repeat(41)
        val failure = runCatching { client.postJson("/v1/payments/domestic", "{}", idempotencyKey = tooLong) }
        assertTrue(failure.isFailure)
    }

    @Test
    fun anAbsoluteUrlOffTheWorkerHostCarriesNoSessionOrClientHeaders() = runTest {
        val rec = Recorder()
        bffHttpClient(MapSettings(SESSION_KEY to TEST_SESSION), FakeAttestation("android"), rec.engine)
            .get("https://raw.githubusercontent.com/openMF/mifos-x-open-banking/refs/heads/dev/LICENSE")

        val headers = rec.requests.single().first.headers
        assertNull(headers[HttpHeaders.Authorization])
        assertNull(headers[HEADER_CLIENT_INSTANCE])
        assertNull(headers[HEADER_CLIENT_PLATFORM])
    }

    @Test
    fun neverSendsABankCredential() = runTest {
        val rec = Recorder()
        bffHttpClient(MapSettings(SESSION_KEY to TEST_SESSION), FakeAttestation("desktop"), rec.engine)
            .postJson("/v1/payments/domestic", "{}", idempotencyKey = "k")

        val headers = rec.requests.single().first.headers
        assertFalse(headers.names().any { it.lowercase().startsWith("x-fapi") || it.lowercase() == "x-jws-signature" })
        assertFalse(headers[HttpHeaders.Authorization].orEmpty().startsWith("Bearer"))
    }
}
