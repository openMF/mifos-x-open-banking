/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.paymentconsent.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.mifosx.openbanking.core.data.banking.PaymentHistoryRepository
import org.mifosx.openbanking.core.data.banking.ScheduledPaymentInitiationRepository
import org.mifosx.openbanking.core.data.banking.SinglePaymentInitiationRepository
import org.mifosx.openbanking.core.data.callback.PaymentAuthRepository
import org.mifosx.openbanking.core.data.callback.PaymentAuthValidation
import org.mifosx.openbanking.core.data.util.toThrowable
import org.mifosx.openbanking.core.model.banking.payment.PaymentDraft
import org.mifosx.openbanking.core.model.banking.payment.ScheduledPaymentDraft
import template.core.base.network.NetworkResult
import template.core.base.ui.viewmodel.BaseViewModel

/**
 * The consent statuses HSBC actually returns, all four of them, in the short form they arrive in.
 *
 * Captured traffic shows only `AWAU`, `AUTH`, `COND` and `RJCT`. The long spellings are accepted
 * defensively — they cost nothing and a bank changing its mind about verbosity should not break the
 * happy path — but they have never been observed on the wire, so nothing is inferred from them.
 */
private val AUTHORISED_STATUSES = setOf("AUTH", "AUTHORISED", "AUTHORIZED")

/**
 * Consumed: the consent has been spent and the payment resource created.
 *
 * A *success* signal, not a stall, which is why it is handled rather than left to the poll. See
 * [PaymentConsentUiState.AlreadySubmitted].
 */
private const val STATUS_CONSUMED = "COND"

/** Rejected: the PSU declined at the bank. Terminal, and nothing was created. */
private const val STATUS_REJECTED = "RJCT"

/**
 * How many times the consent is read back before the wait is called off.
 *
 * The first read happens automatically on return from the bank; the remaining two are the customer
 * tapping Check again. Bounded rather than open-ended because a consent that is still not authorised
 * after three looks is not one the customer can unstick by looking a fourth time — the authorisation
 * did not complete at the bank, and starting again is the only thing that helps.
 */
private const val MAX_STATUS_CHECKS = 3

/**
 * The return leg of a payment authorisation, and the screen that completes the payment.
 *
 * It validates the redirect, exchanges the code for a payments-scoped token, confirms the consent
 * reached `Authorised`, checks funds and submits — the whole tail of the journey, in one place.
 *
 * Submitting here rather than handing back to send-money is not a stylistic choice. Returning to the
 * authenticated graph pops it without saving state, so the send-money ViewModel that built the draft
 * is rebuilt empty and its `submitPayment` finds nothing to send. This screen, by contrast, is alive
 * at the moment the tokens arrive — it just exchanged them — and reads the staged draft back from
 * storage, which is what keeps the submitted `Initiation` byte-identical to the staged one.
 *
 * Every terminal outcome clears the authorisation, so a finished or abandoned payment leaves no
 * consent id, PSU token or draft behind for the next one to find.
 */
class PaymentConsentViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: PaymentAuthRepository,
    private val paymentInitiationRepository: SinglePaymentInitiationRepository,
    private val scheduledPaymentInitiationRepository: ScheduledPaymentInitiationRepository,
    private val paymentHistoryRepository: PaymentHistoryRepository,
) : BaseViewModel<PaymentConsentState, PaymentConsentEvent, PaymentConsentAction>(
    initialState = PaymentConsentState(),
) {

    private val redirectUrl: String = savedStateHandle.get<String>(REDIRECT_URL_ARG).orEmpty()

    private var authorizationCode: String = ""

    /** Unproductive consent reads so far — the ones that came back not yet authorised. */
    private var unauthorisedStatusChecks: Int = 0

    init {
        validate()
    }

    override fun handleAction(action: PaymentConsentAction) {
        when (action) {
            PaymentConsentAction.CheckAgain -> checkConsentStatus()
            PaymentConsentAction.RetryAuthorisation -> restartAuthorisation()
            PaymentConsentAction.AbandonPayment -> abandon()
        }
    }

    /**
     * `state`/`nonce` are checked before anything else and a mismatch is terminal — there is no
     * retry offered for it, because a callback that does not match the authorisation this app
     * launched is not a transient fault.
     *
     * A callback with *nothing* pending is separated out on purpose. It is usually a link the
     * customer already used successfully — the session is cleared on submission — so it gets the
     * benign "there was no payment waiting" copy rather than the security one. Still terminal:
     * there is no authorisation left to carry on with either way.
     */
    private fun validate() {
        when (val result = repository.validateCallback(redirectUrl)) {
            is PaymentAuthValidation.Valid -> {
                authorizationCode = result.code
                updateState { copy(consentId = result.consentId) }
                exchange()
            }

            PaymentAuthValidation.NoPending -> fail(PaymentConsentErrorKind.NoPendingAuthorisation)
            PaymentAuthValidation.SecurityError -> fail(PaymentConsentErrorKind.StateMismatch)
            PaymentAuthValidation.AccessDenied -> fail(PaymentConsentErrorKind.ConsentRejected)
            PaymentAuthValidation.MissingCode -> fail(PaymentConsentErrorKind.CodeExpired)
            is PaymentAuthValidation.Error -> fail(PaymentConsentErrorKind.NetworkError)
        }
    }

    private fun exchange() {
        updateState { copy(uiState = PaymentConsentUiState.Exchanging) }
        viewModelScope.launch {
            when (val result = repository.exchangeCode(authorizationCode)) {
                is NetworkResult.Success -> checkConsentStatus()
                is NetworkResult.Error ->
                    failFromResponse(result.error.toThrowable())
            }
        }
    }

    /**
     * All four statuses the bank returns are answered here, and only `AWAU` means keep waiting.
     *
     * Leaving `COND` and `RJCT` to fall through to the poll was a real defect, not an omission of
     * polish. Both are terminal, so both ran the wait out and reported
     * [PaymentConsentErrorKind.AuthorisationTimedOut] — which tells the customer no money has been
     * moved. For `RJCT` that happens to be true but says the wrong thing; for `COND` it is false,
     * because the bank has already created the payment.
     */
    private fun checkConsentStatus() {
        updateState { copy(uiState = PaymentConsentUiState.Checking()) }
        viewModelScope.launch {
            when (val result = repository.consentStatus(state.consentId)) {
                is NetworkResult.Success -> when (result.data.trim().uppercase()) {
                    in AUTHORISED_STATUSES -> authoriseAndSubmit()
                    STATUS_CONSUMED -> concludeAsAlreadySubmitted()
                    STATUS_REJECTED -> fail(PaymentConsentErrorKind.ConsentRejected)
                    // `AWAU`, and anything unrecognised: waiting is the conservative default.
                    else -> keepWaitingOrGiveUp()
                }

                is NetworkResult.Error ->
                    failFromResponse(result.error.toThrowable())
            }
        }
    }

    private suspend fun authoriseAndSubmit() {
        // Stamped before submitting, so the timeline records approval at the moment it was observed
        // rather than at whatever time the submission happens to land.
        repository.recordApproved()
        updateState { copy(uiState = PaymentConsentUiState.Approved) }
        completePayment()
    }

    /**
     * Ends the journey without writing a failure row.
     *
     * `COND` means the bank created the payment, so recording a failure would file a payment that
     * succeeded as one that did not — and the customer would then meet it twice in their history,
     * once wrongly. The session is still cleared, because this authorisation is spent either way.
     */
    private fun concludeAsAlreadySubmitted() {
        repository.discardAuthorisation()
        updateState { copy(uiState = PaymentConsentUiState.AlreadySubmitted) }
    }

    /**
     * The producer for [PaymentConsentErrorKind.AuthorisationTimedOut].
     *
     * The wait is bounded rather than endless. A consent that is still not authorised after
     * [MAX_STATUS_CHECKS] looks did not complete at the bank, and leaving the customer to tap Check
     * again forever hides that behind a spinner. Timing out says so, and offers the two exits that
     * actually help. Nothing has been submitted at this point, so the outcome is clean.
     */
    private fun keepWaitingOrGiveUp() {
        unauthorisedStatusChecks++
        if (unauthorisedStatusChecks >= MAX_STATUS_CHECKS) {
            fail(PaymentConsentErrorKind.AuthorisationTimedOut)
        } else {
            updateState { copy(uiState = PaymentConsentUiState.Checking(canCheckAgain = true)) }
        }
    }

    /**
     * Confirms funds, then submits the staged instruction.
     *
     * A missing draft is terminal rather than a prompt to rebuild one: the consent was granted
     * against a specific `Initiation`, and anything reconstructed here would be a different
     * instruction wearing the same consent.
     */
    private suspend fun completePayment() {
        // A scheduled instruction is stored under its own key, so ask for it first: finding one is
        // what says this journey skips funds confirmation.
        scheduledPaymentInitiationRepository.stagedDraft()?.let { return submitScheduled(it) }

        val draft = paymentInitiationRepository.stagedDraft()
            ?: return fail(PaymentConsentErrorKind.NoStagedPayment)

        updateState { copy(uiState = PaymentConsentUiState.ConfirmingFunds) }

        when (val funds = paymentInitiationRepository.confirmFunds(state.consentId)) {
            is NetworkResult.Success ->
                if (funds.data) {
                    submit(draft)
                } else {
                    fail(PaymentConsentErrorKind.InsufficientFunds)
                }

            is NetworkResult.Error -> failFromResponse(funds.error.toThrowable())
        }
    }

    /**
     * The scheduled journey: authorised straight to created, with no funds confirmation.
     *
     * There is no endpoint to ask. OBIE defines none for domestic-scheduled and HSBC marks the
     * international-scheduled one unsupported for every brand, so [PaymentConsentUiState
     * .ConfirmingFunds] is never entered on this path — it is no longer a stage every payment passes
     * through.
     *
     * Nothing about this fails to compile if it is wrong, and no shared test covers it, which is why
     * it is stated here rather than left to read as an omission.
     */
    private suspend fun submitScheduled(draft: ScheduledPaymentDraft) {
        updateState { copy(uiState = PaymentConsentUiState.Submitting) }

        when (val result = scheduledPaymentInitiationRepository.submitPayment(draft, state.consentId)) {
            is NetworkResult.Success -> {
                repository.discardAuthorisation()
                sendEvent(PaymentConsentEvent.PaymentSubmitted(result.data.domesticPaymentId))
            }

            is NetworkResult.Error -> failFromSubmission(result.error.toThrowable())
        }
    }

    private suspend fun submit(draft: PaymentDraft) {
        updateState { copy(uiState = PaymentConsentUiState.Submitting) }

        when (val result = paymentInitiationRepository.submitPayment(draft, state.consentId)) {
            is NetworkResult.Success -> {
                repository.discardAuthorisation()
                sendEvent(PaymentConsentEvent.PaymentSubmitted(result.data.domesticPaymentId))
            }

            is NetworkResult.Error -> failFromSubmission(result.error.toThrowable())
        }
    }

    /**
     * Starting again means starting from the form: the consent this screen was handed is spent, and
     * a fresh payment needs a fresh consent staged under fresh keys.
     */
    private fun restartAuthorisation() {
        repository.discardAuthorisation()
        sendEvent(PaymentConsentEvent.RestartAuthorisation)
    }

    private fun abandon() {
        repository.discardAuthorisation()
        sendEvent(PaymentConsentEvent.Abandoned)
    }

    /**
     * Every failure is terminal for this authorisation, so the session goes with it. Leaving the
     * PSU token behind would let a later payment submit on a credential its own authorisation never
     * issued.
     *
     * [detail] is written to history alongside the kind. The bank's code and message were parsed off
     * the wire and dropped before this, which left every rejected payment looking identical after
     * the fact.
     */
    private fun fail(kind: PaymentConsentErrorKind, detail: PaymentConsentErrorDetail? = null) {
        // Read the drafts before discarding the session — discard clears both.
        //
        // Both are asked for, and only one can be present. Asking for just the immediate draft would
        // leave a failed scheduled payment with no history row at all: the row would simply never be
        // written, silently, and the hub would show nothing where a failure belongs.
        val scheduled = scheduledPaymentInitiationRepository.stagedDraft()
        val draft = if (scheduled == null) paymentInitiationRepository.stagedDraft() else null
        repository.discardAuthorisation()

        // Exactly one row, and the scheduled draft wins when both somehow answer. Only one can be
        // real — staging clears the session first on both repositories — so two rows would describe
        // one failure twice. Asking the scheduled side first is what stops a scheduled failure being
        // recorded under the immediate shape, where the hub would read back the wrong endpoint for it.
        if (scheduled != null) {
            viewModelScope.launch {
                paymentHistoryRepository.saveFailed(
                    draft = scheduled,
                    errorKind = kind.name,
                    errorDescription = kind.description(detail),
                )
            }
        } else if (draft != null) {
            viewModelScope.launch {
                paymentHistoryRepository.saveFailed(
                    draft = draft,
                    errorKind = kind.name,
                    errorDescription = kind.description(detail),
                )
            }
        }

        updateState { copy(uiState = PaymentConsentUiState.Error(kind, detail)) }
    }

    /**
     * Fails on the authorisation leg, keeping whatever the bank said about it.
     *
     * The throwable is classified and mined in one place so the two cannot drift apart — a kind that
     * says "the bank refused this" beside a panel that shows none of its reasons is the state this
     * whole change exists to remove.
     */
    private fun failFromResponse(throwable: Throwable) =
        fail(classifyPaymentConsentError(throwable), errorDetailOf(throwable))

    /** As [failFromResponse], for the submission — where a decode failure means something else. */
    private fun failFromSubmission(throwable: Throwable) =
        fail(classifySubmissionError(throwable), errorDetailOf(throwable))

    companion object {
        /** Must match the `PaymentConsentRoute` property name — type-safe nav uses it as the key. */
        const val REDIRECT_URL_ARG: String = "redirectUrl"
    }
}
