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

import android.app.Application
import android.content.Intent
import android.net.Uri
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The whole of MainActivity's redirect handling, on the JVM.
 *
 * The sibling instrumented test answers a different question — whether the *OS* routes
 * `org.mifosx.openbanking://…` to us — which genuinely needs a device and the package manager. This
 * one answers what happens once the intent arrives, which needs neither, so it runs here: fast,
 * deterministic, and in CI. Robolectric is only here for `Uri.parse`.
 *
 * Pinned to a plain [Application]: the manifest's `AndroidApp` starts Koin in `onCreate`, and
 * standing up the whole graph to parse a URI would make this test fail for reasons that have nothing
 * to do with redirects.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ConsentRedirectIntentTest {

    private fun viewIntent(url: String) = Intent(Intent.ACTION_VIEW, Uri.parse(url))

    @Test
    fun `a view intent on our scheme yields the redirect url`() {
        val intent = viewIntent(REDIRECT_URL)

        assertEquals(REDIRECT_URL, intent.consumeConsentRedirectUrl())
    }

    @Test
    fun `the fragment form survives, since that is what the custom scheme delivers on mobile`() {
        val fragmentForm = "$CONSENT_REDIRECT_SCHEME://callback#code=abc&id_token=ey.J.sig&state=st-1"

        assertEquals(fragmentForm, viewIntent(fragmentForm).consumeConsentRedirectUrl())
    }

    @Test
    fun `the oauth host the bridge page targets is accepted too`() {
        val oauthHost = "$CONSENT_REDIRECT_SCHEME://oauth/callback?code=abc&state=st-1"

        assertEquals(oauthHost, viewIntent(oauthHost).consumeConsentRedirectUrl())
    }

    @Test
    fun `the verified relay app link is accepted with its fragment intact`() {
        val appLink = "https://$CONSENT_RELAY_HOST/callback/#code=abc&id_token=ey.J.sig&state=st-1"

        assertEquals(appLink, viewIntent(appLink).consumeConsentRedirectUrl())
    }

    @Test
    fun `other https urls on the relay host or elsewhere are ignored`() {
        assertNull(viewIntent("https://$CONSENT_RELAY_HOST/privacy/").consumeConsentRedirectUrl())
        assertNull(viewIntent("https://evil.example/callback/#code=abc&state=st-1").consumeConsentRedirectUrl())
        assertNull(viewIntent("http://$CONSENT_RELAY_HOST/callback/#code=abc").consumeConsentRedirectUrl())
    }

    @Test
    fun `the url is consumed, so an activity recreate cannot replay it`() {
        val intent = viewIntent(REDIRECT_URL)

        intent.consumeConsentRedirectUrl()

        assertNull(
            intent.data,
            "Android hands the same intent back on every recreate; leaving the data set would " +
                "replay the callback and burn the single-use PendingAuth",
        )
    }

    @Test
    fun `a second read yields null`() {
        val intent = viewIntent(REDIRECT_URL)

        intent.consumeConsentRedirectUrl()

        assertNull(intent.consumeConsentRedirectUrl())
    }

    @Test
    fun `an ordinary launcher start yields null`() {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        assertNull(launcher.consumeConsentRedirectUrl(), "this runs on every single app launch")
    }

    @Test
    fun `a foreign scheme yields null`() {
        assertNull(viewIntent("org.example.other://callback?code=abc").consumeConsentRedirectUrl())
    }

    @Test
    fun `a view intent with no data yields null`() {
        assertNull(Intent(Intent.ACTION_VIEW).consumeConsentRedirectUrl())
    }

    @Test
    fun `an https url on the callback host is not mistaken for the custom scheme`() {
        // The registered redirect_uri is https; only the bridge page's relay should reach us.
        val https = "https://thekalpeshpawar.github.io/obp-callback/callback/?code=abc"

        assertNull(viewIntent(https).consumeConsentRedirectUrl())
    }

    private companion object {
        const val REDIRECT_URL = "$CONSENT_REDIRECT_SCHEME://callback?code=abc&state=st-1"
    }
}
