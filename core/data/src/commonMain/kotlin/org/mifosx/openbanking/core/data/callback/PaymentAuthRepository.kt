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

import org.mifosx.openbanking.core.model.banking.payment.ConsentType
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult

/** The outcome of checking a redirect that came back from a payment authorisation. */
sealed interface PaymentAuthValidation {
    /** Authentic. [code] is the authorization code to exchange, against consent [consentId]. */
    data class Valid(val code: String, val consentId: String) : PaymentAuthValidation

    /** `state` or `nonce` did not match what was issued — treat as hostile, never as a retry. */
    data object SecurityError : PaymentAuthValidation

    /**
     * Nothing was waiting to be authorised.
     *
     * Deliberately not a [SecurityError]. Absence of a session is not evidence of tampering — it is
     * absence of evidence either way, and there is nothing here that a replay would have had to
     * forge. The tampering signal is a `state` that is present and *wrong*, which stays
     * [SecurityError].
     *
     * Note what this is **not**: re-opening the callback link of a payment that already succeeded
     * does not reach here through the redirect bus, because [isPaymentRedirect] answers false with
     * no pending consent and the host routes such a redirect to the sign-in leg instead. What does
     * reach here is a re-validation against a session that has since been cleared — most plausibly
     * a navigation restore after process death, where the redirect URL survives in saved state and
     * is checked again. Direct callers of [validateCallback] can produce it too.
     *
     * Terminal all the same: with no pending authorisation there is nothing to carry on with.
     */
    data object NoPending : PaymentAuthValidation

    /** The PSU declined at the bank. Not an error; the payment simply does not proceed. */
    data object AccessDenied : PaymentAuthValidation

    data object MissingCode : PaymentAuthValidation

    data class Error(val message: String) : PaymentAuthValidation
}

/**
 * The payment authorisation leg — the app-to-app hop that turns a staged consent into one the PSU
 * has approved.
 *
 * Structurally the sign-in leg's twin, and deliberately not the same object: this exchanges on
 * `scope=payments` and stores the result in [PaymentAuthSession], leaving the AIS session alone.
 * Routing a payment authorisation through [ConsentCallbackRepository] would overwrite the account
 * bearer with a payments-scoped one and break every read in the app.
 */
interface PaymentAuthRepository {

    /**
     * Whether [redirectUrl] belongs to a payment authorisation rather than a sign-in.
     *
     * Both legs come back through the same `ConsentRedirectBus` on the same registered redirect URI,
     * so something has to tell them apart before either is processed — a payment return handled as a
     * sign-in would overwrite the account bearer with a payments-scoped token.
     *
     * Answered by matching the redirect's `state` against the one issued for the payment
     * authorisation in flight. That is a value only this leg could have minted, so a sign-in return
     * can never satisfy it. Cheap and side-effect free: it consumes nothing, leaving the real
     * validation to [validateCallback].
     */
    fun isPaymentRedirect(redirectUrl: String): Boolean

    /**
     * Whether [redirectUrl] is the authentic return leg of the authorisation in flight.
     *
     * Checked against the `state` and `nonce` held in [PaymentAuthSession]; the caller never has to
     * parse the URL or reach into storage itself.
     */
    fun validateCallback(redirectUrl: String): PaymentAuthValidation

    /**
     * Exchanges [code] for the payments-scoped PSU token and stores it in [PaymentAuthSession].
     *
     * A successful exchange is what makes funds confirmation and submission possible.
     */
    suspend fun exchangeCode(code: String): NetworkResult<Unit, NetworkError>

    /**
     * Reads the consent's status back after authorisation.
     *
     * Load-bearing rather than cosmetic: the bank refuses a submission against a consent that has
     * not reached `AUTH`, so this is how the flow knows it may proceed. No specific OBIE error code
     * is cited here on purpose — the one previously named appears in no captured response, only in
     * a secondary note, and gating on an unobserved code would be guessing dressed as evidence.
     */
    suspend fun consentStatus(consentId: String): NetworkResult<String, NetworkError>

    /**
     * Which product the authorisation in flight belongs to, or null when it cannot be established.
     *
     * The return leg needs this to decide which repository holds the staged instruction. Asking each
     * repository in turn instead — the shape this replaced — lets call order stand in for the answer:
     * the first non-null draft wins whether or not it belongs to the product that actually came back,
     * and nothing about that fails to compile.
     *
     * Null is not a default to fall back on. It means this build cannot say, and submitting anything
     * on that basis would send one product's instruction to another product's endpoint.
     */
    fun pendingConsentType(): ConsentType?

    /**
     * Forgets the authorisation and everything staged under it.
     *
     * Called at each of the three points a payment stops being in flight — submitted, failed, or
     * abandoned — so a finished attempt leaves no consent id, PSU token or draft behind for the next
     * one to find. Leaves the AIS session untouched, so the PSU stays signed in.
     */
    /**
     * Records that the consent has reached an authorised status, at this moment.
     *
     * The caller decides what counts as authorised, because that is a status-code judgement; this
     * only stamps the time. OBIE reports no per-stage history, so without this the payment detail
     * timeline could show approval happening only by inference from a later event.
     */
    fun recordApproved()

    fun discardAuthorisation()
}
