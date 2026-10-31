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

/**
 * The store build's callback scheme: its applicationId, which is what the manifest registers
 * (`${applicationId}`). Dev builds use their own (see [ANDROID_RETURN_APPS]).
 */
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

/**
 * Android builds a sign-in can return to, index-aligned with `ANDROID_RETURN_APPS` in
 * `bank-gateway/wrangler.toml` and `relay/site/callback/bridge.js` (a relay test pins all three).
 * Each build registers its own applicationId as its callback scheme (`${applicationId}` in the
 * manifest), and the gateway marks a flow started by build `i > 0` with an `a<i>.` state prefix.
 * Index 0 is the store build. Append, never reorder.
 */
internal val ANDROID_RETURN_APPS = listOf(
    "org.mifosx.openbanking",
    "org.mifosx.openbanking.debug",
    "org.mifosx.openbanking.demo",
    "org.mifosx.openbanking.demo.debug",
)

/** [ownScheme] is this build's applicationId — the scheme its manifest registers. */
internal fun Intent.consumeConsentRedirectUrl(ownScheme: String = CONSENT_REDIRECT_SCHEME): String? {
    val url = data
        ?.takeIf { action == Intent.ACTION_VIEW && (it.scheme == ownScheme || it.isRelayAppLink()) }
        ?.toString()
        ?: return null

    data = null
    return url
}

/**
 * The `<package>://callback?…` URL to hand [url] to when it belongs to a different build than
 * [ownApp], else null. The verified relay App Link opens whichever listed build Android picks, so a
 * phone with the store and a dev build installed can deliver one build's sign-in to the other — which
 * then fails it as a security error (its PendingAuth is not there). The `a<i>.` state prefix names the
 * build that started the flow; no prefix means the store build. A redirect with no state is kept.
 */
internal fun siblingRedirectTarget(url: String, ownApp: String): String? {
    val params = url.substringAfter('#', "").ifEmpty { url.substringAfter('?', "") }
    val state = params.split('&').firstOrNull { it.startsWith("state=") }?.removePrefix("state=") ?: return null
    val index = Regex("""^a([1-9][0-9]*)\.""").find(state)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    val target = ANDROID_RETURN_APPS.getOrNull(index) ?: ANDROID_RETURN_APPS.first()
    return if (target == ownApp) null else "$target://callback?$params"
}

private fun android.net.Uri.isRelayAppLink(): Boolean =
    scheme == "https" && host == CONSENT_RELAY_HOST && path.orEmpty().startsWith("/callback")
