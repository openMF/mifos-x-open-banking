/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.data.callback

import com.russhwolf.settings.MapSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.mifosx.openbanking.core.data.bffClient
import org.mifosx.openbanking.core.data.callback.impl.ConsentCallbackRepositoryImpl
import org.mifosx.openbanking.core.data.completedAuthJson
import org.mifosx.openbanking.core.data.consentStartJson
import org.mifosx.openbanking.core.data.jsonHeaders
import org.mifosx.openbanking.core.data.login.ConsentResult
import org.mifosx.openbanking.core.data.login.impl.LoginRepositoryImpl
import org.mifosx.openbanking.core.model.callback.ConsentStatus
import org.mifosx.openbanking.core.network.api.Aisp
import org.mifosx.openbanking.core.network.api.CompletedAuth
import org.mifosx.openbanking.core.network.api.Session
import template.core.base.common.screen.ScreenState
import template.core.base.network.NetworkResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Drives the whole consent journey across the real seam, with only the Worker faked.
 *
 * Wires the real [LoginRepositoryImpl], real [SettingsPendingAuthStore], real
 * [ConsentCallbackRepositoryImpl] and real [SettingsConsentSession] together and feeds the callback
 * the `state` the Worker's authorize URL carried — the join no unit test can check: whether the values
 * one side produces are the values the other side accepts, and whether the session lands where the
 * BFF client reads it.
 */
class ConsentJourneyIntegrationTest {

    private val settings = MapSettings()
    private var now = 10_000L
    private val pendingAuthStore = SettingsPendingAuthStore(settings) { now }
    private var minted = 0

    private fun client(consentStatus: String = "AUTH") = bffClient(settings) { request ->
        when {
            request.url.encodedPath == "/v1/consents/ais" -> {
                minted++
                respond(consentStartJson(CONSENT_ID, flowId = "flow-$minted", state = "state-$minted"), HttpStatusCode.OK, jsonHeaders)
            }
            request.url.encodedPath == "/v1/auth/complete" ->
                respond(completedAuthJson(CONSENT_ID), HttpStatusCode.OK, jsonHeaders)
            request.url.encodedPath.startsWith("/v1/consents/ais/") ->
                respond(consentJson(consentStatus), HttpStatusCode.OK, jsonHeaders)
            else -> respond("{}", HttpStatusCode.OK, jsonHeaders)
        }
    }

    /** Assembled from parts so no single line exceeds the 120-char limit. */
    private fun consentJson(status: String) =
        """{"Data":{"ConsentId":"$CONSENT_ID","Status":"$status",""" +
            """"CreationDateTime":"","ExpirationDateTime":"","Permissions":[],""" +
            """"StatusUpdateDateTime":"","TransactionFromDateTime":"","TransactionToDateTime":""},""" +
            """"Links":{"Self":""},"Meta":{"TotalPages":1},"Risk":{}}"""

    private fun callbackRepository(client: HttpClient) = ConsentCallbackRepositoryImpl(
        session = Session(client),
        aisp = Aisp(client),
        pendingAuthStore = pendingAuthStore,
    )

    private suspend fun mintConsent(client: HttpClient): ConsentResult {
        val result = LoginRepositoryImpl(Session(client)).createConsentAndBuildAuthorizationUrl()
        return assertIs<NetworkResult.Success<ConsentResult>>(result).data
    }

    /** The redirect the relay hands the app: code, id_token and the authorize URL's state. */
    private fun redirectFor(state: String) =
        "org.mifosx.openbanking://callback?code=$AUTH_CODE&id_token=header.payload.signature&state=$state"

    @Test
    fun `login through callback completes end to end`() = runTest {
        val client = client(consentStatus = "AUTH")
        val consent = mintConsent(client)

        pendingAuthStore.save(consent.state, consent.flowId, consent.consentId)

        val callback = callbackRepository(client)
        val validation = callback.validateCallback(redirectFor(consent.state))

        val valid = assertIs<ValidationResult.Valid>(
            validation,
            "the state the Worker's authorize URL carried must be exactly what the callback accepts",
        )
        assertEquals(AUTH_CODE, valid.code)
        assertEquals(CONSENT_ID, valid.consentId)
        assertEquals("flow-1", valid.flowId)

        val done = assertIs<ScreenState.Content<CompletedAuth>>(callback.exchangeCode(valid)).data
        val consentSession = SettingsConsentSession(settings)
        consentSession.save(done.session)
        assertTrue(consentSession.isActive())

        val polled = assertIs<ScreenState.Content<ConsentStatusResult>>(callback.pollConsentStatus(valid.consentId))
        assertEquals(ConsentStatus.Authorised, polled.data.status)
    }

    @Test
    fun `a redirect replayed after a completed journey is refused`() = runTest {
        val client = client()
        val consent = mintConsent(client)
        pendingAuthStore.save(consent.state, consent.flowId, consent.consentId)
        val callback = callbackRepository(client)
        val redirect = redirectFor(consent.state)

        assertIs<ValidationResult.Valid>(callback.validateCallback(redirect))

        assertIs<ValidationResult.SecurityError>(
            callback.validateCallback(redirect),
            "the pending auth is single-use, so a captured redirect cannot be re-submitted",
        )
    }

    @Test
    fun `a redirect carrying another session's state is refused`() = runTest {
        val client = client()
        val consent = mintConsent(client)
        pendingAuthStore.save(consent.state, consent.flowId, consent.consentId)

        val validation = callbackRepository(client)
            .validateCallback(redirectFor("attacker-state"))

        assertIs<ValidationResult.SecurityError>(validation)
    }

    @Test
    fun `a redirect arriving after the worker's pending flow expired is refused`() = runTest {
        val client = client()
        val consent = mintConsent(client)
        pendingAuthStore.save(consent.state, consent.flowId, consent.consentId)

        now += SettingsPendingAuthStore.TTL_SECONDS + 1
        val validation = callbackRepository(client)
            .validateCallback(redirectFor(consent.state))

        assertIs<ValidationResult.SecurityError>(validation)
        assertNull(pendingAuthStore.consume())
    }

    @Test
    fun `each login gets a fresh state and flow`() = runTest {
        val client = client()

        val first = mintConsent(client)
        val second = mintConsent(client)

        assertNotEquals(first.state, second.state, "a reused state would defeat CSRF protection")
        assertNotEquals(first.flowId, second.flowId, "a reused flow would defeat replay protection")
    }

    @Test
    fun `an awaiting consent surfaces as AwaitingAuthorisation rather than completing`() = runTest {
        val client = client(consentStatus = "AWAU")
        val consent = mintConsent(client)
        pendingAuthStore.save(consent.state, consent.flowId, consent.consentId)
        val callback = callbackRepository(client)
        val valid = assertIs<ValidationResult.Valid>(
            callback.validateCallback(redirectFor(consent.state)),
        )

        val polled = assertIs<ScreenState.Content<ConsentStatusResult>>(callback.pollConsentStatus(valid.consentId))

        assertEquals(ConsentStatus.AwaitingAuthorisation, polled.data.status)
    }

    private companion object {
        const val CONSENT_ID = "consent-123"
        const val AUTH_CODE = "auth-code-xyz"
    }
}
