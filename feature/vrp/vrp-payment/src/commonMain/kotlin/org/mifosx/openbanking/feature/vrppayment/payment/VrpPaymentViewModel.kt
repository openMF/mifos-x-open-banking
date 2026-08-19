/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
@file:Suppress("MatchingDeclarationName", "TooManyFunctions")
@file:OptIn(ExperimentalUuidApi::class)

package org.mifosx.openbanking.feature.vrppayment.payment

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.mifosx.openbanking.core.common.parseMinorUnits
import org.mifosx.openbanking.core.data.util.isOutsideControlParameters
import org.mifosx.openbanking.core.data.util.obieSupportReference
import org.mifosx.openbanking.core.data.vrp.VrpConsentRepository
import org.mifosx.openbanking.core.data.vrp.VrpPaymentRepository
import org.mifosx.openbanking.core.model.banking.payment.PaymentDisposition
import org.mifosx.openbanking.core.model.callback.ConsentStatus
import org.mifosx.openbanking.core.model.vrp.FundsAvailability
import org.mifosx.openbanking.core.model.vrp.Money
import org.mifosx.openbanking.core.model.vrp.PeriodType
import org.mifosx.openbanking.core.model.vrp.PeriodUsage
import org.mifosx.openbanking.core.model.vrp.VrpConsent
import org.mifosx.openbanking.core.model.vrp.VrpPayment
import org.mifosx.openbanking.feature.vrppayment.formatExactAmount
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult
import template.core.base.ui.viewmodel.BaseViewModel
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** The scheme a payer must be in for a payment under this consent to be accepted. */
private const val SORT_CODE_ACCOUNT_NUMBER = "UK.OBIE.SortCodeAccountNumber"

/** The length a sort-code-and-account-number identification always has. */
private const val PAYER_IDENTIFICATION_LENGTH = 14

/** The smallest amount the bank accepts, in pence. */
private const val MIN_AMOUNT_MINOR = 1L

/** Which half of the one destination is on screen. */
enum class PaymentPhase {
    Amount,
    Review,
}

/** Screen state for one payment under a VRP. */
data class VrpPaymentState(
    val consentId: String,
    val uiState: VrpPaymentUiState = VrpPaymentUiState.Loading,
)

/** The four rendered states. */
sealed interface VrpPaymentUiState {

    data object Loading : VrpPaymentUiState

    data class Content(
        val phase: PaymentPhase,
        val form: PaymentFormUi,
        val outcome: SubmissionUi = SubmissionUi.NotStarted,
    ) : VrpPaymentUiState {

        /** Whether the amount is usable and nothing is in flight. */
        val canContinue: Boolean get() = outcome !is SubmissionUi.Sending && form.isUsable
    }

    /** The VRP cannot be paid under. Nothing to retry; the only way out is back. */
    data object Unusable : VrpPaymentUiState

    data class Error(val kind: VrpPaymentErrorKind) : VrpPaymentUiState
}

/**
 * What the customer has entered, and what they are told about it.
 *
 * @property remainingAmount What is left of the periodic ceiling. Advisory: the bank decides.
 */
data class PaymentFormUi(
    val payeeName: String = "",
    val payerName: String = "",
    val amount: String = "",
    val problem: AmountProblem? = null,
    val perPaymentCeilingAmount: String = "",
    val remainingAmount: String = "",
    val periodType: PeriodType? = null,
    val fundsWarning: Boolean = false,
) {

    /** Whether the amount can be sent, as far as this app can tell. */
    val isUsable: Boolean get() = amount.isNotBlank() && problem == null
}

/** How a submission is going. */
sealed interface SubmissionUi {

    data object NotStarted : SubmissionUi

    data object Sending : SubmissionUi

    /** The bank accepted it. */
    data class Sent(val payment: VrpPayment) : SubmissionUi {

        /** Whether the bank has posted it, rather than still processing. */
        val settled: Boolean get() = payment.status.disposition == PaymentDisposition.TerminalSuccess
    }

    data class Failed(
        val kind: PaymentFailureKind,
        val supportReference: String = "",
    ) : SubmissionUi
}

/** Why an entered amount cannot be sent. A blank field carries no problem; it is simply not usable. */
enum class AmountProblem {
    NotANumber,
    BelowMinimum,

    /** More than the ceiling for a single payment. */
    OverPerPayment,

    /** More than what is left of the periodic ceiling. */
    OverRemaining,
}

/** The two failures the amount phase distinguishes, both from loading the VRP. */
enum class VrpPaymentErrorKind {
    ConsentUnavailable,
    StorageUnavailable,
}

/**
 * Why a submission did not succeed.
 *
 * [Unconfirmed] is the one that must never be retried automatically: the money may already have
 * moved, and the app cannot tell.
 */
enum class PaymentFailureKind {
    OverLimit,
    ConsentUnusable,
    NeedsReauthorisation,
    Rejected,
    Unconfirmed,
    NetworkUnavailable,
}

/** Whether the customer may safely send again. */
val PaymentFailureKind.isRetryable: Boolean
    get() = this == PaymentFailureKind.NetworkUnavailable

/** Actions the view model owns. */
sealed interface VrpPaymentAction {

    data object RetryLoad : VrpPaymentAction

    data class AmountChanged(val value: String) : VrpPaymentAction

    /** Optional. Reports only bad news; an available answer changes nothing. */
    data object CheckFunds : VrpPaymentAction

    data object Continue : VrpPaymentAction

    data object BackToAmount : VrpPaymentAction

    data object Confirm : VrpPaymentAction

    /** Resubmits under the same idempotency key, so the bank cannot pay twice. */
    data object RetrySubmission : VrpPaymentAction

    data object RefreshOutcome : VrpPaymentAction

    data object Done : VrpPaymentAction

    /** Results the view model raises for itself. Never applied where they arrive. */
    sealed interface Internal : VrpPaymentAction {

        data class ReceiveConsent(
            val consent: VrpConsent?,
            val usage: List<PeriodUsage>,
        ) : Internal

        data class ReceiveFundsResult(
            val result: NetworkResult<FundsAvailability, NetworkError>,
        ) : Internal

        data class ReceivePaymentResult(
            val result: NetworkResult<VrpPayment, NetworkError>,
        ) : Internal
    }
}

/** One-shot instructions for the screen. */
sealed interface VrpPaymentEvent {

    /** The payment is done with. The screen pops back to the VRP. */
    data class Finished(val localId: String) : VrpPaymentEvent
}

/**
 * Drives one payment under a VRP: one destination with an amount phase and a review phase.
 *
 * The amount is held privately and combined into the rendered state, so a usage recalculation
 * landing late cannot wipe what the customer typed.
 */
class VrpPaymentViewModel(
    savedStateHandle: SavedStateHandle,
    private val consents: VrpConsentRepository,
    private val payments: VrpPaymentRepository,
) : BaseViewModel<VrpPaymentState, VrpPaymentEvent, VrpPaymentAction>(
    initialState = VrpPaymentState(
        consentId = savedStateHandle.get<String>(CONSENT_ID_ARG).orEmpty(),
    ),
) {

    private val amount = MutableStateFlow("")
    private val phase = MutableStateFlow(PaymentPhase.Amount)
    private val submission = MutableStateFlow<SubmissionUi>(SubmissionUi.NotStarted)
    private val fundsWarning = MutableStateFlow(false)
    private val loaded = MutableStateFlow<LoadedConsent?>(null)

    /**
     * The key this payment is sent under.
     *
     * Minted once so a retry replays it: the bank returns the original payment rather than making a
     * second one.
     */
    private val idempotencyKey = Uuid.random().toString()

    init {
        observeConsent()

        combine(loaded, amount, phase, submission, fundsWarning) { consent, entered, current, sent, warn ->
            render(consent, entered, current, sent, warn)
        }
            .onEach { rendered -> updateState { copy(uiState = rendered) } }
            .launchIn(viewModelScope)
    }

    @Suppress("CyclomaticComplexMethod")
    override fun handleAction(action: VrpPaymentAction) {
        when (action) {
            VrpPaymentAction.RetryLoad -> observeConsent()
            is VrpPaymentAction.AmountChanged -> amount.update { action.value }
            VrpPaymentAction.CheckFunds -> checkFunds()
            VrpPaymentAction.Continue -> moveToReview()
            VrpPaymentAction.BackToAmount -> backToAmount()
            VrpPaymentAction.Confirm -> submit()
            VrpPaymentAction.RetrySubmission -> submit()
            VrpPaymentAction.RefreshOutcome -> refreshOutcome()
            VrpPaymentAction.Done -> finish()

            is VrpPaymentAction.Internal.ReceiveConsent ->
                loaded.update { LoadedConsent(action.consent, action.usage) }

            is VrpPaymentAction.Internal.ReceiveFundsResult -> applyFundsResult(action.result)
            is VrpPaymentAction.Internal.ReceivePaymentResult -> applyPaymentResult(action.result)
        }
    }

    private fun observeConsent() {
        combine(
            consents.observeById(state.consentId),
            payments.observeUsage(state.consentId),
        ) { consent, usage ->
            VrpPaymentAction.Internal.ReceiveConsent(consent, usage)
        }
            .onEach { sendAction(it) }
            .launchIn(viewModelScope)
    }

    /** Reports only a shortfall: an available answer ignores the limits and promises nothing. */
    private fun checkFunds() {
        val entered = enteredMoney() ?: return
        viewModelScope.launch {
            val result = payments.checkFunds(state.consentId, entered)
            sendAction(VrpPaymentAction.Internal.ReceiveFundsResult(result))
        }
    }

    private fun applyFundsResult(result: NetworkResult<FundsAvailability, NetworkError>) {
        fundsWarning.update { result is NetworkResult.Success && !result.data.available }
    }

    private fun moveToReview() {
        val content = state.uiState as? VrpPaymentUiState.Content ?: return
        if (content.canContinue) phase.update { PaymentPhase.Review }
    }

    private fun backToAmount() {
        if (submission.value is SubmissionUi.Sending) return
        submission.update { SubmissionUi.NotStarted }
        phase.update { PaymentPhase.Amount }
    }

    private fun submit() {
        val consent = loaded.value?.consent
        val entered = enteredMoney()
        if (consent == null || entered == null || submission.value is SubmissionUi.Sending) return

        submission.update { SubmissionUi.Sending }
        viewModelScope.launch {
            val result = payments.pay(
                consent = consent,
                amount = entered,
                instructionIdentification = INSTRUCTION_PREFIX + idempotencyKey.take(KEY_LENGTH),
                endToEndIdentification = END_TO_END_PREFIX + idempotencyKey.take(KEY_LENGTH),
                idempotencyKey = idempotencyKey,
            )
            sendAction(VrpPaymentAction.Internal.ReceivePaymentResult(result))
        }
    }

    private fun refreshOutcome() {
        val sent = submission.value as? SubmissionUi.Sent ?: return
        viewModelScope.launch {
            val result = payments.refreshStatus(sent.payment)
            sendAction(VrpPaymentAction.Internal.ReceivePaymentResult(result))
        }
    }

    private fun applyPaymentResult(result: NetworkResult<VrpPayment, NetworkError>) {
        submission.update {
            when (result) {
                is NetworkResult.Success -> SubmissionUi.Sent(result.data)

                is NetworkResult.Error -> SubmissionUi.Failed(
                    kind = result.error.toFailureKind(),
                    supportReference = result.error.obieSupportReference().orEmpty(),
                )
            }
        }
    }

    private fun finish() {
        val localId = (submission.value as? SubmissionUi.Sent)?.payment?.localId.orEmpty()
        sendEvent(VrpPaymentEvent.Finished(localId))
    }

    private fun enteredMoney(): Money? =
        parseMinorUnits(amount.value)?.let { Money(it, loaded.value?.currency ?: return null) }

    private fun render(
        consent: LoadedConsent?,
        entered: String,
        currentPhase: PaymentPhase,
        sent: SubmissionUi,
        warn: Boolean,
    ): VrpPaymentUiState = when {
        consent == null -> VrpPaymentUiState.Loading
        consent.consent == null -> VrpPaymentUiState.Error(VrpPaymentErrorKind.ConsentUnavailable)
        !consent.consent.isPayable() -> VrpPaymentUiState.Unusable

        else -> VrpPaymentUiState.Content(
            phase = currentPhase,
            form = consent.toFormUi(entered, warn),
            outcome = sent,
        )
    }

    companion object {
        /** Must match the [VrpPaymentRoute] property name — type-safe nav uses it as the key. */
        const val CONSENT_ID_ARG: String = "consentId"

        private const val INSTRUCTION_PREFIX = "VRPINSTR"
        private const val END_TO_END_PREFIX = "VRPE2E"
        private const val KEY_LENGTH = 20
    }
}

/** The VRP and what its ceilings have consumed, read together. */
private data class LoadedConsent(
    val consent: VrpConsent?,
    val usage: List<PeriodUsage>,
) {

    /** The currency the ceilings are set in, which a payment must match. */
    val currency: String? get() = consent?.controlParameters?.maximumIndividualAmount?.currency

    /** The ceiling closest to being exhausted, which is the one that binds. */
    val bindingUsage: PeriodUsage? get() = usage.minByOrNull { it.remaining.minorUnits }
}

private fun LoadedConsent.toFormUi(entered: String, warn: Boolean): PaymentFormUi {
    val consent = consent ?: return PaymentFormUi()
    val perPayment = consent.controlParameters.maximumIndividualAmount

    return PaymentFormUi(
        payeeName = consent.payee.name,
        payerName = consent.payer?.name.orEmpty(),
        amount = entered,
        problem = checkAmount(entered, perPayment, bindingUsage?.remaining),
        perPaymentCeilingAmount = formatExactAmount(perPayment),
        remainingAmount = bindingUsage?.let { formatExactAmount(it.remaining) }.orEmpty(),
        periodType = bindingUsage?.limit?.periodType,
        fundsWarning = warn,
    )
}

/**
 * Checks the amount against both ceilings.
 *
 * The remaining figure is this app's own count, so a pass here is advisory — the bank evaluates the
 * same limits itself and can refuse what this allowed.
 */
internal fun checkAmount(
    raw: String,
    perPayment: Money,
    remaining: Money?,
): AmountProblem? {
    val minorUnits = parseMinorUnits(raw)

    return when {
        raw.isBlank() -> null
        minorUnits == null -> AmountProblem.NotANumber
        minorUnits < MIN_AMOUNT_MINOR -> AmountProblem.BelowMinimum
        minorUnits > perPayment.minorUnits -> AmountProblem.OverPerPayment
        remaining != null && minorUnits > remaining.minorUnits -> AmountProblem.OverRemaining
        else -> null
    }
}

/** Whether a payment under this VRP can be accepted at all. */
private fun VrpConsent.isPayable(): Boolean {
    val live = revokedAt == null && status == ConsentStatus.Authorised
    val chosenPayer = payer

    return live && (
        chosenPayer == null ||
            (
                chosenPayer.schemeName == SORT_CODE_ACCOUNT_NUMBER &&
                    chosenPayer.identification.length == PAYER_IDENTIFICATION_LENGTH
                )
        )
}

/**
 * Which failure a refused payment is.
 *
 * A `2xx` that would not decode is [PaymentFailureKind.Unconfirmed]: the money may have moved and
 * the app cannot tell, so it must never be resent automatically.
 */
private fun NetworkError.toFailureKind(): PaymentFailureKind = when {
    this is NetworkError.Network -> PaymentFailureKind.NetworkUnavailable
    this is NetworkError.Serialization -> PaymentFailureKind.Unconfirmed
    isOutsideControlParameters() -> PaymentFailureKind.OverLimit
    this is NetworkError.Client.Unauthorized -> PaymentFailureKind.NeedsReauthorisation
    this is NetworkError.Client.Forbidden -> PaymentFailureKind.ConsentUnusable
    else -> PaymentFailureKind.Rejected
}
