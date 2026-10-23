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
    fun recordsTheAuthorisationInFlight() {
        val (session, _) = session()

        session.saveAuthorisationInFlight(consentId = "45365", state = "s", flowId = "f", authorizeUrl = "https://a")

        assertEquals("45365", session.pendingConsentId())
        assertEquals("f", session.pendingFlowId())
        assertEquals("https://a", session.pendingAuthorizeUrl())
    }

    @Test
    fun clearingTheAuthorisationInFlightForgetsEveryPart() {
        val (session, _) = session()
        session.saveAuthorisationInFlight(consentId = "45365", state = "s", flowId = "f", authorizeUrl = "https://a")

        session.clearAuthorisationInFlight()

        assertNull(session.pendingConsentId())
        assertNull(session.pendingFlowId())
        assertNull(session.pendingAuthorizeUrl())
        assertFalse(session.matchesPendingState("s"))
    }

    @Test
    fun clearAlsoPurgesRefreshTokensAPreBffBuildLeftOnTheDevice() {
        val (session, settings) = session()
        settings.putString("vrp_refresh_token:45365", "legacy-a")
        settings.putString("vrp_refresh_token:45366", "legacy-b")
        settings.putString("unrelated", "kept")
        session.saveAuthorisationInFlight(consentId = "45365", state = "s", flowId = "f", authorizeUrl = "https://a")

        session.clear()

        assertNull(session.pendingConsentId())
        assertTrue(settings.keys.none { it.startsWith("vrp_refresh_token:") })
        assertEquals("kept", settings.getStringOrNull("unrelated"))
    }

    @Test
    fun matchesOnlyTheStateThatWasSent() {
        val (session, _) = session()
        session.saveAuthorisationInFlight(consentId = "45365", state = "expected", flowId = "f", authorizeUrl = "https://a")

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
