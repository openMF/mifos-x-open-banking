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

import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings

/**
 * The Worker's pending `flowId`, the `state` its authorize URL carries and the `consentId`, held across
 * the browser round-trip so the redirect can be matched and completed when it returns.
 */
data class PendingAuth(
    val state: String,
    val flowId: String,
    val consentId: String,
)

/**
 * Survives the OAuth browser hop — and nothing longer.
 *
 * `state` and `flowId` are single-use, short-lived values, but they cannot live in memory: the PSU
 * leaves the app entirely to authorise at HSBC, and on Android the process may be killed while
 * backgrounded, so the redirect can arrive via a **cold start**. Holding them in memory would fail a
 * legitimate consent with a SecurityError. This mirrors AppAuth, which persists the authorization
 * request for exactly this reason.
 *
 * Two rules keep "persisted" from drifting into "permanent", and both are enforced in
 * [SettingsPendingAuthStore]:
 *  - **single-use** — [consume] deletes on read, so a redirect can never be replayed;
 *  - **TTL** — entries older than [TTL_SECONDS] are refused and purged. The bound is not arbitrary:
 *    it matches the Worker's pending-flow TTL, after which `/v1/auth/complete` answers
 *    `400 unknown_flow` anyway, so a surviving entry is dead weight.
 */
interface PendingAuthStore {
    fun save(state: String, flowId: String, consentId: String)

    /**
     * Reads and **deletes** the pending auth. Returns `null` when absent or past its TTL.
     * Single-use by construction: a second call for the same consent always returns `null`.
     */
    fun consume(): PendingAuth?

    fun clear()
}

class SettingsPendingAuthStore(
    private val secureSettings: Settings,
    private val nowEpochSeconds: () -> Long,
) : PendingAuthStore {

    override fun save(state: String, flowId: String, consentId: String) {
        secureSettings.putString(KEY_STATE, state)
        secureSettings.putString(KEY_FLOW_ID, flowId)
        secureSettings.putString(KEY_CONSENT_ID, consentId)
        secureSettings.putLong(KEY_CREATED_AT, nowEpochSeconds())
    }

    override fun consume(): PendingAuth? {
        val stored = readStored()
        clear()
        val fresh = stored?.takeIf { nowEpochSeconds() - it.createdAt <= TTL_SECONDS }?.auth
        Logger.d(TAG) {
            "consume: found=${stored != null} withinTtl=${fresh != null}"
        }
        return fresh
    }

    /** A stored entry and the moment it was written, so [consume] can judge its age. */
    private class StoredAuth(val auth: PendingAuth, val createdAt: Long)

    /**
     * All four keys or nothing — a half-written entry is not something to honour.
     *
     * Four guard clauses rather than one four-term condition: the condition form trips
     * ComplexCondition and reads no better. `ReturnCount` exists to catch tangled control flow, which
     * four symmetric reads are not.
     */
    @Suppress("ReturnCount")
    private fun readStored(): StoredAuth? {
        val state = secureSettings.getStringOrNull(KEY_STATE) ?: return null
        val flowId = secureSettings.getStringOrNull(KEY_FLOW_ID) ?: return null
        val consentId = secureSettings.getStringOrNull(KEY_CONSENT_ID) ?: return null
        val createdAt = secureSettings.getLongOrNull(KEY_CREATED_AT) ?: return null

        return StoredAuth(
            auth = PendingAuth(state = state, flowId = flowId, consentId = consentId),
            createdAt = createdAt,
        )
    }

    override fun clear() {
        Logger.d(TAG) {
            "clear: hadState=${secureSettings.getStringOrNull(KEY_STATE) != null} " +
                "hadFlowId=${secureSettings.getStringOrNull(KEY_FLOW_ID) != null} " +
                "hadConsentId=${secureSettings.getStringOrNull(KEY_CONSENT_ID) != null}"
        }
        secureSettings.remove(KEY_STATE)
        secureSettings.remove(KEY_FLOW_ID)
        secureSettings.remove(KEY_CONSENT_ID)
        secureSettings.remove(KEY_CREATED_AT)
    }

    companion object {
        private const val TAG = "PendingAuthStore"

        /** Matches the Worker's pending-flow TTL (bff `SessionDO`, 600 s). */
        const val TTL_SECONDS = 600L

        private const val KEY_STATE = "pending_auth_state"
        private const val KEY_FLOW_ID = "pending_auth_flow_id"
        private const val KEY_CONSENT_ID = "pending_auth_consent_id"
        private const val KEY_CREATED_AT = "pending_auth_created_at"
    }
}
