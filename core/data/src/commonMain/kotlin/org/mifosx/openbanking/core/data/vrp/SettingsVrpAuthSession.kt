/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.data.vrp

import com.russhwolf.settings.Settings

/** [VrpAuthSession] backed by secure [Settings]. Refresh tokens are keyed by consent. */
class SettingsVrpAuthSession(
    private val secureSettings: Settings,
) : VrpAuthSession {

    override fun saveAuthorisationInFlight(consentId: String, state: String, nonce: String) {
        secureSettings.putString(KEY_PENDING_CONSENT_ID, consentId)
        secureSettings.putString(KEY_STATE, state)
        secureSettings.putString(KEY_NONCE, nonce)
    }

    override fun pendingConsentId(): String? = secureSettings.getStringOrNull(KEY_PENDING_CONSENT_ID)

    override fun pendingNonce(): String? = secureSettings.getStringOrNull(KEY_NONCE)

    override fun matchesPendingState(state: String?): Boolean {
        val pending = secureSettings.getStringOrNull(KEY_STATE)
        return !state.isNullOrBlank() && state == pending
    }

    override fun clearAuthorisationInFlight() {
        secureSettings.remove(KEY_PENDING_CONSENT_ID)
        secureSettings.remove(KEY_STATE)
        secureSettings.remove(KEY_NONCE)
    }

    override fun saveRefreshToken(consentId: String, refreshToken: String) {
        val key = refreshTokenKey(consentId)
        if (secureSettings.getStringOrNull(key) != null) return
        secureSettings.putString(key, refreshToken)
    }

    override fun refreshToken(consentId: String): String? =
        secureSettings.getStringOrNull(refreshTokenKey(consentId))

    override fun removeRefreshToken(consentId: String) {
        secureSettings.remove(refreshTokenKey(consentId))
    }

    override fun clear() {
        clearAuthorisationInFlight()
        secureSettings.keys
            .filter { it.startsWith(KEY_REFRESH_TOKEN_PREFIX) }
            .forEach { secureSettings.remove(it) }
    }

    private fun refreshTokenKey(consentId: String) = "$KEY_REFRESH_TOKEN_PREFIX$consentId"

    private companion object {
        const val KEY_PENDING_CONSENT_ID = "vrp_pending_consent_id"
        const val KEY_STATE = "vrp_auth_state"
        const val KEY_NONCE = "vrp_auth_nonce"
        const val KEY_REFRESH_TOKEN_PREFIX = "vrp_refresh_token:"
    }
}
