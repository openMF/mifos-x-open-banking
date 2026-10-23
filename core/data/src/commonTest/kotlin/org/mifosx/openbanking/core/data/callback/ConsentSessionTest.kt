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
import org.mifosx.openbanking.core.network.SESSION_KEY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Pins the storage contract [SettingsConsentSession] backs: the Worker session gates the "signed in"
 * state, lives under the one key the BFF client reads, and the consent id + expiry round-trip and are
 * wiped alongside it on [ConsentSession.clear].
 */
@OptIn(ExperimentalTime::class)
class ConsentSessionTest {

    private val settings = MapSettings()
    private val session = SettingsConsentSession(settings)

    private val token = "worker-session-id"

    @Test
    fun aSavedSessionMakesItActiveUnderTheKeyTheBffClientReads() {
        session.save(token)

        assertTrue(session.isActive())
        assertEquals(token, settings.getStringOrNull(SESSION_KEY))
    }

    @Test
    fun anEmptySessionIsNotActive() {
        assertFalse(session.isActive())
    }

    @Test
    fun noBankTokenIsEverStored() {
        session.save(token)
        session.saveConsentMeta("cn-1", "2026-07-01T00:00:00Z")

        assertFalse(settings.keys.any { "token" in it.lowercase() }, "keys: ${settings.keys}")
    }

    @Test
    fun saveConsentMetaRoundTripsIdAndExpiry() {
        session.saveConsentMeta("cn-1", "2026-07-01T00:00:00Z")

        assertEquals("cn-1", session.consentId())
        assertEquals(Instant.parse("2026-07-01T00:00:00Z"), session.consentExpiration())
    }

    @Test
    fun consentExpirationIsNullWhenAbsent() {
        assertNull(session.consentId())
        assertNull(session.consentExpiration())
    }

    @Test
    fun consentExpirationIsNullWhenUnparseable() {
        session.saveConsentMeta("cn-1", "not-a-timestamp")

        assertEquals("cn-1", session.consentId())
        assertNull(session.consentExpiration())
    }

    @Test
    fun clearWipesTheSessionAndConsentMeta() {
        session.save(token)
        session.saveConsentMeta("cn-1", "2026-07-01T00:00:00Z")

        session.clear()

        assertFalse(session.isActive())
        assertNull(session.consentId())
        assertNull(session.consentExpiration())
    }

    @Test
    fun forgetAllWipesEverything() {
        session.save(token)
        session.saveConsentMeta("cn-1", "2026-07-01T00:00:00Z")

        session.forgetAll()

        assertFalse(session.isActive())
        assertNull(session.consentId())
        assertNull(session.consentExpiration())
        assertTrue(settings.keys.isEmpty(), "forgetAll must leave nothing behind")
    }
}
