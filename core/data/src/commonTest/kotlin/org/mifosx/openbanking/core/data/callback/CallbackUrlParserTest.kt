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

import org.mifosx.openbanking.core.data.callback.impl.parseCallbackUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CallbackUrlParserTest {

    @Test
    fun `parses fragment form as delivered by the custom scheme on mobile`() {
        val params = parseCallbackUrl(
            "org.mifosx.openbanking://callback#code=abc&id_token=ey.J.sig&state=st-1",
        )

        assertEquals("abc", params.code)
        assertEquals("ey.J.sig", params.idToken)
        assertEquals("st-1", params.state)
        assertNull(params.error)
        assertNull(params.errorDescription)
    }

    @Test
    fun `parses query form as rewritten by the bridge page for desktop loopback`() {
        val params = parseCallbackUrl(
            "org.mifosx.openbanking://callback?code=abc&id_token=ey.J.sig&state=st-1",
        )

        assertEquals("abc", params.code)
        assertEquals("ey.J.sig", params.idToken)
        assertEquals("st-1", params.state)
    }

    @Test
    fun `parses the raw https callback url hsbc redirects to`() {
        val params = parseCallbackUrl(
            "https://thekalpeshpawar.github.io/obp-callback/callback/#code=abc&id_token=ey.J.sig&state=st-1",
        )

        assertEquals("abc", params.code)
        assertEquals("ey.J.sig", params.idToken)
        assertEquals("st-1", params.state)
    }

    @Test
    fun `parses the web return form the relay hands to a web origin`() {
        val params = parseCallbackUrl(
            "http://localhost:8080/#mifos-callback?code=abc&id_token=ey.J.sig&state=w0.st-1",
        )

        assertEquals("abc", params.code)
        assertEquals("ey.J.sig", params.idToken)
        assertEquals("w0.st-1", params.state)
    }

    @Test
    fun `query wins over fragment because it is the deliberately rewritten form`() {
        val params = parseCallbackUrl(
            "org.mifosx.openbanking://callback?code=from-query#code=from-fragment",
        )

        assertEquals("from-query", params.code)
    }

    @Test
    fun `parses error response`() {
        val params = parseCallbackUrl(
            "org.mifosx.openbanking://callback#error=access_denied&state=st-1",
        )

        assertEquals("access_denied", params.error)
        assertEquals("st-1", params.state)
        assertNull(params.code)
    }

    @Test
    fun `percent decodes error description`() {
        val params = parseCallbackUrl(
            "org.mifosx.openbanking://callback#error=server_error&error_description=Something%20went%20wrong",
        )

        assertEquals("Something went wrong", params.errorDescription)
    }

    @Test
    fun `decodes plus as space in form encoded values`() {
        val params = parseCallbackUrl(
            "org.mifosx.openbanking://callback#error_description=Consent+was+rejected",
        )

        assertEquals("Consent was rejected", params.errorDescription)
    }

    @Test
    fun `decodes multi byte utf8 sequences`() {
        val params = parseCallbackUrl(
            "org.mifosx.openbanking://callback#error_description=caf%C3%A9",
        )

        assertEquals("café", params.errorDescription)
    }

    @Test
    fun `leaves malformed percent escape untouched rather than throwing`() {
        val params = parseCallbackUrl(
            "org.mifosx.openbanking://callback#error_description=100%zz",
        )

        assertEquals("100%zz", params.errorDescription)
    }

    @Test
    fun `returns all nulls for a url with no parameters`() {
        val params = parseCallbackUrl("org.mifosx.openbanking://callback")

        assertNull(params.code)
        assertNull(params.idToken)
        assertNull(params.state)
        assertNull(params.error)
        assertNull(params.errorDescription)
    }

    @Test
    fun `ignores blank and keyless segments`() {
        val params = parseCallbackUrl(
            "org.mifosx.openbanking://callback#&&=orphan&code=abc&",
        )

        assertEquals("abc", params.code)
    }

    @Test
    fun `treats a valueless key as empty rather than dropping it`() {
        val params = parseCallbackUrl("org.mifosx.openbanking://callback#code=&state=st-1")

        assertEquals("", params.code)
        assertEquals("st-1", params.state)
    }

    @Test
    fun `parses a url with no scheme separator`() {
        val params = parseCallbackUrl("callback?code=abc")

        assertEquals("abc", params.code)
    }

    @Test
    fun `ignores unknown parameters`() {
        val params = parseCallbackUrl(
            "org.mifosx.openbanking://callback#code=abc&scope=openid+accounts&token_type=Bearer",
        )

        assertEquals("abc", params.code)
        assertNull(params.error)
    }
}
