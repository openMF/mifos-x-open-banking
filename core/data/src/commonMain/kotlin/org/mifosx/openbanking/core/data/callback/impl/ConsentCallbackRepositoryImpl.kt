/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.data.callback.impl

import org.mifosx.openbanking.core.data.callback.CallbackSecurityRejected
import org.mifosx.openbanking.core.data.callback.ConsentCallbackRepository
import org.mifosx.openbanking.core.data.callback.ConsentRejectedAtBank
import org.mifosx.openbanking.core.data.callback.ConsentStatusResult
import org.mifosx.openbanking.core.data.callback.PendingAuthStore
import org.mifosx.openbanking.core.data.callback.SECURITY_REJECTION_CODES
import org.mifosx.openbanking.core.data.callback.ValidationResult
import org.mifosx.openbanking.core.data.callback.workerErrorCode
import org.mifosx.openbanking.core.data.util.toScreenState
import org.mifosx.openbanking.core.model.callback.ConsentStatus
import org.mifosx.openbanking.core.network.api.Aisp
import org.mifosx.openbanking.core.network.api.CompleteAuthRequest
import org.mifosx.openbanking.core.network.api.CompletedAuth
import org.mifosx.openbanking.core.network.api.Session
import template.core.base.common.screen.DataFreshness
import template.core.base.common.screen.ScreenState
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult

class ConsentCallbackRepositoryImpl(
    private val session: Session,
    private val aisp: Aisp,
    private val pendingAuthStore: PendingAuthStore,
) : ConsentCallbackRepository {

    /**
     * Order matters here:
     *  - [PendingAuthStore.consume] runs first and unconditionally, making the redirect single-use.
     *    Absent or expired means nothing authentic to check against — a hard SecurityError, never a
     *    soft pass.
     *  - Errors are classified BEFORE the `id_token` check, because HSBC returns no `id_token` when the
     *    PSU declines; checking it first would report a plain refusal as a SecurityError.
     *  - The `id_token` itself is verified by the Worker; here it only has to be present.
     */
    @Suppress("ReturnCount")
    override fun validateCallback(redirectUrl: String): ValidationResult {
        val params = parseCallbackUrl(redirectUrl)

        val pending = pendingAuthStore.consume() ?: return ValidationResult.SecurityError

        if (params.state != pending.state) return ValidationResult.SecurityError

        if (params.error != null) {
            return if (params.error == ERROR_ACCESS_DENIED) {
                ValidationResult.AccessDenied
            } else {
                ValidationResult.Error(
                    params.errorDescription ?: "HSBC reported an error: ${params.error}",
                )
            }
        }

        val idToken = params.idToken ?: return ValidationResult.SecurityError
        val code = params.code ?: return ValidationResult.MissingCode
        return ValidationResult.Valid(
            code = code,
            consentId = pending.consentId,
            idToken = idToken,
            state = pending.state,
            flowId = pending.flowId,
        )
    }

    override suspend fun exchangeCode(callback: ValidationResult.Valid): ScreenState<CompletedAuth> {
        val result = session.completeAuth(
            CompleteAuthRequest(
                flowId = callback.flowId,
                code = callback.code,
                idToken = callback.idToken,
                state = callback.state,
            ),
        )
        // The Worker's refusals carry meaning the generic mapping would erase: a failed id_token or
        // state check is a security event, and 409 means the bank itself rejected the consent.
        val error = (result as? NetworkResult.Error)?.error
        val code = workerErrorCode(error?.body)
        return when {
            error is NetworkError.Client.BadRequest && code in SECURITY_REJECTION_CODES ->
                ScreenState.Error(CallbackSecurityRejected(code.orEmpty()))
            error?.statusCode == HTTP_CONFLICT && code == "consent_rejected" ->
                ScreenState.Error(ConsentRejectedAtBank())
            else -> result.toScreenState()
        }
    }

    /** Reads the sign-in consent back through the Worker (session-gated, own consent only). */
    override suspend fun pollConsentStatus(
        consentId: String,
    ): ScreenState<ConsentStatusResult> =
        when (val screen = aisp.getConsent(consentId).toScreenState()) {
            is ScreenState.Content -> ScreenState.Content(
                ConsentStatusResult(
                    status = ConsentStatus.fromString(screen.data.data.status),
                    expirationDateTime = screen.data.data.expirationDateTime,
                ),
                DataFreshness.FRESH,
            )
            is ScreenState.Error -> ScreenState.Error(screen.error)
            is ScreenState.NoNetwork -> ScreenState.NoNetwork(screen.isCaptivePortal)
            is ScreenState.Unauthenticated -> ScreenState.Unauthenticated
            is ScreenState.Loading -> ScreenState.Loading
            is ScreenState.Empty -> ScreenState.Empty
        }

    private companion object {
        const val ERROR_ACCESS_DENIED = "access_denied"
        const val HTTP_CONFLICT = 409
    }
}
