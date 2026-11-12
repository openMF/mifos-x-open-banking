/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.network.attest

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.DeviceCheck.DCAppAttestService
import platform.Foundation.NSData
import platform.Foundation.NSUserDefaults
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.create
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private const val KEY_ID = "gateway_app_attest_key_id"

/**
 * App Attest (DeviceCheck). The key is generated and registered once
 * (`POST /v1/attest/ios/register` with `attestKey(keyId, SHA-256(challenge))`); every attested request
 * then sends `X-Attestation: ios <keyId>.<base64 assertion>` over `SHA-256(clientData)`, as
 * bank-gateway/src/attest/appAttest.ts verifies. Unsupported devices (the simulator) return null.
 */
@OptIn(ExperimentalForeignApi::class)
class AppAttestAttestation : AppAttestation {
    override val platform: String = "ios"

    private val service = DCAppAttestService.sharedService
    private val defaults = NSUserDefaults.standardUserDefaults
    private val lock = Mutex()

    override suspend fun header(clientData: ByteArray, gateway: AttestationGateway): String? {
        val keyId = takeIf { service.isSupported() }
            ?.let { lock.withLock { defaults.stringForKey(KEY_ID) ?: register(gateway) } }
            ?: return null
        return suspendCoroutine<NSData?> { c ->
            service.generateAssertion(keyId, sha256(clientData)) { data, error ->
                c.resume(data.takeIf { error == null })
            }
        }?.let { "ios $keyId.${it.base64EncodedStringWithOptions(0u)}" }
    }

    private suspend fun register(gateway: AttestationGateway): String? {
        val challenge = gateway.challenge() ?: return null
        return attestAndRegister(challenge, gateway)?.also { defaults.setObject(it, KEY_ID) }
    }

    private suspend fun attestAndRegister(challenge: String, gateway: AttestationGateway): String? {
        val keyId = suspendCoroutine<String?> { c ->
            service.generateKeyWithCompletionHandler { id, error -> c.resume(id.takeIf { error == null }) }
        } ?: return null
        val attest64 = suspendCoroutine<NSData?> { c ->
            service.attestKey(keyId, sha256(challenge.encodeToByteArray())) { data, error ->
                c.resume(data.takeIf { error == null })
            }
        }?.base64EncodedStringWithOptions(0u)
        return attest64?.takeIf { gateway.registerIos(keyId, it, challenge) }?.let { keyId }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun sha256(bytes: ByteArray): NSData {
    val out = UByteArray(CC_SHA256_DIGEST_LENGTH)
    bytes.usePinned { input ->
        out.usePinned { digest ->
            CC_SHA256(if (bytes.isEmpty()) null else input.addressOf(0), bytes.size.toUInt(), digest.addressOf(0))
        }
    }
    return out.usePinned { NSData.create(bytes = it.addressOf(0), length = out.size.toULong()) }
}
