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

import com.russhwolf.settings.Settings
import kotlinx.serialization.json.Json
import org.mifosx.openbanking.core.model.banking.payment.ConsentType
import org.mifosx.openbanking.core.model.banking.payment.PaymentDraft
import org.mifosx.openbanking.core.model.banking.payment.ScheduledPaymentDraft
import org.mifosx.openbanking.core.network.model.oauth.PsuTokenResponse

/**
 * The payment authorisation leg's own storage — deliberately separate from [ConsentSession].
 *
 * [ConsentSession] models exactly one consent: `consentId()` returns a single string, and the
 * consent screens and the revoke path all read it as *the* connection this device is signed in
 * under. A payment consent is a second consent — short-lived, single-use, and authorised on
 * `scope=payments` — and its token exchange yields a PSU token scoped to payments rather than
 * accounts. Writing either into [ConsentSession] would overwrite the AIS consent id and bearer, and
 * every account read would start failing.
 *
 * So this holds its own keys, and the two never touch. Clearing a payment authorisation leaves the
 * PSU signed in; signing out clears both.
 *
 * The values must survive the browser hop for the same reason [PendingAuthStore]'s do: the PSU
 * leaves the app to authorise at HSBC and the process may be killed while backgrounded, so the
 * redirect can arrive on a cold start.
 */
interface PaymentAuthSession {

    /**
     * Records the authorisation about to be launched, so the returning redirect can be checked.
     *
     * [type] is what the return leg dispatches on. It is recorded here, at the one moment it is
     * known for certain, because nothing in the redirect names it — every consent family authorises
     * through the same URL with the same scope, so the callback is indistinguishable and this
     * session is the only thing that remembers.
     */
    fun savePending(consentId: String, state: String, nonce: String, type: ConsentType)

    /**
     * The consent family of the authorisation in flight, or null when it cannot be established.
     *
     * Null is not "domestic". It means this build cannot say, and a caller that guesses would send
     * a consent id to an endpoint that never issued it.
     */
    fun pendingConsentType(): ConsentType?

    /** The consent id of the authorisation in flight, or null when none is. */
    fun pendingConsentId(): String?

    /** Whether [state] is the one issued for the authorisation in flight. */
    fun matchesPendingState(state: String?): Boolean

    /** The nonce issued for the authorisation in flight. */
    fun pendingNonce(): String?

    /** The payments-scoped PSU token from a completed authorisation. */
    fun paymentToken(): PsuTokenResponse?

    fun savePaymentToken(tokens: PsuTokenResponse)

    /**
     * Records the staged instruction so the leg that returns from the bank can submit it.
     *
     * The screen that built the draft does not survive the hop: returning to the authenticated
     * graph pops it without saving state, so its ViewModel is rebuilt empty. Only the draft that
     * was actually staged may be stored — the submitted `Initiation` has to be byte-identical to
     * the staged one, so a rebuilt equivalent is not a substitute.
     */
    fun saveDraft(draft: PaymentDraft)

    /** The instruction staged for the authorisation in flight, or null when none is. */
    fun draft(): PaymentDraft?

    /**
     * Stores the scheduled instruction awaiting authorisation.
     *
     * A second key rather than a polymorphic one. Sharing `payment_auth_draft` between two shapes
     * would change its on-disk format, and [draft] swallows a decode failure and returns null — so a
     * device upgrading mid-authorisation would silently lose its draft and the return leg would
     * report that nothing was staged.
     */
    fun saveScheduledDraft(draft: ScheduledPaymentDraft)

    /**
     * The scheduled instruction staged for the authorisation in flight, or null when none is.
     *
     * Never both this and [draft]: staging clears the session first, on both repositories.
     */
    fun scheduledDraft(): ScheduledPaymentDraft?

    /**
     * Records when the bank confirmed the PSU's authorisation.
     *
     * Kept here rather than passed down the submit call because the two moments are separated by
     * network calls the history layer does not see. OBIE returns one `CreationDateTime` and no
     * per-stage history, so this is the only way the payment detail timeline can show when approval
     * actually happened rather than inferring it.
     */
    fun saveApprovedAt(instant: String)

    /** When the consent was confirmed authorised, or null if that has not happened yet. */
    fun approvedAt(): String?

    /** Drops everything this holds. Leaves the AIS session in [ConsentSession] untouched. */
    fun clear()
}

class SettingsPaymentAuthSession(
    private val secureSettings: Settings,
) : PaymentAuthSession {

    private val json = Json { ignoreUnknownKeys = true }

    override fun savePending(consentId: String, state: String, nonce: String, type: ConsentType) {
        secureSettings.putString(KEY_CONSENT_ID, consentId)
        secureSettings.putString(KEY_STATE, state)
        secureSettings.putString(KEY_NONCE, nonce)
        secureSettings.putString(KEY_CONSENT_TYPE, type.wireValue)
    }

    /**
     * Falls back to the staged draft only when no type was recorded, and only for a single payment.
     *
     * A session written before this key existed can only be a single payment — nothing else could
     * stage one then — so its draft still answers, and `CurrencyOfTransfer` gives the rail.
     *
     * The scheduled-draft guard is what keeps that reasoning true now that a second product exists.
     * A scheduled session always writes the type key, so reaching the fallback with a scheduled draft
     * present means the type was lost, and the safe answer is "cannot say". Without the guard the
     * fallback would read the *absence* of a single-payment draft as a domestic single payment and
     * send a scheduled consent id to `domestic-payments`.
     */
    override fun pendingConsentType(): ConsentType? =
        ConsentType.fromWire(secureSettings.getStringOrNull(KEY_CONSENT_TYPE))
            ?: if (secureSettings.getStringOrNull(KEY_SCHEDULED_DRAFT) != null) {
                null
            } else {
                draft()?.let {
                    if (it.currencyOfTransfer != null) {
                        ConsentType.InternationalSinglePayment
                    } else {
                        ConsentType.DomesticSinglePayment
                    }
                }
            }

    override fun pendingConsentId(): String? = secureSettings.getStringOrNull(KEY_CONSENT_ID)

    override fun matchesPendingState(state: String?): Boolean {
        val pending = secureSettings.getStringOrNull(KEY_STATE)
        return !pending.isNullOrBlank() && pending == state
    }

    override fun pendingNonce(): String? = secureSettings.getStringOrNull(KEY_NONCE)

    override fun paymentToken(): PsuTokenResponse? {
        val raw = secureSettings.getStringOrNull(KEY_PAYMENT_TOKENS) ?: return null
        return runCatching { json.decodeFromString(PsuTokenResponse.serializer(), raw) }.getOrNull()
    }

    override fun savePaymentToken(tokens: PsuTokenResponse) {
        secureSettings.putString(
            KEY_PAYMENT_TOKENS,
            json.encodeToString(PsuTokenResponse.serializer(), tokens),
        )
    }

    override fun saveDraft(draft: PaymentDraft) {
        secureSettings.putString(KEY_DRAFT, json.encodeToString(PaymentDraft.serializer(), draft))
    }

    override fun draft(): PaymentDraft? {
        val raw = secureSettings.getStringOrNull(KEY_DRAFT) ?: return null
        return runCatching { json.decodeFromString(PaymentDraft.serializer(), raw) }.getOrNull()
    }

    override fun saveScheduledDraft(draft: ScheduledPaymentDraft) {
        secureSettings.putString(
            KEY_SCHEDULED_DRAFT,
            json.encodeToString(ScheduledPaymentDraft.serializer(), draft),
        )
    }

    override fun scheduledDraft(): ScheduledPaymentDraft? {
        val raw = secureSettings.getStringOrNull(KEY_SCHEDULED_DRAFT) ?: return null
        return runCatching { json.decodeFromString(ScheduledPaymentDraft.serializer(), raw) }.getOrNull()
    }

    override fun saveApprovedAt(instant: String) {
        secureSettings.putString(KEY_APPROVED_AT, instant)
    }

    override fun approvedAt(): String? = secureSettings.getStringOrNull(KEY_APPROVED_AT)

    override fun clear() {
        secureSettings.remove(KEY_CONSENT_ID)
        secureSettings.remove(KEY_STATE)
        secureSettings.remove(KEY_NONCE)
        secureSettings.remove(KEY_PAYMENT_TOKENS)
        secureSettings.remove(KEY_DRAFT)
        secureSettings.remove(KEY_SCHEDULED_DRAFT)
        secureSettings.remove(KEY_APPROVED_AT)
        secureSettings.remove(KEY_CONSENT_TYPE)
    }

    private companion object {
        const val KEY_CONSENT_ID = "payment_auth_consent_id"
        const val KEY_STATE = "payment_auth_state"
        const val KEY_NONCE = "payment_auth_nonce"
        const val KEY_PAYMENT_TOKENS = "payment_auth_tokens"
        const val KEY_APPROVED_AT = "payment_auth_approved_at"
        const val KEY_DRAFT = "payment_auth_draft"
        const val KEY_SCHEDULED_DRAFT = "payment_auth_scheduled_draft"
        const val KEY_CONSENT_TYPE = "payment_auth_consent_type"
    }
}
