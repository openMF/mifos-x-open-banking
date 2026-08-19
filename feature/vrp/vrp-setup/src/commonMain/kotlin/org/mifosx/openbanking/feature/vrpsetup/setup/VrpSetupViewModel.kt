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

package org.mifosx.openbanking.feature.vrpsetup.setup

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate
import org.mifosx.openbanking.core.model.vrp.PeriodType
import org.mifosx.openbanking.feature.vrpsetup.AmountProblem
import org.mifosx.openbanking.feature.vrpsetup.PayeeProblem
import org.mifosx.openbanking.feature.vrpsetup.checkAccountNumber
import org.mifosx.openbanking.feature.vrpsetup.checkAmount
import org.mifosx.openbanking.feature.vrpsetup.checkOrdering
import org.mifosx.openbanking.feature.vrpsetup.checkPayeeDiffersFromPayer
import org.mifosx.openbanking.feature.vrpsetup.checkPayeeName
import org.mifosx.openbanking.feature.vrpsetup.checkSortCode
import org.mifosx.openbanking.feature.vrpsetup.combinedIdentification
import org.mifosx.openbanking.feature.vrpsetup.earliestEndDate
import org.mifosx.openbanking.feature.vrpsetup.isSelectableEndDate
import org.mifosx.openbanking.feature.vrpsetup.todayUtc
import template.core.base.ui.viewmodel.BaseViewModel

/** Which half of the one destination is on screen. */
enum class SetupPhase {
    Form,
    Review,
}

/** Screen state for setting up a VRP. */
data class VrpSetupState(
    val uiState: VrpSetupUiState = VrpSetupUiState.Loading,
)

/** The four rendered states. */
sealed interface VrpSetupUiState {

    data object Loading : VrpSetupUiState

    data class Content(
        val phase: SetupPhase,
        val form: SetupFormUi,
        val isStaging: Boolean = false,
    ) : VrpSetupUiState {

        /** Whether the form is complete enough to move to the review. */
        val canContinue: Boolean get() = !isStaging && form.isComplete
    }

    /** No account can fund a VRP. The customer cannot proceed and there is nothing to retry. */
    data object NoEligiblePayers : VrpSetupUiState

    data class Error(val kind: VrpSetupErrorKind) : VrpSetupUiState
}

/** One account the customer may pay from. */
data class PayerOptionUi(
    val accountId: String,
    val displayName: String,
    val availableBalance: String,
    val identification: String,
)

/** One payee the customer has already saved. */
data class PayeeOptionUi(
    val payeeId: String,
    val displayName: String,
    val initials: String,
    val identification: String,
)

/**
 * Everything the customer has entered, plus what is offered to them.
 *
 * Held privately by the view model and combined into the rendered state, so a list arriving late
 * cannot wipe what has been typed.
 */
data class SetupFormUi(
    val payerOptions: List<PayerOptionUi> = emptyList(),
    val selectedPayerId: String? = null,
    val chooseAtBank: Boolean = false,
    val payerExpanded: Boolean = false,
    val payeeOptions: List<PayeeOptionUi> = emptyList(),
    val selectedPayeeId: String? = null,
    val payNewSelected: Boolean = false,
    val newPayeeName: String = "",
    val newPayeeNameProblem: PayeeProblem? = null,
    val newPayeeSortCode: String = "",
    val newPayeeSortCodeProblem: PayeeProblem? = null,
    val newPayeeAccountNumber: String = "",
    val newPayeeAccountNumberProblem: PayeeProblem? = null,
    val payeeProblem: PayeeProblem? = null,
    val perPaymentAmount: String = "",
    val perPaymentProblem: AmountProblem? = null,
    val periodType: PeriodType = PeriodType.Month,
    val periodicAmount: String = "",
    val periodicProblem: AmountProblem? = null,
    val validTo: LocalDate? = null,
    val datePickerOpen: Boolean = false,
    val earliestSelectableDate: LocalDate = earliestEndDate(todayUtc()),
) {

    /**
     * Whether a payer has been decided.
     *
     * Deferring to the bank is a decision that leaves [selectedPayerId] null, so the id alone cannot
     * answer this.
     */
    val hasPayer: Boolean get() = chooseAtBank || selectedPayerId != null

    /** Whether a payee route has been taken: one of the saved payees, or entering a new one. */
    val hasPayee: Boolean get() = selectedPayeeId != null || payNewSelected

    /** The payee's account, digits only. Short until a new payee is fully typed. */
    val newPayeeIdentification: String
        get() = combinedIdentification(newPayeeSortCode, newPayeeAccountNumber)

    /** The account paying, when it is named here rather than chosen at the bank. */
    val selectedPayer: PayerOptionUi?
        get() = payerOptions.firstOrNull { it.accountId == selectedPayerId }

    /** The payee chosen from the saved list. */
    val selectedPayee: PayeeOptionUi?
        get() = payeeOptions.firstOrNull { it.payeeId == selectedPayeeId }

    /** The account the money goes to, whichever route was taken. */
    val payeeIdentification: String
        get() = if (payNewSelected) newPayeeIdentification else selectedPayee?.identification.orEmpty()

    /**
     * Whether every field is present and no rule is broken.
     *
     * The checks are run here rather than read from the `*Problem` fields, which are null until a
     * field is edited — an untouched form would otherwise report itself complete.
     */
    val isComplete: Boolean
        get() = hasPayer &&
            hasPayee &&
            checkAmount(perPaymentAmount) == null &&
            checkAmount(periodicAmount) == null &&
            checkOrdering(perPaymentAmount, periodicAmount) == null &&
            checkPayeeDiffersFromPayer(payeeIdentification, selectedPayer?.identification) == null &&
            newPayeeIsUsable

    /** Whether a newly entered payee is complete. Vacuously true when a saved payee was chosen. */
    private val newPayeeIsUsable: Boolean
        get() = !payNewSelected ||
            (
                checkPayeeName(newPayeeName) == null &&
                    checkSortCode(newPayeeSortCode) == null &&
                    checkAccountNumber(newPayeeAccountNumber) == null
                )
}

/** The two failures the form distinguishes, both from loading the payer list. */
enum class VrpSetupErrorKind {
    AccountsUnavailable,
    NetworkUnavailable,
}

/** Actions the view model owns. */
sealed interface VrpSetupAction {

    data object RetryLoad : VrpSetupAction

    data object PayerToggled : VrpSetupAction

    data class PayerSelected(val accountId: String) : VrpSetupAction

    data object ChooseAtBankSelected : VrpSetupAction

    data class PayeeSelected(val payeeId: String) : VrpSetupAction

    data object PayNewSelected : VrpSetupAction

    data class NewPayeeNameChanged(val value: String) : VrpSetupAction

    data class NewPayeeSortCodeChanged(val value: String) : VrpSetupAction

    data class NewPayeeAccountNumberChanged(val value: String) : VrpSetupAction

    data class PerPaymentAmountChanged(val value: String) : VrpSetupAction

    data class PeriodTypeSelected(val periodType: PeriodType) : VrpSetupAction

    data class PeriodicAmountChanged(val value: String) : VrpSetupAction

    data object DatePickerOpened : VrpSetupAction

    data object DatePickerDismissed : VrpSetupAction

    data class ValidToSelected(val date: LocalDate) : VrpSetupAction

    data object ValidToCleared : VrpSetupAction

    /** Validates everything and moves to the review. Makes no call. */
    data object Continue : VrpSetupAction

    /** Returns to the form with everything the customer typed intact. */
    data object BackToForm : VrpSetupAction

    data object StageConsent : VrpSetupAction
}

/** One-shot instructions for the screen. */
sealed interface VrpSetupEvent {

    /** Open [url] so the customer can approve at their bank. Only the composition may do this. */
    data class LaunchAuthorisation(val url: String) : VrpSetupEvent

    data class StagingFailed(val kind: VrpSetupErrorKind) : VrpSetupEvent
}

/**
 * Drives VRP setup: one destination with a form phase and a review phase.
 *
 * The form is held in [formState], a private flow combined into the rendered state, so a payer or
 * payee list landing late cannot overwrite what the customer has typed.
 */
class VrpSetupViewModel : BaseViewModel<VrpSetupState, VrpSetupEvent, VrpSetupAction>(
    initialState = VrpSetupState(),
) {

    private val formState = MutableStateFlow(SetupFormUi())
    private val phase = MutableStateFlow(SetupPhase.Form)
    private val staging = MutableStateFlow(false)

    init {
        combine(formState, phase, staging) { form, currentPhase, isStaging ->
            VrpSetupUiState.Content(phase = currentPhase, form = form, isStaging = isStaging)
        }
            .onEach { content -> updateState { copy(uiState = content) } }
            .launchIn(viewModelScope)
    }

    @Suppress("CyclomaticComplexMethod")
    override fun handleAction(action: VrpSetupAction) {
        when (action) {
            VrpSetupAction.RetryLoad -> Unit
            VrpSetupAction.PayerToggled -> editForm { copy(payerExpanded = !payerExpanded) }
            is VrpSetupAction.PayerSelected -> selectPayer(action.accountId)
            VrpSetupAction.ChooseAtBankSelected -> chooseAtBank()
            is VrpSetupAction.PayeeSelected -> selectPayee(action.payeeId)
            VrpSetupAction.PayNewSelected -> payNew()
            is VrpSetupAction.NewPayeeNameChanged -> changeNewPayeeName(action.value)
            is VrpSetupAction.NewPayeeSortCodeChanged -> changeSortCode(action.value)
            is VrpSetupAction.NewPayeeAccountNumberChanged -> changeAccountNumber(action.value)
            is VrpSetupAction.PerPaymentAmountChanged -> changePerPayment(action.value)
            is VrpSetupAction.PeriodTypeSelected -> editForm { copy(periodType = action.periodType) }
            is VrpSetupAction.PeriodicAmountChanged -> changePeriodic(action.value)
            VrpSetupAction.DatePickerOpened -> openDatePicker()
            VrpSetupAction.DatePickerDismissed -> editForm { copy(datePickerOpen = false) }
            is VrpSetupAction.ValidToSelected -> selectValidTo(action.date)
            VrpSetupAction.ValidToCleared -> editForm { copy(validTo = null, datePickerOpen = false) }
            VrpSetupAction.Continue -> continueToReview()
            VrpSetupAction.BackToForm -> phase.update { SetupPhase.Form }
            VrpSetupAction.StageConsent -> Unit
        }
    }

    private fun selectPayer(accountId: String) = editForm {
        copy(
            selectedPayerId = accountId,
            chooseAtBank = false,
            payerExpanded = false,
        ).revalidatePayee()
    }

    /**
     * Defers the payer to the bank.
     *
     * The payee is revalidated because a payer chosen there is unknown here, so a same-as-payer
     * breach recorded against the previous choice can no longer be evaluated and must be cleared.
     */
    private fun chooseAtBank() = editForm {
        copy(
            selectedPayerId = null,
            chooseAtBank = true,
            payerExpanded = false,
        ).revalidatePayee()
    }

    /** Chooses a saved payee, discarding anything typed for a new one. */
    private fun selectPayee(payeeId: String) = editForm {
        copy(
            selectedPayeeId = payeeId,
            payNewSelected = false,
            newPayeeName = "",
            newPayeeNameProblem = null,
            newPayeeSortCode = "",
            newPayeeSortCodeProblem = null,
            newPayeeAccountNumber = "",
            newPayeeAccountNumberProblem = null,
        ).revalidatePayee()
    }

    /** Opens the three entry fields, empty — never carrying another payee's details across. */
    private fun payNew() = editForm {
        copy(
            selectedPayeeId = null,
            payNewSelected = true,
            newPayeeName = "",
            newPayeeSortCode = "",
            newPayeeAccountNumber = "",
            newPayeeNameProblem = null,
            newPayeeSortCodeProblem = null,
            newPayeeAccountNumberProblem = null,
            payeeProblem = null,
        )
    }

    private fun changeNewPayeeName(value: String) = editForm {
        copy(newPayeeName = value, newPayeeNameProblem = checkPayeeName(value))
    }

    private fun changeSortCode(value: String) = editForm {
        copy(newPayeeSortCode = value, newPayeeSortCodeProblem = checkSortCode(value)).revalidatePayee()
    }

    private fun changeAccountNumber(value: String) = editForm {
        copy(
            newPayeeAccountNumber = value,
            newPayeeAccountNumberProblem = checkAccountNumber(value),
        ).revalidatePayee()
    }

    private fun changePerPayment(value: String) = editForm {
        copy(perPaymentAmount = value).revalidateAmounts()
    }

    private fun changePeriodic(value: String) = editForm {
        copy(periodicAmount = value).revalidateAmounts()
    }

    /** Recomputes the floor from the current UTC date — a form can sit open across midnight. */
    private fun openDatePicker() = editForm {
        copy(datePickerOpen = true, earliestSelectableDate = earliestEndDate(todayUtc()))
    }

    /** Re-checks the floor at selection, not only when the picker was opened. */
    private fun selectValidTo(date: LocalDate) = editForm {
        if (isSelectableEndDate(date, todayUtc())) {
            copy(validTo = date, datePickerOpen = false)
        } else {
            copy(datePickerOpen = false, earliestSelectableDate = earliestEndDate(todayUtc()))
        }
    }

    private fun continueToReview() {
        val validated = formState.value.revalidateAll()
        formState.update { validated }
        if (validated.isComplete) phase.update { SetupPhase.Review }
    }

    private fun editForm(edit: SetupFormUi.() -> SetupFormUi) {
        formState.update { it.edit() }
    }
}

/** Re-checks both ceilings and the rule that binds them. */
private fun SetupFormUi.revalidateAmounts(): SetupFormUi {
    val perPaymentOwn = checkAmount(perPaymentAmount)
    val periodicOwn = checkAmount(periodicAmount)
    val ordering = checkOrdering(perPaymentAmount, periodicAmount)

    return copy(
        perPaymentProblem = perPaymentOwn ?: ordering,
        periodicProblem = periodicOwn,
    )
}

/**
 * Re-checks the payee against the payer.
 *
 * The breach belongs to neither field on its own, so it is recorded on the section rather than
 * against the sort code or the account number the customer happened to edit last.
 */
private fun SetupFormUi.revalidatePayee(): SetupFormUi = copy(
    payeeProblem = checkPayeeDiffersFromPayer(payeeIdentification, selectedPayer?.identification),
)

/** Re-checks everything, for the moment the customer asks to continue. */
private fun SetupFormUi.revalidateAll(): SetupFormUi {
    val withAmounts = revalidateAmounts().revalidatePayee()
    if (!payNewSelected) return withAmounts

    return withAmounts.copy(
        newPayeeNameProblem = checkPayeeName(newPayeeName),
        newPayeeSortCodeProblem = checkSortCode(newPayeeSortCode),
        newPayeeAccountNumberProblem = checkAccountNumber(newPayeeAccountNumber),
    )
}
