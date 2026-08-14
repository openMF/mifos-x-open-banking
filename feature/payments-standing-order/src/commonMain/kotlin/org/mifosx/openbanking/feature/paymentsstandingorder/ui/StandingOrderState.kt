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

import kotlinx.datetime.LocalDate
import org.mifosx.openbanking.core.data.util.RemoteException
import org.mifosx.openbanking.core.data.util.isDebtorAccountRefusal
import org.mifosx.openbanking.core.data.util.isFinalPaymentDateRefusal
import org.mifosx.openbanking.core.data.util.isFirstPaymentDateRefusal
import org.mifosx.openbanking.core.data.util.isFrequencyRefusal
import org.mifosx.openbanking.core.data.util.obieErrorCode
import org.mifosx.openbanking.core.data.util.obieSupportReference
import org.mifosx.openbanking.core.model.banking.BankAccount
import org.mifosx.openbanking.core.model.banking.payment.ChargeBearer
import org.mifosx.openbanking.core.model.banking.payment.CreditorSelection
import org.mifosx.openbanking.core.model.banking.payment.PaymentRail
import org.mifosx.openbanking.core.model.banking.payment.StandingOrderDraft
import org.mifosx.openbanking.core.model.banking.payment.StandingOrderFrequency
import template.core.base.network.NetworkError

/** The two steps of one screen, held as state rather than as separate destinations. */
enum class StandingOrderStep { Form, Review }

/** How far the handoff to the bank has got. */
enum class StandingOrderStage {
    StagingConsent,
    AwaitingAuthorisation,
}

/**
 * What went wrong, in terms a recovery can be offered for.
 *
 * Fourteen kinds, and the two date members are the reason there are not thirteen. HSBC raises a
 * single `U003` whose message recites three rules at once — not today or tomorrow, within twelve
 * months, and after the first payment date — whichever was actually broken. The code cannot say which
 * of the customer's two dates to change, and the OBIE **path** can, so the two are kept apart and
 * classified on it.
 *
 * Three of these — [RequestMalformed], [SchemeNotSupported] and [FrequencyRefused] — indicate the app
 * built a body its own rules should have prevented. They exist so a defect surfaces as a message
 * rather than a crash, and their copy must not read as something the customer can fix.
 */
enum class StandingOrderErrorKind {
    /** `U019` — the detached signature was missing or unacceptable. Nothing the customer can do. */
    SignatureMissing,

    /** `U009` — the consent was not authorised. */
    ConsentNotAuthorised,

    /** `U008` — the submitted mandate differed from the staged one. */
    ConsentMismatch,

    /** `U014` — outside the limits agreed with the bank. */
    OutsideControlParameters,

    /** `U002`/`U021` on a field other than a date or the payer. */
    InvalidField,

    /** The first payment date was refused. */
    FirstDateRefused,

    /** The final payment date was refused — too early, too soon, or beyond twelve months. */
    FinalDateRefused,

    /** `U002` on the frequency. The closed enum should make this unreachable. */
    FrequencyRefused,

    /** `U004`/`U005` — a field was missing or not expected. A defect, not a customer error. */
    RequestMalformed,

    /** `U027` — a creditor or debtor scheme the rail cannot accept. */
    SchemeNotSupported,

    /** 403 — the consent was revoked. */
    ConsentRevoked,

    /** 401 — the payments token expired before the mandate was submitted. */
    TokenExpired,

    /** 429. */
    RateLimited,

    /** The bank refused the chosen payer; the picker will stop offering it. */
    PayerNotSupported,

    NetworkError,
}

internal val StandingOrderErrorKind.isRetryable: Boolean
    get() = this == StandingOrderErrorKind.NetworkError ||
        this == StandingOrderErrorKind.RateLimited ||
        this == StandingOrderErrorKind.TokenExpired

internal val StandingOrderErrorKind.needsReauthorisation: Boolean
    get() = this == StandingOrderErrorKind.ConsentNotAuthorised ||
        this == StandingOrderErrorKind.TokenExpired

internal val StandingOrderErrorKind.needsAmountChange: Boolean
    get() = this == StandingOrderErrorKind.OutsideControlParameters

/** Which of the two dates to send the customer back to, or null when neither is at fault. */
internal val StandingOrderErrorKind.refusedDateRole: StandingOrderDateRole?
    get() = when (this) {
        StandingOrderErrorKind.FirstDateRefused -> StandingOrderDateRole.First
        StandingOrderErrorKind.FinalDateRefused -> StandingOrderDateRole.Final
        else -> null
    }

data class StandingOrderFieldErrors(
    val sortCodeInvalid: Boolean = false,
    val accountNumberInvalid: Boolean = false,
    val ibanInvalid: Boolean = false,
) {
    val isEmpty: Boolean
        get() = !sortCodeInvalid && !accountNumberInvalid && !ibanInvalid
}

enum class StandingOrderAmountProblem {
    NotANumber,
    TooManyDecimals,
    NotPositive,
    ExceedsAvailableBalance,
}

data class StandingOrderPickerRow(
    val id: String,
    val initials: String,
    val headline: String,
    val supporting: String,
    val shortName: String = "",
)

data class StandingOrderAccountRow(
    val id: String,
    val nickname: String,
    val accountSubType: String,
    val accountNumber: String,
    val rawIdentification: String,
    val supporting: String,
)

sealed interface StandingOrderUiState {

    data object Loading : StandingOrderUiState

    /**
     * The form and the review, as one state carrying a [step].
     *
     * Note what the international rail does **not** get: [recurringAmountEnabled], [finalAmountEnabled]
     * and [referenceEnabled] all go false there, and the corresponding inputs are emptied. The fields
     * are not rendered at all on that rail — `OBInternationalStandingOrder4` has no member for any of
     * them, so there is nothing to fill in.
     *
     * The emptying still matters even though the controls are gone: `buildDraft` reads these inputs
     * only on the domestic rail, and that is the guarantee the bank actually sees. A value typed
     * before a rail switch cannot reach the wire whether or not its field is on screen.
     */
    data class Content(
        val step: StandingOrderStep,
        val rail: PaymentRail = PaymentRail.Domestic,
        val debtorAccounts: List<BankAccount>,
        val beneficiaries: List<StandingOrderPickerRow>,
        val debtorRows: List<StandingOrderAccountRow>,
        val debtorAccountId: String? = null,
        val payerPickerExpanded: Boolean = false,
        val letBankChoosePayer: Boolean = false,
        val creditor: CreditorSelection? = null,
        val creditorLabel: String = "",
        val creditorSupporting: String = "",
        val debtorAccountRow: StandingOrderAccountRow? = null,
        val manualEntryVisible: Boolean = false,
        val manualSortCode: String = "",
        val manualAccountNumber: String = "",
        val manualIban: String = "",
        val manualName: String = "",
        val frequency: StandingOrderFrequency = StandingOrderFrequency.Monthly,
        val firstPaymentDate: LocalDate? = null,
        val firstPaymentDateLabel: String = "",
        val finalPaymentDate: LocalDate? = null,
        val finalPaymentDateLabel: String = "",
        val datePickerRole: StandingOrderDateRole? = null,
        val amountInput: String = "",
        val amountLabel: String = "",
        val recurringAmountInput: String = "",
        val finalAmountInput: String = "",
        val instructedCurrency: String = "GBP",
        val chargeBearer: ChargeBearer = ChargeBearer.BorneByCreditor,
        val reference: String = "",
        val today: LocalDate,
        val amountProblem: StandingOrderAmountProblem? = null,
        val fieldErrors: StandingOrderFieldErrors = StandingOrderFieldErrors(),
        val availableBalanceMinorUnits: Long? = null,
        val availableBalanceLabel: String = "",
        val debtorCurrency: String = "",
        val offeredCurrencies: List<String> = emptyList(),
        val payeesFailed: Boolean = false,
        val payeesLoading: Boolean = false,
    ) : StandingOrderUiState {

        /** The three optional refinements the international rail has no wire fields for. */
        val recurringAmountEnabled: Boolean get() = rail == PaymentRail.Domestic
        val finalAmountEnabled: Boolean get() = rail == PaymentRail.Domestic
        val referenceEnabled: Boolean get() = rail == PaymentRail.Domestic

        val datePickerVisible: Boolean get() = datePickerRole != null

        val canReview: Boolean
            get() = amountProblem == null &&
                amountInput.isNotBlank() &&
                creditor != null &&
                firstPaymentDate != null &&
                !payerUndecided

        val payerUndecided: Boolean
            get() = debtorAccountId == null && !letBankChoosePayer

        val payeesUnavailable: Boolean
            get() = debtorAccountId == null

        val showsConversionAdvisory: Boolean
            get() = debtorCurrency.isNotBlank() &&
                !debtorCurrency.equals(instructedCurrency, ignoreCase = true)

        val hasBeneficiaries: Boolean
            get() = beneficiaries.isNotEmpty()

        /** An open-ended mandate runs until the customer stops it, which the copy must say out loud. */
        val isOpenEnded: Boolean get() = finalPaymentDate == null
    }

    data class Submitting(
        val stage: StandingOrderStage,
        val amountLabel: String,
        val creditorName: String,
        val consentId: String? = null,
    ) : StandingOrderUiState

    data class Error(
        val kind: StandingOrderErrorKind,
        val supportReference: String? = null,
    ) : StandingOrderUiState
}

data class StandingOrderState(
    val uiState: StandingOrderUiState = StandingOrderUiState.Loading,
    val draft: StandingOrderDraft? = null,
    val consentId: String? = null,
)

sealed interface StandingOrderAction {
    data class SelectRail(val rail: PaymentRail) : StandingOrderAction
    data class SelectDebtorAccount(val accountId: String) : StandingOrderAction
    data object TogglePayerPicker : StandingOrderAction
    data object LetBankChoosePayer : StandingOrderAction
    data class SelectCreditor(val beneficiaryId: String) : StandingOrderAction
    data object ShowManualCreditorEntry : StandingOrderAction
    data class EnterManualSortCode(val sortCode: String) : StandingOrderAction
    data class EnterManualAccountNumber(val accountNumber: String) : StandingOrderAction
    data class EnterManualIban(val iban: String) : StandingOrderAction
    data class EnterManualName(val name: String) : StandingOrderAction
    data object ConfirmManualCreditor : StandingOrderAction
    data class SelectFrequency(val frequency: StandingOrderFrequency) : StandingOrderAction
    data class OpenDatePicker(val role: StandingOrderDateRole) : StandingOrderAction
    data object DismissDatePicker : StandingOrderAction
    data class SelectDate(val role: StandingOrderDateRole, val date: LocalDate) : StandingOrderAction
    data object ClearFinalDate : StandingOrderAction
    data class EnterAmount(val amount: String) : StandingOrderAction
    data class EnterRecurringAmount(val amount: String) : StandingOrderAction
    data class EnterFinalAmount(val amount: String) : StandingOrderAction
    data class SelectInstructedCurrency(val currency: String) : StandingOrderAction
    data class SelectChargeBearer(val bearer: ChargeBearer) : StandingOrderAction
    data class EnterReference(val reference: String) : StandingOrderAction
    data object ReviewStandingOrder : StandingOrderAction
    data object ConfirmAndStageConsent : StandingOrderAction
    data object RetryStaging : StandingOrderAction
    data object ChangePayer : StandingOrderAction
    data object AbandonAuthorisation : StandingOrderAction
    data object BackStep : StandingOrderAction
    data object RetryLoad : StandingOrderAction
    data object RetryPayees : StandingOrderAction
}

sealed interface StandingOrderEvent {
    data class LaunchAuthorisation(val url: String) : StandingOrderEvent
}

/**
 * Classifies a refusal into something a recovery can be offered for.
 *
 * Order matters, and it is not the obvious one. The OBIE **path** is read before the code, because
 * one code covers many fields: `U002` alone says only "Invalid Field" and is raised for a refused
 * payer, a refused frequency and a date beyond the window alike. Reading the code first would land a
 * refused date on "check your details" — advice pointing at everything except the one control the
 * customer has to change.
 *
 * The two date paths are checked separately for the same reason at finer grain: the bank's single
 * `U003` message recites all three date rules whichever was broken, so only the path distinguishes
 * "your end date is too soon" from "your start date is in the past".
 */
internal fun classifyStandingOrderError(throwable: Throwable): StandingOrderErrorKind =
    throwable.firstDateRefusalKind()
        ?: throwable.finalDateRefusalKind()
        ?: throwable.payerRefusalKind()
        ?: throwable.frequencyRefusalKind()
        ?: throwable.obieErrorCode()?.let(::obieCodeToKind)
        ?: throwable.transportKind()

private fun Throwable.firstDateRefusalKind(): StandingOrderErrorKind? =
    StandingOrderErrorKind.FirstDateRefused.takeIf { isFirstPaymentDateRefusal() }

private fun Throwable.finalDateRefusalKind(): StandingOrderErrorKind? =
    StandingOrderErrorKind.FinalDateRefused.takeIf { isFinalPaymentDateRefusal() }

private fun Throwable.payerRefusalKind(): StandingOrderErrorKind? =
    StandingOrderErrorKind.PayerNotSupported.takeIf { isDebtorAccountRefusal() }

/**
 * Checked after the dates, not before.
 *
 * The frequency path fragment is `Frequency`, which also appears inside
 * `MandateRelatedInformation.Frequency.Type` — and a date refusal's path names the date, not the
 * frequency, so the ordering only matters if the bank ever reports both. It reports one.
 */
private fun Throwable.frequencyRefusalKind(): StandingOrderErrorKind? =
    StandingOrderErrorKind.FrequencyRefused.takeIf { isFrequencyRefusal() }

private fun Throwable.transportKind(): StandingOrderErrorKind =
    when ((this as? RemoteException)?.networkError) {
        is NetworkError.Client.Unauthorized -> StandingOrderErrorKind.TokenExpired
        is NetworkError.Client.Forbidden -> StandingOrderErrorKind.ConsentRevoked
        is NetworkError.Client.RateLimited -> StandingOrderErrorKind.RateLimited
        else -> StandingOrderErrorKind.NetworkError
    }

private fun obieCodeToKind(code: String): StandingOrderErrorKind? = when {
    code.endsWith("U019") -> StandingOrderErrorKind.SignatureMissing
    code.endsWith("U009") -> StandingOrderErrorKind.ConsentNotAuthorised
    code.endsWith("U008") -> StandingOrderErrorKind.ConsentMismatch
    code.endsWith("U014") -> StandingOrderErrorKind.OutsideControlParameters
    code.endsWith("U002") -> StandingOrderErrorKind.InvalidField
    code.endsWith("U021") -> StandingOrderErrorKind.InvalidField
    code.endsWith("U003") -> StandingOrderErrorKind.FinalDateRefused
    code.endsWith("U004") || code.endsWith("U005") -> StandingOrderErrorKind.RequestMalformed
    code.endsWith("U027") -> StandingOrderErrorKind.SchemeNotSupported
    else -> null
}

internal fun supportReferenceOf(throwable: Throwable): String? = throwable.obieSupportReference()
