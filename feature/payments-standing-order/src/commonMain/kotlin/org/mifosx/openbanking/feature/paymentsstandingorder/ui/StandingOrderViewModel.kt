/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.paymentsstandingorder.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.mifosx.openbanking.core.common.formatMinorUnits
import org.mifosx.openbanking.core.common.formatSortCode
import org.mifosx.openbanking.core.common.parseMinorUnits
import org.mifosx.openbanking.core.data.banking.AccountCapabilityRegistry
import org.mifosx.openbanking.core.data.banking.AccountsOverviewRepository
import org.mifosx.openbanking.core.data.banking.BeneficiariesRepository
import org.mifosx.openbanking.core.data.banking.PaymentHistoryFeed
import org.mifosx.openbanking.core.data.banking.PaymentHistoryRepository
import org.mifosx.openbanking.core.data.banking.PaymentStatusRepository
import org.mifosx.openbanking.core.data.banking.StandingOrderInitiationRepository
import org.mifosx.openbanking.core.data.util.toThrowable
import org.mifosx.openbanking.core.model.banking.AccountWithBalance
import org.mifosx.openbanking.core.model.banking.BankAccount
import org.mifosx.openbanking.core.model.banking.BeneficiaryItem
import org.mifosx.openbanking.core.model.banking.BeneficiaryScheme
import org.mifosx.openbanking.core.model.banking.payment.ChargeBearer
import org.mifosx.openbanking.core.model.banking.payment.ConsentType
import org.mifosx.openbanking.core.model.banking.payment.CreditorSelection
import org.mifosx.openbanking.core.model.banking.payment.PaymentHistoryRow
import org.mifosx.openbanking.core.model.banking.payment.PaymentRail
import org.mifosx.openbanking.core.model.banking.payment.StandingOrderDraft
import org.mifosx.openbanking.core.model.banking.payment.StandingOrderFrequency
import org.mifosx.openbanking.core.model.hsbcProduct.AccountEndpoint
import org.mifosx.openbanking.core.model.hsbcProduct.HsbcProductCapability
import org.mifosx.openbanking.core.model.hsbcProduct.HsbcProductType
import org.mifosx.openbanking.core.ui.payee.initialsOf
import org.mifosx.openbanking.core.ui.payment.toHistoryEntry
import template.core.base.common.screen.DataFreshness
import template.core.base.common.screen.ScreenState
import template.core.base.common.screen.combineContent
import template.core.base.network.NetworkResult
import template.core.base.ui.viewmodel.BaseViewModel
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val SORT_CODE_DIGITS = 6
private const val ACCOUNT_NUMBER_DIGITS = 8

/** ISO 13616 bounds: a country's IBAN is fixed-length, but the shortest is 15 and the longest 34. */
private const val IBAN_MIN_LENGTH = 15
private const val IBAN_MAX_LENGTH = 34
private const val IBAN_COUNTRY_PREFIX_LENGTH = 2

/** Written-down IBANs carry spaces and are often lower case; OBIE wants neither. */
private fun String.normaliseIban(): String = filterNot { it.isWhitespace() }.uppercase()

/**
 * Whether a string could be an IBAN at all.
 *
 * Shape only — length, alphanumeric, and the two-letter country prefix followed by check digits. It
 * deliberately stops short of the mod-97 checksum: this is the guard that stops obvious nonsense
 * reaching the bank, not a claim that the account exists.
 */
private fun String.isPlausibleIban(): Boolean = normaliseIban().let { candidate ->
    candidate.length in IBAN_MIN_LENGTH..IBAN_MAX_LENGTH &&
        candidate.all { it.isLetterOrDigit() } &&
        candidate.take(IBAN_COUNTRY_PREFIX_LENGTH).all { it.isLetter() }
}

/**
 * The currency the domestic rail instructs and transfers in.
 *
 * Sterling is not a default here, it is the whole of the domestic rail: Faster Payments moves pounds,
 * so neither currency is offered and both are normalised back to this whenever the rail is chosen.
 */
private const val DOMESTIC_CURRENCY = "GBP"

/** Both standing-order rails: the history is one list, whichever rail a mandate was set up on. */
private val STANDING_ORDER_TYPES = setOf(
    ConsentType.DomesticStandingOrder,
    ConsentType.InternationalStandingOrder,
)

private const val HISTORY_LIMIT = 5

/** One more than is shown, which is what answers whether "See all" has anything behind it. */
private const val HISTORY_PROBE_LIMIT = HISTORY_LIMIT + 1

/**
 * How many words an avatar caption keeps before the rest becomes an initial.
 *
 * "John Sharma" reads as "John S." under a 56dp circle; the full name would either wrap to three
 * lines or be truncated mid-word, and neither says who is being paid.
 */
private const val SHORT_NAME_WORDS = 1

/**
 * Drives the payment form up to the point the customer leaves for their bank.
 *
 * It builds the instruction, stages it, and hands off to the browser. It does **not** submit: the
 * hop to the bank pops this screen without saving state, so the ViewModel that returns is a new and
 * empty one. Submitting therefore belongs to the leg that comes back — `PaymentConsentViewModel` —
 * which reads the staged draft out of storage. There is no funds confirmation anywhere in this
 * journey: neither scheduled rail offers the endpoint.
 *
 * Two things here are load-bearing and neither is obvious from the shape of the code:
 *
 * The **draft and its two idempotency keys are minted exactly once**, in [confirmAndStageConsent].
 * Regenerating them would produce a second instruction the bank cannot recognise as a duplicate, and
 * the draft is stored rather than rebuilt because the submitted `Initiation` must be byte-identical
 * to the staged one. The two keys differ from each other because the consent and submission bodies
 * differ; see [ScheduledPaymentDraft].
 *
 * The **form survives stream emissions**. Loaded data ([accountsScreen], [beneficiariesScreen]) and
 * entered data ([form]) are separate flows combined into one `Content`, so an account refresh
 * arriving mid-form cannot discard what the PSU has already typed.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalUuidApi::class)
@Suppress("TooManyFunctions")
class StandingOrderViewModel(
    private val accountsOverviewRepository: AccountsOverviewRepository,
    private val beneficiariesRepository: BeneficiariesRepository,
    private val paymentInitiationRepository: StandingOrderInitiationRepository,
    private val capabilityRegistry: AccountCapabilityRegistry,
    /**
     * Injected so the date window can be tested against a fixed day.
     *
     * The boundaries this feature enforces are all relative to today, so a suite reading the real
     * clock would assert a different window every day it ran — and the midnight-rollover behaviour
     * could not be exercised at all.
     */
    paymentHistoryRepository: PaymentHistoryRepository,
    paymentStatusRepository: PaymentStatusRepository,
    private val clock: Clock = Clock.System,
) : BaseViewModel<StandingOrderState, StandingOrderEvent, StandingOrderAction>(
    initialState = StandingOrderState(),
) {

    private val historyFeed = PaymentHistoryFeed(paymentHistoryRepository, paymentStatusRepository)

    /** Everything the PSU has entered. Held apart from loaded data so a refresh cannot clear it. */
    private data class Form(
        val step: StandingOrderStep = StandingOrderStep.Form,
        val rail: PaymentRail = PaymentRail.Domestic,
        val debtorAccountId: String? = null,
        /**
         * Presentation state, held beside the entered data rather than inside the loaded accounts.
         *
         * Same reason `feature/home` keeps `accountSelectorVisible` on `HomeState`: the picker is
         * open or shut because of something the customer did, and an account stream refreshing
         * underneath must not be able to change that.
         */
        val payerPickerExpanded: Boolean = false,
        val letBankChoosePayer: Boolean = false,
        val creditor: CreditorSelection? = null,
        val manualEntryVisible: Boolean = false,
        val manualSortCode: String = "",
        val manualAccountNumber: String = "",
        val manualIban: String = "",
        val manualName: String = "",
        val amountInput: String = "",
        /**
         * `InstructedAmount.Currency`. Selectable internationally; forced to GBP domestically.
         *
         * The form's only currency. `CurrencyOfTransfer` was a second field here and is now derived
         * from this one in [buildDraft].
         */
        val instructedCurrency: String = DOMESTIC_CURRENCY,
        val chargeBearer: ChargeBearer = ChargeBearer.BorneByCreditor,
        val reference: String = "",
        /** The ongoing amount, when it differs from the first. Domestic only. */
        val recurringAmountInput: String = "",
        /** The closing amount, when it differs from the rest. Domestic only. */
        val finalAmountInput: String = "",
        val frequency: StandingOrderFrequency = StandingOrderFrequency.Monthly,
        val firstPaymentDate: LocalDate? = null,
        /** Null means the mandate runs until the customer stops it, which both rails accept. */
        val finalPaymentDate: LocalDate? = null,
        /** Which picker is open, or null when none is. One dialog serves both dates. */
        val datePickerRole: StandingOrderDateRole? = null,
        /** Restamped on every picker open, never captured once. See [openDatePicker]. */
        val today: LocalDate,
        val amountProblem: StandingOrderAmountProblem? = null,
        val fieldErrors: StandingOrderFieldErrors = StandingOrderFieldErrors(),
    )

    /** Which of the three screen states is showing. The form only renders under [Phase.Form]. */
    private sealed interface Phase {
        data object Form : Phase
        data class Submitting(val stage: StandingOrderStage, val consentId: String?) : Phase
        data class Error(val kind: StandingOrderErrorKind, val supportReference: String?) : Phase
    }

    /**
     * The loaded accounts, already narrowed to those that may fund a payment.
     *
     * Filtered once here, as the stream lands, so the picker rows, the seeded selection, the
     * balance comparison and the draft's debtor can never disagree about which accounts are
     * payable. See [canFundAPayment] for why a credit card is not one of them.
     */
    private val accountsScreen =
        MutableStateFlow<ScreenState<List<AccountWithBalance>>>(ScreenState.Loading)
    private val beneficiariesScreen =
        MutableStateFlow<ScreenState<List<BeneficiaryItem>>>(ScreenState.Loading)
    private val form = MutableStateFlow(Form(today = todayUtc(clock)))
    private val phase = MutableStateFlow<Phase>(Phase.Form)
    private val selectedAccountId = MutableStateFlow("")
    private val history = MutableStateFlow<List<PaymentHistoryRow>>(emptyList())

    /**
     * Payees for the chosen payer, re-fetched whenever that changes.
     *
     * A blank id emits an empty list rather than being filtered out. Filtering meant de-selecting an
     * account produced no emission at all, so the previous account's payees stayed in state and on
     * screen under a payer that no longer existed — they are scoped to an account, and with no
     * account the honest answer is none.
     */
    private val beneficiariesStream: Flow<ScreenState<List<BeneficiaryItem>>> =
        // No distinctUntilChanged: a StateFlow already drops equal values, and applying it here is
        // a deprecated no-op. It was meaningful only while a filter() sat in front of this.
        selectedAccountId
            .flatMapLatest { accountId ->
                if (accountId.isBlank()) {
                    flowOf(ScreenState.Content(emptyList(), DataFreshness.FRESH))
                } else {
                    beneficiariesRepository.beneficiariesStream(accountId, viewModelScope).state
                }
            }

    init {
        // Two filters, deliberately different in kind. canFundAPayment is a PREDICTION from the
        // product matrix, and now catches both refused products: the credit card by its subtype and
        // the Global Money wallet by its description. The registry is what the bank has actually
        // refused this session — the safety net for anything the matrix cannot predict. It is only
        // a backstop now, not the sole defence, which matters because it does not survive a restart.
        accountsOverviewRepository.overviewState(viewModelScope)
            .combineContent(capabilityRegistry.unsupportedStream()) { accounts, refused, _ ->
                accounts
                    .filter { it.account.canFundAPayment() }
                    .filterNot {
                        AccountEndpoint.PaymentDebtor in refused[it.account.accountId].orEmpty()
                    }
            }
            .onEach { screen -> accountsScreen.value = screen }
            .launchIn(viewModelScope)

        beneficiariesStream
            .onEach { beneficiariesScreen.value = it }
            .launchIn(viewModelScope)

        historyFeed.rows(STANDING_ORDER_TYPES, HISTORY_PROBE_LIMIT, viewModelScope)
            .onEach { rows -> history.value = rows }
            .launchIn(viewModelScope)

        combine(
            accountsScreen,
            beneficiariesScreen,
            form,
            phase,
            history,
        ) { accounts, payees, entered, current, payments ->
            render(accounts, payees, entered, current, payments)
        }
            .onEach { rendered -> updateState { copy(uiState = rendered) } }
            .launchIn(viewModelScope)
    }

    override fun handleAction(action: StandingOrderAction) {
        when (action) {
            is StandingOrderAction.SelectRail -> selectRail(action.rail)
            is StandingOrderAction.SelectDebtorAccount -> selectDebtor(action.accountId)
            StandingOrderAction.TogglePayerPicker ->
                form.value = form.value.copy(payerPickerExpanded = !form.value.payerPickerExpanded)
            StandingOrderAction.LetBankChoosePayer -> letBankChoosePayer()
            is StandingOrderAction.SelectCreditor -> selectCreditor(action.beneficiaryId)
            StandingOrderAction.ShowManualCreditorEntry -> form.value = form.value.copy(manualEntryVisible = true)
            is StandingOrderAction.EnterManualSortCode -> enterSortCode(action.sortCode)
            is StandingOrderAction.EnterManualAccountNumber -> enterAccountNumber(action.accountNumber)
            is StandingOrderAction.EnterManualIban -> enterIban(action.iban)
            is StandingOrderAction.EnterManualName -> form.value = form.value.copy(manualName = action.name)
            StandingOrderAction.ConfirmManualCreditor -> confirmManualCreditor()
            is StandingOrderAction.EnterAmount -> enterAmount(action.amount)
            is StandingOrderAction.SelectInstructedCurrency ->
                form.value = form.value.copy(instructedCurrency = action.currency).revalidated()
            is StandingOrderAction.SelectChargeBearer ->
                form.value =
                    form.value.copy(chargeBearer = action.bearer)
            is StandingOrderAction.EnterReference -> enterReference(action.reference)
            is StandingOrderAction.EnterRecurringAmount ->
                form.value = form.value.copy(recurringAmountInput = action.amount)
            is StandingOrderAction.EnterFinalAmount ->
                form.value = form.value.copy(finalAmountInput = action.amount)
            is StandingOrderAction.SelectFrequency ->
                form.value = form.value.copy(frequency = action.frequency)
            is StandingOrderAction.OpenDatePicker -> openDatePicker(action.role)
            StandingOrderAction.DismissDatePicker ->
                form.value = form.value.copy(datePickerRole = null)
            is StandingOrderAction.SelectDate -> selectDate(action.role, action.date)
            StandingOrderAction.ClearFinalDate ->
                form.value = form.value.copy(finalPaymentDate = null)
            StandingOrderAction.ReviewStandingOrder ->
                form.value = form.value.copy(step = StandingOrderStep.Review)
            StandingOrderAction.ConfirmAndStageConsent -> confirmAndStageConsent()
            StandingOrderAction.RetryStaging -> confirmAndStageConsent()
            StandingOrderAction.ChangePayer -> changePayer()
            StandingOrderAction.AbandonAuthorisation -> abandonAuthorisation()
            StandingOrderAction.BackStep -> backStep()
            StandingOrderAction.RetryLoad -> accountsOverviewRepository.refresh()
        }
    }

    /**
     * Changing the payer re-keys the payee list: counterparties are saved per account, not per customer.
     *
     * Choosing also shuts the picker. Leaving it open would keep four accounts between the choice
     * and the amount, which is the thing collapsing it was for.
     */
    private fun selectDebtor(accountId: String) {
        selectedAccountId.value = accountId
        // Revalidated because the balance rung reads the account that just changed: moving to a
        // poorer account has to flag an amount that was payable from the previous one, and moving to
        // an account in another currency has to withdraw a comparison that no longer means anything.
        form.value = form.value.copy(
            debtorAccountId = accountId,
            payerPickerExpanded = false,
            letBankChoosePayer = false,
            creditor = null,
        ).revalidated()
    }

    /**
     * Send no `DebtorAccount` and let the PSU pick the account at their bank.
     *
     * A sanctioned shape rather than a gap: HSBC's own international sample omits the block, and
     * both rails accept a consent without it. The payee is dropped with it — beneficiaries are saved
     * per account, so a payee chosen under one payer means nothing once there is no payer at all.
     */
    private fun letBankChoosePayer() {
        selectedAccountId.value = ""
        form.value = form.value.copy(
            debtorAccountId = null,
            payerPickerExpanded = false,
            letBankChoosePayer = true,
            creditor = null,
        ).revalidated()
    }

    /**
     * Switching rails invalidates the payee and everything typed towards one.
     *
     * The two rails identify a creditor differently — sort code and account number against an IBAN —
     * so a payee chosen under one is not a payee under the other, and half-typed fields for the
     * wrong scheme would be carried into a request that cannot accept them. The **amount survives**
     * on purpose: it means the same thing on both rails, and re-typing it is a cost with no reason.
     *
     * **The currency is normalised back to sterling on the domestic rail**, which offers no control
     * for it. Anything else would stage a currency the customer was never shown.
     *
     * It is not normalised in the other direction. Sterling is a valid international choice:
     * `CurrencyOfTransfer: GBP` stages `201`/`AWAU` (INT-04). The two-value USD/EUR list this
     * replaced forced the currency off GBP on the way in, which silently changed a decision the
     * customer had not made.
     */
    private fun selectRail(rail: PaymentRail) {
        val current = form.value
        val domestic = rail == PaymentRail.Domestic
        form.value = current.copy(
            rail = rail,
            creditor = null,
            manualEntryVisible = false,
            manualSortCode = "",
            manualAccountNumber = "",
            manualIban = "",
            fieldErrors = StandingOrderFieldErrors(),
            instructedCurrency = if (domestic) DOMESTIC_CURRENCY else current.instructedCurrency,
            // Both dates survive a rail switch. Unlike the scheduled module, the two rails accept the
            // same days here — weekends included — so there is nothing a switch can invalidate.
            //
            // The three fields the international rail has no wire member for are cleared as well as
            // disabled. Disabling is what the customer sees; emptying is what stops a value typed
            // under one rail reaching a body built under the other. The draft builder reads only
            // enabled fields too, so this is the first of two guarantees rather than the only one.
            recurringAmountInput = if (domestic) current.recurringAmountInput else "",
            finalAmountInput = if (domestic) current.finalAmountInput else "",
            reference = if (domestic) current.reference else "",
        ).revalidated()
    }

    /**
     * Opens the date dialog against a freshly measured window.
     *
     * The restamp is the whole reason this is a function rather than a flag flip. The window is
     * `today+1 .. today+365`, and an app can sit open across midnight — a window captured when the
     * screen was built would, after that, still be offering yesterday's tomorrow, which the bank
     * refuses `U003` as a date in the past.
     *
     * A date already chosen is re-checked at the same time, for the same reason: it was valid when it
     * was picked and may not be now.
     */
    private fun openDatePicker(role: StandingOrderDateRole) {
        val today = todayUtc(clock)
        val current = form.value
        val first = current.firstPaymentDate?.takeIf { isSelectableFirstPaymentDate(it, today) }
        form.value = current.copy(
            today = today,
            datePickerRole = role,
            firstPaymentDate = first,
            // Re-checked against the possibly-cleared first date, not the one held a moment ago: an
            // end date is only meaningful relative to a start, and the start may have just expired.
            finalPaymentDate = current.finalPaymentDate
                ?.takeIf { isSelectableFinalPaymentDate(it, today, first) },
        )
    }

    /**
     * Accepts a date from the picker, re-checking it rather than trusting the control.
     *
     * The picker already greys out everything unselectable, so this can only reject a date the dialog
     * should not have offered. It is still checked: `SelectableDates` is a rendering concern, and the
     * rule that decides what the bank will accept must not live only inside a widget.
     */
    /**
     * Applies a chosen date, and re-checks the other one against it.
     *
     * Moving the start forward can invalidate an end already chosen — the bank refuses a final date on
     * or before the first — so that pair is resolved here rather than left for the bank to reject with
     * a message that recites three rules and names neither date.
     */
    private fun selectDate(role: StandingOrderDateRole, date: LocalDate) {
        val current = form.value
        if (!isSelectableDate(date, current.today, role, current.firstPaymentDate)) {
            form.value = current.copy(datePickerRole = null)
            return
        }
        form.value = when (role) {
            StandingOrderDateRole.First -> current.copy(
                firstPaymentDate = date,
                finalPaymentDate = current.finalPaymentDate
                    ?.takeIf { isSelectableFinalPaymentDate(it, current.today, date) },
                datePickerRole = null,
            )

            StandingOrderDateRole.Final -> current.copy(finalPaymentDate = date, datePickerRole = null)
        }
    }

    /**
     * Releases the screen after the customer came back from the bank without authorising.
     *
     * The staged consent is deliberately left alone. It cannot be withdrawn — the bank answers `405`
     * to a delete and keeps the consent — so pretending to cancel it would be a lie. What this fixes
     * is the screen: without it, returning through the task switcher rather than the redirect leaves
     * a spinner running against a callback that will never arrive.
     *
     * The draft is kept for the same reason. Nothing about the mandate changed by walking away from
     * the browser, so confirming again must replay the keys already minted and land on the consent
     * that already exists, rather than stage a second one that is equally impossible to withdraw.
     * The consent id is dropped because staging returns it afresh.
     */
    private fun abandonAuthorisation() {
        form.value = form.value.copy(step = StandingOrderStep.Review)
        phase.value = Phase.Form
        updateState { copy(consentId = null) }
    }

    private fun enterIban(raw: String) {
        form.value = form.value.copy(
            manualIban = raw,
            // Quiet until something has been typed: flagging an empty field tells someone they got
            // it wrong before they have had a go.
            fieldErrors = form.value.fieldErrors.copy(
                ibanInvalid = raw.isNotBlank() && !raw.isPlausibleIban(),
            ),
        )
    }

    /**
     * The international rail's manual payee: a name and an IBAN.
     *
     * The IBAN is normalised before it becomes the identification — written down it carries spaces
     * and is often lower case, and OBIE wants it unpunctuated and upper case.
     */
    private fun confirmManualIbanCreditor(current: Form) {
        val iban = current.manualIban.normaliseIban()
        if (!iban.isPlausibleIban()) {
            form.value = current.copy(
                fieldErrors = StandingOrderFieldErrors(ibanInvalid = true),
            )
            return
        }
        form.value = current.copy(
            creditor = CreditorSelection(
                name = current.manualName,
                scheme = BeneficiaryScheme.Iban,
                identification = iban,
                isOwnAccount = isOwnAccount(iban),
            ),
            fieldErrors = StandingOrderFieldErrors(),
        )
    }

    private fun selectCreditor(identification: String) {
        val payee = beneficiaries().firstOrNull { it.identification == identification } ?: return
        form.value = form.value.copy(
            creditor = CreditorSelection(
                name = payee.creditorName,
                scheme = payee.scheme,
                identification = payee.identification,
                beneficiaryId = payee.beneficiaryId,
                isOwnAccount = isOwnAccount(payee.identification),
            ),
            reference = payee.reference,
            manualEntryVisible = false,
        )
    }

    /**
     * Validates as the PSU types, but stays quiet on an empty field: flagging a length error before
     * anyone has typed anything reads as the form being broken rather than incomplete.
     */
    private fun enterSortCode(raw: String) {
        val digits = raw.filter(Char::isDigit)
        form.value = form.value.copy(
            manualSortCode = raw,
            fieldErrors = form.value.fieldErrors.copy(
                sortCodeInvalid = raw.isNotEmpty() && digits.length != SORT_CODE_DIGITS,
            ),
        )
    }

    private fun enterAccountNumber(raw: String) {
        val digits = raw.filter(Char::isDigit)
        form.value = form.value.copy(
            manualAccountNumber = raw,
            fieldErrors = form.value.fieldErrors.copy(
                accountNumberInvalid = raw.isNotEmpty() && digits.length != ACCOUNT_NUMBER_DIGITS,
            ),
        )
    }

    /**
     * Accepts a hand-keyed payee once both fields hold the right number of digits.
     *
     * Separators are stripped before validation and before the identification is built: OBIE wants
     * the fourteen digits unpunctuated, while `40-12-09` is how a sort code is written down.
     */
    private fun confirmManualCreditor() {
        val current = form.value
        if (current.rail == PaymentRail.International) {
            confirmManualIbanCreditor(current)
            return
        }
        val sortCode = current.manualSortCode.filter(Char::isDigit)
        val accountNumber = current.manualAccountNumber.filter(Char::isDigit)
        val errors = StandingOrderFieldErrors(
            sortCodeInvalid = sortCode.length != SORT_CODE_DIGITS,
            accountNumberInvalid = accountNumber.length != ACCOUNT_NUMBER_DIGITS,
        )
        if (!errors.isEmpty) {
            form.value = current.copy(fieldErrors = errors)
            return
        }
        val identification = sortCode + accountNumber
        form.value = current.copy(
            creditor = CreditorSelection(
                name = current.manualName,
                scheme = BeneficiaryScheme.SortCode,
                identification = identification,
                isOwnAccount = isOwnAccount(identification),
            ),
            fieldErrors = StandingOrderFieldErrors(),
        )
    }

    private fun enterAmount(amount: String) {
        form.value = form.value.copy(amountInput = amount).revalidated()
    }

    /**
     * Re-runs the amount ladder against the form as it now stands.
     *
     * Called by everything that can change an input the ladder reads — the amount itself, the payer,
     * the instructed currency, the rail — because the amount is the only field whose validity
     * depends on three others. See [amountProblemOf] for the ladder.
     */
    private fun Form.revalidated(): Form = copy(amountProblem = amountProblemOf(amountInput, comparableBalance()))

    /**
     * The payer's available balance, but **only when the amount is denominated in the same currency**.
     *
     * `null` — no comparison at all — when the payer is unknown or holds another currency. Comparing
     * 250 USD against a sterling balance is not a smaller mistake than not comparing: at 1.27 it
     * refuses payments the account covers twice over, and in the other direction it would pass ones
     * it cannot. The bank's funds confirmation is binding either way; this rung was always advisory,
     * and an advisory check that is wrong is worse than one that is absent.
     */
    private fun Form.comparableBalance(): Long? =
        accounts()
            .firstOrNull { it.account.accountId == debtorAccountId }
            ?.takeIf { it.account.currency.equals(instructedCurrency, ignoreCase = true) }
            ?.balance
            ?.availableAmount
            ?.let(::parseMinorUnits)

    private fun enterReference(reference: String) {
        form.value = form.value.copy(reference = reference)
    }

    /**
     * Fixes the instruction and hands it to the bank.
     *
     * Both idempotency keys are minted here, and minted **once per mandate**. A retry after a failed
     * staging must replay the keys the first attempt used: if that attempt reached the bank and only
     * the response was lost, a fresh key stages a second consent instead of returning the first —
     * and a standing-order consent cannot be withdrawn, because the bank answers `405` to a delete.
     * Every retry with a new key would therefore leave a permanent orphan.
     *
     * "The same mandate" is decided by comparing the rebuilt draft with the held one on everything
     * except the keys, rather than by clearing the draft at each edit site. Any edit changes some
     * field, so the keys regenerate on their own — and no future field can be added to the form and
     * forgotten here, which is the failure a list of clear-sites invites.
     */
    private fun confirmAndStageConsent() {
        val rebuilt = buildDraft() ?: return
        val draft = state.draft?.takeIf { it.isSameMandateAs(rebuilt) } ?: rebuilt
        phase.value = Phase.Submitting(StandingOrderStage.StagingConsent, consentId = null)
        updateState { copy(draft = draft) }

        viewModelScope.launch {
            when (val result = paymentInitiationRepository.stageStandingOrder(draft)) {
                is NetworkResult.Success -> {
                    val staged = result.data
                    updateState { copy(consentId = staged.consentId) }
                    phase.value = Phase.Submitting(StandingOrderStage.AwaitingAuthorisation, staged.consentId)
                    sendEvent(StandingOrderEvent.LaunchAuthorisation(staged.authorizationUrl))
                }

                is NetworkResult.Error -> fail(result.error.toThrowable())
            }
        }
    }

    private fun fail(throwable: Throwable) {
        phase.value = Phase.Error(
            kind = classifyStandingOrderError(throwable),
            supportReference = supportReferenceOf(throwable),
        )
    }

    /**
     * Returns to the payer step after the bank refused the account the payment came from.
     *
     * Clears the payer as well as the draft: the account that was selected is the thing that failed,
     * and by now the registry has removed it from the list, so leaving it selected would point at a
     * row that no longer exists. The payee is kept — nothing was wrong with it.
     */
    private fun changePayer() {
        form.value = form.value.copy(
            step = StandingOrderStep.Form,
            debtorAccountId = null,
            letBankChoosePayer = false,
        )
        phase.value = Phase.Form
        updateState { copy(draft = null, consentId = null) }
    }

    /**
     * Returns to the form from a failure, keeping the payer and payee already chosen.
     *
     * The draft is dropped deliberately: editing the amount changes the instruction, so the next
     * attempt is a different payment and must be staged under a new key rather than replayed.
     */
    private fun backStep() {
        form.value = form.value.copy(step = StandingOrderStep.Form)
        phase.value = Phase.Form
        updateState { copy(draft = null, consentId = null) }
    }

    /**
     * Four symmetric guards rather than tangled control flow: the draft can only be built once the
     * payer, payee and amount are all present, and each absence means the same thing — not ready.
     */
    @Suppress("ReturnCount")
    private fun buildDraft(): StandingOrderDraft? {
        val current = form.value
        val creditor = current.creditor ?: return null
        // Major units in, minor units on the wire. The one conversion point, so a draft can never
        // disagree with what the amount card showed.
        val amount = amountMinorUnits(current.amountInput) ?: return null
        // Re-checked here and not only at the picker. This is the last point before the mandate is
        // fixed, and the window may have moved since the date was chosen — the app can sit on a
        // filled review across midnight.
        val today = todayUtc(clock)
        val firstPaymentDate = current.firstPaymentDate
            ?.takeIf { isSelectableFirstPaymentDate(it, today) }
            ?: return null
        val isInternational = current.rail == PaymentRail.International
        return StandingOrderDraft(
            // Null when the PSU left the choice to the bank; both mappers omit the block.
            debtorAccount = debtorAccount(),
            creditor = creditor,
            frequency = current.frequency,
            // Bare ISO dates. The mapper appends a fixed midnight-UTC suffix, which is what makes the
            // consent body and the resource body identical without either one reading a clock.
            firstPaymentDate = firstPaymentDate.toIsoDate(),
            // Dropped rather than carried when it no longer qualifies: an open-ended mandate is a
            // legitimate outcome, and staging a refused pair is not.
            finalPaymentDate = current.finalPaymentDate
                ?.takeIf { isSelectableFinalPaymentDate(it, today, firstPaymentDate) }
                ?.toIsoDate(),
            firstPaymentAmountMinorUnits = amount,
            // The second of the two guarantees: only enabled fields are read. Even if a value
            // survived a rail switch in state, it cannot reach a body the rail has no member for.
            recurringPaymentAmountMinorUnits = current.recurringAmountInput
                .takeIf { !isInternational }
                ?.let(::amountMinorUnits),
            finalPaymentAmountMinorUnits = current.finalAmountInput
                .takeIf { !isInternational }
                ?.let(::amountMinorUnits),
            // What the customer chose, not what the payer account holds.
            currency = current.instructedCurrency,
            reference = current.reference.takeIf { it.isNotBlank() && !isInternational },
            consentIdempotencyKey = Uuid.generateV4().toString(),
            paymentIdempotencyKey = Uuid.generateV4().toString(),
            // Still null on the domestic rail. That nullness is the rail discriminator in two places —
            // `StandingOrderInitiationRepositoryImpl.isInternational()` routes staging and submission
            // by it, and `PaymentHistoryMapper.paymentType()` persists it so the status read-back hits
            // the right endpoint — so filling it in unconditionally would send every domestic mandate
            // down the international rail.
            currencyOfTransfer = if (isInternational) current.instructedCurrency else null,
            chargeBearer = if (isInternational) current.chargeBearer else null,
        )
    }

    /**
     * The same mandate, disregarding the keys minted to send it.
     *
     * Written as a copy-and-compare rather than a field-by-field check so that it stays correct as
     * [StandingOrderDraft] grows: a new field is included the day it is added. Spelling the
     * comparison out by hand would silently keep treating an edited mandate as unchanged.
     */
    private fun StandingOrderDraft.isSameMandateAs(other: StandingOrderDraft): Boolean =
        copy(
            consentIdempotencyKey = other.consentIdempotencyKey,
            paymentIdempotencyKey = other.paymentIdempotencyKey,
        ) == other

    private fun render(
        accounts: ScreenState<List<AccountWithBalance>>,
        payees: ScreenState<List<BeneficiaryItem>>,
        entered: Form,
        current: Phase,
        payments: List<PaymentHistoryRow>,
    ): StandingOrderUiState = when (current) {
        is Phase.Submitting -> StandingOrderUiState.Submitting(
            stage = current.stage,
            amountLabel = amountLabel(entered),
            creditorName = entered.creditor?.name.orEmpty(),
            consentId = current.consentId,
        )

        is Phase.Error -> StandingOrderUiState.Error(current.kind, current.supportReference)
        Phase.Form -> renderForm(accounts, payees, entered, payments)
    }

    private fun renderForm(
        accounts: ScreenState<List<AccountWithBalance>>,
        payees: ScreenState<List<BeneficiaryItem>>,
        entered: Form,
        payments: List<PaymentHistoryRow>,
    ): StandingOrderUiState = when (accounts) {
        is ScreenState.Content -> content(accounts.data, payees, entered, payments)
        is ScreenState.Error -> StandingOrderUiState.Error(classifyStandingOrderError(accounts.error), null)
        is ScreenState.NoNetwork -> StandingOrderUiState.Error(StandingOrderErrorKind.NetworkError, null)
        ScreenState.Unauthenticated -> StandingOrderUiState.Error(StandingOrderErrorKind.TokenExpired, null)
        ScreenState.Empty, ScreenState.Loading -> StandingOrderUiState.Loading
    }

    private fun content(
        accounts: List<AccountWithBalance>,
        payees: ScreenState<List<BeneficiaryItem>>,
        entered: Form,
        payments: List<PaymentHistoryRow>,
    ): StandingOrderUiState.Content {
        // Every non-Content state used to flatten to an empty list here, and renderForm switches
        // only on the accounts stream — so a refused beneficiaries read rendered as "no saved
        // payees" and nothing on screen ever said the bank had said no.
        val payeeList = (payees as? ScreenState.Content)?.data.orEmpty()
        val filteredPayees = payeeList.filter { payee ->
            when (entered.rail) {
                PaymentRail.Domestic -> payee.scheme == BeneficiaryScheme.SortCode
                PaymentRail.International -> payee.scheme == BeneficiaryScheme.Iban
            }
        }
        val selected = accounts.firstOrNull { it.account.accountId == entered.debtorAccountId }
        val availableMinorUnits = availableBalanceMinorUnits()
        return StandingOrderUiState.Content(
            step = entered.step,
            rail = entered.rail,
            debtorAccounts = accounts.map { it.account },
            debtorRows = accounts.map { it.toAccountRow() },
            beneficiaries = filteredPayees.map { it.toPickerRow() },
            debtorAccountId = entered.debtorAccountId,
            payerPickerExpanded = entered.payerPickerExpanded,
            letBankChoosePayer = entered.letBankChoosePayer,
            creditor = entered.creditor,
            creditorLabel = entered.creditor?.name.orEmpty(),
            creditorSupporting = entered.creditor?.let { schemeLabel(it) }.orEmpty(),
            debtorAccountRow = selected?.toAccountRow(),
            debtorCurrency = selected?.account?.currency.orEmpty(),
            manualEntryVisible = entered.manualEntryVisible,
            manualSortCode = entered.manualSortCode,
            manualAccountNumber = entered.manualAccountNumber,
            manualIban = entered.manualIban,
            manualName = entered.manualName,
            amountInput = entered.amountInput,
            amountLabel = amountLabel(entered),
            instructedCurrency = entered.instructedCurrency,
            recentPayments = payments.take(HISTORY_LIMIT).map { it.toHistoryEntry() },
            hasMorePayments = payments.size > HISTORY_LIMIT,
            payeesFailed = payees.isFailure(),
            // Gated on a payer, not read straight off the stream. beneficiariesScreen is seeded
            // Loading and the blank-id branch short-circuits to Content(emptyList()), so between
            // the two there is a first paint where the stream says Loading and nothing is in
            // flight — labelling that "still asking" would put a shimmer under the notice that
            // already explains why the list is empty, and would say two things at once.
            payeesLoading = entered.debtorAccountId != null && payees is ScreenState.Loading,
            offeredCurrencies = if (entered.rail == PaymentRail.International) {
                OFFERED_CURRENCIES
            } else {
                emptyList()
            },
            chargeBearer = entered.chargeBearer,
            reference = entered.reference,
            frequency = entered.frequency,
            firstPaymentDate = entered.firstPaymentDate,
            firstPaymentDateLabel = entered.firstPaymentDate?.let(::formatStandingOrderDate).orEmpty(),
            finalPaymentDate = entered.finalPaymentDate,
            finalPaymentDateLabel = entered.finalPaymentDate?.let(::formatStandingOrderDate).orEmpty(),
            datePickerRole = entered.datePickerRole,
            recurringAmountInput = entered.recurringAmountInput,
            finalAmountInput = entered.finalAmountInput,
            today = entered.today,
            amountProblem = entered.amountProblem,
            fieldErrors = entered.fieldErrors,
            availableBalanceMinorUnits = availableMinorUnits,
            // Blank when no payer is chosen, which is what hides the balance line under the amount:
            // there is no account to state a balance for, and £0.00 would be a claim about one.
            availableBalanceLabel = availableMinorUnits
                // The account's own currency, which is not necessarily the instructed one. Stating
                // a sterling balance as dollars because the amount is in dollars would misreport
                // what is in the account by whatever the rate happens to be.
                ?.let { formatMinorUnits(it, selected?.balance?.currency ?: DOMESTIC_CURRENCY) }
                .orEmpty(),
        )
    }

    /** In the instructed currency — the amount is not the payer account's own currency. */
    private fun amountLabel(entered: Form): String {
        val minor = amountMinorUnits(entered.amountInput) ?: return ""
        return formatMinorUnits(minor, entered.instructedCurrency)
    }

    private fun accounts(): List<AccountWithBalance> =
        (accountsScreen.value as? ScreenState.Content)?.data.orEmpty()

    private fun beneficiaries(): List<BeneficiaryItem> =
        (beneficiariesScreen.value as? ScreenState.Content)?.data.orEmpty()

    private fun debtorAccount(): BankAccount? =
        accounts().firstOrNull { it.account.accountId == form.value.debtorAccountId }?.account

    private fun availableBalanceMinorUnits(): Long? =
        accounts()
            .firstOrNull { it.account.accountId == form.value.debtorAccountId }
            ?.balance
            ?.availableAmount
            ?.let(::parseMinorUnits)

    /** Whether the destination is one of the PSU's own accounts, which selects `TransferToSelf`. */
    private fun isOwnAccount(identification: String): Boolean {
        val digits = identification.filter(Char::isDigit)
        if (digits.isEmpty()) return false
        return accounts().any { (it.account.sortCode + it.account.accountNumber) == digits }
    }
}

/**
 * Whether a payee read came back as a failure rather than as an answer.
 *
 * `Loading` is not one — it is the read still in progress, and a notice under a list that is about
 * to arrive would flash on every payer change. That exclusion is now only half the answer: it keeps
 * the failure notice away, but for a long time nothing else picked the state up, so the in-flight
 * window fell through to "no saved payees" instead. `payeesLoading` is the field that catches it.
 * `Empty` is not a failure either: it is the bank saying there are none.
 *
 * **`Unauthenticated` is treated as a payee failure and deliberately NOT escalated** to the
 * full-screen `TokenExpired` the accounts stream raises. Three reasons, in order of weight:
 *  - The evidence says the token is fine. The failure this exists for was `403` on `beneficiaries`
 *    for every account while `accounts`, `balances` and `transactions` all returned `200` on that
 *    same token. Telling the customer their authorisation had expired would be false, and the
 *    recovery it offers — a browser round-trip to re-authorise — would not fix it.
 *  - It cannot be told apart from an expiry anyway. `ErrorCategory.categorize` folds both `401` and
 *    `403` into `Auth`, so this state cannot distinguish "consent no longer covers beneficiaries"
 *    from "token expired", and the more alarming of the two readings must not be the one asserted.
 *  - A real expiry escalates on its own. The accounts stream runs on the same credential and would
 *    reach `Unauthenticated` too, and `renderForm` turns that into the full-screen `TokenExpired`.
 *    Escalating here would only ever pre-empt that by taking away a form the PSU can still complete
 *    by hand — manual entry needs no beneficiaries read at all.
 */
private fun ScreenState<*>.isFailure(): Boolean = when (this) {
    is ScreenState.Error, is ScreenState.NoNetwork, ScreenState.Unauthenticated -> true
    is ScreenState.Content, ScreenState.Empty, ScreenState.Loading -> false
}

/**
 * Carries the account's raw fields rather than a finished name.
 *
 * `nickname` is blank on most HSBC accounts, so using it as the headline renders an empty row —
 * which is exactly what shipped before this. The readable label is resolved at render by
 * `core/ui`'s `accountDisplayName`, the same resolver Home and Accounts use.
 */
private fun AccountWithBalance.toAccountRow(): StandingOrderAccountRow = StandingOrderAccountRow(
    id = account.accountId,
    nickname = account.nickname,
    accountSubType = account.accountSubType,
    accountNumber = account.accountNumber,
    rawIdentification = account.rawIdentification,
    supporting = balance?.let { formatMinorUnits(parseMinorUnits(it.availableAmount) ?: 0L, it.currency) }
        .orEmpty(),
)

/**
 * Whether this account's PRODUCT is known to be unable to fund a payment.
 *
 * Catches both products the sandbox refuses as a named payer: the credit card, visible in
 * [BankAccount.accountSubType], and the Global Money wallet, which reports `AccountTypeCode: CACC`
 * and is identifiable **only** by its free-text [BankAccount.description]. That description used to
 * be passed as an empty string here, so every wallet resolved to a plain current account, was
 * offered as a payer, and was removed only after the bank refused it with `U002` — and because the
 * capability registry is in memory, it came back on the next launch to fail the same way.
 *
 * The registry still runs alongside this, and still matters: it catches whatever the product matrix
 * cannot predict. This is the prediction; that is the correction.
 *
 * Fails open through [HsbcProductCapability.supports]: a product this app has never met keeps the
 * payer role and is corrected by the bank, which is the safer default for an unknown.
 */
private fun BankAccount.canFundAPayment(): Boolean = HsbcProductCapability.supports(
    endpoint = AccountEndpoint.PaymentDebtor,
    productType = HsbcProductType.resolve(
        accountSubType = accountSubType,
        accountTypeCode = "",
        description = description,
    ),
)

private fun BeneficiaryItem.toPickerRow(): StandingOrderPickerRow = StandingOrderPickerRow(
    id = identification,
    initials = initialsOf(creditorName),
    headline = creditorName,
    supporting = schemeLabelFor(scheme, identification),
    shortName = shortNameOf(creditorName),
)

/**
 * "John Sharma" as "John S." — the caption an avatar can carry.
 *
 * A single-word name is left whole: "Vodafone" has nothing to abbreviate, and "V." names nobody.
 */
internal fun shortNameOf(name: String): String {
    val words = name.trim().split(' ').filter { it.isNotBlank() }
    if (words.size <= SHORT_NAME_WORDS) return words.joinToString(" ")
    val kept = words.take(SHORT_NAME_WORDS).joinToString(" ")
    return "$kept ${words[SHORT_NAME_WORDS].first().uppercase()}."
}

private fun schemeLabel(creditor: CreditorSelection): String =
    schemeLabelFor(creditor.scheme, creditor.identification)

/**
 * The identifier as it is written down rather than as it is transmitted — a sort code reads in
 * pairs, an IBAN in fours, and neither is how OBIE carries it.
 */
private fun schemeLabelFor(scheme: BeneficiaryScheme, identification: String): String = when (scheme) {
    BeneficiaryScheme.SortCode -> {
        val digits = identification.filter(Char::isDigit)
        val sortCode = digits.take(SORT_CODE_DIGITS)
        val accountNumber = digits.drop(SORT_CODE_DIGITS)
        "Sort Code · ${formatSortCode(sortCode)} $accountNumber".trimEnd()
    }

    BeneficiaryScheme.Iban -> "IBAN · $identification"
    BeneficiaryScheme.Paym -> "Paym · $identification"
    BeneficiaryScheme.Card -> "Card · $identification"
    BeneficiaryScheme.Account -> identification
}
