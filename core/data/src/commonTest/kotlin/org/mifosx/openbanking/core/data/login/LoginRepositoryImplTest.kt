/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.data.login

import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.mifosx.openbanking.core.data.consentStartJson
import org.mifosx.openbanking.core.data.gatewayClient
import org.mifosx.openbanking.core.data.jsonHeaders
import org.mifosx.openbanking.core.data.login.impl.LoginRepositoryImpl
import org.mifosx.openbanking.core.model.hsbcPermission.PermissionId
import org.mifosx.openbanking.core.network.api.Session
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LoginRepositoryImplTest {

    private val sent = mutableListOf<Triple<HttpMethod, String, String>>()

    private fun buildRepo(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = consentStartJson("consent-123", flowId = "flow-9", state = "state-9"),
    ) = LoginRepositoryImpl(
        Session(
            gatewayClient { request ->
                sent += Triple(request.method, request.url.encodedPath, request.body.toByteArray().decodeToString())
                respond(body, status, jsonHeaders)
            },
        ),
    )

    @Test
    fun `getPermissions returns the 20 HSBC UK Personal supported permissions`() = runTest {
        val permissions = buildRepo().getPermissions()
        // HSBC UK Personal supports exactly 20 AIS permissions. ReadRefundAccount is a valid OBIE
        // scope but NOT one of them — requesting it makes the bank reject the whole consent with
        // 400 U005, so it must never appear in the requested set.
        assertEquals(20, permissions.size)
        assertTrue(permissions.none { it.id == PermissionId.ReadRefundAccount })
    }

    @Test
    fun `starts the sign-in at the worker and returns its authorize url flow and state`() = runTest {
        val result = buildRepo().createConsentAndBuildAuthorizationUrl()

        val consent = assertIs<NetworkResult.Success<ConsentResult>>(result).data
        assertEquals("consent-123", consent.consentId)
        assertEquals("flow-9", consent.flowId)
        assertEquals("state-9", consent.state)
        assertTrue(consent.authorizationUrl.startsWith("https://sandbox.ob.hsbc.co.uk/"))

        val (method, path, body) = sent.single()
        assertEquals(HttpMethod.Post, method)
        assertEquals("/v1/consents/ais", path)
        val permissions = Json.parseToJsonElement(body).jsonObject.getValue("permissions").jsonArray
        assertEquals(20, permissions.size)
    }

    @Test
    fun `a worker refusal is an error`() = runTest {
        val repo = buildRepo(HttpStatusCode.TooManyRequests, """{"error":"rate_limited","retryAfter":60}""")
        val result = repo.createConsentAndBuildAuthorizationUrl()
        assertIs<NetworkError.Client.RateLimited>(assertIs<NetworkResult.Error<NetworkError>>(result).error)
    }

    @Test
    fun `an authorize url without a state is an error and never a sign-in that cannot be matched`() = runTest {
        val repo = buildRepo(
            body = """{"flowId":"f","consentId":"c","authorizeUrl":"https://sandbox.ob.hsbc.co.uk/a"}""",
        )
        assertIs<NetworkResult.Error<NetworkError>>(repo.createConsentAndBuildAuthorizationUrl())
    }
}
