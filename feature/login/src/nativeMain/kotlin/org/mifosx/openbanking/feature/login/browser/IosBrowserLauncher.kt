/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.login.browser

import org.mifosx.openbanking.core.data.callback.ConsentRedirectBus
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject

/** The scheme the relay hands the bank's answer to (must match Info.plist and relay/bridge.js). */
private const val CALLBACK_SCHEME = "org.mifosx.openbanking"

/**
 * Opens the bank's authorisation page in ASWebAuthenticationSession — Apple's in-app sheet for
 * OAuth, which RFC 8252 and FAPI allow (the app cannot read it), unlike an embedded WKWebView.
 * When the relay redirects to `org.mifosx.openbanking://callback`, the session returns that URL
 * here and it goes to the same [ConsentRedirectBus] the other platforms publish to.
 */
class IosBrowserLauncher : BrowserLauncher {
    // Held until the sheet finishes: a session that is garbage-collected is dismissed.
    private var session: ASWebAuthenticationSession? = null

    private val anchorProvider = object : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
        override fun presentationAnchorForWebAuthenticationSession(
            session: ASWebAuthenticationSession,
        ): ASPresentationAnchor = UIApplication.sharedApplication.keyWindow ?: UIWindow()
    }

    override fun launch(url: String) {
        val nsUrl = NSURL.URLWithString(url) ?: throw BrowserLaunchException("Invalid authorisation URL")
        val authSession = ASWebAuthenticationSession(
            uRL = nsUrl,
            callbackURLScheme = CALLBACK_SCHEME,
        ) { callbackUrl, _ ->
            // A cancelled sheet returns no URL; the app stays on the screen that launched it.
            callbackUrl?.absoluteString?.let(ConsentRedirectBus::publish)
            session = null
        }
        authSession.presentationContextProvider = anchorProvider
        // Share the Safari cookie jar so a returning customer isn't asked to log in from scratch.
        authSession.prefersEphemeralWebBrowserSession = false
        session = authSession
        if (!authSession.start()) {
            session = null
            throw BrowserLaunchException("Could not start the authorisation sheet")
        }
    }
}
