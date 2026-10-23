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

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsPendingAuthStoreTest {

    private val settings = MapSettings()
    private var now = 1_000L

    private fun store() = SettingsPendingAuthStore(
        secureSettings = settings,
        nowEpochSeconds = { now },
    )

    @Test
    fun `save then consume round trips all three values`() {
        val store = store()
        store.save(state = "st-1", flowId = "fl-1", consentId = "cn-1")

        val pending = store.consume()

        assertNotNull(pending)
        assertEquals("st-1", pending.state)
        assertEquals("fl-1", pending.flowId)
        assertEquals("cn-1", pending.consentId)
    }

    @Test
    fun `consume is single use so a redirect cannot be replayed`() {
        val store = store()
        store.save(state = "st-1", flowId = "fl-1", consentId = "cn-1")

        assertNotNull(store.consume())
        assertNull(store.consume())
    }

    @Test
    fun `consume returns null when nothing was saved`() {
        assertNull(store().consume())
    }

    @Test
    fun `consume deletes every key including the timestamp`() {
        val store = store()
        store.save(state = "st-1", flowId = "fl-1", consentId = "cn-1")

        store.consume()

        assertTrue(settings.keys.isEmpty(), "expected no residue, found ${settings.keys}")
    }

    @Test
    fun `entry is accepted at the ttl boundary`() {
        val store = store()
        store.save(state = "st-1", flowId = "fl-1", consentId = "cn-1")
        now += SettingsPendingAuthStore.TTL_SECONDS

        assertNotNull(store.consume())
    }

    @Test
    fun `entry is refused one second past the ttl`() {
        val store = store()
        store.save(state = "st-1", flowId = "fl-1", consentId = "cn-1")
        now += SettingsPendingAuthStore.TTL_SECONDS + 1

        assertNull(store.consume())
    }

    @Test
    fun `an expired entry is still purged rather than left behind`() {
        val store = store()
        store.save(state = "st-1", flowId = "fl-1", consentId = "cn-1")
        now += SettingsPendingAuthStore.TTL_SECONDS + 1

        store.consume()

        assertTrue(settings.keys.isEmpty())
    }

    @Test
    fun `clear removes a pending auth without consuming it`() {
        val store = store()
        store.save(state = "st-1", flowId = "fl-1", consentId = "cn-1")

        store.clear()

        assertNull(store.consume())
        assertTrue(settings.keys.isEmpty())
    }

    @Test
    fun `clear on an empty store is a no-op`() {
        store().clear()

        assertTrue(settings.keys.isEmpty())
    }

    @Test
    fun `save overwrites a previous pending auth`() {
        val store = store()
        store.save(state = "st-1", flowId = "fl-1", consentId = "cn-1")
        store.save(state = "st-2", flowId = "fl-2", consentId = "cn-2")

        val pending = store.consume()

        assertNotNull(pending)
        assertEquals("st-2", pending.state)
        assertEquals("fl-2", pending.flowId)
        assertEquals("cn-2", pending.consentId)
    }

    @Test
    fun `a partially written entry is refused rather than half honoured`() {
        val store = store()
        store.save(state = "st-1", flowId = "fl-1", consentId = "cn-1")
        settings.remove("pending_auth_flow_id")

        assertNull(store.consume())
    }

    @Test
    fun `an entry with no timestamp is refused because its age cannot be established`() {
        val store = store()
        store.save(state = "st-1", flowId = "fl-1", consentId = "cn-1")
        settings.remove("pending_auth_created_at")

        assertNull(store.consume())
    }

    @Test
    fun `ttl matches the worker pending flow lifetime`() {
        assertEquals(600L, SettingsPendingAuthStore.TTL_SECONDS)
    }
}
