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

/**
 * Secure storage for VRP authorisation state and credentials.
 *
 * Separate from the account-access and payment sessions: those hold one set of credentials for the
 * whole app, this holds one refresh token per consent.
 */
interface VrpAuthSession {

    /** Records the consent being authorised and the values the callback must echo back. */
    fun saveAuthorisationInFlight(consentId: String, state: String, nonce: String)

    /** The consent currently being authorised, or null when none is. */
    fun pendingConsentId(): String?

    /** The nonce sent with the authorisation currently in flight. */
    fun pendingNonce(): String?

    /** Whether [state] is the one sent with the authorisation currently in flight. */
    fun matchesPendingState(state: String?): Boolean

    /** Drops the authorisation in flight, leaving stored refresh tokens alone. */
    fun clearAuthorisationInFlight()

    /**
     * Stores the refresh token for [consentId].
     *
     * Does nothing when one is already stored for that consent.
     */
    fun saveRefreshToken(consentId: String, refreshToken: String)

    /** The refresh token for [consentId], or null when none is stored. */
    fun refreshToken(consentId: String): String?

    /** Removes the refresh token for [consentId]. */
    fun removeRefreshToken(consentId: String)

    /** Removes the authorisation in flight and every stored refresh token. */
    fun clear()
}
