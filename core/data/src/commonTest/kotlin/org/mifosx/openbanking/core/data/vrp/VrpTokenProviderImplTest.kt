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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private const val TOKEN_URL = "https://secure.example.test/oauth2/token"
private const val TOKEN_LIFETIME_SECONDS = 299

class VrpTokenProviderImplTest {

    private class FixedClock(var instant: Instant) : Clock {
        override fun now(): Instant = instant
    }

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun tokenJson(accessToken: String, refreshToken: String = "returned-refresh") = """
        {
          "access_token": "$accessToken",
          "token_type": "Bearer",
          "expires_in": $TOKEN_LIFETIME_SECONDS,
          "refresh_token": "$refreshToken",
          "scope": "openid payments"
        }
    """.trimIndent()

    private suspend fun provider(
        settings: MapSettings = MapSettings(),
        clock: FixedClock = FixedClock(Instant.fromEpochSeconds(0)),
        respondWith: (Int) -> String = { tokenJson("access-$it") },
    ): Triple<VrpTokenProviderImpl, MapSettings, MutableList<String>> {
        val calls = mutableListOf<String>()
        val client = HttpClient(
            MockEngine { request ->
                calls += request.url.encodedPath
                respond(respondWith(calls.size), HttpStatusCode.OK, jsonHeaders)
            },
        ) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val oauth = OAuth(client, TOKEN_URL, "test-client", "test-kid", TestSigningKey.pem())
        val session = SettingsVrpAuthSession(secureSettings = settings)
        return Triple(VrpTokenProviderImpl(oauth, session, clock), settings, calls)
    }

    @Test
    fun holdsTheAccessTokenUntilItExpires() = runTest {
        val (tokens, settings, calls) = provider()
        SettingsVrpAuthSession(settings).saveRefreshToken("45365", "stored-refresh")

        val first = tokens.accessToken("45365")
        val second = tokens.accessToken("45365")

        assertIs<NetworkResult.Success<String>>(first)
        assertIs<NetworkResult.Success<String>>(second)
        assertEquals(first.data, second.data)
        assertEquals(1, calls.size)
    }

    @Test
    fun redeemsAgainOnceTheHeldTokenHasExpired() = runTest {
        val clock = FixedClock(Instant.fromEpochSeconds(0))
        val (tokens, settings, calls) = provider(clock = clock)
        SettingsVrpAuthSession(settings).saveRefreshToken("45365", "stored-refresh")

        val first = tokens.accessToken("45365")
        clock.instant = clock.instant + TOKEN_LIFETIME_SECONDS.seconds
        val second = tokens.accessToken("45365")

        assertIs<NetworkResult.Success<String>>(first)
        assertIs<NetworkResult.Success<String>>(second)
        assertEquals("access-1", first.data)
        assertEquals("access-2", second.data)
        assertEquals(2, calls.size)
    }

    @Test
    fun holdsATokenPerConsent() = runTest {
        val (tokens, settings, _) = provider()
        val session = SettingsVrpAuthSession(settings)
        session.saveRefreshToken("45365", "refresh-a")
        session.saveRefreshToken("45366", "refresh-b")

        val first = tokens.accessToken("45365")
        val second = tokens.accessToken("45366")

        assertIs<NetworkResult.Success<String>>(first)
        assertIs<NetworkResult.Success<String>>(second)
        assertEquals("access-1", first.data)
        assertEquals("access-2", second.data)
    }

    @Test
    fun leavesTheStoredRefreshTokenAsItWas() = runTest {
        val (tokens, settings, _) = provider(
            respondWith = { tokenJson("access-$it", refreshToken = "rotated-$it") },
        )
        val session = SettingsVrpAuthSession(settings)
        session.saveRefreshToken("45365", "stored-refresh")

        tokens.accessToken("45365")

        assertEquals("stored-refresh", session.refreshToken("45365"))
    }

    @Test
    fun refreshAccessTokenRedeemsEvenWhenAHeldTokenIsStillValid() = runTest {
        val (tokens, settings, calls) = provider()
        SettingsVrpAuthSession(settings).saveRefreshToken("45365", "stored-refresh")

        tokens.accessToken("45365")
        val refreshed = tokens.refreshAccessToken("45365")

        assertIs<NetworkResult.Success<String>>(refreshed)
        assertEquals("access-2", refreshed.data)
        assertEquals(2, calls.size)
    }

    @Test
    fun redeemsAgainAfterTheHeldTokenIsInvalidated() = runTest {
        val (tokens, settings, calls) = provider()
        SettingsVrpAuthSession(settings).saveRefreshToken("45365", "stored-refresh")

        tokens.accessToken("45365")
        tokens.invalidateAccessToken("45365")
        tokens.accessToken("45365")

        assertEquals(2, calls.size)
    }

    @Test
    fun failsWhenTheConsentHasNoStoredCredential() = runTest {
        val (tokens, _, calls) = provider()

        val result = tokens.accessToken("45365")

        assertIs<NetworkResult.Error<NetworkError>>(result)
        assertIs<NetworkError.Client.Unauthorized>(result.error)
        assertEquals(0, calls.size)
    }
}
