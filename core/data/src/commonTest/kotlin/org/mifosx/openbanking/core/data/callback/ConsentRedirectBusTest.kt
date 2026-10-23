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

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [ConsentRedirectBus] is a process-wide object with a replay cache, so state carries between tests.
 * Every test therefore publishes its own unique URL before asserting, rather than assuming an empty
 * bus.
 */
class ConsentRedirectBusTest {

    @Test
    fun `a published redirect reaches a collector`() = runTest {
        val url = "org.mifosx.openbanking://callback?code=reaches-collector"

        ConsentRedirectBus.publish(url)

        assertEquals(url, ConsentRedirectBus.redirects.first())
    }

    @Test
    fun `replay delivers a redirect published before anyone subscribed`() = runTest {
        val url = "org.mifosx.openbanking://callback?code=published-first"

        ConsentRedirectBus.publish(url)

        assertEquals(
            url,
            ConsentRedirectBus.redirects.first(),
            "replay=1 is what makes an Android cold start work: the intent is published in " +
                "onCreate, before composition subscribes",
        )
    }

    @Test
    fun `a later redirect supersedes the replayed one`() = runTest {
        ConsentRedirectBus.publish("org.mifosx.openbanking://callback?code=stale")
        ConsentRedirectBus.publish("org.mifosx.openbanking://callback?code=fresh")

        assertEquals(
            "org.mifosx.openbanking://callback?code=fresh",
            ConsentRedirectBus.redirects.first(),
        )
    }

    @Test
    fun `an active collector receives consecutive redirects in order`() = runTest {
        ConsentRedirectBus.publish("org.mifosx.openbanking://callback?code=seed")

        val received = mutableListOf<String>()
        // UnconfinedTestDispatcher so the collector is subscribed before the publishes below.
        // Started lazily on the default dispatcher, it would not run until the first suspension —
        // by which point replay=1 has already collapsed both publishes into one.
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            received += ConsentRedirectBus.redirects.take(3).toList()
        }

        ConsentRedirectBus.publish("org.mifosx.openbanking://callback?code=first")
        ConsentRedirectBus.publish("org.mifosx.openbanking://callback?code=second")
        job.join()

        assertEquals(
            listOf(
                "org.mifosx.openbanking://callback?code=seed",
                "org.mifosx.openbanking://callback?code=first",
                "org.mifosx.openbanking://callback?code=second",
            ),
            received,
        )
    }

    /**
     * Leaving the app with Back finishes the Activity but keeps the process, so a relaunch builds a new
     * navigator that subscribes afresh. Without the acknowledgement, replay hands it the redirect that
     * was already handled, the single-use PendingAuth is gone, and the PSU sees "Security check failed".
     */
    @Test
    fun `a routed redirect is not replayed to the next subscriber`() = runTest {
        val url = "org.mifosx.openbanking://callback?code=routed-once"
        ConsentRedirectBus.publish(url)
        assertEquals(url, ConsentRedirectBus.redirects.first())

        ConsentRedirectBus.markRouted(url)

        assertTrue(ConsentRedirectBus.redirects.replayCache.isEmpty())
    }

    @Test
    fun `acknowledging an older redirect keeps a newer one`() = runTest {
        ConsentRedirectBus.publish("org.mifosx.openbanking://callback?code=older")
        ConsentRedirectBus.publish("org.mifosx.openbanking://callback?code=newer")

        ConsentRedirectBus.markRouted("org.mifosx.openbanking://callback?code=older")

        assertEquals("org.mifosx.openbanking://callback?code=newer", ConsentRedirectBus.redirects.first())
    }
}
