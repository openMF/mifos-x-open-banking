/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.paymentsschedulepayment

import kotlinx.datetime.LocalDate
import org.mifosx.openbanking.core.common.formatMinorUnits
import org.mifosx.openbanking.core.model.banking.AccountBalance
import org.mifosx.openbanking.core.model.banking.AccountWithBalance
import org.mifosx.openbanking.core.model.banking.BankAccount
import org.mifosx.openbanking.core.model.banking.BeneficiaryItem
import org.mifosx.openbanking.core.model.banking.BeneficiaryScheme
import org.mifosx.openbanking.core.model.banking.payment.ChargeBearer
import org.mifosx.openbanking.core.model.banking.payment.CreditorSelection
import org.mifosx.openbanking.core.model.banking.payment.PaymentRail
import org.mifosx.openbanking.core.model.banking.payment.PaymentReceipt
import org.mifosx.openbanking.core.model.banking.payment.PaymentStatus
import org.mifosx.openbanking.core.model.banking.payment.StagedConsent
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.OFFERED_CURRENCIES
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.SchedulePaymentAccountRow
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.SchedulePaymentAmountProblem
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.SchedulePaymentErrorKind
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.SchedulePaymentPickerRow
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.SchedulePaymentStage
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.SchedulePaymentState
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.SchedulePaymentStep
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.SchedulePaymentUiState

/**
 * The sandbox capture the whole spec is told through: £850.00 from the PSU's current account to
 * Jameson Lettings, consent 812774903, payment PMT-812774903-01.
 *
 * Shared by the ViewModel, desktop UI, Robolectric and screenshot suites so all four assert against
 * one payment rather than four unrelated ones.
 */
object SchedulePaymentFixtures {

    /**
     * A fixed "today", so the date window a fixture describes never moves with the calendar.
     *
     * Every date fixture is stated relative to this rather than to the real clock. A suite that read
     * the real one would assert a different window on every run, and would pass or fail depending on
     * what day of the week it happened to be — which is exactly the axis this feature's rules turn on.
     */
    val TODAY: LocalDate = LocalDate(2026, 8, 12)

    /** A Friday, three days after [TODAY] — inside the window and a weekday on both rails. */
    val EXECUTION_DATE: LocalDate = LocalDate(2026, 8, 14)

    const val EXECUTION_DATE_LABEL = "Friday, 14 August 2026"

    const val CURRENT_ACCOUNT_ID = "123456791"
    const val SAVINGS_ACCOUNT_ID = "1123456841"
    const val CREDIT_CARD_ID = "1123456842"
    const val GLOBAL_MONEY_ID = "1123456843"

    /** The payee keys the picker selects by: the destination account. */
    const val JAMESON_ID = "40120965872310"
    const val SHARMA_ID = "23058011223344"
    const val EDF_ID = "60000199887766"
    const val WEISS_ID = "DE89370400440532013000"
    const val DUPONT_ID = "FR1420041010050500013M02606"
    const val CONSENT_ID = "812774903"
    const val PAYMENT_ID = "PMT-812774903-01"
    const val SUPPORT_REFERENCE = "9b7e4d20-1a6c-4f88-9d3a-2c5b7e10f4a6"

    /** £21,530.92 available — comfortably covers the fixture payment. */
    fun currentAccount(): BankAccount = BankAccount(
        accountId = CURRENT_ACCOUNT_ID,
        nickname = "Current account ·· 3349",
        accountSubType = "CurrentAccount",
        currency = "GBP",
        sortCode = "802001",
        accountNumber = "10203349",
        rawIdentification = "80200110203349",
    )

    /** £482.10 available — the account TC-SEND-003 overdraws. */
    fun savingsAccount(): BankAccount = BankAccount(
        accountId = SAVINGS_ACCOUNT_ID,
        nickname = "BMM ACCOUNT ·· 3695",
        accountSubType = "Savings",
        currency = "GBP",
        sortCode = "801225",
        accountNumber = "90953695",
        rawIdentification = "80122590953695",
    )

    /**
     * A credit card, as HSBC returns one: the account-number field is a masked PAN slice, not a
     * real account number, which is why it cannot fund a payment. Present in [accounts] on purpose
     * — the picker must be proven to exclude it, and a fixture without one proves nothing.
     */
    fun creditCard(): BankAccount = BankAccount(
        accountId = CREDIT_CARD_ID,
        nickname = "",
        // "CARD", not "CreditCard": v4.0 has no AccountSubType, so AccountMapper falls through to
        // AccountTypeCode, which HSBC sends as CARD. Both resolve, but the fixture mirrors the bank.
        accountSubType = "CARD",
        currency = "GBP",
        sortCode = "",
        accountNumber = "xxxx-xxxx-xxxx-3456",
        rawIdentification = "xxxx-xxxx-xxxx-3456",
    )

    /**
     * The Global Money wallet exactly as HSBC returns it: `AccountTypeCode: CACC`, so
     * `accountSubType` reads "CACC" and is indistinguishable from a current account. The one field
     * that gives it away is the free-text description, which the sandbox returns verbatim as
     * "GLOBAL MONEY ACCOUNT" — so the fixture carries it. It stays in [accounts] on purpose: the
     * picker must be proven to exclude it, and a fixture without one proves nothing.
     */
    fun globalMoneyWallet(): BankAccount = BankAccount(
        accountId = GLOBAL_MONEY_ID,
        nickname = "",
        accountSubType = "CACC",
        currency = "GBP",
        sortCode = "801197",
        accountNumber = "70009652",
        rawIdentification = "80119770009652",
        description = "GLOBAL MONEY ACCOUNT",
    )

    fun accounts(): List<AccountWithBalance> = listOf(
        AccountWithBalance(
            account = currentAccount(),
            balance = AccountBalance(
                accountId = CURRENT_ACCOUNT_ID,
                currency = "GBP",
                currentAmount = "21530.92",
                availableAmount = "21530.92",
            ),
        ),
        AccountWithBalance(
            account = savingsAccount(),
            balance = AccountBalance(
                accountId = SAVINGS_ACCOUNT_ID,
                currency = "GBP",
                currentAmount = "482.10",
                availableAmount = "482.10",
            ),
        ),
        AccountWithBalance(
            account = creditCard(),
            balance = AccountBalance(
                accountId = CREDIT_CARD_ID,
                currency = "GBP",
                currentAmount = "245865.06",
                availableAmount = "245865.06",
            ),
        ),
        AccountWithBalance(
            account = globalMoneyWallet(),
            balance = AccountBalance(
                accountId = GLOBAL_MONEY_ID,
                currency = "GBP",
                currentAmount = "303167.25",
                availableAmount = "303167.25",
            ),
        ),
    )

    fun beneficiaries(): List<BeneficiaryItem> = listOf(
        BeneficiaryItem(
            beneficiaryId = JAMESON_ID,
            accountId = CURRENT_ACCOUNT_ID,
            creditorName = "Jameson Lettings",
            scheme = BeneficiaryScheme.SortCode,
            identification = "40120965872310",
            reference = "RENT-FLAT12",
        ),
        BeneficiaryItem(
            beneficiaryId = SHARMA_ID,
            accountId = CURRENT_ACCOUNT_ID,
            creditorName = "John Sharma",
            scheme = BeneficiaryScheme.SortCode,
            identification = "23058011223344",
            reference = "FAMILY",
        ),
        BeneficiaryItem(
            beneficiaryId = EDF_ID,
            accountId = CURRENT_ACCOUNT_ID,
            creditorName = "EDF Energy",
            scheme = BeneficiaryScheme.SortCode,
            identification = "60000199887766",
            reference = "ELEC-8841",
        ),
    )

    fun stagedConsent(): StagedConsent = StagedConsent(
        consentId = CONSENT_ID,
        status = "AWAU",
        authorizationUrl = "https://sandbox.ob.hsbc.co.uk/authorize?request=jwt",
        state = "state-1",
        nonce = "nonce-1",
    )

    fun receipt(): PaymentReceipt = PaymentReceipt(
        domesticPaymentId = PAYMENT_ID,
        consentId = CONSENT_ID,
        status = PaymentStatus.AcceptedSettlementInProcess,
        creationDateTime = "2026-08-05T10:44:05+00:00",
        statusUpdateDateTime = "2026-08-05T10:44:05+00:00",
        amountLabel = "£850.00",
        creditorName = "Jameson Lettings",
    )

    /**
     * Raw fields, not a finished label — the readable name is resolved at render by
     * `accountDisplayName`, exactly as it is in production. A fixture that pre-baked the label would
     * have passed while the live screen rendered blank rows, which is what happened.
     */
    private fun debtorRows(): List<SchedulePaymentAccountRow> = listOf(
        SchedulePaymentAccountRow(
            id = CURRENT_ACCOUNT_ID,
            nickname = "",
            accountSubType = "CurrentAccount",
            accountNumber = "10203349",
            rawIdentification = "80200110203349",
            supporting = "£21,530.92",
        ),
        SchedulePaymentAccountRow(
            id = SAVINGS_ACCOUNT_ID,
            nickname = "",
            accountSubType = "Savings",
            accountNumber = "90953695",
            rawIdentification = "80122590953695",
            supporting = "£482.10",
        ),
    )

    private fun creditorRows(): List<SchedulePaymentPickerRow> = listOf(
        SchedulePaymentPickerRow(JAMESON_ID, "JL", "Jameson Lettings", "Sort Code · 40-12-09 65872310", "Jameson L."),
        SchedulePaymentPickerRow(SHARMA_ID, "JS", "John Sharma", "Sort Code · 23-05-80 11223344", "John S."),
        SchedulePaymentPickerRow(EDF_ID, "EE", "EDF Energy", "Sort Code · 60-00-01 99887766", "EDF E."),
    )

    /**
     * IBAN payees, which is all the international rail ever lists.
     *
     * The ViewModel filters the two schemes apart, so a fixture handing sort-code payees to an
     * international form would depict a screen the app cannot produce — and a golden of it would
     * document the wrong thing.
     */
    private fun internationalCreditorRows(): List<SchedulePaymentPickerRow> = listOf(
        SchedulePaymentPickerRow(WEISS_ID, "KW", "Klara Weiss", "IBAN · DE89 3704 0044 0532 0130 00", "Klara W."),
        SchedulePaymentPickerRow(
            DUPONT_ID,
            "MD",
            "Marie Dupont",
            "IBAN · FR14 2004 1010 0505 0001 3M02 606",
            "Marie D.",
        ),
    )

    /** Whichever scheme the rail lists. */
    private fun payeesFor(rail: PaymentRail): List<SchedulePaymentPickerRow> = when (rail) {
        PaymentRail.Domestic -> creditorRows()
        PaymentRail.International -> internationalCreditorRows()
    }

    fun loadingState(): SchedulePaymentState = SchedulePaymentState(uiState = SchedulePaymentUiState.Loading)

    /**
     * The form page — payer, payee and amount, which used to be three separate steps.
     *
     * Empty by default, which is how the page first renders. [filledFormState] is the same page with
     * a payee and an amount already entered, for the cases that care about the action being enabled.
     */
    fun formState(
        rail: PaymentRail = PaymentRail.Domestic,
        executionDate: LocalDate? = null,
        datePickerVisible: Boolean = false,
        beneficiaries: List<SchedulePaymentPickerRow> = payeesFor(rail),
        manualEntryVisible: Boolean = false,
        creditor: CreditorSelection? = null,
        creditorLabel: String = "",
        amountInput: String = "",
        amountLabel: String = "",
        reference: String = "",
        problem: SchedulePaymentAmountProblem? = null,
        debtorAccountId: String? = CURRENT_ACCOUNT_ID,
        payerPickerExpanded: Boolean = false,
        letBankChoosePayer: Boolean = false,
        debtorCurrency: String = "GBP",
        instructedCurrency: String = "GBP",
        payeesFailed: Boolean = false,
        payeesLoading: Boolean = false,
    ): SchedulePaymentState = SchedulePaymentState(
        uiState = SchedulePaymentUiState.Content(
            step = SchedulePaymentStep.Form,
            rail = rail,
            // The production list, not a hand-written pair. A fixture that carried its own two-value
            // list is what let the picker's "only USD and EUR" claim survive being disproved.
            offeredCurrencies = if (rail == PaymentRail.International) OFFERED_CURRENCIES else emptyList(),
            instructedCurrency = instructedCurrency,
            payeesFailed = payeesFailed,
            payeesLoading = payeesLoading,
            debtorAccounts = listOf(currentAccount(), savingsAccount()),
            debtorRows = debtorRows(),
            // Payees are account-scoped, so with no payer the ViewModel emits none — and neither a
            // failed read nor one still in flight has any either, because `content()` reads the
            // list off `ScreenState.Content` and every other state yields an empty one. Handing a
            // list to any of the three would depict a state production cannot reach, and a golden
            // of it would document the wrong screen.
            //
            // The `payeesLoading` term is the one that could not be written before: the fixture
            // modelled two outcomes, a list or no list, and had no way to say "not yet".
            beneficiaries = if (debtorAccountId == null || payeesFailed || payeesLoading) {
                emptyList()
            } else {
                beneficiaries
            },
            debtorAccountId = debtorAccountId,
            payerPickerExpanded = payerPickerExpanded,
            letBankChoosePayer = letBankChoosePayer,
            // Blank without a payer, as the ViewModel derives it: there is no account to read a
            // currency off, and a fixture that named one would make the forbidden-combination check
            // pass on evidence production does not have.
            debtorCurrency = if (debtorAccountId == null) "" else debtorCurrency,
            creditor = creditor,
            creditorLabel = creditorLabel,
            manualEntryVisible = manualEntryVisible,
            // Major units, as the amount card now reads them: 850 means £850, not £8.50.
            amountInput = amountInput,
            amountLabel = amountLabel,
            reference = reference,
            amountProblem = problem,
            availableBalanceMinorUnits = 2_153_092L,
            availableBalanceLabel = if (debtorAccountId == null) "" else "£21,530.92",
            today = TODAY,
            executionDate = executionDate,
            executionDateLabel = executionDate?.let { EXECUTION_DATE_LABEL }.orEmpty(),
            datePickerVisible = datePickerVisible,
        ),
    )

    /** The form with everything filled in, so `canReview` is true unless [problem] says otherwise. */
    fun filledFormState(
        amountInput: String = "850",
        problem: SchedulePaymentAmountProblem? = null,
        executionDate: LocalDate? = EXECUTION_DATE,
    ): SchedulePaymentState = formState(
        executionDate = executionDate,
        creditor = jamesonSelection(),
        creditorLabel = "Jameson Lettings",
        amountInput = amountInput,
        amountLabel = "£850.00",
        reference = "RENT-FLAT12",
        problem = problem,
    )

    /**
     * The international form, filled in.
     *
     * An IBAN payee and no reference, because that is the only shape the international rail
     * produces — a sort-code payee here would depict a form the app cannot reach.
     *
     * [amountLabel] follows [instructedCurrency], as `amountLabel` in the ViewModel does: the label
     * is formatted in the currency the amount is instructed in, so a `$` figure under a `USD`
     * control is what the screen actually shows.
     */
    fun filledInternationalFormState(
        instructedCurrency: String = "GBP",
    ): SchedulePaymentState = formState(
        rail = PaymentRail.International,
        creditor = weissSelection(),
        creditorLabel = "Klara Weiss",
        amountInput = "850",
        amountLabel = formatMinorUnits(85_000L, instructedCurrency),
        instructedCurrency = instructedCurrency,
    )

    /** The chosen payee, as the form and review carry it forward. */
    fun jamesonSelection(): CreditorSelection = CreditorSelection(
        name = "Jameson Lettings",
        scheme = BeneficiaryScheme.SortCode,
        identification = "40120965872310",
        beneficiaryId = JAMESON_ID,
    )

    /** The international rail's payee, identified by IBAN as that rail requires. */
    fun weissSelection(): CreditorSelection = CreditorSelection(
        name = "Klara Weiss",
        scheme = BeneficiaryScheme.Iban,
        identification = "DE89370400440532013000",
        beneficiaryId = WEISS_ID,
    )

    fun reviewState(
        reference: String = "RENT-FLAT12",
        rail: PaymentRail = PaymentRail.Domestic,
        debtorAccountRow: SchedulePaymentAccountRow? = debtorRows().first(),
        instructedCurrency: String = "GBP",
        chargeBearer: ChargeBearer = ChargeBearer.BorneByCreditor,
    ): SchedulePaymentState = SchedulePaymentState(
        uiState = SchedulePaymentUiState.Content(
            step = SchedulePaymentStep.Review,
            rail = rail,
            offeredCurrencies = if (rail == PaymentRail.International) OFFERED_CURRENCIES else emptyList(),
            instructedCurrency = instructedCurrency,
            chargeBearer = chargeBearer,
            debtorAccounts = listOf(currentAccount(), savingsAccount()),
            debtorRows = debtorRows(),
            beneficiaries = payeesFor(rail),
            debtorAccountId = if (debtorAccountRow == null) null else CURRENT_ACCOUNT_ID,
            debtorAccountRow = debtorAccountRow,
            // The payee follows the rail, as the payee LIST already did. It did not, so the
            // international review golden showed a sort-code payee under "International payment" —
            // a screen the app cannot produce, because the ViewModel filters the two schemes apart
            // and refuses each other's with U027.
            creditor = if (rail == PaymentRail.International) weissSelection() else jamesonSelection(),
            creditorLabel = if (rail == PaymentRail.International) "Klara Weiss" else "Jameson Lettings",
            creditorSupporting = if (rail == PaymentRail.International) {
                "IBAN · DE89 3704 0044 0532 0130 00"
            } else {
                "Sort Code · 40-12-09 65872310"
            },
            amountInput = "850",
            // In the instructed currency, as the ViewModel formats it — the review's hero figure and
            // its total are the only place the currency is now stated, so a fixture that always said
            // "£850.00" would hide whether the amount follows the selector at all.
            amountLabel = formatMinorUnits(85_000L, instructedCurrency),
            reference = reference,
            availableBalanceMinorUnits = 2_153_092L,
            availableBalanceLabel = "£21,530.92",
            today = TODAY,
            // A review always has a date: it cannot be reached without one.
            executionDate = EXECUTION_DATE,
            executionDateLabel = EXECUTION_DATE_LABEL,
        ),
    )

    fun submittingState(
        stage: SchedulePaymentStage = SchedulePaymentStage.AwaitingAuthorisation,
    ): SchedulePaymentState = SchedulePaymentState(
        uiState = SchedulePaymentUiState.Submitting(
            stage = stage,
            amountLabel = "£850.00",
            creditorName = "Jameson Lettings",
            consentId = CONSENT_ID,
        ),
    )

    fun errorState(
        kind: SchedulePaymentErrorKind = SchedulePaymentErrorKind.OutsideControlParameters,
        supportReference: String? = SUPPORT_REFERENCE,
    ): SchedulePaymentState = SchedulePaymentState(
        uiState = SchedulePaymentUiState.Error(kind = kind, supportReference = supportReference),
    )
}
