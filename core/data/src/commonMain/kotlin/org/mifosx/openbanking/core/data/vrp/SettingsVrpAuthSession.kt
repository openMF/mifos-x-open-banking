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

/** [VrpAuthSession] backed by secure [Settings]. */
class SettingsVrpAuthSession(
    private val secureSettings: Settings,
) : VrpAuthSession {

    override fun saveAuthorisationInFlight(consentId: String, state: String, flowId: String, authorizeUrl: String) {
        secureSettings.putString(KEY_PENDING_CONSENT_ID, consentId)
        secureSettings.putString(KEY_STATE, state)
        secureSettings.putString(KEY_FLOW_ID, flowId)
        secureSettings.putString(KEY_AUTHORIZE_URL, authorizeUrl)
    }

    override fun pendingConsentId(): String? = secureSettings.getStringOrNull(KEY_PENDING_CONSENT_ID)

    override fun pendingFlowId(): String? = secureSettings.getStringOrNull(KEY_FLOW_ID)

    override fun pendingAuthorizeUrl(): String? = secureSettings.getStringOrNull(KEY_AUTHORIZE_URL)

    override fun matchesPendingState(state: String?): Boolean {
        val pending = secureSettings.getStringOrNull(KEY_STATE)
        return !state.isNullOrBlank() && state == pending
    }

    override fun clearAuthorisationInFlight() {
        secureSettings.remove(KEY_PENDING_CONSENT_ID)
        secureSettings.remove(KEY_STATE)
        secureSettings.remove(KEY_FLOW_ID)
        secureSettings.remove(KEY_AUTHORIZE_URL)
    }

    /** Also purges the per-consent refresh tokens a pre-BFF build stored on the device. */
    override fun clear() {
        clearAuthorisationInFlight()
        secureSettings.keys
            .filter { it.startsWith(LEGACY_REFRESH_TOKEN_PREFIX) }
            .forEach { secureSettings.remove(it) }
    }

    private companion object {
        const val KEY_PENDING_CONSENT_ID = "vrp_pending_consent_id"
        const val KEY_STATE = "vrp_auth_state"
        const val KEY_FLOW_ID = "vrp_auth_flow_id"
        const val KEY_AUTHORIZE_URL = "vrp_auth_authorize_url"
        const val LEGACY_REFRESH_TOKEN_PREFIX = "vrp_refresh_token:"
    }
}
