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

package org.mifosx.openbanking.feature.vrpconsents.consentDetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.mifosx.openbanking.core.common.formatIsoDate
import org.mifosx.openbanking.core.data.vrp.VrpConsentRepository
import org.mifosx.openbanking.core.data.vrp.VrpPaymentRepository
import org.mifosx.openbanking.core.model.banking.payment.PaymentDisposition
import org.mifosx.openbanking.core.model.banking.payment.PaymentStatus
import org.mifosx.openbanking.core.model.callback.ConsentStatus
import org.mifosx.openbanking.core.model.vrp.PeriodType
import org.mifosx.openbanking.core.model.vrp.PeriodUsage
import org.mifosx.openbanking.core.model.vrp.VrpConsent
import org.mifosx.openbanking.core.model.vrp.VrpPayment
import org.mifosx.openbanking.feature.vrpconsents.formatExactAmount
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult
import template.core.base.ui.viewmodel.BaseViewModel
import kotlin.time.Instant

/** The scheme a payer must be in for a payment under this consent to be accepted. */
private const val SORT_CODE_ACCOUNT_NUMBER = "UK.OBIE.SortCodeAccountNumber"

/** The length a sort-code-and-account-number identification always has. */
private const val PAYER_IDENTIFICATION_LENGTH = 14

/** Screen state for one standing payment. */
data class VrpConsentDetailState(
    val consentId: String,
    val uiState: VrpConsentDetailUiState = VrpConsentDetailUiState.Loading,
)

/** The six rendered states. */
sealed interface VrpConsentDetailUiState {

    data object Loading : VrpConsentDetailUiState

    data class Content(
        val payeeName: String,
        val payerName: String,
        val status: ConsentStatus,
        val validUntil: String?,
        val syncedAt: Instant?,
        val perPaymentCeilingAmount: String,
        val limits: List<LimitRowUi>,
        val periodicLimitUsage: PeriodicLimitUsageUi?,
        val payments: List<PaymentRowUi>,
        val revoke: RevokePhase = RevokePhase.Idle,
    ) : VrpConsentDetailUiState {

        /** Whether a payment may be started. Not while a removal is in flight. */
        val canPay: Boolean
            get() = status == ConsentStatus.Authorised && revoke != RevokePhase.Revoking

        /** Whether the authority may be removed. Not while a removal is already in flight. */
        val canRevoke: Boolean
            get() = revoke != RevokePhase.Revoking &&
                (status == ConsentStatus.Authorised || status == ConsentStatus.AwaitingAuthorisation)
    }

    /**
     * Authorised, but no payment under it can ever be accepted because the account chosen at the
     * bank cannot make these payments. Retrying cannot help.
     */
    data object Unusable : VrpConsentDetailUiState

    /**
     * Removed, or gone at the bank. History stays readable; the actions do not.
     *
     * @property bankRefusedRemoval The removal was refused, so the authority may still be live at
     *   the bank even though this app has dropped it.
     */
    data class Ended(
        val payeeName: String,
        val payments: List<PaymentRowUi>,
        val bankRefusedRemoval: Boolean = false,
    ) : VrpConsentDetailUiState

    /** No local record of this standing payment. */
    data object NotFound : VrpConsentDetailUiState
}

/**
 * Where the two-step removal has got to.
 *
 * Neither step is a network state: [Confirming] is the gate the customer has opened and nothing has
 * been called yet, and [Revoking] is what a completed [NetworkResult] cannot express. There is no
 * failed phase, because a refused removal still ends the authority locally and leaves this screen.
 */
enum class RevokePhase {
    Idle,

    /** The confirmation is open. No call has been made. */
    Confirming,

    /** The call is in flight and the actions are locked. */
    Revoking,
}

/**
 * One ceiling.
 *
 * @property periodType The window it covers. Null for the per-payment ceiling.
 * @property ceilingAmount The most that may be sent, e.g. `£500.00`.
 */
data class LimitRowUi(
    val periodType: PeriodType?,
    val ceilingAmount: String,
)

/**
 * Spend against one periodic ceiling, in the window running now.
 *
 * @property periodType The window the ceiling covers.
 * @property sentAmount What has been sent in this window, e.g. `£120.00`.
 * @property ceilingAmount The most that may be sent in the window, e.g. `£500.00`.
 * @property remainingAmount What is left of the ceiling, e.g. `£380.00`.
 * @property sentAmountFraction How much of the ceiling has been sent, 0 to 1.
 */
data class PeriodicLimitUsageUi(
    val periodType: PeriodType,
    val sentAmount: String,
    val ceilingAmount: String,
    val remainingAmount: String,
    val sentAmountFraction: Float,
)

/**
 * One payment made under this standing payment.
 *
 * @property localId The row key.
 * @property sentAmount What was sent, e.g. `£45.00`.
 * @property status The bank's answer.
 * @property sentOn When it was sent, e.g. `14 Aug 2026`.
 */
data class PaymentRowUi(
    val localId: String,
    val sentAmount: String,
    val status: PaymentStatus,
    val sentOn: String,
)

/** Actions the view model owns. Paying is navigation, carried by the screen's lambda. */
sealed interface VrpConsentDetailAction {

    /** Opens the confirmation gate. Makes no call. */
    data object RevokeRequested : VrpConsentDetailAction

    data object RevokeDismissed : VrpConsentDetailAction

    data object RevokeConfirmed : VrpConsentDetailAction

    /** Results the view model raises for itself. Never applied where they arrive. */
    sealed interface Internal : VrpConsentDetailAction {

        data class ReceiveConsent(
            val consent: VrpConsent?,
            val payments: List<VrpPayment>,
            val usage: List<PeriodUsage>,
        ) : Internal

        data class ReceiveRevokeResult(val result: NetworkResult<Unit, NetworkError>) : Internal
    }
}

/** One-shot instructions for the screen. */
sealed interface VrpConsentDetailEvent {

    /** The standing payment is gone. The screen pops back to the list. */
    data object Revoked : VrpConsentDetailEvent
}

/** Drives one standing payment: its limits, what they have consumed, its payments, and removal. */
class VrpConsentDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val consents: VrpConsentRepository,
    private val payments: VrpPaymentRepository,
) : BaseViewModel<VrpConsentDetailState, VrpConsentDetailEvent, VrpConsentDetailAction>(
    initialState = VrpConsentDetailState(
        consentId = savedStateHandle.get<String>(CONSENT_ID_ARG).orEmpty(),
    ),
) {

    /** Whether the bank refused the removal this app has already carried out locally. */
    private var bankRefusedRemoval = false

    /** The payments already read back on this visit, so an emission cannot start the read again. */
    private val readBackPayments = mutableSetOf<String>()

    init {
        observeConsent()
        refreshStatus()
    }

    override fun handleAction(action: VrpConsentDetailAction) {
        when (action) {
            VrpConsentDetailAction.RevokeRequested -> moveRevokeTo(RevokePhase.Confirming)
            VrpConsentDetailAction.RevokeDismissed -> moveRevokeTo(RevokePhase.Idle)
            VrpConsentDetailAction.RevokeConfirmed -> revoke()

            is VrpConsentDetailAction.Internal.ReceiveConsent -> {
                applyConsent(action.consent, action.payments, action.usage)
                refreshUnsettledPayments(action.payments)
            }

            is VrpConsentDetailAction.Internal.ReceiveRevokeResult -> applyRevokeResult(action.result)
        }
    }

    private fun observeConsent() {
        combine(
            consents.observeById(state.consentId),
            payments.observeForConsent(state.consentId),
            payments.observeUsage(state.consentId),
        ) { consent, made, usage ->
            VrpConsentDetailAction.Internal.ReceiveConsent(consent, made, usage)
        }
            .onEach { sendAction(it) }
            .launchIn(viewModelScope)
    }

    /**
     * Reads the consent back from the bank so the screen is not showing a stored status alone.
     *
     * The result is discarded: a successful read is written to storage by the repository and reaches
     * the screen through the flow, and a failed one must leave what is stored intact.
     */
    private fun refreshStatus() {
        viewModelScope.launch { consents.refreshStatus(state.consentId) }
    }

    /**
     * Reads back the payments the bank had not finished with.
     *
     * A submission reports an interim status, and nothing else ever revisits it: left alone the row
     * reads as still sending for good, and — because usage counts settled payments only — the money
     * it consumed is never charged against the ceiling.
     *
     * Each payment is read once per visit. The read writes to storage, which emits back into here,
     * so repeating on every emission would not stop.
     */
    private fun refreshUnsettledPayments(made: List<VrpPayment>) {
        made.filter { it.status.disposition == PaymentDisposition.InProgress }
            .filter { readBackPayments.add(it.localId) }
            .forEach { payment -> viewModelScope.launch { payments.refreshStatus(payment) } }
    }

    private fun revoke() {
        moveRevokeTo(RevokePhase.Revoking)
        viewModelScope.launch {
            val result = consents.revoke(state.consentId)
            sendAction(VrpConsentDetailAction.Internal.ReceiveRevokeResult(result))
        }
    }

    /**
     * Records the bank's answer to a removal.
     *
     * A refusal still leaves the authority ended here, so the screen has usually moved to
     * [VrpConsentDetailUiState.Ended] by the time this arrives. Both orderings are covered: the flag
     * is marked for a later emission and stamped on the state already rendered.
     */
    private fun applyRevokeResult(result: NetworkResult<Unit, NetworkError>) {
        when (result) {
            is NetworkResult.Success -> sendEvent(VrpConsentDetailEvent.Revoked)

            is NetworkResult.Error -> {
                bankRefusedRemoval = true
                (state.uiState as? VrpConsentDetailUiState.Ended)?.let { ended ->
                    updateState { copy(uiState = ended.copy(bankRefusedRemoval = true)) }
                }
            }
        }
    }

    private fun moveRevokeTo(phase: RevokePhase) {
        val content = state.uiState as? VrpConsentDetailUiState.Content ?: return
        updateState { copy(uiState = content.copy(revoke = phase)) }
    }

    private fun applyConsent(
        consent: VrpConsent?,
        made: List<VrpPayment>,
        usage: List<PeriodUsage>,
    ) {
        val rows = made.map { it.toRowUi() }
        val uiState = when {
            consent == null -> VrpConsentDetailUiState.NotFound
            consent.revokedAt != null || consent.status.hasEnded() ->
                VrpConsentDetailUiState.Ended(consent.payee.name, rows, bankRefusedRemoval)

            consent.status == ConsentStatus.Authorised && !consent.canBePaidUnder() ->
                VrpConsentDetailUiState.Unusable

            else -> consent.toContent(rows, usage, heldRevokePhase())
        }
        updateState { copy(uiState = uiState) }
    }

    /** The phase in flight, so a storage emission mid-removal does not reopen the actions. */
    private fun heldRevokePhase(): RevokePhase =
        (state.uiState as? VrpConsentDetailUiState.Content)?.revoke ?: RevokePhase.Idle

    companion object {
        /** Must match the [VrpConsentDetailRoute] property name — type-safe nav uses it as the key. */
        const val CONSENT_ID_ARG: String = "consentId"
    }
}

/** Whether this status means the authority is over. */
private fun ConsentStatus.hasEnded(): Boolean = when (this) {
    ConsentStatus.Revoked,
    ConsentStatus.Rejected,
    ConsentStatus.Cancelled,
    ConsentStatus.Expired,
    ConsentStatus.Consumed,
    -> true

    ConsentStatus.AwaitingAuthorisation,
    ConsentStatus.Authorised,
    -> false
}

/**
 * Whether a payment under this consent can be accepted.
 *
 * A payer the customer chose at the bank that is not a sort code and account number — a card — makes
 * every payment fail permanently, so the screen says so rather than offering to pay.
 */
private fun VrpConsent.canBePaidUnder(): Boolean {
    val chosenPayer = payer ?: return true
    return chosenPayer.schemeName == SORT_CODE_ACCOUNT_NUMBER &&
        chosenPayer.identification.length == PAYER_IDENTIFICATION_LENGTH
}

private fun VrpConsent.toContent(
    rows: List<PaymentRowUi>,
    usage: List<PeriodUsage>,
    revoke: RevokePhase,
): VrpConsentDetailUiState.Content {
    val headline = controlParameters.periodicLimits.maxByOrNull { it.amount.minorUnits }

    return VrpConsentDetailUiState.Content(
        payeeName = payee.name,
        payerName = payer?.name.orEmpty(),
        status = status,
        validUntil = validity?.validTo?.let { formatIsoDate(it.toString()) },
        syncedAt = syncedAt,
        perPaymentCeilingAmount = formatExactAmount(controlParameters.maximumIndividualAmount),
        limits = controlParameters.periodicLimits.map {
            LimitRowUi(periodType = it.periodType, ceilingAmount = formatExactAmount(it.amount))
        },
        periodicLimitUsage = usage
            .firstOrNull { it.limit.periodType == headline?.periodType }
            ?.toPeriodicLimitUsageUi(),
        payments = rows,
        revoke = revoke,
    )
}

private fun PeriodUsage.toPeriodicLimitUsageUi(): PeriodicLimitUsageUi = PeriodicLimitUsageUi(
    periodType = limit.periodType,
    sentAmount = formatExactAmount(consumed),
    ceilingAmount = formatExactAmount(limit.amount),
    remainingAmount = formatExactAmount(remaining),
    sentAmountFraction = if (limit.amount.minorUnits <= 0L) {
        0f
    } else {
        (consumed.minorUnits.toFloat() / limit.amount.minorUnits.toFloat()).coerceIn(0f, 1f)
    },
)

private fun VrpPayment.toRowUi(): PaymentRowUi = PaymentRowUi(
    localId = localId,
    sentAmount = formatExactAmount(amount),
    status = status,
    sentOn = formatIsoDate(createdAt.toString()),
)

/** Whether this payment failed, for the row's error styling. */
internal val PaymentRowUi.hasFailed: Boolean
    get() = status.disposition == PaymentDisposition.TerminalFailure
