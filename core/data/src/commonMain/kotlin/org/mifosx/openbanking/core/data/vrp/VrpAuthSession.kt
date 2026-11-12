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
 * Secure storage for the VRP authorisation in flight.
 *
 * Holds no credential: the Worker keeps each consent's refresh token on the app's session and
 * refreshes it itself. All that survives the browser hop is what the redirect is matched and redeemed
 * against — the consent, the authorize URL's `state`, and the Worker's pending `flowId`.
 */
interface VrpAuthSession {

    /** Records a staged consent awaiting the customer's approval at [authorizeUrl]. */
    fun saveAuthorisationInFlight(consentId: String, state: String, flowId: String, authorizeUrl: String)

    /** The consent currently being authorised, or null when none is. */
    fun pendingConsentId(): String?

    /** The Worker's pending flow for the authorisation in flight. */
    fun pendingFlowId(): String?

    /** The Worker-signed URL the customer opens to approve the consent in flight. */
    fun pendingAuthorizeUrl(): String?

    /** Whether [state] is the one sent with the authorisation currently in flight. */
    fun matchesPendingState(state: String?): Boolean

    /** Drops the authorisation in flight. */
    fun clearAuthorisationInFlight()

    /** Drops everything this holds. */
    fun clear()
}
