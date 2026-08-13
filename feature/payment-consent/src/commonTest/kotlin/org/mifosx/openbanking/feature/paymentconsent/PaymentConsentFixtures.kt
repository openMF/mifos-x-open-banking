/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.paymentconsent

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.mifosx.openbanking.core.data.banking.PaymentHistoryRepository
import org.mifosx.openbanking.core.data.banking.ScheduledPaymentInitiationRepository
import org.mifosx.openbanking.core.data.banking.SinglePaymentInitiationRepository
import org.mifosx.openbanking.core.data.banking.StandingOrderInitiationRepository
import org.mifosx.openbanking.core.data.callback.PaymentAuthRepository
import org.mifosx.openbanking.core.data.callback.PaymentAuthValidation
import org.mifosx.openbanking.core.model.banking.BankAccount
import org.mifosx.openbanking.core.model.banking.BeneficiaryScheme
import org.mifosx.openbanking.core.model.banking.payment.ConsentType
import org.mifosx.openbanking.core.model.banking.payment.CreditorSelection
import org.mifosx.openbanking.core.model.banking.payment.PaymentDraft
import org.mifosx.openbanking.core.model.banking.payment.PaymentHistoryItem
import org.mifosx.openbanking.core.model.banking.payment.PaymentReceipt
import org.mifosx.openbanking.core.model.banking.payment.PaymentStageTimestamps
import org.mifosx.openbanking.core.model.banking.payment.PaymentStatus
import org.mifosx.openbanking.core.model.banking.payment.ScheduledPaymentDraft
import org.mifosx.openbanking.core.model.banking.payment.StagedConsent
import org.mifosx.openbanking.core.model.banking.payment.StandingOrderDraft
import org.mifosx.openbanking.core.model.banking.payment.StandingOrderFrequency
import org.mifosx.openbanking.feature.paymentconsent.ui.PaymentConsentErrorKind
import org.mifosx.openbanking.feature.paymentconsent.ui.PaymentConsentState
import org.mifosx.openbanking.feature.paymentconsent.ui.PaymentConsentUiState
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult

object PaymentConsentFixtures {

    const val CONSENT_ID = "812774903"
    const val PAYMENT_ID = "58923-002"
    const val CODE = "auth-code-1"
    const val REDIRECT_URL = "https://callback.example/?code=$CODE&state=state-1"

    fun validatingState(): PaymentConsentState =
        PaymentConsentState(uiState = PaymentConsentUiState.Validating, consentId = CONSENT_ID)

    fun exchangingState(): PaymentConsentState =
        PaymentConsentState(uiState = PaymentConsentUiState.Exchanging, consentId = CONSENT_ID)

    fun checkingState(canCheckAgain: Boolean = false): PaymentConsentState = PaymentConsentState(
        uiState = PaymentConsentUiState.Checking(canCheckAgain = canCheckAgain),
        consentId = CONSENT_ID,
    )

    fun approvedState(): PaymentConsentState =
        PaymentConsentState(uiState = PaymentConsentUiState.Approved, consentId = CONSENT_ID)

    fun alreadySubmittedState(): PaymentConsentState =
        PaymentConsentState(uiState = PaymentConsentUiState.AlreadySubmitted, consentId = CONSENT_ID)

    fun confirmingFundsState(): PaymentConsentState =
        PaymentConsentState(uiState = PaymentConsentUiState.ConfirmingFunds, consentId = CONSENT_ID)

    fun submittingState(): PaymentConsentState =
        PaymentConsentState(uiState = PaymentConsentUiState.Submitting, consentId = CONSENT_ID)

    fun errorState(
        kind: PaymentConsentErrorKind = PaymentConsentErrorKind.StateMismatch,
    ): PaymentConsentState =
        PaymentConsentState(uiState = PaymentConsentUiState.Error(kind), consentId = CONSENT_ID)

    /**
     * A payer with no nickname, as HSBC actually returns them. Keeping the blank there stops a
     * fixture from quietly asserting a field the live bank does not populate.
     */
    fun draft(): PaymentDraft = PaymentDraft(
        debtorAccount = BankAccount(
            accountId = "acc-1",
            nickname = "",
            accountSubType = "CurrentAccount",
            currency = "GBP",
            sortCode = "802001",
            accountNumber = "10203349",
            rawIdentification = "80200110203349",
        ),
        creditor = CreditorSelection(
            name = "Liam Walker",
            scheme = BeneficiaryScheme.SortCode,
            identification = "40120965872310",
        ),
        amountMinorUnits = 50_000L,
        currency = "GBP",
        reference = "Invoice 2026-05",
        instructionIdentification = "MFX20260805T1042330001",
        endToEndIdentification = "E2E-RENT-FLAT12-202608",
        consentIdempotencyKey = "consent-key-1",
        paymentIdempotencyKey = "payment-key-1",
    )

    fun receipt(paymentId: String = PAYMENT_ID): PaymentReceipt = PaymentReceipt(
        domesticPaymentId = paymentId,
        consentId = CONSENT_ID,
        status = PaymentStatus.AcceptedSettlementInProcess,
        creationDateTime = "2026-08-05T10:42:33Z",
        statusUpdateDateTime = "2026-08-05T10:42:33Z",
        amountLabel = "£500.00",
        creditorName = "Liam Walker",
    )
}

class FakePaymentAuthRepository(
    private var validation: PaymentAuthValidation =
        PaymentAuthValidation.Valid(PaymentConsentFixtures.CODE, PaymentConsentFixtures.CONSENT_ID),
    private var exchange: NetworkResult<Unit, NetworkError> = NetworkResult.Success(Unit),
    private var status: NetworkResult<String, NetworkError> = NetworkResult.Success("AUTH"),
) : PaymentAuthRepository {

    val exchangedCodes = mutableListOf<String>()
    val statusChecks = mutableListOf<String>()

    /** How many times the authorisation was discarded — the session-clearing assertion hangs on it. */
    var discardCount: Int = 0
        private set

    override fun isPaymentRedirect(redirectUrl: String): Boolean = true

    override fun validateCallback(redirectUrl: String): PaymentAuthValidation = validation

    override suspend fun exchangeCode(code: String): NetworkResult<Unit, NetworkError> {
        exchangedCodes += code
        return exchange
    }

    override suspend fun consentStatus(consentId: String): NetworkResult<String, NetworkError> {
        statusChecks += consentId
        return status
    }

    /**
     * Which product the return leg believes authorised.
     *
     * Drivable per test because it is now the thing that chooses the repository — a test that leaves
     * it at the default and stages a mandate is asserting against a journey the app will not take.
     */
    var pendingType: ConsentType? = ConsentType.DomesticSinglePayment

    override fun pendingConsentType(): ConsentType? = pendingType

    var approvedRecordedCount: Int = 0
        private set

    override fun recordApproved() {
        approvedRecordedCount++
    }

    override fun discardAuthorisation() {
        discardCount++
    }

    fun validationReturns(result: PaymentAuthValidation) {
        validation = result
    }

    fun exchangeReturns(result: NetworkResult<Unit, NetworkError>) {
        exchange = result
    }

    fun statusReturns(result: NetworkResult<String, NetworkError>) {
        status = result
    }
}

/**
 * The payment write path, recorded rather than performed.
 *
 * [stagedDraft] defaults to a real draft because the callback's whole job now depends on finding
 * one; the null case is the interesting exception, not the baseline.
 */
class FakeSinglePaymentInitiationRepository(
    private var staged: PaymentDraft? = PaymentConsentFixtures.draft(),
    private var funds: NetworkResult<Boolean, NetworkError> = NetworkResult.Success(true),
    private var submission: NetworkResult<PaymentReceipt, NetworkError> =
        NetworkResult.Success(PaymentConsentFixtures.receipt()),
) : SinglePaymentInitiationRepository {

    val submittedDrafts = mutableListOf<PaymentDraft>()
    val fundsChecks = mutableListOf<String>()

    /**
     * Runs at the moment the staged draft is read.
     *
     * That is the one observable instant between the consent being reported authorised and the funds
     * check starting, which is what makes [PaymentConsentUiState.Approved] — a state the flow passes
     * straight through — assertable at all.
     */
    var onStagedDraft: (() -> Unit)? = null

    override suspend fun stagePayment(draft: PaymentDraft): NetworkResult<StagedConsent, NetworkError> =
        throw UnsupportedOperationException("The callback leg never stages a payment")

    override suspend fun confirmFunds(consentId: String): NetworkResult<Boolean, NetworkError> {
        fundsChecks += consentId
        return funds
    }

    override suspend fun submitPayment(
        draft: PaymentDraft,
        consentId: String,
    ): NetworkResult<PaymentReceipt, NetworkError> {
        submittedDrafts += draft
        return submission
    }

    override fun stagedDraft(): PaymentDraft? {
        onStagedDraft?.invoke()
        return staged
    }

    fun stagedDraftReturns(draft: PaymentDraft?) {
        staged = draft
    }

    fun fundsReturn(result: NetworkResult<Boolean, NetworkError>) {
        funds = result
    }

    fun submissionReturns(result: NetworkResult<PaymentReceipt, NetworkError>) {
        submission = result
    }
}

/**
 * The payment history write path, recorded rather than persisted.
 *
 * The callback leg writes a row on both outcomes — a submitted payment and a pre-submission failure
 * — so both are captured separately rather than counted, which is what lets a test assert that a
 * failure was recorded once and not also saved as a submission.
 */
class FakePaymentHistoryRepository : PaymentHistoryRepository {

    val submitted = mutableListOf<Pair<PaymentReceipt, PaymentDraft>>()

    /** Kept apart from [submitted] so a test can prove which draft shape was actually recorded. */
    val submittedScheduled = mutableListOf<Pair<PaymentReceipt, ScheduledPaymentDraft>>()

    /** Kept apart again: a mandate recorded under a payment's shape is the defect being guarded. */
    val submittedStandingOrders = mutableListOf<Pair<PaymentReceipt, StandingOrderDraft>>()

    val scheduledFailures = mutableListOf<Triple<ScheduledPaymentDraft, String, String>>()
    val standingOrderFailures = mutableListOf<Triple<StandingOrderDraft, String, String>>()
    val failures = mutableListOf<Triple<PaymentDraft, String, String>>()
    var refreshCount: Int = 0
        private set

    override fun observeRecent(): Flow<List<PaymentHistoryItem>> = flowOf(emptyList())

    override suspend fun saveSubmitted(receipt: PaymentReceipt, draft: PaymentDraft) {
        submitted += receipt to draft
    }

    override suspend fun saveSubmitted(receipt: PaymentReceipt, draft: ScheduledPaymentDraft) {
        submittedScheduled += receipt to draft
    }

    override suspend fun saveFailed(
        draft: ScheduledPaymentDraft,
        errorKind: String,
        errorDescription: String,
    ) {
        scheduledFailures += Triple(draft, errorKind, errorDescription)
    }

    override suspend fun saveFailed(draft: PaymentDraft, errorKind: String, errorDescription: String) {
        failures += Triple(draft, errorKind, errorDescription)
    }

    override suspend fun saveSubmitted(receipt: PaymentReceipt, draft: StandingOrderDraft) {
        submittedStandingOrders += receipt to draft
    }

    override suspend fun saveFailed(
        draft: StandingOrderDraft,
        errorKind: String,
        errorDescription: String,
    ) {
        standingOrderFailures += Triple(draft, errorKind, errorDescription)
    }

    override suspend fun refreshStatuses() {
        refreshCount++
    }

    /** The callback leg never reads a rail back; it always has the draft in hand. */
    override suspend fun consentTypeOf(paymentId: String): ConsentType? = null

    /** This fake keeps no rows, so it has no stage times to report. */
    override suspend fun stageTimestampsOf(paymentId: String): PaymentStageTimestamps? = null
}

/**
 * The scheduled write path as the return leg sees it.
 *
 * `confirmFunds` is absent from the interface entirely, which is what lets the strongest test in this
 * suite be written as an assertion about the *immediate* fake's empty call log: a scheduled journey
 * that confirmed funds could only have done so by taking the wrong branch.
 */
class FakeScheduledPaymentInitiationRepository(
    private var staged: ScheduledPaymentDraft? = null,
    private var submission: NetworkResult<PaymentReceipt, NetworkError> =
        NetworkResult.Success(PaymentConsentFixtures.receipt()),
) : ScheduledPaymentInitiationRepository {

    val submittedDrafts = mutableListOf<ScheduledPaymentDraft>()
    val submittedConsentIds = mutableListOf<String>()

    override suspend fun stagePayment(
        draft: ScheduledPaymentDraft,
    ): NetworkResult<StagedConsent, NetworkError> =
        error("the callback leg never stages a payment")

    override suspend fun submitPayment(
        draft: ScheduledPaymentDraft,
        consentId: String,
    ): NetworkResult<PaymentReceipt, NetworkError> {
        submittedDrafts += draft
        submittedConsentIds += consentId
        return submission
    }

    override fun stagedDraft(): ScheduledPaymentDraft? = staged

    fun stagedDraftReturns(draft: ScheduledPaymentDraft?) {
        staged = draft
    }

    fun submissionReturns(result: NetworkResult<PaymentReceipt, NetworkError>) {
        submission = result
    }
}

/**
 * The standing-order write path as the return leg sees it.
 *
 * Like its scheduled sibling it has no `confirmFunds` at all, and here the absence is not merely an
 * unsupported endpoint: OBIE defines no funds-confirmation sub-resource for either standing-order
 * consent, so there is nothing to call.
 */
class FakeStandingOrderInitiationRepository(
    private var staged: StandingOrderDraft? = null,
    private var submission: NetworkResult<PaymentReceipt, NetworkError> =
        NetworkResult.Success(PaymentConsentFixtures.receipt()),
) : StandingOrderInitiationRepository {

    val submittedDrafts = mutableListOf<StandingOrderDraft>()
    val submittedConsentIds = mutableListOf<String>()

    override suspend fun stageStandingOrder(
        draft: StandingOrderDraft,
    ): NetworkResult<StagedConsent, NetworkError> =
        error("the callback leg never stages a standing order")

    override suspend fun submitStandingOrder(
        draft: StandingOrderDraft,
        consentId: String,
    ): NetworkResult<PaymentReceipt, NetworkError> {
        submittedDrafts += draft
        submittedConsentIds += consentId
        return submission
    }

    override fun stagedDraft(): StandingOrderDraft? = staged

    fun stagedDraftReturns(draft: StandingOrderDraft?) {
        staged = draft
    }

    fun submissionReturns(result: NetworkResult<PaymentReceipt, NetworkError>) {
        submission = result
    }
}

/** A monthly mandate starting next week, the shape the return leg receives. */
fun standingOrderDraftFixture(): StandingOrderDraft = StandingOrderDraft(
    debtorAccount = null,
    creditor = CreditorSelection(
        name = "Liam Walker",
        scheme = BeneficiaryScheme.SortCode,
        identification = "80200110203350",
    ),
    frequency = StandingOrderFrequency.Monthly,
    firstPaymentDate = "2026-08-20",
    finalPaymentDate = null,
    firstPaymentAmountMinorUnits = 25_000L,
    currency = "GBP",
    reference = "FLAT 4B RENT",
    consentIdempotencyKey = "so-consent-key-1",
    paymentIdempotencyKey = "so-payment-key-1",
)

/** A scheduled instruction due next week, the shape the return leg receives. */
fun scheduledDraftFixture(): ScheduledPaymentDraft = ScheduledPaymentDraft(
    debtorAccount = null,
    creditor = CreditorSelection(
        name = "Mr Dharani C",
        scheme = BeneficiaryScheme.SortCode,
        identification = "80200110203350",
    ),
    amountMinorUnits = 25_000L,
    currency = "GBP",
    reference = "RENT-AUG",
    instructionIdentification = "MFX20260811T1000000001",
    endToEndIdentification = "E2E-SCHED-202608",
    consentIdempotencyKey = "consent-key-1",
    paymentIdempotencyKey = "payment-key-1",
    requestedExecutionDate = "2026-08-14",
)
