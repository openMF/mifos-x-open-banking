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

package org.mifosx.openbanking.feature.vrpsetup.setup

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.mifosx.openbanking.core.common.formatMoney
import org.mifosx.openbanking.core.common.parseMinorUnits
import org.mifosx.openbanking.core.data.banking.AccountCapabilityRegistry
import org.mifosx.openbanking.core.data.banking.AccountsOverviewRepository
import org.mifosx.openbanking.core.data.banking.BeneficiariesRepository
import org.mifosx.openbanking.core.data.vrp.VrpAuthRepository
import org.mifosx.openbanking.core.data.vrp.VrpConsentRepository
import org.mifosx.openbanking.core.model.banking.AccountWithBalance
import org.mifosx.openbanking.core.model.banking.BankAccount
import org.mifosx.openbanking.core.model.banking.BeneficiaryItem
import org.mifosx.openbanking.core.model.hsbcProduct.AccountEndpoint
import org.mifosx.openbanking.core.model.hsbcProduct.HsbcProductCapability
import org.mifosx.openbanking.core.model.hsbcProduct.HsbcProductType
import org.mifosx.openbanking.core.model.vrp.AccountIdentity
import org.mifosx.openbanking.core.model.vrp.Money
import org.mifosx.openbanking.core.model.vrp.PeriodType
import org.mifosx.openbanking.core.model.vrp.PeriodicLimit
import org.mifosx.openbanking.core.model.vrp.ValidityWindow
import org.mifosx.openbanking.core.model.vrp.VrpConsentDraft
import org.mifosx.openbanking.core.model.vrp.VrpControlParameters
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
import org.mifosx.openbanking.feature.vrpsetup.initialsOf
import org.mifosx.openbanking.feature.vrpsetup.isSelectableEndDate
import org.mifosx.openbanking.feature.vrpsetup.shortPayeeName
import org.mifosx.openbanking.feature.vrpsetup.todayUtc
import template.core.base.common.screen.ScreenState
import template.core.base.common.screen.combineContent
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult
import template.core.base.ui.viewmodel.BaseViewModel
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** The only payer and payee scheme the bank accepts for a VRP. */
private const val SORT_CODE_ACCOUNT_NUMBER = "UK.OBIE.SortCodeAccountNumber"

/** The only VRP type this app sets up. */
private const val VRP_TYPE_SWEEPING = "UK.OBIE.VRPType.Sweeping"

/**
 * The currency a VRP's limits are set in.
 *
 * Fixed rather than read from the paying account: the bank refuses any other currency on the control
 * parameters, whatever the account itself is denominated in. The account's own currency is what its
 * balance is formatted with.
 */
private const val LIMIT_CURRENCY = "GBP"

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

/**
 * One account the customer may pay from.
 *
 * @property accountSubType Resolved to the type line by the picker.
 * @property accountNumber Masked by the picker; never shown whole.
 * @property identification The sort code and account number the bank is sent, and what the
 *   same-as-payee check compares.
 * @property availableBalance Already formatted, e.g. `£3,482.19`.
 */
data class PayerOptionUi(
    val accountId: String,
    val displayName: String,
    val accountSubType: String,
    val accountNumber: String,
    val identification: String,
    val availableBalance: String,
)

/**
 * One payee the customer has already saved.
 *
 * @property payeeId The destination account, which is what the picker selects by.
 * @property displayName The payee's full name.
 * @property shortName [displayName] cut to the length the avatar caption holds.
 * @property initials Up to two letters for the avatar.
 */
data class PayeeOptionUi(
    val payeeId: String,
    val displayName: String,
    val shortName: String,
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

    /** The result of staging and then building the authorisation URL. */
    data class ReceiveAuthorisationUrl(
        val result: NetworkResult<String, NetworkError>,
    ) : VrpSetupAction
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
@OptIn(ExperimentalCoroutinesApi::class)
class VrpSetupViewModel(
    private val accountsOverviewRepository: AccountsOverviewRepository,
    private val beneficiariesRepository: BeneficiariesRepository,
    private val capabilityRegistry: AccountCapabilityRegistry,
    private val consents: VrpConsentRepository,
    private val auth: VrpAuthRepository,
) : BaseViewModel<VrpSetupState, VrpSetupEvent, VrpSetupAction>(
    initialState = VrpSetupState(),
) {

    private val formState = MutableStateFlow(SetupFormUi())
    private val phase = MutableStateFlow(SetupPhase.Form)
    private val staging = MutableStateFlow(false)
    private val payersScreen =
        MutableStateFlow<ScreenState<List<AccountWithBalance>>>(ScreenState.Loading)

    init {
        observePayers()
        observePayees()

        combine(formState, phase, staging, payersScreen) { form, currentPhase, isStaging, payers ->
            render(form, currentPhase, isStaging, payers)
        }
            .onEach { rendered -> updateState { copy(uiState = rendered) } }
            .launchIn(viewModelScope)
    }

    /**
     * Loads the accounts that may pay, through two filters of different kinds.
     *
     * The product matrix is a prediction; the registry is what the bank has refused this session.
     * The prediction fails open, so a product this app has never met keeps the payer role.
     */
    private fun observePayers() {
        accountsOverviewRepository.overviewState(viewModelScope)
            .combineContent(capabilityRegistry.unsupportedStream()) { accounts, refused, _ ->
                accounts
                    .filter { it.account.canFundAVrp() }
                    .filterNot { AccountEndpoint.VrpPayer in refused[it.account.accountId].orEmpty() }
            }
            .onEach { payersScreen.value = it }
            .launchIn(viewModelScope)
    }

    /**
     * Keeps the saved payees following whichever account is paying.
     *
     * Nothing is read until a payer is chosen. Switching payer cancels the read still running for
     * the previous one, and clearing the payer empties the list rather than leaving its payees on
     * screen under an account that is no longer selected.
     */
    private fun observePayees() {
        formState
            .map { it.selectedPayerId.orEmpty() }
            .distinctUntilChanged()
            .flatMapLatest { accountId ->
                if (accountId.isBlank()) {
                    flowOf(emptyList())
                } else {
                    beneficiariesRepository.beneficiariesStream(accountId, viewModelScope)
                        .state
                        .map { screen -> (screen as? ScreenState.Content)?.data.orEmpty() }
                }
            }
            .onEach { payees -> editForm { copy(payeeOptions = payees.map { it.toOptionUi() }) } }
            .launchIn(viewModelScope)
    }

    private fun render(
        form: SetupFormUi,
        currentPhase: SetupPhase,
        isStaging: Boolean,
        payers: ScreenState<List<AccountWithBalance>>,
    ): VrpSetupUiState = when (payers) {
        ScreenState.Loading -> VrpSetupUiState.Loading
        ScreenState.Empty -> VrpSetupUiState.NoEligiblePayers
        ScreenState.Unauthenticated -> VrpSetupUiState.Error(VrpSetupErrorKind.AccountsUnavailable)
        is ScreenState.NoNetwork -> VrpSetupUiState.Error(VrpSetupErrorKind.NetworkUnavailable)
        is ScreenState.Error -> VrpSetupUiState.Error(VrpSetupErrorKind.AccountsUnavailable)

        is ScreenState.Content -> if (payers.data.isEmpty()) {
            VrpSetupUiState.NoEligiblePayers
        } else {
            VrpSetupUiState.Content(
                phase = currentPhase,
                form = form.copy(payerOptions = payers.data.map { it.toOptionUi() }),
                isStaging = isStaging,
            )
        }
    }

    @Suppress("CyclomaticComplexMethod")
    override fun handleAction(action: VrpSetupAction) {
        when (action) {
            VrpSetupAction.RetryLoad -> accountsOverviewRepository.refresh()
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
            VrpSetupAction.StageConsent -> stageConsent()
            is VrpSetupAction.ReceiveAuthorisationUrl -> applyAuthorisationUrl(action.result)
        }
    }

    /**
     * Creates the consent at the bank, then builds the URL that authorises it.
     *
     * Both steps are one operation: a staged consent nobody can approve is of no use, so the screen
     * stays locked until there is a URL to open or a failure to report.
     */
    private fun stageConsent() {
        val form = formState.value
        if (staging.value || !form.isComplete) return

        staging.value = true
        viewModelScope.launch {
            val result = when (val staged = consents.stageConsent(form.toDraft())) {
                is NetworkResult.Success -> auth.beginAuthorisation(staged.data.consentId)
                is NetworkResult.Error -> staged
            }
            sendAction(VrpSetupAction.ReceiveAuthorisationUrl(result))
        }
    }

    private fun applyAuthorisationUrl(result: NetworkResult<String, NetworkError>) {
        staging.value = false
        when (result) {
            is NetworkResult.Success -> sendEvent(VrpSetupEvent.LaunchAuthorisation(result.data))
            is NetworkResult.Error -> sendEvent(VrpSetupEvent.StagingFailed(result.error.toErrorKind()))
        }
    }

    /** Validates everything, then moves to the review with every entered value intact. */
    private fun continueToReview() {
        val validated = formState.value.revalidateAll()
        formState.update { validated.copy(datePickerOpen = false, payerExpanded = false) }
        if (validated.isComplete) phase.update { SetupPhase.Review }
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

    private fun editForm(edit: SetupFormUi.() -> SetupFormUi) {
        formState.update { it.edit() }
    }
}

/**
 * Whether this account may fund a VRP.
 *
 * Fails open: a product this app has never met keeps the payer role and is corrected by the bank.
 */
private fun BankAccount.canFundAVrp(): Boolean = HsbcProductCapability.supports(
    endpoint = AccountEndpoint.VrpPayer,
    productType = HsbcProductType.resolve(
        accountSubType = accountSubType,
        accountTypeCode = "",
        description = description,
    ),
)

private fun AccountWithBalance.toOptionUi(): PayerOptionUi = PayerOptionUi(
    accountId = account.accountId,
    displayName = account.nickname.ifBlank { account.accountSubType },
    accountSubType = account.accountSubType,
    accountNumber = account.accountNumber,
    identification = account.rawIdentification,
    availableBalance = balance?.let { formatMoney(it.availableAmount, it.currency) }.orEmpty(),
)

private fun BeneficiaryItem.toOptionUi(): PayeeOptionUi = PayeeOptionUi(
    payeeId = identification,
    displayName = creditorName,
    shortName = shortPayeeName(creditorName),
    initials = initialsOf(creditorName),
    identification = identification,
)

/** The consent this form asks the bank to create. */
private fun SetupFormUi.toDraft(): VrpConsentDraft = VrpConsentDraft(
    payee = AccountIdentity(
        schemeName = SORT_CODE_ACCOUNT_NUMBER,
        identification = payeeIdentification,
        name = if (payNewSelected) newPayeeName else selectedPayee?.displayName.orEmpty(),
    ),
    controlParameters = VrpControlParameters(
        maximumIndividualAmount = Money(parseMinorUnits(perPaymentAmount) ?: 0L, LIMIT_CURRENCY),
        periodicLimits = listOf(
            PeriodicLimit(
                periodType = periodType,
                amount = Money(parseMinorUnits(periodicAmount) ?: 0L, LIMIT_CURRENCY),
            ),
        ),
        interactionType = VRP_TYPE_SWEEPING,
    ),
    idempotencyKey = Uuid.random().toString(),
    payer = selectedPayer?.let {
        AccountIdentity(
            schemeName = SORT_CODE_ACCOUNT_NUMBER,
            identification = it.identification,
            name = it.displayName,
        )
    },
    payerAccountId = selectedPayerId,
    validity = validTo?.let { ValidityWindow(validFrom = null, validTo = it) },
)

/** Which failure the screen reports for a staging refusal. */
private fun NetworkError.toErrorKind(): VrpSetupErrorKind = when (this) {
    is NetworkError.Network -> VrpSetupErrorKind.NetworkUnavailable
    else -> VrpSetupErrorKind.AccountsUnavailable
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
