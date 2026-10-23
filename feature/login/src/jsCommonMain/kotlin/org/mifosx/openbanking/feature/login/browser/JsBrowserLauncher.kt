/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.login.browser

import kotlinx.browser.window

/**
 * Web (js / wasmJs): the app is a plain HTTPS client of the Worker BFF (no client certificate), so it
 * can start the consent journey — the tab navigates to the Worker-signed HSBC authorize URL.
 *
 * Known gap: the relay page does not yet hand the result back to a web origin (it knows the mobile
 * scheme and the desktop loopback only), so a web sign-in stops on the relay page after the bank login.
 */
class JsBrowserLauncher : BrowserLauncher {
    override val isSupported: Boolean = true

    override fun launch(url: String) {
        window.location.assign(url)
    }
}
