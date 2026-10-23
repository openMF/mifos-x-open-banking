/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.data

import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import org.mifosx.openbanking.core.network.SESSION_KEY
import org.mifosx.openbanking.core.network.attest.NoAttestation
import org.mifosx.openbanking.core.network.gatewayHttpClient

/** A Worker session id in the shape the Worker mints (43 base64url chars). */
internal const val WORKER_SESSION = "SSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS"

internal val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

/** Settings holding a signed-in Worker session. */
internal fun signedInSettings(): Settings = MapSettings(SESSION_KEY to WORKER_SESSION)

/**
 * The production gateway client over a [MockEngine], so tests exercise the real headers and paths.
 * Desktop platform: no attestation round-trip.
 */
internal fun gatewayClient(
    settings: Settings = signedInSettings(),
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
): HttpClient = gatewayHttpClient(settings, NoAttestation("desktop"), MockEngine(handler))

/** The authorize URL the Worker signs; the app only ever reads its `state`. */
internal fun authorizeUrl(state: String = "state-1"): String =
    "https://sandbox.ob.hsbc.co.uk/obie/open-banking/v1.1/oauth2/authorize?client_id=c&state=$state&request=jwt"

/** A Worker consent POST response: `{flowId, consentId, authorizeUrl}`. */
internal fun consentStartJson(consentId: String, flowId: String = "flow-1", state: String = "state-1"): String =
    """{"flowId":"$flowId","consentId":"$consentId","authorizeUrl":"${authorizeUrl(state)}"}"""

/** A Worker `/v1/auth/complete` response. */
internal fun completedAuthJson(
    consentId: String,
    leg: String = "ais",
    session: String = WORKER_SESSION,
    status: String = "Authorised",
    consentExpiresAt: String = "2027-01-20T00:00:00Z",
): String =
    """{"session":"$session","expiresAt":1800000000000,"leg":"$leg","consentId":"$consentId",""" +
        """"consentStatus":"$status","consentExpiresAt":"$consentExpiresAt"}"""
