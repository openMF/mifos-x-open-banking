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

import com.russhwolf.settings.Settings
import org.mifosx.openbanking.core.network.SESSION_KEY
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Whether the PSU has a usable Worker session — the opaque id `/v1/auth/complete` returned — and the
 * consent behind it.
 *
 * In an AISP app "signed in" is not a flag someone sets: it is whether an authorised consent produced
 * a session. Deriving it from the stored session keeps the two from drifting.
 *
 * The app holds no bank token at all: the Worker keeps the PSU tokens and refreshes them. The session
 * id lives under [SESSION_KEY], the one key `BffHttpClient` reads on every request, so there is
 * exactly one session store and nothing for a second copy to disagree with.
 */
interface ConsentSession {
    /**
     * True when a completed sign-in left a session behind. Read synchronously — the app-open route is
     * derived from it; an expired session surfaces as a 401 on the next data call.
     */
    fun isActive(): Boolean

    /** Persists the opaque Worker session — this write is what signs the PSU in. */
    fun save(session: String)

    /** Persists the consent's id and its ISO-8601 expiry alongside the session. */
    fun saveConsentMeta(consentId: String, expirationDateTime: String)

    /** The stored consent id, or null when none was saved. */
    fun consentId(): String?

    /** The stored consent expiry parsed to an [Instant], or null when absent or unparseable. */
    @OptIn(ExperimentalTime::class)
    fun consentExpiration(): Instant?

    /** Drops the session, current consent id and expiry — the revoke and start-over path. */
    fun clear()

    /** An explicit sign-out that should leave nothing behind. */
    fun forgetAll()
}

class SettingsConsentSession(
    private val secureSettings: Settings,
) : ConsentSession {

    override fun isActive(): Boolean = !secureSettings.getStringOrNull(SESSION_KEY).isNullOrBlank()

    override fun save(session: String) {
        secureSettings.putString(SESSION_KEY, session)
    }

    override fun saveConsentMeta(consentId: String, expirationDateTime: String) {
        secureSettings.putString(KEY_CONSENT_ID, consentId)
        secureSettings.putString(KEY_CONSENT_EXPIRATION, expirationDateTime)
    }

    override fun consentId(): String? = secureSettings.getStringOrNull(KEY_CONSENT_ID)

    @OptIn(ExperimentalTime::class)
    override fun consentExpiration(): Instant? =
        secureSettings.getStringOrNull(KEY_CONSENT_EXPIRATION)
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }

    override fun clear() {
        secureSettings.remove(SESSION_KEY)
        secureSettings.remove(KEY_CONSENT_ID)
        secureSettings.remove(KEY_CONSENT_EXPIRATION)
    }

    override fun forgetAll() {
        clear()
    }

    private companion object {
        const val KEY_CONSENT_ID = "hsbc_consent_id"
        const val KEY_CONSENT_EXPIRATION = "hsbc_consent_expiration"
    }
}
