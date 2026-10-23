/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.consentcallback

import org.mifosx.openbanking.core.data.callback.ConsentCallbackRepository
import org.mifosx.openbanking.core.data.callback.ConsentStatusResult
import org.mifosx.openbanking.core.data.callback.ValidationResult
import org.mifosx.openbanking.core.model.callback.ConsentStatus
import org.mifosx.openbanking.core.network.api.CompletedAuth
import template.core.base.common.screen.DataFreshness
import template.core.base.common.screen.ScreenState

class FakeConsentCallbackRepository : ConsentCallbackRepository {

    var validateResult: ValidationResult = VALID

    var exchangeResult: ScreenState<CompletedAuth> = ScreenState.Content(
        COMPLETED_AUTH,
        DataFreshness.FRESH,
    )

    /** Successive poll results, so the awaiting-then-authorised journey can be driven. */
    var pollResults: MutableList<ScreenState<ConsentStatusResult>> = mutableListOf(
        ScreenState.Content(ConsentStatusResult(ConsentStatus.Authorised, null), DataFreshness.FRESH),
    )

    var validatedUrls: MutableList<String> = mutableListOf()
        private set

    var exchangedCodes: MutableList<String> = mutableListOf()
        private set

    var polledConsentIds: MutableList<String> = mutableListOf()
        private set

    override fun validateCallback(redirectUrl: String): ValidationResult {
        validatedUrls += redirectUrl
        return validateResult
    }

    override suspend fun exchangeCode(callback: ValidationResult.Valid): ScreenState<CompletedAuth> {
        exchangedCodes += callback.code
        return exchangeResult
    }

    override suspend fun pollConsentStatus(consentId: String): ScreenState<ConsentStatusResult> {
        polledConsentIds += consentId
        return if (pollResults.size > 1) pollResults.removeFirst() else pollResults.first()
    }

    companion object {
        val VALID = ValidationResult.Valid(
            code = "auth-code",
            consentId = "cn-1",
            idToken = "id.token.jwt",
            state = "st-1",
            flowId = "flow-1",
        )

        val COMPLETED_AUTH = CompletedAuth(
            session = "worker-session",
            expiresAt = 1_800_000_000_000,
            leg = "ais",
            consentId = "cn-1",
            consentStatus = "Authorised",
            consentExpiresAt = "2026-07-01T00:00:00Z",
        )

        /** [ScreenState.Error] carries a raw [Throwable]; its message is what the VM surfaces. */
        val NETWORK_ERROR = RuntimeException("Token exchange failed.")
    }
}
