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
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.mifosx.openbanking.core.data.authorizeUrl
import org.mifosx.openbanking.core.data.bffClient
import org.mifosx.openbanking.core.data.completedAuthJson
import org.mifosx.openbanking.core.data.jsonHeaders
import org.mifosx.openbanking.core.network.api.Session
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val CONSENT_ID = "45411"
private const val STATE = "issued-state"
private const val FLOW_ID = "flow-vrp"
private const val REDIRECT_URI = "org.mifosx.openbanking://callback/"

/**
 * Covers [VrpAuthRepositoryImpl]: the round trip that turns a staged consent into an approved one.
 * The Worker keeps the consent's credential; the app only redeems the redirect.
 */
class VrpAuthRepositoryImplTest {

    private class Fixture(
        val repository: VrpAuthRepositoryImpl,
        val session: SettingsVrpAuthSession,
        val bodies: MutableList<Pair<String, String>>,
    )

    private fun fixture(status: HttpStatusCode = HttpStatusCode.OK, staged: Boolean = true): Fixture {
        val bodies = mutableListOf<Pair<String, String>>()
        val client = bffClient { req ->
            bodies += req.url.encodedPath to req.body.toByteArray().decodeToString()
            respond(completedAuthJson(CONSENT_ID, leg = "vrp"), status, jsonHeaders)
        }
        val session = SettingsVrpAuthSession(MapSettings())
        if (staged) session.saveAuthorisationInFlight(CONSENT_ID, STATE, FLOW_ID, authorizeUrl(STATE))
        return Fixture(VrpAuthRepositoryImpl(bff = Session(client), session = session), session, bodies)
    }

    private fun redirect(
        state: String?,
        code: String? = "auth-code-1",
        idToken: String? = "h.p.s",
        error: String? = null,
    ): String = REDIRECT_URI + "#" + listOfNotNull(
        code?.let { "code=$it" },
        state?.let { "state=$it" },
        idToken?.let { "id_token=$it" },
        error?.let { "error=$it" },
    ).joinToString("&")

    @Test
    fun returnsTheWorkerSignedUrlRecordedWhenTheConsentWasStaged() = runTest {
        val f = fixture()

        assertEquals(authorizeUrl(STATE), assertIs<NetworkResult.Success<String>>(f.repository.beginAuthorisation(CONSENT_ID)).data)
        assertIs<NetworkResult.Error<NetworkError>>(f.repository.beginAuthorisation("some-other-consent"))
    }

    @Test
    fun doesNotClaimARedirectWhenNothingIsInFlight() = runTest {
        assertFalse(fixture(staged = false).repository.isVrpRedirect(redirect(state = STATE)))
    }

    @Test
    fun claimsOnlyARedirectCarryingTheStateItIssued() = runTest {
        val f = fixture()

        assertTrue(f.repository.isVrpRedirect(redirect(state = STATE)))
        assertFalse(f.repository.isVrpRedirect(redirect(state = "not-the-issued-state")))
    }

    @Test
    fun reportsNoPendingWhenTheSessionHasBeenCleared() = runTest {
        assertIs<VrpAuthValidation.NoPending>(fixture(staged = false).repository.validateCallback(redirect(state = STATE)))
    }

    @Test
    fun treatsAStateThatDoesNotMatchAsTampering() = runTest {
        assertIs<VrpAuthValidation.SecurityError>(fixture().repository.validateCallback(redirect(state = "forged-state")))
    }

    @Test
    fun treatsAMissingIdTokenAsTampering() = runTest {
        assertIs<VrpAuthValidation.SecurityError>(fixture().repository.validateCallback(redirect(state = STATE, idToken = null)))
    }

    @Test
    fun readsADeclinedAuthorisationAsRefusalNotTampering() = runTest {
        val outcome = fixture().repository.validateCallback(
            redirect(state = STATE, code = null, idToken = null, error = "access_denied"),
        )

        assertIs<VrpAuthValidation.AccessDenied>(outcome)
    }

    @Test
    fun acceptsARedirectCarryingTheIssuedState() = runTest {
        val valid = assertIs<VrpAuthValidation.Valid>(fixture().repository.validateCallback(redirect(state = STATE)))

        assertEquals("auth-code-1", valid.code)
        assertEquals(CONSENT_ID, valid.consentId)
    }

    @Test
    fun redeemsTheValidatedRedirectAtTheWorkerAndClearsTheRoundTrip() = runTest {
        val f = fixture()
        f.repository.validateCallback(redirect(state = STATE))

        val result = f.repository.exchangeAndPersistCredential("auth-code-1", CONSENT_ID)

        assertIs<NetworkResult.Success<Unit>>(result)
        val (path, body) = f.bodies.single()
        assertEquals("/v1/auth/complete", path)
        assertEquals("""{"flowId":"$FLOW_ID","code":"auth-code-1","idToken":"h.p.s","state":"$STATE"}""", body)
        assertNull(f.session.pendingConsentId())
    }

    @Test
    fun refusesAnExchangeWithoutAValidatedRedirect() = runTest {
        val f = fixture()

        assertIs<NetworkResult.Error<NetworkError>>(f.repository.exchangeAndPersistCredential("auth-code-1", CONSENT_ID))
        assertTrue(f.bodies.isEmpty())
    }

    @Test
    fun keepsTheRoundTripWhenTheWorkerRefusesTheExchange() = runTest {
        val f = fixture(status = HttpStatusCode.BadRequest)
        f.repository.validateCallback(redirect(state = STATE))

        assertIs<NetworkResult.Error<NetworkError>>(f.repository.exchangeAndPersistCredential("auth-code-1", CONSENT_ID))
        assertEquals(CONSENT_ID, f.session.pendingConsentId())
    }
}
