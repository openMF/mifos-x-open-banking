/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package cmp.android.app

import android.content.Intent

/** Must stay in lock-step with the `android:scheme` on the VIEW intent-filter in the manifest. */
internal const val CONSENT_REDIRECT_SCHEME = "org.mifosx.openbanking"

/**
 * Returns HSBC's consent redirect if this intent carries one, and consumes it.
 *
 * Ignores anything else: the Activity is launched for ordinary reasons far more often than for a
 * callback, and a launcher start carries `ACTION_MAIN` with no data.
 *
 * Clearing [Intent.setData] is the point of the "consume" — the redirect is a one-time event, but the
 * Activity's intent is not. Android hands the same intent back on every recreate, so without this a
 * rotation would replay the callback, burn the single-use PendingAuth, and fail a legitimate consent
 * with a SecurityError.
 *
 * Deliberately a free function rather than a private method on the Activity: this is the whole of the
 * redirect-handling logic, and it needs no Activity, no Koin graph and no device to verify.
 */
/**
 * The consent relay's host. Its `/callback/` path is a verified Android App Link (see the
 * `autoVerify` filter in the manifest and `relay/site/.well-known/assetlinks.json`), so the bank's
 * redirect — the tail of the customer's "Finish" tap — opens the app directly, fragment included,
 * without the relay page's "Reopen the app" button. Must match the manifest `android:host`.
 */
internal const val CONSENT_RELAY_HOST = "mifos-x-open-banking-relay.pages.dev"

internal fun Intent.consumeConsentRedirectUrl(): String? {
    val url = data
        ?.takeIf { action == Intent.ACTION_VIEW && (it.scheme == CONSENT_REDIRECT_SCHEME || it.isRelayAppLink()) }
        ?.toString()
        ?: return null

    data = null
    return url
}

private fun android.net.Uri.isRelayAppLink(): Boolean =
    scheme == "https" && host == CONSENT_RELAY_HOST && path.orEmpty().startsWith("/callback")
