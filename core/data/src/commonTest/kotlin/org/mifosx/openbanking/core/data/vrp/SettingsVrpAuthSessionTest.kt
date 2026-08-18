/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.data.vrp

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsVrpAuthSessionTest {

    private fun session(settings: MapSettings = MapSettings()) =
        SettingsVrpAuthSession(secureSettings = settings) to settings

    @Test
    fun keepsTheFirstRefreshTokenStoredForAConsent() {
        val (session, _) = session()

        session.saveRefreshToken("45365", "first")
        session.saveRefreshToken("45365", "second")

        assertEquals("first", session.refreshToken("45365"))
    }

    @Test
    fun storesARefreshTokenPerConsent() {
        val (session, _) = session()

        session.saveRefreshToken("45365", "token-a")
        session.saveRefreshToken("45366", "token-b")

        assertEquals("token-a", session.refreshToken("45365"))
        assertEquals("token-b", session.refreshToken("45366"))
    }

    @Test
    fun removesOneConsentsRefreshTokenWithoutTouchingTheOthers() {
        val (session, _) = session()
        session.saveRefreshToken("45365", "token-a")
        session.saveRefreshToken("45366", "token-b")

        session.removeRefreshToken("45365")

        assertNull(session.refreshToken("45365"))
        assertEquals("token-b", session.refreshToken("45366"))
    }

    @Test
    fun clearRemovesEveryRefreshTokenAndTheAuthorisationInFlight() {
        val (session, _) = session()
        session.saveAuthorisationInFlight(consentId = "45365", state = "s", nonce = "n")
        session.saveRefreshToken("45365", "token-a")
        session.saveRefreshToken("45366", "token-b")

        session.clear()

        assertNull(session.pendingConsentId())
        assertNull(session.pendingNonce())
        assertNull(session.refreshToken("45365"))
        assertNull(session.refreshToken("45366"))
    }

    @Test
    fun clearingTheAuthorisationInFlightKeepsRefreshTokens() {
        val (session, _) = session()
        session.saveAuthorisationInFlight(consentId = "45365", state = "s", nonce = "n")
        session.saveRefreshToken("45365", "token-a")

        session.clearAuthorisationInFlight()

        assertNull(session.pendingConsentId())
        assertEquals("token-a", session.refreshToken("45365"))
    }

    @Test
    fun matchesOnlyTheStateThatWasSent() {
        val (session, _) = session()
        session.saveAuthorisationInFlight(consentId = "45365", state = "expected", nonce = "n")

        assertTrue(session.matchesPendingState("expected"))
        assertFalse(session.matchesPendingState("other"))
        assertFalse(session.matchesPendingState(null))
        assertFalse(session.matchesPendingState(""))
    }

    @Test
    fun matchesNoStateWhenNoAuthorisationIsInFlight() {
        val (session, _) = session()

        assertFalse(session.matchesPendingState("anything"))
    }
}
