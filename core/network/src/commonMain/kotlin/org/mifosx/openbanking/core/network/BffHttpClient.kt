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

import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.plugin
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.content.TextContent
import io.ktor.http.encodedPath
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.AttributeKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.mifosx.openbanking.core.network.attest.AppAttestation
import org.mifosx.openbanking.core.network.attest.AttestationBff
import org.mifosx.openbanking.core.network.config.BffConfig
import template.core.base.network.httpClient
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import co.touchlab.kermit.Logger.Companion as KermitLogger

/**
 * The opaque Worker session — the ONLY credential the app holds. Written by the sign-in callback
 * (core/data `ConsentSession`), read fresh on every request here. There is no other token store.
 */
const val SESSION_KEY = "bff_session"

/** Install-scoped random id; the Worker keys its consent / sign-in rate limits on it. Not a secret. */
const val INSTANCE_KEY = "bff_client_instance"

const val HEADER_CLIENT_PLATFORM = "X-Client-Platform"
const val HEADER_CLIENT_INSTANCE = "X-Client-Instance"
const val HEADER_ATTESTATION = "X-Attestation"
const val HEADER_IDEMPOTENCY_KEY = "X-Idempotency-Key"

/** OBIE caps the idempotency key at 40 characters; the Worker refuses anything longer. */
internal const val IDEMPOTENCY_KEY_MAX_LENGTH = 40

private const val REQUEST_TIMEOUT_MS = 60_000L
private const val CHALLENGE_PATH = "/v1/attest/challenge"
private const val IOS_REGISTER_PATH = "/v1/attest/ios/register"

/** The platforms the Worker attests (attest/gate.ts); desktop and web run on session + rate limits. */
private val ATTESTED_PLATFORMS = setOf("android", "ios")

/** Marks a request whose route is `attest: true` in the Worker's route table. */
internal val Attested = AttributeKey<Unit>("bff-attested")

/**
 * The app's single HTTP client: plain HTTPS to the Cloudflare Worker BFF.
 *
 * No mTLS, no bank token, no refresh interceptor — the Worker holds the TPP identity and every HSBC
 * token. Each request carries `Authorization: Session <id>` (read fresh from [settings]),
 * `X-Client-Platform` and `X-Client-Instance`; requests flagged [Attested] also carry
 * `X-Attestation` over `"METHOD /path\n" + body` on Android / iOS.
 *
 * @param engine test seam only; production uses the platform engine from core-base.
 */
fun bffHttpClient(
    settings: Settings,
    attestation: AppAttestation,
    engine: HttpClientEngine? = null,
): HttpClient {
    val instance = clientInstance(settings)
    val config: HttpClientConfig<*>.() -> Unit = {
        install(ContentNegotiation) {
            json(Json { isLenient = true; ignoreUnknownKeys = true; explicitNulls = false })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            socketTimeoutMillis = REQUEST_TIMEOUT_MS
        }
        // INFO: method, URL and status only — never headers (the session) or bodies (auth codes).
        install(Logging) {
            level = LogLevel.INFO
            logger = object : Logger {
                override fun log(message: String) = KermitLogger.i(tag = "BffClient", messageString = message)
            }
        }
        defaultRequest {
            url(BffConfig.BFF_BASE_URL)
            header(HttpHeaders.Accept, "application/json")
        }
    }
    val client = engine?.let { HttpClient(it, config) } ?: httpClient(config)
    client.installWorkerHeaders(settings, attestation, instance)
    return client
}

@OptIn(ExperimentalUuidApi::class)
private fun clientInstance(settings: Settings): String =
    settings.getStringOrNull(INSTANCE_KEY)
        ?: Uuid.random().toString().also { settings.putString(INSTANCE_KEY, it) }

/**
 * Adds the Worker headers on the final URL, and only when it is the Worker's host: the same client
 * also fetches absolute URLs elsewhere (the licence text), which must never see the session.
 *
 * `X-Attestation` goes on [Attested] requests from Android / iOS. A device that cannot attest (no
 * credential yet, emulator, simulator) sends the request without it; the Worker's
 * `ATTESTATION_MODE` decides whether that is refused.
 */
private fun HttpClient.installWorkerHeaders(settings: Settings, attestation: AppAttestation, instance: String) {
    val workerHost = Url(BffConfig.BFF_BASE_URL).host
    val bff = WorkerAttestationBff(this)
    plugin(HttpSend).intercept { request ->
        if (request.url.host == workerHost) {
            request.headers[HEADER_CLIENT_PLATFORM] = attestation.platform
            request.headers[HEADER_CLIENT_INSTANCE] = instance
            settings.getStringOrNull(SESSION_KEY)?.let { request.headers[HttpHeaders.Authorization] = "Session $it" }
            if (request.attributes.contains(Attested) && attestation.platform in ATTESTED_PLATFORMS) {
                val body = (request.body as? TextContent)?.text.orEmpty()
                val clientData = "${request.method.value} ${request.url.encodedPath}\n$body".encodeToByteArray()
                runCatching { attestation.header(clientData, bff) }.getOrNull()
                    ?.let { request.headers[HEADER_ATTESTATION] = it }
            }
        }
        execute(request)
    }
}

@Serializable
private data class Challenge(val challenge: String)

@Serializable
private data class IosRegistration(val keyId: String, val attestationObject: String, val challenge: String)

/** The two unattested Worker calls attestation itself needs. */
private class WorkerAttestationBff(private val client: HttpClient) : AttestationBff {
    override suspend fun challenge(): String? {
        val res = client.post(CHALLENGE_PATH)
        return if (res.status.isSuccess()) res.body<Challenge>().challenge else null
    }

    override suspend fun registerIos(keyId: String, attestationObject: String, challenge: String): Boolean =
        client.postJson(IOS_REGISTER_PATH, Json.encodeToString(IosRegistration(keyId, attestationObject, challenge)))
            .status.isSuccess()
}

/**
 * POSTs [body] — already-serialised compact JSON — byte-for-byte. The Worker signs exactly these
 * bytes for HSBC and refuses a non-compact body, so content negotiation must not re-serialise it.
 */
internal suspend fun HttpClient.postJson(
    path: String,
    body: String,
    attest: Boolean = false,
    idempotencyKey: String? = null,
): HttpResponse {
    idempotencyKey?.let {
        require(it.isNotBlank() && it.length <= IDEMPOTENCY_KEY_MAX_LENGTH) {
            "X-Idempotency-Key must be 1..$IDEMPOTENCY_KEY_MAX_LENGTH characters, was ${it.length}"
        }
    }
    return post(path) {
        if (attest) attributes.put(Attested, Unit)
        idempotencyKey?.let { header(HEADER_IDEMPOTENCY_KEY, it) }
        setBody(TextContent(body, ContentType.Application.Json))
    }
}
