/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.vrppayment.payment

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.mifosx.openbanking.core.model.banking.payment.PaymentStatus
import org.mifosx.openbanking.core.model.callback.ConsentStatus
import org.mifosx.openbanking.core.model.vrp.AccountIdentity
import org.mifosx.openbanking.core.model.vrp.FundsAvailability
import org.mifosx.openbanking.core.model.vrp.Money
import org.mifosx.openbanking.core.model.vrp.PeriodType
import org.mifosx.openbanking.core.model.vrp.PeriodUsage
import org.mifosx.openbanking.core.model.vrp.PeriodicLimit
import org.mifosx.openbanking.core.model.vrp.VrpConsent
import org.mifosx.openbanking.core.model.vrp.VrpControlParameters
import org.mifosx.openbanking.core.model.vrp.VrpPayment
import org.mifosx.openbanking.feature.vrppayment.FakeVrpConsentRepository
import org.mifosx.openbanking.feature.vrppayment.FakeVrpPaymentRepository
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val CONSENT_ID = "45411"
private const val SORT_CODE_ACCOUNT_NUMBER = "UK.OBIE.SortCodeAccountNumber"

/** A body carrying the code the bank refuses an over-limit payment with. */
private const val OVER_LIMIT_BODY = """
{"Id":"ref-9911","Code":"UK.OBIE.Unsupported","Errors":[{"ErrorCode":"U014",
"Message":"You have reached the allowed payment limit",
"Path":"Data.Instruction.InstructedAmount.Amount"}]}
"""

class VrpPaymentViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var consents: FakeVrpConsentRepository
    private lateinit var payments: FakeVrpPaymentRepository

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        consents = FakeVrpConsentRepository(byId = consent())
        payments = FakeVrpPaymentRepository(usage = listOf(monthUsage()))
        payments.payReturns(NetworkResult.Success(payment()))
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = VrpPaymentViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(VrpPaymentViewModel.CONSENT_ID_ARG to CONSENT_ID),
        ),
        consents = consents,
        payments = payments,
    )

    private fun content(vm: VrpPaymentViewModel) =
        assertIs<VrpPaymentUiState.Content>(vm.stateFlow.value.uiState)

    /** A view model with an amount entered and the review on screen. */
    private fun TestScope.reviewing(amount: String = "45.00"): VrpPaymentViewModel {
        val vm = viewModel()
        advanceUntilIdle()
        vm.trySendAction(VrpPaymentAction.AmountChanged(amount))
        advanceUntilIdle()
        vm.trySendAction(VrpPaymentAction.Continue)
        advanceUntilIdle()
        return vm
    }

    // loading

    @Test
    fun theScreenStartsLoading() {
        val vm = viewModel()

        assertEquals(VrpPaymentUiState.Loading, vm.stateFlow.value.uiState)
    }

    @Test
    fun theCeilingsAndRemainingFigureAreRendered() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        val form = content(vm).form
        assertEquals("Sarah Chen", form.payeeName)
        assertEquals("£200.00", form.perPaymentCeilingAmount)
        assertEquals("£380.00", form.remainingAmount)
        assertEquals(PeriodType.Month, form.periodType)
    }

    /** The ceiling closest to being exhausted is the one that binds, whatever its period. */
    @Test
    fun theRemainingFigureComesFromTheTightestCeiling() = runTest {
        payments.emitUsage(
            listOf(
                monthUsage(),
                PeriodUsage(
                    limit = PeriodicLimit(PeriodType.Week, Money(150_00L, "GBP")),
                    consumed = Money(140_00L, "GBP"),
                    remaining = Money(10_00L, "GBP"),
                    periodStart = Instant.parse("2026-08-17T00:00:00Z"),
                ),
            ),
        )
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals("£10.00", content(vm).form.remainingAmount)
        assertEquals(PeriodType.Week, content(vm).form.periodType)
    }

    @Test
    fun aConsentWithNoStoredRecordIsAnError() = runTest {
        consents.emit(null)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(
            VrpPaymentUiState.Error(VrpPaymentErrorKind.ConsentUnavailable),
            vm.stateFlow.value.uiState,
        )
    }

    @Test
    fun aRevokedConsentCannotBePaidUnder() = runTest {
        consents.emit(consent(revokedAt = Instant.parse("2026-08-19T12:00:00Z")))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(VrpPaymentUiState.Unusable, vm.stateFlow.value.uiState)
    }

    @Test
    fun anUnapprovedConsentCannotBePaidUnder() = runTest {
        consents.emit(consent(status = ConsentStatus.AwaitingAuthorisation))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(VrpPaymentUiState.Unusable, vm.stateFlow.value.uiState)
    }

    /** A payer chosen at the bank that is not a sort code and account number is a card. */
    @Test
    fun aCardPayerCannotBePaidUnder() = runTest {
        consents.emit(
            consent(
                payer = AccountIdentity(
                    schemeName = "UK.OBIE.PAN",
                    identification = "4444333322221111",
                    name = "Credit Card",
                ),
            ),
        )
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(VrpPaymentUiState.Unusable, vm.stateFlow.value.uiState)
    }

    // the amount

    /** An untouched field is not an error; it is simply not ready to send. */
    @Test
    fun anEmptyAmountReportsNoProblemAndCannotContinue() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(null, content(vm).form.problem)
        assertEquals(false, content(vm).canContinue)
    }

    @Test
    fun anAmountThatIsNotANumberIsRefused() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.trySendAction(VrpPaymentAction.AmountChanged("ten pounds"))
        advanceUntilIdle()

        assertEquals(AmountProblem.NotANumber, content(vm).form.problem)
    }

    @Test
    fun anAmountBelowAPennyIsRefused() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.trySendAction(VrpPaymentAction.AmountChanged("0.00"))
        advanceUntilIdle()

        assertEquals(AmountProblem.BelowMinimum, content(vm).form.problem)
    }

    @Test
    fun anAmountOverThePerPaymentCeilingIsRefused() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.trySendAction(VrpPaymentAction.AmountChanged("200.01"))
        advanceUntilIdle()

        assertEquals(AmountProblem.OverPerPayment, content(vm).form.problem)
    }

    @Test
    fun anAmountOverWhatIsLeftOfThePeriodIsRefused() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.trySendAction(VrpPaymentAction.AmountChanged("190.00"))
        advanceUntilIdle()

        assertEquals(null, content(vm).form.problem)

        payments.emitUsage(
            listOf(
                PeriodUsage(
                    limit = PeriodicLimit(PeriodType.Month, Money(500_00L, "GBP")),
                    consumed = Money(400_00L, "GBP"),
                    remaining = Money(100_00L, "GBP"),
                    periodStart = Instant.parse("2026-08-01T00:00:00Z"),
                ),
            ),
        )
        advanceUntilIdle()

        assertEquals(AmountProblem.OverRemaining, content(vm).form.problem)
    }

    /** A recalculation landing late must not wipe what the customer typed. */
    @Test
    fun aUsageUpdateKeepsTheEnteredAmount() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.trySendAction(VrpPaymentAction.AmountChanged("45.00"))
        advanceUntilIdle()

        payments.emitUsage(listOf(monthUsage()))
        advanceUntilIdle()

        assertEquals("45.00", content(vm).form.amount)
    }

    @Test
    fun aUsableAmountMovesToTheReview() = runTest {
        val vm = reviewing()

        assertEquals(PaymentPhase.Review, content(vm).phase)
    }

    @Test
    fun anUnusableAmountStaysOnTheAmountPhase() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.trySendAction(VrpPaymentAction.AmountChanged("200.01"))
        advanceUntilIdle()
        vm.trySendAction(VrpPaymentAction.Continue)
        advanceUntilIdle()

        assertEquals(PaymentPhase.Amount, content(vm).phase)
    }

    // the funds check

    /** Only bad news is reported: an available answer ignores the limits and promises nothing. */
    @Test
    fun aShortfallIsWarnedAbout() = runTest {
        payments.fundsReturns(NetworkResult.Success(fundsAvailability(available = false)))
        val vm = viewModel()
        advanceUntilIdle()
        vm.trySendAction(VrpPaymentAction.AmountChanged("45.00"))
        vm.trySendAction(VrpPaymentAction.CheckFunds)
        advanceUntilIdle()

        assertContentEquals(listOf(Money(45_00L, "GBP")), payments.fundsChecks)
        assertTrue(content(vm).form.fundsWarning)
    }

    @Test
    fun anAvailableAnswerWarnsAboutNothing() = runTest {
        payments.fundsReturns(NetworkResult.Success(fundsAvailability(available = true)))
        val vm = viewModel()
        advanceUntilIdle()
        vm.trySendAction(VrpPaymentAction.AmountChanged("45.00"))
        vm.trySendAction(VrpPaymentAction.CheckFunds)
        advanceUntilIdle()

        assertEquals(false, content(vm).form.fundsWarning)
    }

    @Test
    fun aFailedFundsCheckWarnsAboutNothing() = runTest {
        payments.fundsReturns(NetworkResult.Error(NetworkError.Network(IllegalStateException("offline"))))
        val vm = viewModel()
        advanceUntilIdle()
        vm.trySendAction(VrpPaymentAction.AmountChanged("45.00"))
        vm.trySendAction(VrpPaymentAction.CheckFunds)
        advanceUntilIdle()

        assertEquals(false, content(vm).form.fundsWarning)
    }

    @Test
    fun noFundsCheckIsMadeWithoutAnAmount() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.trySendAction(VrpPaymentAction.CheckFunds)
        advanceUntilIdle()

        assertContentEquals(emptyList(), payments.fundsChecks)
    }

    // submitting

    @Test
    fun confirmingSendsTheEnteredAmount() = runTest {
        val vm = reviewing()
        vm.trySendAction(VrpPaymentAction.Confirm)
        advanceUntilIdle()

        assertEquals(Money(45_00L, "GBP"), payments.payCalls.single().amount)
        assertIs<SubmissionUi.Sent>(content(vm).outcome)
    }

    @Test
    fun confirmingTwiceDoesNotPayTwice() = runTest {
        val vm = reviewing()
        vm.trySendAction(VrpPaymentAction.Confirm)
        vm.trySendAction(VrpPaymentAction.Confirm)
        advanceUntilIdle()

        assertEquals(1, payments.payCalls.size)
    }

    /** A retry replays the key, so the bank returns the original payment rather than making a second. */
    @Test
    fun aRetryRepeatsTheKeyAndBothIdentifications() = runTest {
        payments.payReturns(NetworkResult.Error(NetworkError.Network(IllegalStateException("offline"))))
        val vm = reviewing()
        vm.trySendAction(VrpPaymentAction.Confirm)
        advanceUntilIdle()

        vm.trySendAction(VrpPaymentAction.RetrySubmission)
        advanceUntilIdle()

        val (first, second) = payments.payCalls
        assertEquals(first.idempotencyKey, second.idempotencyKey)
        assertEquals(first.instructionIdentification, second.instructionIdentification)
        assertEquals(first.endToEndIdentification, second.endToEndIdentification)
    }

    /** Two payments under the same VRP must never share a key, or the second returns the first. */
    @Test
    fun twoPaymentsUnderOneConsentUseDifferentKeys() = runTest {
        val first = reviewing()
        first.trySendAction(VrpPaymentAction.Confirm)
        advanceUntilIdle()

        val second = reviewing()
        second.trySendAction(VrpPaymentAction.Confirm)
        advanceUntilIdle()

        val keys = payments.payCalls.map { it.idempotencyKey }
        assertNotEquals(keys[0], keys[1])
    }

    @Test
    fun goingBackFromTheReviewClearsAFailedAttempt() = runTest {
        payments.payReturns(NetworkResult.Error(NetworkError.Network(IllegalStateException("offline"))))
        val vm = reviewing()
        vm.trySendAction(VrpPaymentAction.Confirm)
        advanceUntilIdle()

        vm.trySendAction(VrpPaymentAction.BackToAmount)
        advanceUntilIdle()

        assertEquals(SubmissionUi.NotStarted, content(vm).outcome)
        assertEquals(PaymentPhase.Amount, content(vm).phase)
    }

    // how a refusal is classified

    @Test
    fun aBreachOfTheConsentLimitsIsReportedAsOverLimit() = runTest {
        payments.payReturns(NetworkResult.Error(NetworkError.Client.BadRequest(OVER_LIMIT_BODY)))
        val vm = reviewing()
        vm.trySendAction(VrpPaymentAction.Confirm)
        advanceUntilIdle()

        val failed = assertIs<SubmissionUi.Failed>(content(vm).outcome)
        assertEquals(PaymentFailureKind.OverLimit, failed.kind)
        assertEquals("ref-9911", failed.supportReference)
        assertEquals(false, failed.kind.isRetryable)
    }

    @Test
    fun anExpiredCredentialIsReportedAsNeedingReauthorisation() = runTest {
        payments.payReturns(NetworkResult.Error(NetworkError.Client.Unauthorized(null)))
        val vm = reviewing()
        vm.trySendAction(VrpPaymentAction.Confirm)
        advanceUntilIdle()

        assertEquals(
            PaymentFailureKind.NeedsReauthorisation,
            assertIs<SubmissionUi.Failed>(content(vm).outcome).kind,
        )
    }

    @Test
    fun aForbiddenPaymentIsReportedAsAnUnusableConsent() = runTest {
        payments.payReturns(NetworkResult.Error(NetworkError.Client.Forbidden(null)))
        val vm = reviewing()
        vm.trySendAction(VrpPaymentAction.Confirm)
        advanceUntilIdle()

        assertEquals(
            PaymentFailureKind.ConsentUnusable,
            assertIs<SubmissionUi.Failed>(content(vm).outcome).kind,
        )
    }

    /** The money may already have moved and the app cannot tell, so this is never safe to resend. */
    @Test
    fun anUndecodableResponseIsReportedAsUnconfirmedAndIsNotRetryable() = runTest {
        payments.payReturns(
            NetworkResult.Error(NetworkError.Serialization(IllegalStateException("bad body"))),
        )
        val vm = reviewing()
        vm.trySendAction(VrpPaymentAction.Confirm)
        advanceUntilIdle()

        val failed = assertIs<SubmissionUi.Failed>(content(vm).outcome)
        assertEquals(PaymentFailureKind.Unconfirmed, failed.kind)
        assertEquals(false, failed.kind.isRetryable)
    }

    /** The one failure that is safe to send again: nothing reached the bank. */
    @Test
    fun aTransportFailureIsTheOnlyRetryableOne() = runTest {
        payments.payReturns(NetworkResult.Error(NetworkError.Network(IllegalStateException("offline"))))
        val vm = reviewing()
        vm.trySendAction(VrpPaymentAction.Confirm)
        advanceUntilIdle()

        val failed = assertIs<SubmissionUi.Failed>(content(vm).outcome)
        assertEquals(PaymentFailureKind.NetworkUnavailable, failed.kind)
        assertTrue(failed.kind.isRetryable)
    }

    @Test
    fun anythingElseIsReportedAsRejected() = runTest {
        payments.payReturns(NetworkResult.Error(NetworkError.Client.BadRequest(null)))
        val vm = reviewing()
        vm.trySendAction(VrpPaymentAction.Confirm)
        advanceUntilIdle()

        assertEquals(
            PaymentFailureKind.Rejected,
            assertIs<SubmissionUi.Failed>(content(vm).outcome).kind,
        )
    }

    // the outcome

    /** A submitted payment reports an interim status; the settled one appears only on a later read. */
    @Test
    fun anAcceptedPaymentIsNotSettledUntilItIsReadBack() = runTest {
        payments.payReturns(
            NetworkResult.Success(payment(status = PaymentStatus.AcceptedSettlementInProcess)),
        )
        val vm = reviewing()
        vm.trySendAction(VrpPaymentAction.Confirm)
        advanceUntilIdle()

        assertEquals(false, assertIs<SubmissionUi.Sent>(content(vm).outcome).settled)

        payments.refreshReturns(
            NetworkResult.Success(payment(status = PaymentStatus.AcceptedCreditSettlementCompleted)),
        )
        vm.trySendAction(VrpPaymentAction.RefreshOutcome)
        advanceUntilIdle()

        assertTrue(assertIs<SubmissionUi.Sent>(content(vm).outcome).settled)
    }

    @Test
    fun nothingIsReadBackBeforeAPaymentHasBeenAccepted() = runTest {
        val vm = reviewing()
        vm.trySendAction(VrpPaymentAction.RefreshOutcome)
        advanceUntilIdle()

        assertContentEquals(emptyList(), payments.refreshedPayments)
    }

    @Test
    fun finishingCarriesThePaymentBack() = runTest {
        val vm = reviewing()
        vm.trySendAction(VrpPaymentAction.Confirm)
        advanceUntilIdle()

        vm.trySendAction(VrpPaymentAction.Done)
        advanceUntilIdle()

        assertEquals(VrpPaymentEvent.Finished("pay-1"), vm.eventFlow.first())
    }

    @Test
    fun finishingWithNothingSentCarriesNoPayment() = runTest {
        val vm = reviewing()
        vm.trySendAction(VrpPaymentAction.Done)
        advanceUntilIdle()

        assertEquals(VrpPaymentEvent.Finished(""), vm.eventFlow.first())
    }

    private fun consent(
        status: ConsentStatus = ConsentStatus.Authorised,
        payer: AccountIdentity? = null,
        revokedAt: Instant? = null,
    ) = VrpConsent(
        consentId = CONSENT_ID,
        status = status,
        createdAt = Instant.parse("2026-08-01T12:00:00Z"),
        controlParameters = VrpControlParameters(
            maximumIndividualAmount = Money(200_00L, "GBP"),
            periodicLimits = listOf(PeriodicLimit(PeriodType.Month, Money(500_00L, "GBP"))),
            interactionType = "UK.OBIE.VRPType.Sweeping",
        ),
        payee = AccountIdentity(
            schemeName = SORT_CODE_ACCOUNT_NUMBER,
            identification = "40478412345678",
            name = "Sarah Chen",
        ),
        payer = payer,
        revokedAt = revokedAt,
    )

    private fun monthUsage() = PeriodUsage(
        limit = PeriodicLimit(PeriodType.Month, Money(500_00L, "GBP")),
        consumed = Money(120_00L, "GBP"),
        remaining = Money(380_00L, "GBP"),
        periodStart = Instant.parse("2026-08-01T00:00:00Z"),
    )

    private fun payment(
        status: PaymentStatus = PaymentStatus.AcceptedCreditSettlementCompleted,
    ) = VrpPayment(
        localId = "pay-1",
        consentId = CONSENT_ID,
        amount = Money(45_00L, "GBP"),
        status = status,
        createdAt = Instant.parse("2026-08-19T12:00:00Z"),
    )

    private fun fundsAvailability(available: Boolean) = FundsAvailability(
        available = available,
        amount = Money(45_00L, "GBP"),
        checkedAt = Instant.parse("2026-08-19T12:00:00Z"),
    )
}
