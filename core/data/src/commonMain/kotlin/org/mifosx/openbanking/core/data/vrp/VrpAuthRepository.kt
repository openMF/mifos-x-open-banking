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

import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult

/** The outcome of checking a redirect that came back from a VRP authorisation. */
sealed interface VrpAuthValidation {

    /** Authentic. [code] is the authorization code to exchange, against consent [consentId]. */
    data class Valid(val code: String, val consentId: String) : VrpAuthValidation

    /** `state` did not match, or no `id_token` came back. Treat as hostile, never as a retry. */
    data object SecurityError : VrpAuthValidation

    /** No VRP authorisation was in flight. Terminal: there is nothing to carry on with. */
    data object NoPending : VrpAuthValidation

    /** The customer declined at the bank. Not an error; the consent stays unapproved. */
    data object AccessDenied : VrpAuthValidation

    /** The redirect carried no authorization code. */
    data object MissingCode : VrpAuthValidation

    /** The bank reported [message]. */
    data class Error(val message: String) : VrpAuthValidation
}

/**
 * The VRP authorisation leg — the hop that turns a staged consent into one the customer approved.
 *
 * Kept apart from the payment and sign-in legs because it ends differently: the Worker keeps this
 * consent's refresh token on the app's session, which is what lets the app pay later without sending
 * the customer back to the bank.
 */
interface VrpAuthRepository {

    /**
     * The Worker-signed URL the customer opens to approve [consentId], recorded when the consent was
     * staged so the redirect can be checked against it.
     */
    suspend fun beginAuthorisation(consentId: String): NetworkResult<String, NetworkError>

    /**
     * Whether [redirectUrl] belongs to a VRP authorisation rather than a sign-in or a payment.
     *
     * All three come back on the same registered redirect URI, so something has to tell them apart
     * before any of them is processed. Answered by matching the redirect's `state` against the one
     * issued here, a value only this leg could have minted. Consumes nothing.
     */
    fun isVrpRedirect(redirectUrl: String): Boolean

    /** Checks [redirectUrl] against the authorisation in flight. */
    fun validateCallback(redirectUrl: String): VrpAuthValidation

    /**
     * Redeems [code] at the Worker, which stores the consent's credentials on the app's session.
     *
     * Separate from reading the consent back so a caller can tell the two apart: a failure here
     * means the customer approved at the bank and this app holds nothing, which is unrecoverable,
     * while a failure reading back leaves a usable consent.
     */
    suspend fun exchangeAndPersistCredential(
        code: String,
        consentId: String,
    ): NetworkResult<Unit, NetworkError>

    /** The consent currently being authorised, or null when none is. */
    fun pendingConsentId(): String?

    /** Drops the authorisation in flight. */
    fun discardAuthorisation()
}
