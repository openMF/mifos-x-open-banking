/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
@file:Suppress("MatchingDeclarationName")

package org.mifosx.openbanking.feature.vrpcallback.callback

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.mifosx.openbanking.core.data.util.obieSupportReference
import org.mifosx.openbanking.core.data.vrp.VrpAuthRepository
import org.mifosx.openbanking.core.data.vrp.VrpAuthValidation
import org.mifosx.openbanking.core.data.vrp.VrpConsentRepository
import org.mifosx.openbanking.core.model.vrp.VrpConsent
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult
import template.core.base.ui.viewmodel.BackgroundEvent
import template.core.base.ui.viewmodel.BaseViewModel

/** The scheme a payer must be in for a payment under this consent to be accepted. */
private const val SORT_CODE_ACCOUNT_NUMBER = "UK.OBIE.SortCodeAccountNumber"

/** The length a sort-code-and-account-number identification always has. */
private const val PAYER_IDENTIFICATION_LENGTH = 14

/**
 * Which step is running.
 *
 * Storing the credential has no stage of its own: it happens inside the exchange call, so
 * [Exchanging] covers both. [Validating] is the state the screen is built with and is replaced
 * before the first frame, since validation makes no call.
 */
enum class CallbackStage {
    Validating,
    Exchanging,
    Confirming,
}

/** Screen state for the return from the bank. */
data class VrpCallbackState(
    val redirectUrl: String,
    val uiState: VrpCallbackUiState = VrpCallbackUiState.Working(CallbackStage.Validating),
)

/** The four rendered states. [Working] is the loading state; there is no separate one. */
sealed interface VrpCallbackUiState {

    data class Working(val stage: CallbackStage) : VrpCallbackUiState

    data class Success(val consentId: String, val payeeName: String) : VrpCallbackUiState

    /**
     * Authorised and stored, but the account chosen at the bank cannot make these payments.
     *
     * Not a failure: nothing went wrong and retrying cannot help.
     */
    data class Unusable(val consentId: String) : VrpCallbackUiState

    data class Failed(
        val kind: CallbackErrorKind,
        val supportReference: String = "",
    ) : VrpCallbackUiState
}

/**
 * Why the return did not finish, named by the step that failed.
 *
 * The customer has already approved at their bank by the time most of these can happen, so a single
 * "something went wrong" would be the worst available message.
 */
enum class CallbackErrorKind {
    /** `state` or `nonce` did not match. Nothing was exchanged. */
    CallbackInvalid,

    /** The bank would not exchange the code. Expiry is the dominant cause. */
    CodeExpiredOrUsed,

    /**
     * The bank has a live consent and this app could not store the credential.
     *
     * Unrecoverable and invisible afterwards, so it is never folded into a generic failure.
     */
    AuthorityNotSaved,

    /** The credential is stored and only the read-back failed. The consent is usable. */
    ConfirmationFailed,

    NetworkUnavailable,
}

/** Whether this failure left a usable VRP behind. */
val CallbackErrorKind.leavesAUsableConsent: Boolean
    get() = this == CallbackErrorKind.ConfirmationFailed

/** Actions the view model owns. There is no retry: the code is single-use and spent. */
sealed interface VrpCallbackAction {

    /** Leave for the list. Back does the same. */
    data object Dismiss : VrpCallbackAction

    /** Leave for setup, offered only where starting again can help. */
    data object StartAgain : VrpCallbackAction

    /** Results the view model raises for itself. Never applied where they arrive. */
    sealed interface Internal : VrpCallbackAction {

        data class ReceiveCredentialResult(
            val consentId: String,
            val result: NetworkResult<Unit, NetworkError>,
        ) : Internal

        data class ReceiveConsentResult(
            val consentId: String,
            val result: NetworkResult<VrpConsent, NetworkError>,
        ) : Internal
    }
}

/**
 * One-shot instructions for the host.
 *
 * Every event is a [BackgroundEvent]: an ordinary event is dropped while the app is not resumed, and
 * a customer who approves at their bank and switches apps is the normal case, not an edge one.
 */
sealed interface VrpCallbackEvent : BackgroundEvent {

    /** The VRP is stored. Emitted only after the credential is persisted. */
    data class Completed(val consentId: String) : VrpCallbackEvent

    data object Abandoned : VrpCallbackEvent
}

/**
 * Completes the return from the bank in four ordered steps.
 *
 * The credential is persisted before completion is signalled. Completion navigates away, and a write
 * started after that can be cancelled — leaving an authorised consent whose refresh token was never
 * stored, which nothing afterwards can detect or repair.
 */
class VrpCallbackViewModel(
    savedStateHandle: SavedStateHandle,
    private val auth: VrpAuthRepository,
    private val consents: VrpConsentRepository,
) : BaseViewModel<VrpCallbackState, VrpCallbackEvent, VrpCallbackAction>(
    initialState = VrpCallbackState(
        redirectUrl = savedStateHandle.get<String>(REDIRECT_URL_ARG).orEmpty(),
    ),
) {

    /**
     * The consent whose credential is stored, once step 3 has run.
     *
     * Held here because a failed read-back reports no consent of its own, yet the VRP exists and is
     * usable — leaving without it would strand a consent the customer had already approved.
     */
    private var storedConsentId: String? = null

    init {
        validate()
    }

    override fun handleAction(action: VrpCallbackAction) {
        when (action) {
            VrpCallbackAction.Dismiss -> leave()
            VrpCallbackAction.StartAgain -> sendEvent(VrpCallbackEvent.Abandoned)

            is VrpCallbackAction.Internal.ReceiveCredentialResult ->
                applyCredentialResult(action.consentId, action.result)

            is VrpCallbackAction.Internal.ReceiveConsentResult ->
                applyConsentResult(action.consentId, action.result)
        }
    }

    /** Step 1: check the redirect against the authorisation in flight, exchanging nothing yet. */
    private fun validate() {
        when (val outcome = auth.validateCallback(state.redirectUrl)) {
            is VrpAuthValidation.Valid -> exchangeAndPersist(outcome.code, outcome.consentId)

            VrpAuthValidation.AccessDenied,
            VrpAuthValidation.NoPending,
            -> fail(CallbackErrorKind.CallbackInvalid)

            VrpAuthValidation.SecurityError,
            VrpAuthValidation.MissingCode,
            -> fail(CallbackErrorKind.CallbackInvalid)

            is VrpAuthValidation.Error -> fail(CallbackErrorKind.CallbackInvalid)
        }
    }

    /** Steps 2 and 3: exchange the code, then store the credential before anything else happens. */
    private fun exchangeAndPersist(code: String, consentId: String) {
        moveTo(CallbackStage.Exchanging)
        viewModelScope.launch {
            val result = auth.exchangeAndPersistCredential(code, consentId)
            sendAction(VrpCallbackAction.Internal.ReceiveCredentialResult(consentId, result))
        }
    }

    private fun applyCredentialResult(
        consentId: String,
        result: NetworkResult<Unit, NetworkError>,
    ) {
        when (result) {
            is NetworkResult.Success -> {
                storedConsentId = consentId
                confirm(consentId)
            }

            is NetworkResult.Error -> fail(result.error.toCredentialFailure(), result.error)
        }
    }

    /** Step 4: read the consent back, which stores the row and reveals an unusable payer. */
    private fun confirm(consentId: String) {
        moveTo(CallbackStage.Confirming)
        viewModelScope.launch {
            val result = consents.refreshStatus(consentId)
            sendAction(VrpCallbackAction.Internal.ReceiveConsentResult(consentId, result))
        }
    }

    private fun applyConsentResult(
        consentId: String,
        result: NetworkResult<VrpConsent, NetworkError>,
    ) {
        val uiState = when (result) {
            is NetworkResult.Error -> VrpCallbackUiState.Failed(CallbackErrorKind.ConfirmationFailed)

            is NetworkResult.Success -> if (result.data.canBePaidUnder()) {
                VrpCallbackUiState.Success(consentId, result.data.payee.name)
            } else {
                VrpCallbackUiState.Unusable(consentId)
            }
        }
        updateState { copy(uiState = uiState) }
    }

    /**
     * Leaves the screen, saying whether a VRP was stored.
     *
     * The credential is already persisted by the time any of these states render, so a failed
     * read-back still reports completion — the consent is usable and will appear in the list.
     */
    private fun leave() {
        val consentId = when (val uiState = state.uiState) {
            is VrpCallbackUiState.Success -> uiState.consentId
            is VrpCallbackUiState.Unusable -> uiState.consentId
            is VrpCallbackUiState.Failed -> if (uiState.kind.leavesAUsableConsent) storedConsentId else null
            is VrpCallbackUiState.Working -> null
        }

        sendEvent(
            consentId
                ?.let { VrpCallbackEvent.Completed(it) }
                ?: VrpCallbackEvent.Abandoned,
        )
    }

    private fun moveTo(stage: CallbackStage) {
        updateState { copy(uiState = VrpCallbackUiState.Working(stage)) }
    }

    private fun fail(kind: CallbackErrorKind, error: NetworkError? = null) {
        updateState {
            copy(
                uiState = VrpCallbackUiState.Failed(
                    kind = kind,
                    supportReference = error?.obieSupportReference().orEmpty(),
                ),
            )
        }
    }

    companion object {
        /** Must match the [VrpCallbackRoute] property name — type-safe nav uses it as the key. */
        const val REDIRECT_URL_ARG: String = "redirectUrl"
    }
}

/**
 * Which step a failed exchange belongs to.
 *
 * A transport failure could have left the bank with a live consent, so it is reported as the
 * unrecoverable case rather than as a plain network error.
 */
private fun NetworkError.toCredentialFailure(): CallbackErrorKind = when (this) {
    is NetworkError.Network -> CallbackErrorKind.NetworkUnavailable
    is NetworkError.Client.BadRequest -> CallbackErrorKind.CodeExpiredOrUsed
    is NetworkError.Client.Unauthorized -> CallbackErrorKind.CodeExpiredOrUsed
    else -> CallbackErrorKind.AuthorityNotSaved
}

/**
 * Whether a payment under this consent can be accepted.
 *
 * A payer chosen at the bank that is not a sort code and account number — a card — makes every
 * payment fail permanently.
 */
private fun VrpConsent.canBePaidUnder(): Boolean {
    val chosenPayer = payer ?: return true
    return chosenPayer.schemeName == SORT_CODE_ACCOUNT_NUMBER &&
        chosenPayer.identification.length == PAYER_IDENTIFICATION_LENGTH
}
