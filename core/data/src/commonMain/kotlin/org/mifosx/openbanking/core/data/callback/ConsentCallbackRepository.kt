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

import org.mifosx.openbanking.core.model.callback.ConsentStatus
import org.mifosx.openbanking.core.network.api.CompletedAuth
import template.core.base.common.screen.ScreenState

sealed interface ValidationResult {
    /**
     * The redirect matches the sign-in in flight. Carries everything `/v1/auth/complete` needs, so the
     * caller never has to re-parse the URL or reach into [PendingAuthStore] itself. The Worker then
     * verifies the `id_token` in full (signature, iss, aud, exp, nonce, c_hash, s_hash).
     */
    data class Valid(
        val code: String,
        val consentId: String,
        val idToken: String,
        val state: String,
        val flowId: String,
    ) : ValidationResult

    data object SecurityError : ValidationResult
    data object AccessDenied : ValidationResult
    data object MissingCode : ValidationResult
    data class Error(val message: String) : ValidationResult
}

interface ConsentCallbackRepository {
    /**
     * Parses and authenticates HSBC's raw redirect URL.
     *
     * Takes the URL rather than pre-parsed parts on purpose: the `state` it is checked against comes
     * from [PendingAuthStore], which is this layer's business. If the caller supplied the expected
     * value, the caller could pass the wrong one.
     *
     * Single-use: consumes the stored [PendingAuth], so the same redirect cannot be replayed.
     */
    fun validateCallback(redirectUrl: String): ValidationResult

    /**
     * Redeems a validated redirect at the Worker (`POST /v1/auth/complete`) for the opaque session.
     * The Worker reads the consent status first: a rejected consent is an error (409), never a session.
     */
    suspend fun exchangeCode(callback: ValidationResult.Valid): ScreenState<CompletedAuth>

    suspend fun pollConsentStatus(
        consentId: String,
    ): ScreenState<ConsentStatusResult>
}

/**
 * The outcome of a consent-status poll: the mapped [status] plus the raw ISO-8601
 * [expirationDateTime] the bank returned, so the caller can persist when the consent expires.
 *
 * @property status The consent's current status.
 * @property expirationDateTime The consent's expiry as HSBC sent it, or null when absent.
 */
data class ConsentStatusResult(
    val status: ConsentStatus,
    val expirationDateTime: String?,
)
