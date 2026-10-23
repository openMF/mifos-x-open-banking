/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.consentcallback.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mifosx.openbanking.core.data.callback.CallbackSecurityRejected
import org.mifosx.openbanking.core.data.callback.ConsentCallbackRepository
import org.mifosx.openbanking.core.data.callback.ConsentRejectedAtBank
import org.mifosx.openbanking.core.data.callback.ConsentSession
import org.mifosx.openbanking.core.data.callback.ValidationResult
import org.mifosx.openbanking.core.network.api.CompletedAuth
import template.core.base.common.screen.ScreenState
import template.core.base.ui.viewmodel.BackgroundEvent
import template.core.base.ui.viewmodel.BaseViewModel

/** How long the success screen lingers before the navigator swaps to Home. */
private const val NAVIGATION_DELAY_MS = 1500L

sealed interface ConsentCallbackUiState {
    data object Loading : ConsentCallbackUiState
    data object Content : ConsentCallbackUiState
    data object Awaiting : ConsentCallbackUiState
    data class Error(val message: String) : ConsentCallbackUiState
    data object AccessDenied : ConsentCallbackUiState
    data object SecurityError : ConsentCallbackUiState
}

sealed interface ConsentCallbackEvent {
    data object NavigateToHome : ConsentCallbackEvent, BackgroundEvent
    data object NavigateToLogin : ConsentCallbackEvent, BackgroundEvent
}

sealed interface ConsentCallbackAction {
    /**
     * The raw redirect URL as the OS handed it to us. Parsing and matching belong to
     * [ConsentCallbackRepository], which alone holds the expected `state` and the Worker's flow.
     */
    data class ProcessCallback(val redirectUrl: String) : ConsentCallbackAction
    data object PollConsentStatus : ConsentCallbackAction
    data object NavigateRetry : ConsentCallbackAction
    data object NavigateLogin : ConsentCallbackAction
}

class ConsentCallbackViewModel(
    private val repository: ConsentCallbackRepository,
    private val consentSession: ConsentSession,
) : BaseViewModel<ConsentCallbackUiState, ConsentCallbackEvent, ConsentCallbackAction>(
    initialState = ConsentCallbackUiState.Loading,
) {
    private var pendingConsentId: String? = null

    override fun handleAction(action: ConsentCallbackAction) {
        when (action) {
            is ConsentCallbackAction.ProcessCallback -> process(action)
            ConsentCallbackAction.PollConsentStatus -> repoll()
            ConsentCallbackAction.NavigateRetry -> {
                sendEvent(ConsentCallbackEvent.NavigateToLogin)
            }
            ConsentCallbackAction.NavigateLogin -> {
                consentSession.clear()
                sendEvent(ConsentCallbackEvent.NavigateToLogin)
            }
        }
    }

    private fun process(action: ConsentCallbackAction.ProcessCallback) {
        updateState { ConsentCallbackUiState.Loading }

        when (val v = repository.validateCallback(action.redirectUrl)) {
            ValidationResult.SecurityError ->
                updateState { ConsentCallbackUiState.SecurityError }

            ValidationResult.AccessDenied ->
                updateState { ConsentCallbackUiState.AccessDenied }

            is ValidationResult.Error ->
                updateState { ConsentCallbackUiState.Error(v.message) }

            ValidationResult.MissingCode ->
                updateState { ConsentCallbackUiState.Error("No authorisation code received.") }

            is ValidationResult.Valid -> {
                pendingConsentId = v.consentId
                viewModelScope.launch {
                    executeExchange(v)
                }
            }
        }
    }

    private suspend fun executeExchange(callback: ValidationResult.Valid) {
        when (val result = repository.exchangeCode(callback)) {
            is ScreenState.Content -> completeSignIn(result.data)

            is ScreenState.Error -> when (val error = result.error) {
                is CallbackSecurityRejected -> updateState { ConsentCallbackUiState.SecurityError }
                is ConsentRejectedAtBank -> {
                    consentSession.clear()
                    updateState { ConsentCallbackUiState.Error(error.message.orEmpty()) }
                }
                else -> updateState { ConsentCallbackUiState.Error(error.message ?: "Token exchange failed.") }
            }

            is ScreenState.Unauthenticated ->
                updateState { ConsentCallbackUiState.Error("Authorisation failed.") }

            is ScreenState.NoNetwork ->
                updateState { ConsentCallbackUiState.Error("Unable to reach HSBC.") }

            else -> {}
        }
    }

    /**
     * Completes the sign-in after the Worker issued a session.
     *
     * The Worker only issues one against a consent that is not `Rejected` (409 otherwise), and the
     * status it reports a beat after authorisation can still read `AwaitingAuthorisation` while it
     * settles — a propagation lag, not a pending decision. So the consent id is recorded
     * unconditionally (this is the only writer of `ConsentSession.consentId()`, which the consent
     * screens read), with the expiry the Worker read alongside it.
     *
     * Order is load-bearing: the consent is recorded BEFORE the session is persisted. Persisting the
     * session flips [ConsentSession] active, which the root navigator reacts to by routing to the
     * authenticated graph and tearing this screen (and this coroutine) down.
     */
    private suspend fun completeSignIn(auth: CompletedAuth) {
        consentSession.saveConsentMeta(auth.consentId, auth.consentExpiresAt.orEmpty())
        consentSession.save(auth.session)
        updateState { ConsentCallbackUiState.Content }
        delay(NAVIGATION_DELAY_MS)
        sendEvent(ConsentCallbackEvent.NavigateToHome)
    }

    /** Records the current consent id (best-effort expiry from a single status read). */
    private suspend fun recordConsent() {
        val consentId = pendingConsentId ?: return
        val expiry = (repository.pollConsentStatus(consentId) as? ScreenState.Content)
            ?.data?.expirationDateTime.orEmpty()
        consentSession.saveConsentMeta(consentId, expiry)
    }

    /**
     * A manual re-check from the (now vestigial) awaiting screen: re-records the consent and moves on.
     * A no-op before any callback has produced a pending consent id.
     */
    private fun repoll() {
        if (pendingConsentId == null) return
        viewModelScope.launch {
            recordConsent()
            updateState { ConsentCallbackUiState.Content }
            delay(NAVIGATION_DELAY_MS)
            sendEvent(ConsentCallbackEvent.NavigateToHome)
        }
    }

}
