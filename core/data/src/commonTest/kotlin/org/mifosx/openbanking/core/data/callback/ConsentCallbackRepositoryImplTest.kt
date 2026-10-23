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

import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.mifosx.openbanking.core.data.callback.impl.ConsentCallbackRepositoryImpl
import org.mifosx.openbanking.core.data.completedAuthJson
import org.mifosx.openbanking.core.data.gatewayClient
import org.mifosx.openbanking.core.data.jsonHeaders
import org.mifosx.openbanking.core.model.callback.ConsentStatus
import org.mifosx.openbanking.core.network.api.Aisp
import org.mifosx.openbanking.core.network.api.CompletedAuth
import org.mifosx.openbanking.core.network.api.Session
import template.core.base.common.screen.ScreenState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers [ConsentCallbackRepositoryImpl] — `validateCallback`, the local gate on HSBC's redirect
 * (single-use, state match, error classification), and the two Worker calls behind it. The `id_token`
 * is verified by the Worker (`400 invalid_id_token`); the app only requires it to be present.
 */
class ConsentCallbackRepositoryImplTest {

    private val pendingAuthStore = FakePendingAuthStore()
    private val sent = mutableListOf<Pair<String, String>>()

    private fun buildRepo(
        completeStatus: HttpStatusCode = HttpStatusCode.OK,
        completeBody: String = completedAuthJson("cn-1"),
        consentStatus: String = "Authorised",
        consentHttpStatus: HttpStatusCode = HttpStatusCode.OK,
        consentExpiration: String = "",
    ): ConsentCallbackRepositoryImpl {
        val client = gatewayClient { request ->
            sent += request.url.encodedPath to request.body.toByteArray().decodeToString()
            when {
                request.url.encodedPath == "/v1/auth/complete" -> respond(completeBody, completeStatus, jsonHeaders)
                request.url.encodedPath.startsWith("/v1/consents/ais/") ->
                    respond(consentJson(consentStatus, consentExpiration), consentHttpStatus, jsonHeaders)
                else -> respond("{}", HttpStatusCode.OK, jsonHeaders)
            }
        }
        return ConsentCallbackRepositoryImpl(
            session = Session(client),
            aisp = Aisp(client),
            pendingAuthStore = pendingAuthStore,
        )
    }

    /** Assembled from parts so no single line exceeds the 120-char limit. */
    private fun consentJson(status: String, expiration: String = "") =
        """{"Data":{"ConsentId":"cn-1","Status":"$status",""" +
            """"CreationDateTime":"","ExpirationDateTime":"$expiration","Permissions":[],""" +
            """"StatusUpdateDateTime":"","TransactionFromDateTime":"","TransactionToDateTime":""},""" +
            """"Links":{"Self":""},"Meta":{"TotalPages":1},"Risk":{}}"""

    /** An opaque id_token — the app never opens it; the Worker verifies it. */
    private val idToken = "header.payload.signature"

    private fun savePending(
        state: String = "st-1",
        flowId: String = "fl-1",
        consentId: String = "cn-1",
    ) = pendingAuthStore.save(state, flowId, consentId)

    private fun callbackUrl(
        code: String? = "auth-code",
        state: String? = "st-1",
        idToken: String? = this.idToken,
        error: String? = null,
        errorDescription: String? = null,
    ): String {
        val parts = buildList {
            code?.let { add("code=$it") }
            state?.let { add("state=$it") }
            idToken?.let { add("id_token=$it") }
            error?.let { add("error=$it") }
            errorDescription?.let { add("error_description=$it") }
        }
        return "org.mifosx.openbanking://callback?" + parts.joinToString("&")
    }

    @Test
    fun `validateCallback returns Valid with everything auth complete needs`() = runTest {
        savePending(flowId = "fl-1", consentId = "cn-1")

        val result = buildRepo().validateCallback(callbackUrl(code = "auth-code"))

        val valid = assertIs<ValidationResult.Valid>(result)
        assertEquals("auth-code", valid.code)
        assertEquals("cn-1", valid.consentId)
        assertEquals(idToken, valid.idToken)
        assertEquals("st-1", valid.state)
        assertEquals("fl-1", valid.flowId)
    }

    @Test
    fun `validateCallback returns SecurityError when no pending auth was stored`() = runTest {
        assertIs<ValidationResult.SecurityError>(buildRepo().validateCallback(callbackUrl()))
    }

    @Test
    fun `validateCallback returns SecurityError for state mismatch`() = runTest {
        savePending(state = "st-expected")

        assertIs<ValidationResult.SecurityError>(
            buildRepo().validateCallback(callbackUrl(state = "st-attacker")),
        )
    }

    @Test
    fun `validateCallback returns SecurityError when the id_token is absent entirely`() = runTest {
        savePending()

        assertIs<ValidationResult.SecurityError>(buildRepo().validateCallback(callbackUrl(idToken = null)))
    }

    @Test
    fun `validateCallback returns AccessDenied when the psu declines`() = runTest {
        savePending()

        assertIs<ValidationResult.AccessDenied>(
            buildRepo().validateCallback(callbackUrl(code = null, error = "access_denied")),
        )
    }

    @Test
    fun `AccessDenied is reported even though a declined consent carries no id_token`() = runTest {
        savePending()

        val result = buildRepo().validateCallback(
            callbackUrl(code = null, idToken = null, error = "access_denied"),
        )

        assertIs<ValidationResult.AccessDenied>(
            result,
            "errors must be classified before the id_token check, or a plain refusal surfaces as a " +
                "SecurityError",
        )
    }

    @Test
    fun `validateCallback surfaces the error description for a non access_denied error`() = runTest {
        savePending()

        val result = buildRepo().validateCallback(
            callbackUrl(code = null, error = "server_error", errorDescription = "HSBC%20is%20down"),
        )

        assertEquals("HSBC is down", assertIs<ValidationResult.Error>(result).message)
    }

    @Test
    fun `validateCallback falls back to the error code when no description is given`() = runTest {
        savePending()

        val result = buildRepo().validateCallback(callbackUrl(code = null, error = "server_error"))

        assertTrue(assertIs<ValidationResult.Error>(result).message.contains("server_error"))
    }

    @Test
    fun `validateCallback returns MissingCode when the response has no code`() = runTest {
        savePending()

        assertIs<ValidationResult.MissingCode>(
            buildRepo().validateCallback(callbackUrl(code = null)),
        )
    }

    @Test
    fun `validateCallback consumes the pending auth so a replay is refused`() = runTest {
        savePending()
        val repo = buildRepo()
        val url = callbackUrl()

        assertIs<ValidationResult.Valid>(repo.validateCallback(url))
        assertIs<ValidationResult.SecurityError>(
            repo.validateCallback(url),
            "the same redirect must not validate twice",
        )
    }

    @Test
    fun `validateCallback consumes the pending auth even when validation fails`() = runTest {
        savePending(state = "st-expected")

        buildRepo().validateCallback(callbackUrl(state = "st-attacker"))

        assertNull(pendingAuthStore.consume(), "a failed attempt must not leave a reusable entry")
    }

    @Test
    fun `validateCallback reads the fragment form delivered by the mobile custom scheme`() = runTest {
        savePending()

        val result = buildRepo().validateCallback(
            "org.mifosx.openbanking://callback#code=auth-code&state=st-1&id_token=$idToken",
        )

        assertIs<ValidationResult.Valid>(result)
    }

    @Test
    fun `exchangeCode redeems the validated redirect at the worker for a session`() = runTest {
        savePending(flowId = "fl-1")
        val repo = buildRepo()
        val valid = assertIs<ValidationResult.Valid>(repo.validateCallback(callbackUrl(code = "auth-code")))

        val result = repo.exchangeCode(valid)

        val done = assertIs<ScreenState.Content<CompletedAuth>>(result).data
        assertEquals("cn-1", done.consentId)
        assertEquals("Authorised", done.consentStatus)
        val (path, body) = sent.single { it.first == "/v1/auth/complete" }
        assertEquals("/v1/auth/complete", path)
        assertEquals("""{"flowId":"fl-1","code":"auth-code","idToken":"$idToken","state":"st-1"}""", body)
    }

    @Test
    fun `exchangeCode returns Error when the worker refuses the id_token`() = runTest {
        val repo = buildRepo(
            completeStatus = HttpStatusCode.BadRequest,
            completeBody = """{"error":"invalid_id_token"}""",
        )

        val error = assertIs<ScreenState.Error>(
            repo.exchangeCode(ValidationResult.Valid("c", "cn-1", idToken, "st-1", "fl-1")),
        )
        assertIs<CallbackSecurityRejected>(error.error)
    }

    @Test
    fun `exchangeCode treats a state mismatch at the worker as a security rejection`() = runTest {
        val repo = buildRepo(
            completeStatus = HttpStatusCode.BadRequest,
            completeBody = """{"error":"state_mismatch"}""",
        )

        val error = assertIs<ScreenState.Error>(
            repo.exchangeCode(ValidationResult.Valid("c", "cn-1", idToken, "st-1", "fl-1")),
        )
        assertIs<CallbackSecurityRejected>(error.error)
    }

    @Test
    fun `a rejected consent never yields a session`() = runTest {
        val repo = buildRepo(
            completeStatus = HttpStatusCode.Conflict,
            completeBody = """{"error":"consent_rejected"}""",
        )

        val error = assertIs<ScreenState.Error>(
            repo.exchangeCode(ValidationResult.Valid("c", "cn-1", idToken, "st-1", "fl-1")),
        )
        assertIs<ConsentRejectedAtBank>(error.error)
    }

    @Test
    fun `pollConsentStatus maps an authorised consent to Content`() = runTest {
        val polled = buildRepo(consentStatus = "Authorised").pollConsentStatus("cn-1")

        val content = assertIs<ScreenState.Content<ConsentStatusResult>>(polled)
        assertEquals(ConsentStatus.Authorised, content.data.status)
    }

    @Test
    fun `pollConsentStatus maps an awaiting consent to Content`() = runTest {
        val polled = buildRepo(consentStatus = "AwaitingAuthorisation").pollConsentStatus("cn-1")

        val content = assertIs<ScreenState.Content<ConsentStatusResult>>(polled)
        assertEquals(ConsentStatus.AwaitingAuthorisation, content.data.status)
    }

    @Test
    fun `pollConsentStatus surfaces the consent expiry alongside the status`() = runTest {
        val polled = buildRepo(consentStatus = "Authorised", consentExpiration = "2026-07-01T00:00:00Z")
            .pollConsentStatus("cn-1")

        val content = assertIs<ScreenState.Content<ConsentStatusResult>>(polled)
        assertEquals(ConsentStatus.Authorised, content.data.status)
        assertEquals("2026-07-01T00:00:00Z", content.data.expirationDateTime)
    }

    @Test
    fun `pollConsentStatus returns Error when the consent lookup fails`() = runTest {
        val repo = buildRepo(consentHttpStatus = HttpStatusCode.InternalServerError)

        assertIs<ScreenState.Error>(repo.pollConsentStatus("cn-1"))
    }

    @Test
    fun `pollConsentStatus returns Unauthenticated when the consent lookup is rejected`() = runTest {
        val repo = buildRepo(consentHttpStatus = HttpStatusCode.Unauthorized)

        assertIs<ScreenState.Unauthenticated>(repo.pollConsentStatus("cn-1"))
    }
}
