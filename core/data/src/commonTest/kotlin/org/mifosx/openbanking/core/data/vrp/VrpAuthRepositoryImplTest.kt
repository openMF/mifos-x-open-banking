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

import com.russhwolf.settings.MapSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.mifosx.openbanking.core.data.TestSigningKey
import org.mifosx.openbanking.core.network.api.OAuth
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val TOKEN_URL = "https://secure.example.test/oauth2/token"
private const val CONSENT_ID = "45411"
private const val REDIRECT_URI = "org.mifosx.openbanking://callback/"
private const val REFRESH_TOKEN = "issued-refresh-token"

/**
 * Covers [VrpAuthRepositoryImpl]: the round trip that turns a staged consent into an approved one,
 * and the credential it leaves behind.
 */
@OptIn(ExperimentalEncodingApi::class)
class VrpAuthRepositoryImplTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private class Fixture(
        val repository: VrpAuthRepositoryImpl,
        val session: SettingsVrpAuthSession,
    )

    private suspend fun fixture(tokenBody: String? = null): Fixture {
        val client = HttpClient(
            MockEngine {
                respond(
                    tokenBody ?: """{"access_token":"psu-token","token_type":"Bearer",""" +
                        """"expires_in":299,"refresh_token":"$REFRESH_TOKEN","scope":"openid payments"}""",
                    HttpStatusCode.OK,
                    jsonHeaders,
                )
            },
        ) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val session = SettingsVrpAuthSession(MapSettings())
        val oauth = OAuth(client, TOKEN_URL, "test-client", "test-kid", TestSigningKey.pem())
        return Fixture(
            repository = VrpAuthRepositoryImpl(
                oauth = oauth,
                session = session,
                signingKeyPem = TestSigningKey.pem(),
                clientId = "test-client",
                kid = "test-kid",
                bankHost = "secure.example.test",
                authorizeHost = "authorize.example.test",
                redirectUri = REDIRECT_URI,
            ),
            session = session,
        )
    }

    /** An `id_token` whose `nonce` claim is [nonce]. Only the payload is read. */
    private fun idToken(nonce: String): String {
        val payload = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
            .encode("""{"nonce":"$nonce"}""".encodeToByteArray())
        return "header.$payload.signature"
    }

    private fun redirect(
        state: String?,
        code: String? = "auth-code-1",
        nonce: String? = null,
        error: String? = null,
    ): String = buildString {
        append(REDIRECT_URI)
        append('#')
        val parts = mutableListOf<String>()
        code?.let { parts += "code=$it" }
        state?.let { parts += "state=$it" }
        nonce?.let { parts += "id_token=${idToken(it)}" }
        error?.let { parts += "error=$it" }
        append(parts.joinToString("&"))
    }

    @Test
    fun bindsTheAuthorisationUrlToTheConsentAndRecordsTheRoundTrip() = runTest {
        val f = fixture()

        val result = f.repository.beginAuthorisation(CONSENT_ID)

        val url = assertIs<NetworkResult.Success<String>>(result).data
        assertTrue(url.startsWith("https://authorize.example.test/"))
        assertTrue(url.contains("request="))
        assertEquals(CONSENT_ID, f.session.pendingConsentId())
        assertNotNull(f.session.pendingNonce())
    }

    @Test
    fun doesNotClaimARedirectWhenNothingIsInFlight() = runTest {
        val f = fixture()

        assertFalse(f.repository.isVrpRedirect(redirect(state = "some-other-state")))
    }

    @Test
    fun claimsOnlyARedirectCarryingTheStateItIssued() = runTest {
        val f = fixture()
        val state = stateIn(f.repository.beginAuthorisation(CONSENT_ID).urlOrFail())

        assertTrue(f.repository.isVrpRedirect(redirect(state = state)))
        assertFalse(f.repository.isVrpRedirect(redirect(state = "not-the-issued-state")))
    }

    @Test
    fun reportsNoPendingWhenTheSessionHasBeenCleared() = runTest {
        val f = fixture()

        val outcome = f.repository.validateCallback(redirect(state = "anything"))

        assertIs<VrpAuthValidation.NoPending>(outcome)
    }

    @Test
    fun treatsAStateThatDoesNotMatchAsTampering() = runTest {
        val f = fixture()
        f.repository.beginAuthorisation(CONSENT_ID)

        val outcome = f.repository.validateCallback(redirect(state = "forged-state"))

        assertIs<VrpAuthValidation.SecurityError>(outcome)
    }

    @Test
    fun readsADeclinedAuthorisationAsRefusalNotTampering() = runTest {
        val f = fixture()
        val state = stateIn(f.repository.beginAuthorisation(CONSENT_ID).urlOrFail())

        val outcome = f.repository.validateCallback(
            redirect(state = state, code = null, error = "access_denied"),
        )

        assertIs<VrpAuthValidation.AccessDenied>(outcome)
    }

    @Test
    fun acceptsARedirectCarryingTheIssuedStateAndNonce() = runTest {
        val f = fixture()
        val state = stateIn(f.repository.beginAuthorisation(CONSENT_ID).urlOrFail())

        val outcome = f.repository.validateCallback(
            redirect(state = state, nonce = f.session.pendingNonce()),
        )

        val valid = assertIs<VrpAuthValidation.Valid>(outcome)
        assertEquals("auth-code-1", valid.code)
        assertEquals(CONSENT_ID, valid.consentId)
    }

    @Test
    fun keepsTheRefreshTokenUnderTheConsentItBelongsTo() = runTest {
        val f = fixture()
        f.repository.beginAuthorisation(CONSENT_ID)

        val result = f.repository.exchangeAndPersistCredential("auth-code-1", CONSENT_ID)

        assertIs<NetworkResult.Success<Unit>>(result)
        assertEquals(REFRESH_TOKEN, f.session.refreshToken(CONSENT_ID))
        assertNull(f.session.refreshToken("some-other-consent"))
    }

    @Test
    fun clearsTheRoundTripOnceTheCredentialIsStored() = runTest {
        val f = fixture()
        f.repository.beginAuthorisation(CONSENT_ID)

        f.repository.exchangeAndPersistCredential("auth-code-1", CONSENT_ID)

        assertNull(f.session.pendingConsentId())
    }

    @Test
    fun refusesAnExchangeThatReturnsNoRefreshToken() = runTest {
        val f = fixture(
            tokenBody = """{"access_token":"psu-token","token_type":"Bearer","expires_in":299,""" +
                """"scope":"openid payments"}""",
        )
        f.repository.beginAuthorisation(CONSENT_ID)

        val result = f.repository.exchangeAndPersistCredential("auth-code-1", CONSENT_ID)

        assertIs<NetworkResult.Error<NetworkError>>(result)
        assertNull(f.session.refreshToken(CONSENT_ID))
    }
}

/** The `state` the authorisation URL carries — the value the browser echoes back. */
private fun stateIn(authorizationUrl: String): String =
    authorizationUrl.substringAfter("state=").substringBefore('&')

/** The authorisation URL, or a test failure if the call did not produce one. */
private fun NetworkResult<String, NetworkError>.urlOrFail(): String =
    assertIs<NetworkResult.Success<String>>(this).data
