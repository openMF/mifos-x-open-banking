/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.sendmoney.ui

import org.mifosx.openbanking.core.data.util.RemoteException
import org.mifosx.openbanking.core.data.util.isDebtorAccountRefusal
import org.mifosx.openbanking.core.data.util.obieErrorCode
import org.mifosx.openbanking.core.data.util.obieSupportReference
import org.mifosx.openbanking.core.model.banking.BankAccount
import org.mifosx.openbanking.core.model.banking.payment.ChargeBearer
import org.mifosx.openbanking.core.model.banking.payment.CreditorSelection
import org.mifosx.openbanking.core.model.banking.payment.PaymentDraft
import org.mifosx.openbanking.core.model.banking.payment.PaymentRail
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
enum class SendMoneyStep {
    Form,
    Review,
}

/**
 * How far the hand-off to the bank has got. Distinct from [SendMoneyStep]: nothing is editable here.
 *
 * It stops at [AwaitingAuthorisation] because that is where this screen's part ends: the customer
 * leaves for the bank, and the leg that returns — payment-consent — confirms funds and submits.
 */
enum class SendMoneyStage {
    StagingConsent,
    AwaitingAuthorisation,
}

/**
 * Why a payment failed, and therefore what to offer next.
 *
 * Ten kinds and four recoveries, deliberately not collapsed: retrying a revoked consent will never
 * succeed, and re-authorising an insufficient balance does not add money to the account.
 */
enum class SendMoneyErrorKind {
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

    /** The consent was withdrawn. */
    ConsentRevoked,

    /** The payments token expired before the payment was submitted. */
    TokenExpired,

    RateLimited,

    /** The funds confirmation came back false, so nothing was submitted. */
    InsufficientFunds,

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
internal val SendMoneyErrorKind.isRetryable: Boolean
    get() = this == SendMoneyErrorKind.NetworkError ||
        this == SendMoneyErrorKind.RateLimited ||
        this == SendMoneyErrorKind.TokenExpired

/** Whether the PSU should be sent back to re-authorise at the bank. */
internal val SendMoneyErrorKind.needsReauthorisation: Boolean
    get() = this == SendMoneyErrorKind.ConsentNotAuthorised || this == SendMoneyErrorKind.TokenExpired

/** Whether the amount is the thing to change. */
internal val SendMoneyErrorKind.needsAmountChange: Boolean
    get() = this == SendMoneyErrorKind.OutsideControlParameters ||
        this == SendMoneyErrorKind.InsufficientFunds

/**
 * Which manual-entry fields are currently wrong.
 *
 * Per-field rather than one message, because both can be wrong at once and a single slot would hide
 * one of them. Flags rather than text: the wording is a string resource the composable resolves.
 */
data class SendMoneyFieldErrors(
    val sortCodeInvalid: Boolean = false,
    val accountNumberInvalid: Boolean = false,
    val ibanInvalid: Boolean = false,
) {
    val isEmpty: Boolean
        get() = !sortCodeInvalid && !accountNumberInvalid && !ibanInvalid
}

/** Why the entered amount is not yet payable. */
enum class SendMoneyAmountProblem {
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
data class SendMoneyPickerRow(
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
data class SendMoneyAccountRow(
    val id: String,
    val nickname: String,
    val accountSubType: String,
    val accountNumber: String,
    val rawIdentification: String,
    val supporting: String,
)

sealed interface SendMoneyUiState {

    data object Loading : SendMoneyUiState

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
        val step: SendMoneyStep,
        val rail: PaymentRail = PaymentRail.Domestic,
        val debtorAccounts: List<BankAccount>,
        val beneficiaries: List<SendMoneyPickerRow>,
        val debtorRows: List<SendMoneyAccountRow>,
        val debtorAccountId: String? = null,
        val payerPickerExpanded: Boolean = false,
        val letBankChoosePayer: Boolean = false,
        val creditor: CreditorSelection? = null,
        val creditorLabel: String = "",
        val creditorSupporting: String = "",
        val debtorAccountRow: SendMoneyAccountRow? = null,
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
        val amountProblem: SendMoneyAmountProblem? = null,
        val fieldErrors: SendMoneyFieldErrors = SendMoneyFieldErrors(),
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
    ) : SendMoneyUiState {

        /**
         * Review is reachable only once the amount is payable, a payee is chosen and the payer
         * question has been answered one way or the other.
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

    /** In flight. There is no CTA at all here — see the note on [SendMoneyStage]. */
    data class Submitting(
        val stage: SendMoneyStage,
        val amountLabel: String,
        val creditorName: String,
        val consentId: String? = null,
    ) : SendMoneyUiState

    data class Error(
        val kind: SendMoneyErrorKind,
        val supportReference: String? = null,
    ) : SendMoneyUiState
}

/**
 * @property draft The instruction as it was staged, carrying both idempotency keys. Retained so a
 *   retry resubmits byte-identical content rather than rebuilding it.
 */
data class SendMoneyState(
    val uiState: SendMoneyUiState = SendMoneyUiState.Loading,
    val draft: PaymentDraft? = null,
    val consentId: String? = null,
)

sealed interface SendMoneyAction {
    data class SelectRail(val rail: PaymentRail) : SendMoneyAction
    data class SelectDebtorAccount(val accountId: String) : SendMoneyAction

    /**
     * Opens or closes the payer picker.
     *
     * Only the expansion — choosing an account is [SelectDebtorAccount], which closes the picker as
     * a consequence of the choice rather than needing a second action from the composable.
     */
    data object TogglePayerPicker : SendMoneyAction

    /** Send no `DebtorAccount` and let the PSU pick the account at their bank. */
    data object LetBankChoosePayer : SendMoneyAction
    data class SelectCreditor(val beneficiaryId: String) : SendMoneyAction
    data object ShowManualCreditorEntry : SendMoneyAction
    data class EnterManualSortCode(val sortCode: String) : SendMoneyAction
    data class EnterManualAccountNumber(val accountNumber: String) : SendMoneyAction
    data class EnterManualIban(val iban: String) : SendMoneyAction
    data class EnterManualName(val name: String) : SendMoneyAction
    data object ConfirmManualCreditor : SendMoneyAction

    /** @param amount In MAJOR units, as typed: `250` and `250.00` both mean £250. */
    data class EnterAmount(val amount: String) : SendMoneyAction

    /**
     * `InstructedAmount.Currency` — what the amount is denominated in, and the form's only currency
     * decision.
     *
     * There was a second action for `CurrencyOfTransfer`. Two selectors made a combination the bank
     * refuses reachable, and stated the currency twice for a customer who was making one decision;
     * the transfer currency is now derived from this one.
     */
    data class SelectInstructedCurrency(val currency: String) : SendMoneyAction
    data class SelectChargeBearer(val bearer: ChargeBearer) : SendMoneyAction
    data class EnterReference(val reference: String) : SendMoneyAction
    data object ReviewPayment : SendMoneyAction
    data object ConfirmAndStageConsent : SendMoneyAction

    /** Re-stages after a staging failure. Nothing reached the bank, so there is nothing to replay. */
    data object RetryStaging : SendMoneyAction

    /** Returns to the payer step after the bank refused the account the payment came from. */
    data object ChangePayer : SendMoneyAction
    data object BackStep : SendMoneyAction
    data object RetryLoad : SendMoneyAction

    /**
     * Re-reads the saved payees for the chosen payer.
     *
     * Separate from [RetryLoad], which refreshes the accounts. The two streams fail independently —
     * the failure this exists for was `403` on beneficiaries while accounts returned `200` — so one
     * button retrying both would re-fetch something that never failed and still leave the payee row
     * with no way back.
     */
}

/**
 * The one-shot effect the Screen owns rather than the ViewModel.
 *
 * Opening the bank's authorisation page is something only the composition can do, so it leaves as an
 * event instead of becoming state the screen has to interpret.
 */
sealed interface SendMoneyEvent {
    data class LaunchAuthorisation(val url: String) : SendMoneyEvent
}

/**
 * Resolves a failure to the kind that decides the recovery offered.
 *
 * The OBIE error code is consulted before the HTTP status because four of these arrive as `400` and
 * only the code separates them — "outside your limits" and "diverged from what you approved" want
 * different actions from the PSU.
 */
internal fun classifySendMoneyError(throwable: Throwable): SendMoneyErrorKind =
    throwable.payerRefusalKind()
        ?: throwable.obieErrorCode()?.let(::obieCodeToKind)
        ?: throwable.transportKind()

/**
 * The payer case, checked before the code.
 *
 * One code covers many fields — `U002` is only "Invalid Field" — so the code alone cannot tell a
 * refused payer from a refused reference, and only the payer has a different recovery. Reading the
 * path first also means a future code on the same path lands correctly without being enumerated.
 */
private fun Throwable.payerRefusalKind(): SendMoneyErrorKind? =
    SendMoneyErrorKind.PayerNotSupported.takeIf { isDebtorAccountRefusal() }

private fun Throwable.transportKind(): SendMoneyErrorKind =
    when ((this as? RemoteException)?.networkError) {
        is NetworkError.Client.Unauthorized -> SendMoneyErrorKind.TokenExpired
        is NetworkError.Client.Forbidden -> SendMoneyErrorKind.ConsentRevoked
        is NetworkError.Client.RateLimited -> SendMoneyErrorKind.RateLimited
        else -> SendMoneyErrorKind.NetworkError
    }

private fun obieCodeToKind(code: String): SendMoneyErrorKind? = when {
    code.endsWith("U019") -> SendMoneyErrorKind.SignatureMissing
    code.endsWith("U009") -> SendMoneyErrorKind.ConsentNotAuthorised
    code.endsWith("U008") -> SendMoneyErrorKind.ConsentMismatch
    code.endsWith("U014") -> SendMoneyErrorKind.OutsideControlParameters
    code.endsWith("U002") -> SendMoneyErrorKind.InvalidField
    // U021 names a field the bank would not accept, so it belongs with U002 rather than falling
    // through to NetworkError. Observed live when a credit card was sent as DebtorAccount: the app
    // told the customer to check their connection and offered a Retry that could only fail again.
    code.endsWith("U021") -> SendMoneyErrorKind.InvalidField
    else -> null
}

/** The bank's own reference for a failure, surfaced so a support call has something to quote. */
internal fun supportReferenceOf(throwable: Throwable): String? = throwable.obieSupportReference()
