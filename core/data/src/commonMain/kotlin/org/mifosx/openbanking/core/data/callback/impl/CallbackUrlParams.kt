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

import org.mifosx.openbanking.core.network.api.ConsentStart

internal data class CallbackUrlParams(
    val code: String? = null,
    val idToken: String? = null,
    val state: String? = null,
    val error: String? = null,
    val errorDescription: String? = null,
)

/**
 * Parses HSBC's redirect URL.
 *
 * Reads **both** the fragment and the query, because the same values legitimately arrive either way.
 * HSBC uses the OIDC hybrid flow (`response_type = "code id_token"`), which returns the result in the
 * fragment — `…/callback/#code=…&id_token=…&state=…`. Android and iOS receive that fragment intact
 * through the custom scheme. A fragment is never sent to a server, though, so for desktop the bridge
 * page rewrites it into a query string before relaying to the loopback listener. Query wins on
 * conflict, since that is the deliberately-rewritten form. On web the relay returns to the app's own
 * origin as `<origin>/#mifos-callback?…` (fragment, so the web server never sees it); that marker is
 * stripped before the fragment is decoded.
 */
/** The fragment prefix the relay uses when it hands a web sign-in back to its origin (relay bridge.js). */
const val WEB_CALLBACK_MARKER = "mifos-callback?"

internal fun parseCallbackUrl(url: String): CallbackUrlParams {
    val afterScheme = url.substringAfter("://", url)
    val fragment = afterScheme.substringAfter('#', "").removePrefix(WEB_CALLBACK_MARKER)
    val query = afterScheme.substringBefore('#').substringAfter('?', "")

    val params = decodeParams(fragment) + decodeParams(query)

    return CallbackUrlParams(
        code = params["code"],
        idToken = params["id_token"],
        state = params["state"],
        error = params["error"],
        errorDescription = params["error_description"],
    )
}

private fun decodeParams(raw: String): Map<String, String> = raw
    .split('&')
    .filter { it.isNotBlank() }
    .mapNotNull { pair ->
        val key = pair.substringBefore('=')
        val value = pair.substringAfter('=', "")
        if (key.isBlank()) null else key to percentDecode(value)
    }
    .toMap()

/**
 * Minimal percent-decoder. `error_description` is free text and routinely arrives percent-encoded;
 * `+` is the form-encoded space. Decoding is byte-wise so multi-byte UTF-8 sequences survive.
 */
private fun percentDecode(value: String): String {
    if ('%' !in value && '+' !in value) return value

    val bytes = mutableListOf<Byte>()
    var i = 0
    while (i < value.length) {
        val c = value[i]
        when {
            c == '%' && i + 2 < value.length -> {
                val hex = value.substring(i + 1, i + 3).toIntOrNull(radix = 16)
                if (hex == null) {
                    bytes.add(c.code.toByte())
                    i++
                } else {
                    bytes.add(hex.toByte())
                    i += 3
                }
            }

            c == '+' -> {
                bytes.add(' '.code.toByte())
                i++
            }

            else -> {
                c.toString().encodeToByteArray().forEach(bytes::add)
                i++
            }
        }
    }
    return bytes.toByteArray().decodeToString()
}

/**
 * The `state` the Worker put on its authorize URL. The redirect must echo it back, so the app keeps
 * it to match the return leg locally before handing it to `/v1/auth/complete`.
 */
internal fun ConsentStart.authorizeState(): String? = parseCallbackUrl(authorizeUrl).state
