/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.paymentsschedulepayment.ui

import kotlinx.datetime.LocalDate
import org.mifosx.openbanking.core.data.util.RemoteException
import org.mifosx.openbanking.core.data.util.isDebtorAccountRefusal
import org.mifosx.openbanking.core.data.util.isExecutionDateRefusal
import org.mifosx.openbanking.core.data.util.obieErrorCode
import org.mifosx.openbanking.core.data.util.obieSupportReference
import org.mifosx.openbanking.core.model.banking.BankAccount
import org.mifosx.openbanking.core.model.banking.payment.ChargeBearer
import org.mifosx.openbanking.core.model.banking.payment.CreditorSelection
import org.mifosx.openbanking.core.model.banking.payment.PaymentRail
import org.mifosx.openbanking.core.model.banking.payment.ScheduledPaymentDraft
import template.core.base.network.NetworkError

/**
 * The two pages the form moves through inside a single `Content` state.
 *
 * Modelled as data rather than as separate screen states because the step is a property of one form
 * — what the customer chose stays live, and going back must not discard it. That is also why
 * [Review] is a page here rather than its own route: a separate destination would be rebuilt empty
 * on the way back, and the draft has to survive being edited.
 *
 * Payer, payee and amount were three steps until they became one scrolling page; nothing about the
 * form needed the customer to be walked through it in order.
 */
enum class SchedulePaymentStep {
    Form,
    Review,
}

/**
 * How far the hand-off to the bank has got. Distinct from [SchedulePaymentStep]: nothing is editable here.
 *
 * It stops at [AwaitingAuthorisation] because that is where this screen's part ends: the customer
 * leaves for the bank, and the leg that returns — payment-consent — confirms funds and submits.
 */
enum class SchedulePaymentStage {
    StagingConsent,

    /**
     * The customer has left for the bank and may never come back to this screen.
     *
     * **Escapable on purpose.** The immediate rail leaves its equivalent state with no way out: if the
     * customer returns through the task switcher rather than through the redirect, no callback ever
     * arrives, the ViewModel survives, and the spinner runs forever. This screen offers
     * [SchedulePaymentAction.AbandonAuthorisation] instead. Abandoning is not cancelling — the staged
     * consent cannot be withdrawn (the bank answers `405` on a delete and keeps it) — it only stops
     * this screen claiming to be waiting for something that is not coming.
     */
    AwaitingAuthorisation,
}

/**
 * Why a payment failed, and therefore what to offer next.
 *
 * Twelve kinds and five recoveries, deliberately not collapsed: retrying a revoked consent will never
 * succeed, and re-authorising a refused date does not make the date acceptable.
 *
 * Four of these — [DateRefused], [RequestMalformed], [SchemeNotSupported] and the date half of
 * [InvalidField] — exist because the codes behind them reach this feature and reach nothing else. Left
 * unclassified they fall through to [NetworkError], which tells the customer to check their internet
 * connection and offers a Retry that can only fail again. That exact defect shipped once already.
 *
 * There is no insufficient-funds kind. Neither scheduled rail has a funds-confirmation endpoint, so
 * this app never learns whether the money is there — and would be guessing if it said so.
 */
enum class SchedulePaymentErrorKind {
    /** `U019` — the JWS was missing or rejected. A defect in this app; the PSU can do nothing. */
    SignatureMissing,

    /** `U009` — submitted before the PSU finished authorising. */
    ConsentNotAuthorised,

    /** `U008` — the submitted instruction diverged from the staged one. Must be re-staged. */
    ConsentMismatch,

    /** `U014` — outside the limits agreed on the consent. */
    OutsideControlParameters,

    /** `U002` — a field the bank would not accept. */
    InvalidField,

    /**
     * The bank refused the execution date.
     *
     * `U003` (today or earlier), `U002` (beyond the accepted window) and `U004` (omitted) all report
     * the same path, so they arrive here together. Its own kind because it is the only failure with a
     * recovery the customer can actually carry out on this screen: change the date. Reaching it means
     * the picker and the bank disagreed — most likely because the app sat open across midnight and
     * the window moved under a date already chosen.
     */
    DateRefused,

    /**
     * `U004`/`U005` — a field this app omitted, or sent where it is not accepted.
     *
     * A defect in this app, not something the customer did. There is no retry: the same body would be
     * built again and refused again. It is kept apart from [InvalidField] because that kind offers
     * "check your details", and here there are no details to check.
     */
    RequestMalformed,

    /**
     * `U027` — the payer or payee product cannot be used on this rail.
     *
     * Domestic refuses an IBAN creditor, international refuses a sort-code creditor, and a card
     * debtor is refused on both. Nothing is mistyped, so the recovery is a different account rather
     * than a correction.
     */
    SchemeNotSupported,

    /** The consent was withdrawn. */
    ConsentRevoked,

    /** The payments token expired before the payment was submitted. */
    TokenExpired,

    RateLimited,

    /**
     * The bank refused the account the payment would come FROM.
     *
     * Distinct from [InvalidField] because there is nothing to correct: the details are right, the
     * account simply cannot send payments. Telling someone to check them would send them looking
     * for a mistake they did not make. Observed on a credit card (`U021`) and a Global Money wallet
     * (`U002`), both on `Data.Initiation.DebtorAccount.Identification`.
     */
    PayerNotSupported,

    NetworkError,
}

/**
 * Whether offering Retry makes sense.
 *
 * Retry replays the same idempotency key against the same consent, so it only helps when the
 * failure was transport or credential shaped. Everything else needs a different action.
 */
internal val SchedulePaymentErrorKind.isRetryable: Boolean
    get() = this == SchedulePaymentErrorKind.NetworkError ||
        this == SchedulePaymentErrorKind.RateLimited ||
        this == SchedulePaymentErrorKind.TokenExpired

/** Whether the PSU should be sent back to re-authorise at the bank. */
internal val SchedulePaymentErrorKind.needsReauthorisation: Boolean
    get() = this == SchedulePaymentErrorKind.ConsentNotAuthorised || this == SchedulePaymentErrorKind.TokenExpired

/** Whether the amount is the thing to change. */
internal val SchedulePaymentErrorKind.needsAmountChange: Boolean
    get() = this == SchedulePaymentErrorKind.OutsideControlParameters

/**
 * Whether the date is the thing to change.
 *
 * The one recovery this feature adds, and the only one that lands the customer back on a control they
 * can actually operate — every other refusal on this screen is answered by a different account or by
 * nothing at all.
 */
internal val SchedulePaymentErrorKind.needsDateChange: Boolean
    get() = this == SchedulePaymentErrorKind.DateRefused

/**
 * Which manual-entry fields are currently wrong.
 *
 * Per-field rather than one message, because both can be wrong at once and a single slot would hide
 * one of them. Flags rather than text: the wording is a string resource the composable resolves.
 */
data class SchedulePaymentFieldErrors(
    val sortCodeInvalid: Boolean = false,
    val accountNumberInvalid: Boolean = false,
    val ibanInvalid: Boolean = false,
) {
    val isEmpty: Boolean
        get() = !sortCodeInvalid && !accountNumberInvalid && !ibanInvalid
}

/** Why the entered amount is not yet payable. */
enum class SchedulePaymentAmountProblem {
    NotANumber,

    /**
     * More than two decimal places, e.g. `250.999`.
     *
     * Its own problem rather than [NotANumber] because it IS a number, and rounding it silently
     * would send an amount nobody typed — the parser truncates, so `250.999` would leave as £250.99.
     */
    TooManyDecimals,
    NotPositive,
    ExceedsAvailableBalance,
}

/**
 * One selectable payee row. Its name comes from the bank and needs no resolving.
 *
 * @property shortName The name as the avatar caption carries it — "John S." — because a 56dp circle
 *   has room for two words at most. Shortened in the ViewModel, like every other display string.
 */
data class SchedulePaymentPickerRow(
    val id: String,
    val initials: String,
    val headline: String,
    val supporting: String,
    val shortName: String = "",
)

/**
 * One selectable account row, carrying the raw fields rather than a finished label.
 *
 * HSBC leaves `Nickname` blank on most accounts, so the readable name — "Current account ·· 3349" —
 * has to be derived. That derivation lives in `core/ui`'s `accountDisplayName`, which is
 * `@Composable` because it resolves a string resource per account type, so it cannot run in the
 * ViewModel. Passing the ingredients up and resolving them at render keeps this list showing exactly
 * what Home, Accounts and account-detail show, instead of a second, emptier answer.
 */
data class SchedulePaymentAccountRow(
    val id: String,
    val nickname: String,
    val accountSubType: String,
    val accountNumber: String,
    val rawIdentification: String,
    val supporting: String,
)

sealed interface SchedulePaymentUiState {

    data object Loading : SchedulePaymentUiState

    /**
     * The form. [step] moves through it; everything else survives the whole way, so returning to an
     * earlier step from an error keeps what the PSU already chose.
     *
     * @property availableBalanceMinorUnits Advisory only. The binding check is the bank's funds
     *   confirmation, which can refuse a payment this comparison allows.
     * @property amountInput What the customer typed, in MAJOR units — `250` or `250.00` meaning
     *   £250. The draft still carries minor units; the conversion happens on the way in, once.
     * @property payerPickerExpanded Whether the payer picker is showing its accounts. Presentation
     *   state carried on the rendered state rather than inside the loaded data, the same way
     *   `feature/home` keeps `accountSelectorVisible` on `HomeState`: a stream emission must not be
     *   able to open or close it.
     */
    data class Content(
        val step: SchedulePaymentStep,
        val rail: PaymentRail = PaymentRail.Domestic,
        val debtorAccounts: List<BankAccount>,
        val beneficiaries: List<SchedulePaymentPickerRow>,
        val debtorRows: List<SchedulePaymentAccountRow>,
        val debtorAccountId: String? = null,
        val payerPickerExpanded: Boolean = false,
        val letBankChoosePayer: Boolean = false,
        val creditor: CreditorSelection? = null,
        val creditorLabel: String = "",
        val creditorSupporting: String = "",
        val debtorAccountRow: SchedulePaymentAccountRow? = null,
        val manualEntryVisible: Boolean = false,
        val manualSortCode: String = "",
        val manualAccountNumber: String = "",
        val manualIban: String = "",
        val manualName: String = "",
        val amountInput: String = "",
        val amountLabel: String = "",
        /**
         * `InstructedAmount.Currency` — what the amount is denominated in, and the only currency
         * the form asks for.
         *
         * Sterling on the domestic rail, which offers no control at all, and selectable on the
         * international one. `CurrencyOfTransfer` is derived from it rather than chosen separately:
         * HSBC requires the instructed currency to equal the debtor account's currency **or** the
         * currency of transfer, so deriving one from the other satisfies the rule by construction.
         */
        val instructedCurrency: String = "GBP",
        val chargeBearer: ChargeBearer = ChargeBearer.BorneByCreditor,
        val reference: String = "",
        /**
         * The date the payment is to be made, or `null` until one is chosen.
         *
         * Mandatory — this feature has no immediate path, and the bank refuses the request outright
         * when the field is missing. That is why it gates [canReview] rather than defaulting to
         * tomorrow: a date nobody chose is still a date the customer is committed to.
         */
        val executionDate: LocalDate? = null,
        /** The same date, written out — "Friday, 14 August 2026". Formatted in the ViewModel. */
        val executionDateLabel: String = "",
        val datePickerVisible: Boolean = false,
        /**
         * Today in UTC, restamped every time the picker opens.
         *
         * Carried on the state rather than read inside the picker so the window cannot be captured
         * once and then go stale: an app left open overnight would otherwise still be offering
         * yesterday's tomorrow, which the bank refuses `U003` as a date in the past.
         */
        val today: LocalDate,
        val amountProblem: SchedulePaymentAmountProblem? = null,
        val fieldErrors: SchedulePaymentFieldErrors = SchedulePaymentFieldErrors(),
        val availableBalanceMinorUnits: Long? = null,
        /** The same figure, formatted. Blank when no payer is chosen, which hides the balance line. */
        val availableBalanceLabel: String = "",
        val debtorCurrency: String = "",
        /** What the amount's currency control offers. Empty on the domestic rail, which has none. */
        val offeredCurrencies: List<String> = emptyList(),
        /**
         * The saved-payee read failed, as opposed to succeeding with nothing saved.
         *
         * Its own flag rather than an empty list because the two want different words and different
         * offers: "you have no saved payees" is a statement about the account, and rendering it over
         * a refused read told the customer something untrue about their own bank. Observed live —
         * HSBC answered `403` on `beneficiaries` for every account while every other AIS read
         * returned `200` on the same token.
         */
        val payeesFailed: Boolean = false,
        /**
         * The saved-payee read is still running, as opposed to having finished with nothing.
         *
         * The third outcome of the same stream, and the one that had nowhere to go: `isFailure()`
         * excludes `Loading` deliberately — its KDoc says why, that "a notice under a list that is
         * about to arrive would flash on every payer change" — but that intent was carried into no
         * field, so everything downstream still saw a non-`Content` state flattened to an empty
         * list and said "no saved payees" about a read that had not answered yet.
         *
         * The window is wider than it looks. It opens the moment a payer is chosen, reopens on
         * every payer switch, and opens again on Retry — the failure path caches no content, so
         * `ScreenDataStream` has nothing to preserve and the re-read passes back through `Loading`,
         * flashing "no saved payees" over the failure card the customer has just tapped.
         *
         * **Not true merely because the stream says `Loading`.** The stream is seeded `Loading`
         * before any payer exists, and with no payer nothing is in flight to wait for — so the
         * ViewModel gates this on a chosen payer. Without that gate the very first paint of the
         * form, which correctly says "choose an account to pay from", would also claim to be
         * fetching that account's payees.
         */
        val payeesLoading: Boolean = false,
    ) : SchedulePaymentUiState {

        /**
         * Review is reachable only once the amount is payable, a payee is chosen, a date is set and
         * the payer question has been answered one way or the other.
         *
         * The date clause has no equivalent on the immediate rail, where there is nothing to choose.
         * Here the field is mandatory at the bank, so letting the customer reach a review without one
         * would build a request that can only be refused `U004`.
         *
         * The payer clause matters because "no account chosen" and "the bank will choose" look the
         * same in the draft — both send no `DebtorAccount`. Without it someone could reach a review
         * saying "you'll choose at your bank" for a decision they never made.
         *
         * There is no currency clause any more. HSBC requires the instructed currency to equal the
         * debtor account's currency or the currency of transfer; with one selector the second holds
         * by construction, so the combination the bank refuses is no longer reachable to gate.
         */
        val canReview: Boolean
            get() = amountProblem == null &&
                amountInput.isNotBlank() &&
                creditor != null &&
                executionDate != null &&
                !payerUndecided

        /** Neither an account picked nor the bank asked to pick one. */
        val payerUndecided: Boolean
            get() = debtorAccountId == null && !letBankChoosePayer

        /**
         * Whether saved payees can be listed at all.
         *
         * Beneficiaries are an account-scoped resource, so this is about the account and not about
         * the payer decision: asking the bank to choose settles the payer but still leaves no
         * account to read payees from, and only manual entry works from there.
         */
        val payeesUnavailable: Boolean
            get() = debtorAccountId == null

        /**
         * Whether to warn that the bank will convert on the way out.
         *
         * The comparison is against [instructedCurrency] rather than against a hardcoded `"GBP"`,
         * which is what it tested while the instructed currency was a constant. Once that became
         * selectable the old test was wrong in both directions: it stayed silent on a sterling
         * account instructed in dollars, which does convert, and warned on a dollar account
         * instructed in dollars, which does not.
         *
         * This app cannot say at what rate — no consent exists yet to quote one, and
         * `ExchangeRateInformation` is refused `U005` — so the notice states that a conversion will
         * happen and stops there.
         */
        val showsConversionAdvisory: Boolean
            get() = debtorCurrency.isNotBlank() &&
                !debtorCurrency.equals(instructedCurrency, ignoreCase = true)

        val hasBeneficiaries: Boolean
            get() = beneficiaries.isNotEmpty()
    }

    /** In flight. There is no CTA at all here — see the note on [SchedulePaymentStage]. */
    data class Submitting(
        val stage: SchedulePaymentStage,
        val amountLabel: String,
        val creditorName: String,
        val consentId: String? = null,
    ) : SchedulePaymentUiState

    data class Error(
        val kind: SchedulePaymentErrorKind,
        val supportReference: String? = null,
    ) : SchedulePaymentUiState
}

/**
 * @property draft The instruction as it was staged, carrying both idempotency keys. Retained so a
 *   retry resubmits byte-identical content rather than rebuilding it.
 */
data class SchedulePaymentState(
    val uiState: SchedulePaymentUiState = SchedulePaymentUiState.Loading,
    val draft: ScheduledPaymentDraft? = null,
    val consentId: String? = null,
)

sealed interface SchedulePaymentAction {
    data class SelectRail(val rail: PaymentRail) : SchedulePaymentAction
    data class SelectDebtorAccount(val accountId: String) : SchedulePaymentAction

    /**
     * Opens or closes the payer picker.
     *
     * Only the expansion — choosing an account is [SelectDebtorAccount], which closes the picker as
     * a consequence of the choice rather than needing a second action from the composable.
     */
    data object TogglePayerPicker : SchedulePaymentAction

    /** Send no `DebtorAccount` and let the PSU pick the account at their bank. */
    data object LetBankChoosePayer : SchedulePaymentAction
    data class SelectCreditor(val beneficiaryId: String) : SchedulePaymentAction
    data object ShowManualCreditorEntry : SchedulePaymentAction
    data class EnterManualSortCode(val sortCode: String) : SchedulePaymentAction
    data class EnterManualAccountNumber(val accountNumber: String) : SchedulePaymentAction
    data class EnterManualIban(val iban: String) : SchedulePaymentAction
    data class EnterManualName(val name: String) : SchedulePaymentAction
    data object ConfirmManualCreditor : SchedulePaymentAction

    /** @param amount In MAJOR units, as typed: `250` and `250.00` both mean £250. */
    data class EnterAmount(val amount: String) : SchedulePaymentAction

    /**
     * `InstructedAmount.Currency` — what the amount is denominated in, and the form's only currency
     * decision.
     *
     * There was a second action for `CurrencyOfTransfer`. Two selectors made a combination the bank
     * refuses reachable, and stated the currency twice for a customer who was making one decision;
     * the transfer currency is now derived from this one.
     */
    data class SelectInstructedCurrency(val currency: String) : SchedulePaymentAction
    data class SelectChargeBearer(val bearer: ChargeBearer) : SchedulePaymentAction
    data class EnterReference(val reference: String) : SchedulePaymentAction

    /**
     * Opens the execution-date dialog, restamping the window as it goes.
     *
     * The restamp is the point: the allowed range is a function of today, and an app left open across
     * midnight would otherwise keep offering a window computed against the previous day.
     */
    data object OpenDatePicker : SchedulePaymentAction

    /** Closes the dialog without choosing. Any date already chosen survives — this is not a clear. */
    data object DismissDatePicker : SchedulePaymentAction
    data class SelectExecutionDate(val date: LocalDate) : SchedulePaymentAction
    data object ReviewPayment : SchedulePaymentAction
    data object ConfirmAndStageConsent : SchedulePaymentAction

    /** Re-stages after a staging failure. Nothing reached the bank, so there is nothing to replay. */
    data object RetryStaging : SchedulePaymentAction

    /** Returns to the payer step after the bank refused the account the payment came from. */
    data object ChangePayer : SchedulePaymentAction

    /**
     * Leaves the waiting state after the customer came back without authorising.
     *
     * Not a cancel. The staged consent stays where it is — a scheduled consent cannot be withdrawn,
     * and the bank answers `405` to a delete — so this only releases the screen, which would
     * otherwise wait for a callback that is never coming.
     */
    data object AbandonAuthorisation : SchedulePaymentAction
    data object BackStep : SchedulePaymentAction
    data object RetryLoad : SchedulePaymentAction

    /**
     * Re-reads the saved payees for the chosen payer.
     *
     * Separate from [RetryLoad], which refreshes the accounts. The two streams fail independently —
     * the failure this exists for was `403` on beneficiaries while accounts returned `200` — so one
     * button retrying both would re-fetch something that never failed and still leave the payee row
     * with no way back.
     */
    data object RetryPayees : SchedulePaymentAction
}

/**
 * The one-shot effect the Screen owns rather than the ViewModel.
 *
 * Opening the bank's authorisation page is something only the composition can do, so it leaves as an
 * event instead of becoming state the screen has to interpret.
 */
sealed interface SchedulePaymentEvent {
    data class LaunchAuthorisation(val url: String) : SchedulePaymentEvent
}

/**
 * Resolves a failure to the kind that decides the recovery offered.
 *
 * The OBIE error code is consulted before the HTTP status because four of these arrive as `400` and
 * only the code separates them — "outside your limits" and "diverged from what you approved" want
 * different actions from the PSU.
 */
internal fun classifySchedulePaymentError(throwable: Throwable): SchedulePaymentErrorKind =
    throwable.executionDateRefusalKind()
        ?: throwable.payerRefusalKind()
        ?: throwable.obieErrorCode()?.let(::obieCodeToKind)
        ?: throwable.transportKind()

/**
 * The date case, checked before everything else.
 *
 * Ordered first because it is the most specific and the most frequently mis-served: `U002` on the
 * date field is the same code as `U002` on a refused payer, and the code alone would classify a date
 * beyond the window as a generic invalid field — sending the customer to check details that are
 * correct instead of to the one control that would fix it.
 *
 * Reading the path also means `U003`, `U002` and `U004` on this field need no enumeration, and a
 * future fourth code on the same path lands correctly without another edit here.
 */
private fun Throwable.executionDateRefusalKind(): SchedulePaymentErrorKind? =
    SchedulePaymentErrorKind.DateRefused.takeIf { isExecutionDateRefusal() }

/**
 * The payer case, checked before the code.
 *
 * One code covers many fields — `U002` is only "Invalid Field" — so the code alone cannot tell a
 * refused payer from a refused reference, and only the payer has a different recovery. Reading the
 * path first also means a future code on the same path lands correctly without being enumerated.
 */
private fun Throwable.payerRefusalKind(): SchedulePaymentErrorKind? =
    SchedulePaymentErrorKind.PayerNotSupported.takeIf { isDebtorAccountRefusal() }

private fun Throwable.transportKind(): SchedulePaymentErrorKind =
    when ((this as? RemoteException)?.networkError) {
        is NetworkError.Client.Unauthorized -> SchedulePaymentErrorKind.TokenExpired
        is NetworkError.Client.Forbidden -> SchedulePaymentErrorKind.ConsentRevoked
        is NetworkError.Client.RateLimited -> SchedulePaymentErrorKind.RateLimited
        else -> SchedulePaymentErrorKind.NetworkError
    }

private fun obieCodeToKind(code: String): SchedulePaymentErrorKind? = when {
    code.endsWith("U019") -> SchedulePaymentErrorKind.SignatureMissing
    code.endsWith("U009") -> SchedulePaymentErrorKind.ConsentNotAuthorised
    code.endsWith("U008") -> SchedulePaymentErrorKind.ConsentMismatch
    code.endsWith("U014") -> SchedulePaymentErrorKind.OutsideControlParameters
    code.endsWith("U002") -> SchedulePaymentErrorKind.InvalidField
    // U021 names a field the bank would not accept, so it belongs with U002 rather than falling
    // through to NetworkError. Observed live when a credit card was sent as DebtorAccount: the app
    // told the customer to check their connection and offered a Retry that could only fail again.
    code.endsWith("U021") -> SchedulePaymentErrorKind.InvalidField
    // U003 reaches here only if the bank ever reports it without a path. The path check above is the
    // usual route; this is the belt to its braces, because a past date must never read as a network
    // failure whatever shape the refusal arrives in.
    code.endsWith("U003") -> SchedulePaymentErrorKind.DateRefused
    code.endsWith("U004") || code.endsWith("U005") -> SchedulePaymentErrorKind.RequestMalformed
    code.endsWith("U027") -> SchedulePaymentErrorKind.SchemeNotSupported
    else -> null
}

/** The bank's own reference for a failure, surfaced so a support call has something to quote. */
internal fun supportReferenceOf(throwable: Throwable): String? = throwable.obieSupportReference()
