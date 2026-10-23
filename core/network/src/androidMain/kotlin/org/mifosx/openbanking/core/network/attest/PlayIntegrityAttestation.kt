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

import android.content.Context
import android.util.Base64
import com.google.android.gms.tasks.Task
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Play Integrity standard API. `X-Attestation: play <challenge>.<token>` with
 * `requestHash = base64url(SHA-256(challenge ‖ clientData))` — exactly what bank-gateway/src/attest/playIntegrity.ts
 * recomputes. Returns null (the request goes out unattested) until the Google Cloud project number is
 * configured; the Worker's ATTESTATION_MODE decides whether that is refused.
 */
class PlayIntegrityAttestation(
    private val context: Context,
    private val cloudProjectNumber: String,
) : AppAttestation {
    override val platform: String = "android"

    private val lock = Mutex()
    private var provider: StandardIntegrityTokenProvider? = null

    override suspend fun header(clientData: ByteArray, gateway: AttestationGateway): String? {
        val project = cloudProjectNumber.toLongOrNull() ?: return null
        return gateway.challenge()?.let { challenge ->
            val digest = MessageDigest.getInstance("SHA-256").digest(challenge.encodeToByteArray() + clientData)
            val hash = Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            val request = StandardIntegrityTokenRequest.builder().setRequestHash(hash).build()
            try {
                "play $challenge.${provider(project).request(request).await().token()}"
            } catch (e: Exception) {
                lock.withLock { provider = null } // e.g. INTEGRITY_TOKEN_PROVIDER_INVALID: re-prepare next time
                throw e
            }
        }
    }

    private suspend fun provider(project: Long): StandardIntegrityTokenProvider = lock.withLock {
        provider ?: IntegrityManagerFactory.createStandard(context)
            .prepareIntegrityToken(PrepareIntegrityTokenRequest.builder().setCloudProjectNumber(project).build())
            .await()
            .also { provider = it }
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
}
