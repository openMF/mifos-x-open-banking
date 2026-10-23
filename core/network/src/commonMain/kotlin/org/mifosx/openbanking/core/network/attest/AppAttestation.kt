/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.network.attest

/**
 * Device attestation for the Worker's `attest: true` routes (every consent POST, `/v1/auth/complete`,
 * the VRP funds check). Bound per platform in `networkPlatformModule`.
 *
 * Wire format (bff/src/attest/gate.ts): both platforms sign over `clientData` = UTF-8
 * `"METHOD /path\n"` followed by the exact body bytes.
 * - Android: `play <challenge>.<token>`, the Play Integrity standard request with
 *   `requestHash = base64url(SHA-256(challenge ‖ clientData))`.
 * - iOS: `ios <keyId>.<base64 assertion>`, an App Attest assertion over `SHA-256(clientData)`, with the
 *   key registered once at `/v1/attest/ios/register`.
 */
interface AppAttestation {
    /** The `X-Client-Platform` header: `android` | `ios` | `desktop` | `web`. */
    val platform: String

    /** The `X-Attestation` header over [clientData], or null when this device cannot attest. */
    suspend fun header(clientData: ByteArray, bff: AttestationBff): String?
}

/** The Worker calls an attestation provider may make. */
interface AttestationBff {
    /** A single-use challenge from `POST /v1/attest/challenge`, or null. */
    suspend fun challenge(): String?

    /** `POST /v1/attest/ios/register`; true when the Worker accepted the key. */
    suspend fun registerIos(keyId: String, attestationObject: String, challenge: String): Boolean
}

/** Desktop and web: no attestation — the Worker gates them on session + rate limits only (GOAL D3). */
class NoAttestation(override val platform: String) : AppAttestation {
    override suspend fun header(clientData: ByteArray, bff: AttestationBff): String? = null
}
