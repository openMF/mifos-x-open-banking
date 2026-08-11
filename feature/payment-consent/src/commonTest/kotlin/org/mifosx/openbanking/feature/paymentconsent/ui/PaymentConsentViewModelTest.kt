/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.paymentconsent.ui

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.mifosx.openbanking.core.data.callback.PaymentAuthValidation
import org.mifosx.openbanking.feature.paymentconsent.FakePaymentAuthRepository
import org.mifosx.openbanking.feature.paymentconsent.FakePaymentHistoryRepository
import org.mifosx.openbanking.feature.paymentconsent.FakeScheduledPaymentInitiationRepository
import org.mifosx.openbanking.feature.paymentconsent.FakeSinglePaymentInitiationRepository
import org.mifosx.openbanking.feature.paymentconsent.PaymentConsentFixtures
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult
import template.core.base.ui.viewmodel.BackgroundEvent
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PaymentConsentViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        repository: FakePaymentAuthRepository = FakePaymentAuthRepository(),
        payments: FakeSinglePaymentInitiationRepository = FakeSinglePaymentInitiationRepository(),
        history: FakePaymentHistoryRepository = FakePaymentHistoryRepository(),
        scheduled: FakeScheduledPaymentInitiationRepository = FakeScheduledPaymentInitiationRepository(),
    ) = PaymentConsentViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(PaymentConsentViewModel.REDIRECT_URL_ARG to PaymentConsentFixtures.REDIRECT_URL),
        ),
        repository = repository,
        paymentInitiationRepository = payments,
        scheduledPaymentInitiationRepository = scheduled,
        paymentHistoryRepository = history,
    )

    // region — the happy path

    @Test
    fun anAuthenticCallbackExchangesTheCodeAndConfirmsTheConsent() = runTest {
        val repository = FakePaymentAuthRepository()

        viewModel(repository)

        assertEquals(listOf(PaymentConsentFixtures.CODE), repository.exchangedCodes)
        assertEquals(listOf(PaymentConsentFixtures.CONSENT_ID), repository.statusChecks)
    }

    /**
     * Approval is stamped when it is observed, because the bank never reports it.
     *
     * OBIE returns one `CreationDateTime` and no per-stage history, so if this moment is not
     * recorded here the payment detail timeline can only infer approval from a later event.
     */
    @Test
    fun anAuthorisedConsentRecordsWhenItWasApproved() = runTest {
        val repository = FakePaymentAuthRepository()

        viewModel(repository)

        assertEquals(1, repository.approvedRecordedCount)
    }

    /** A consent still awaiting the PSU has not been approved, so nothing is stamped. */
    @Test
    fun aConsentStillAwaitingAuthorisationRecordsNothing() = runTest {
        val repository = FakePaymentAuthRepository()
        repository.statusReturns(NetworkResult.Success("AWAU"))

        viewModel(repository)

        assertEquals(0, repository.approvedRecordedCount)
    }

    /**
     * The whole point of the move: the leg that returns from the bank is the one that submits,
     * because the screen that built the draft no longer exists by the time the redirect lands.
     */
    @Test
    fun anAuthorisedConsentConfirmsFundsAndSubmitsTheStagedDraft() = runTest {
        val payments = FakeSinglePaymentInitiationRepository()

        viewModel(payments = payments)

        assertEquals(listOf(PaymentConsentFixtures.CONSENT_ID), payments.fundsChecks)
        assertEquals(listOf(PaymentConsentFixtures.draft()), payments.submittedDrafts)
    }

    @Test
    fun aSubmittedPaymentLeavesAsAnEventCarryingItsId() = runTest {
        val vm = viewModel()

        val event = vm.eventFlow.first()

        assertEquals(
            PaymentConsentEvent.PaymentSubmitted(PaymentConsentFixtures.PAYMENT_ID),
            event,
        )
    }

    /**
     * A completed payment must not leave its consent id, PSU token or draft behind: the next payment
     * would otherwise find a credential its own authorisation never issued.
     */
    @Test
    fun aSubmittedPaymentClearsTheAuthorisation() = runTest {
        val repository = FakePaymentAuthRepository()

        viewModel(repository)

        assertEquals(1, repository.discardCount)
    }

    /**
     * The approved panel is the only positive thing this screen ever shows — a submitted payment
     * leaves immediately for its receipt — so the flow must actually pass through it rather than
     * jumping from "waiting for your bank" to "checking the money is available".
     *
     * Observed at the one instant it is visible: the staged draft is read after the state is set and
     * before the funds check replaces it. The consent is parked in Checking first so the ViewModel
     * exists to be observed by the time the hook fires.
     */
    @Test
    fun anAuthorisedConsentIsShownAsApprovedBeforeTheFundsCheckStarts() = runTest {
        val repository = FakePaymentAuthRepository()
        repository.statusReturns(NetworkResult.Success("AWAU"))
        val payments = FakeSinglePaymentInitiationRepository()
        val vm = viewModel(repository, payments)
        val seen = mutableListOf<PaymentConsentUiState>()
        payments.onStagedDraft = { seen += vm.stateFlow.value.uiState }

        repository.statusReturns(NetworkResult.Success("AUTH"))
        vm.trySendAction(PaymentConsentAction.CheckAgain)
        advanceUntilIdle()

        assertEquals(listOf<PaymentConsentUiState>(PaymentConsentUiState.Approved), seen)
    }

    /**
     * `COND` means the bank already created the payment. The regression this pins is the copy.
     *
     * Before this case existed the status fell through to the poll, ran the wait out and reported
     * `AuthorisationTimedOut` — whose panel says "No money has been moved" about a payment the bank
     * has already taken. Asserting the state is really asserting that the app stops saying that.
     */
    @Test
    fun aConsumedConsentIsReportedAsAlreadySubmittedRatherThanTimingOut() = runTest {
        val repository = FakePaymentAuthRepository()
        repository.statusReturns(NetworkResult.Success("COND"))

        val vm = viewModel(repository)

        assertEquals(PaymentConsentUiState.AlreadySubmitted, vm.stateFlow.value.uiState)
    }

    /** It is terminal on the first look: polling a consumed consent cannot improve on it. */
    @Test
    fun aConsumedConsentStopsOnTheFirstLook() = runTest {
        val repository = FakePaymentAuthRepository()
        repository.statusReturns(NetworkResult.Success("COND"))

        viewModel(repository)

        assertEquals(1, repository.statusChecks.size)
    }

    /**
     * A payment the bank created must not be filed as a failure.
     *
     * Writing a failed row here would put a successful payment into history as a failed one, and the
     * customer would then meet the same payment twice — once wrongly.
     */
    @Test
    fun aConsumedConsentRecordsNoFailureButStillClearsTheSession() = runTest {
        val repository = FakePaymentAuthRepository()
        repository.statusReturns(NetworkResult.Success("COND"))
        val history = FakePaymentHistoryRepository()

        viewModel(repository, history = history)

        assertTrue(history.failures.isEmpty())
        assertEquals(1, repository.discardCount)
    }

    /** Nothing is submitted off a consumed consent — the payment it refers to already exists. */
    @Test
    fun aConsumedConsentSubmitsNothingFurther() = runTest {
        val repository = FakePaymentAuthRepository()
        repository.statusReturns(NetworkResult.Success("COND"))
        val payments = FakeSinglePaymentInitiationRepository()

        viewModel(repository, payments)

        assertTrue(payments.submittedDrafts.isEmpty())
        assertTrue(payments.fundsChecks.isEmpty())
    }

    /**
     * `RJCT` is the PSU declining at the bank, observed through the status read rather than through
     * the callback's `error` parameter. Both routes must reach the same declined outcome; this one
     * used to report a timeout instead.
     */
    @Test
    fun aRejectedConsentReadsAsDeclinedRatherThanTimingOut() = runTest {
        val repository = FakePaymentAuthRepository()
        repository.statusReturns(NetworkResult.Success("RJCT"))

        val vm = viewModel(repository)

        val state = assertIs<PaymentConsentUiState.Error>(vm.stateFlow.value.uiState)
        assertEquals(PaymentConsentErrorKind.ConsentRejected, state.kind)
        assertEquals(1, repository.statusChecks.size)
    }

    /** HSBC reports it as `AUTH`; some responses spell it out. Both mean authorised. */
    @Test
    fun acceptsEitherSpellingOfAnAuthorisedConsent() = runTest {
        val repository = FakePaymentAuthRepository()
        repository.statusReturns(NetworkResult.Success("Authorised"))
        val payments = FakeSinglePaymentInitiationRepository()

        viewModel(repository, payments)

        assertEquals(1, payments.submittedDrafts.size)
    }

    // endregion

    // region — the funds gate

    /**
     * The protocol calls funds confirmation optional, but once made its answer is binding: submitting
     * anyway would knowingly send a payment the bank has just said cannot be covered.
     */
    @Test
    fun aNegativeFundsCheckStopsBeforeSubmitting() = runTest {
        val payments = FakeSinglePaymentInitiationRepository()
        payments.fundsReturn(NetworkResult.Success(false))

        val vm = viewModel(payments = payments)

        assertTrue(payments.submittedDrafts.isEmpty())
        val state = assertIs<PaymentConsentUiState.Error>(vm.stateFlow.value.uiState)
        assertEquals(PaymentConsentErrorKind.InsufficientFunds, state.kind)
    }

    /**
     * Without the staged draft there is nothing to send. Rebuilding one here would submit an
     * `Initiation` the consent was never granted against, so this fails closed instead.
     */
    @Test
    fun aMissingStagedDraftIsTerminalRatherThanRebuilt() = runTest {
        val payments = FakeSinglePaymentInitiationRepository()
        payments.stagedDraftReturns(null)

        val vm = viewModel(payments = payments)

        assertTrue(payments.fundsChecks.isEmpty())
        assertTrue(payments.submittedDrafts.isEmpty())
        val state = assertIs<PaymentConsentUiState.Error>(vm.stateFlow.value.uiState)
        assertEquals(PaymentConsentErrorKind.NoStagedPayment, state.kind)
    }

    /**
     * A `400` on the submission is the bank declining to create the payment, so it is a rejection
     * rather than the catch-all failure. It used to share [PaymentConsentErrorKind.SubmissionFailed]
     * with a dropped connection, which meant a refusal the bank had explained was described with
     * copy written for an outcome nobody could explain.
     */
    @Test
    fun aRefusedSubmissionSurfacesAsARejection() = runTest {
        val payments = FakeSinglePaymentInitiationRepository()
        payments.submissionReturns(
            NetworkResult.Error(NetworkError.Client.BadRequest("U008")),
        )

        val vm = viewModel(payments = payments)

        val state = assertIs<PaymentConsentUiState.Error>(vm.stateFlow.value.uiState)
        assertEquals(PaymentConsentErrorKind.RequestRejected, state.kind)
    }

    /**
     * A dropped connection *during* the submission says nothing about whether the payment was
     * taken, so it must not be reported as one of the outcomes that promises no money moved. This is
     * the case the old hardcoded `SubmissionFailed` got right by accident and a naive widening would
     * get wrong.
     */
    @Test
    fun aSubmissionThatLostTheConnectionStillReadsAsSubmissionFailed() = runTest {
        val payments = FakeSinglePaymentInitiationRepository()
        payments.submissionReturns(
            NetworkResult.Error(NetworkError.Network(cause = RuntimeException("offline"))),
        )

        val vm = viewModel(payments = payments)

        val state = assertIs<PaymentConsentUiState.Error>(vm.stateFlow.value.uiState)
        assertEquals(PaymentConsentErrorKind.SubmissionFailed, state.kind)
    }

    /** A credential the bank rejected outright never reached processing, so it is a clean failure. */
    @Test
    fun aSubmissionRefusedOnAnExpiredTokenReadsAsExpired() = runTest {
        val payments = FakeSinglePaymentInitiationRepository()
        payments.submissionReturns(NetworkResult.Error(NetworkError.Client.Unauthorized(null)))

        val vm = viewModel(payments = payments)

        val state = assertIs<PaymentConsentUiState.Error>(vm.stateFlow.value.uiState)
        assertEquals(PaymentConsentErrorKind.CodeExpired, state.kind)
    }

    @Test
    fun aSubmissionForbiddenByTheBankReadsAsDeclined() = runTest {
        val payments = FakeSinglePaymentInitiationRepository()
        payments.submissionReturns(NetworkResult.Error(NetworkError.Client.Forbidden(null)))

        val vm = viewModel(payments = payments)

        val state = assertIs<PaymentConsentUiState.Error>(vm.stateFlow.value.uiState)
        assertEquals(PaymentConsentErrorKind.ConsentRejected, state.kind)
    }

    // endregion

    // region — the poll gate

    /**
     * Submitting against a consent that has not reached AUTH returns 400 U009, so a consent still
     * awaiting authorisation must not be handed back as authorised.
     */
    @Test
    fun aConsentStillAwaitingAuthorisationIsNotHandedBack() = runTest {
        val repository = FakePaymentAuthRepository()
        repository.statusReturns(NetworkResult.Success("AWAU"))

        val state = assertIs<PaymentConsentUiState.Checking>(viewModel(repository).stateFlow.value.uiState)
        assertTrue(state.canCheckAgain)
    }

    /** A slow bank is normal, so the way out is another look rather than an error. */
    @Test
    fun checkingAgainRepollsTheConsentStatus() = runTest {
        val repository = FakePaymentAuthRepository()
        repository.statusReturns(NetworkResult.Success("AWAU"))
        val payments = FakeSinglePaymentInitiationRepository()
        val vm = viewModel(repository, payments)

        repository.statusReturns(NetworkResult.Success("AUTH"))
        vm.trySendAction(PaymentConsentAction.CheckAgain)

        assertEquals(2, repository.statusChecks.size)
        assertEquals(1, payments.submittedDrafts.size)
    }

    /**
     * The producer `AuthorisationTimedOut` never had.
     *
     * The wait is bounded: three unproductive reads mean the authorisation did not complete at the
     * bank, and a fourth look cannot change that. Saying so beats an endless Check again button that
     * hides a dead authorisation behind a spinner.
     */
    @Test
    fun aConsentThatNeverAuthorisesEventuallyTimesOut() = runTest {
        val repository = FakePaymentAuthRepository()
        repository.statusReturns(NetworkResult.Success("AWAU"))
        val vm = viewModel(repository)

        vm.trySendAction(PaymentConsentAction.CheckAgain)
        advanceUntilIdle()
        vm.trySendAction(PaymentConsentAction.CheckAgain)
        advanceUntilIdle()

        assertEquals(3, repository.statusChecks.size)
        val state = assertIs<PaymentConsentUiState.Error>(vm.stateFlow.value.uiState)
        assertEquals(PaymentConsentErrorKind.AuthorisationTimedOut, state.kind)
    }

    /** Nothing was submitted on the way to the timeout, which is what makes the outcome clean. */
    @Test
    fun aTimedOutAuthorisationSubmitsNothingAndClearsTheSession() = runTest {
        val repository = FakePaymentAuthRepository()
        repository.statusReturns(NetworkResult.Success("AWAU"))
        val payments = FakeSinglePaymentInitiationRepository()
        val vm = viewModel(repository, payments)

        repeat(2) {
            vm.trySendAction(PaymentConsentAction.CheckAgain)
            advanceUntilIdle()
        }

        assertTrue(payments.submittedDrafts.isEmpty())
        assertTrue(payments.fundsChecks.isEmpty())
        assertEquals(1, repository.discardCount)
    }

    /** The last look before the limit still offers another, so the bound is not off by one. */
    @Test
    fun theLookBeforeTheLimitStillOffersCheckAgain() = runTest {
        val repository = FakePaymentAuthRepository()
        repository.statusReturns(NetworkResult.Success("AWAU"))
        val vm = viewModel(repository)

        vm.trySendAction(PaymentConsentAction.CheckAgain)
        advanceUntilIdle()

        val state = assertIs<PaymentConsentUiState.Checking>(vm.stateFlow.value.uiState)
        assertTrue(state.canCheckAgain)
    }

    // endregion

    // region — replay and refusal

    /**
     * A callback whose `state` does not match the authorisation this app launched is a replay
     * signal, not a transient fault — it fails closed and is never retried through.
     */
    @Test
    fun aMismatchedStateIsRefusedWithoutExchangingAnything() = runTest {
        val repository = FakePaymentAuthRepository()
        repository.validationReturns(PaymentAuthValidation.SecurityError)

        val vm = viewModel(repository)

        val state = assertIs<PaymentConsentUiState.Error>(vm.stateFlow.value.uiState)
        assertEquals(PaymentConsentErrorKind.StateMismatch, state.kind)
        assertTrue(repository.exchangedCodes.isEmpty())
        assertTrue(repository.statusChecks.isEmpty())
    }

    /**
     * A callback with nothing pending is not the same event as one whose `state` does not match, and
     * the two used to share `SecurityError`.
     *
     * The session is cleared the moment a payment finishes, so the ordinary way to land here is
     * re-opening a link for a payment that already went through. Accusing that of tampering is both
     * wrong and alarming; it gets the benign copy instead.
     */
    @Test
    fun aCallbackWithNothingPendingIsBenignRatherThanASecurityEvent() = runTest {
        val repository = FakePaymentAuthRepository()
        repository.validationReturns(PaymentAuthValidation.NoPending)

        val vm = viewModel(repository)

        val state = assertIs<PaymentConsentUiState.Error>(vm.stateFlow.value.uiState)
        assertEquals(PaymentConsentErrorKind.NoPendingAuthorisation, state.kind)
        assertTrue(repository.exchangedCodes.isEmpty())
        assertTrue(repository.statusChecks.isEmpty())
    }

    /**
     * A consent the bank cannot find reads as expiry, not as "nothing was pending".
     *
     * The two are different events and must not share a state: "nothing was pending" describes an
     * app with no session and is a dead end, whereas a bank that has lost track of a consent
     * mid-flow is a failure the customer can and should start again from. Conflating them would
     * strand a live failure on a panel with no way forward.
     */
    @Test
    fun aConsentTheBankCannotFindReadsAsExpiredRatherThanNothingPending() = runTest {
        val repository = FakePaymentAuthRepository()
        repository.statusReturns(NetworkResult.Error(NetworkError.Client.NotFound(null)))

        val state = assertIs<PaymentConsentUiState.Error>(viewModel(repository).stateFlow.value.uiState)
        assertEquals(PaymentConsentErrorKind.CodeExpired, state.kind)
    }

    /** The bank answered, just not usefully or in time — a timeout, not a connection failure. */
    @Test
    fun aRateLimitedStatusReadReadsAsATimeout() = runTest {
        val repository = FakePaymentAuthRepository()
        repository.statusReturns(NetworkResult.Error(NetworkError.Client.RateLimited(null)))

        val state = assertIs<PaymentConsentUiState.Error>(viewModel(repository).stateFlow.value.uiState)
        assertEquals(PaymentConsentErrorKind.AuthorisationTimedOut, state.kind)
    }

    @Test
    fun aBankSideFailureDuringTheExchangeReadsAsATimeout() = runTest {
        val repository = FakePaymentAuthRepository()
        repository.exchangeReturns(NetworkResult.Error(NetworkError.Server(statusCode = 503)))

        val state = assertIs<PaymentConsentUiState.Error>(viewModel(repository).stateFlow.value.uiState)
        assertEquals(PaymentConsentErrorKind.AuthorisationTimedOut, state.kind)
    }

    @Test
    fun aDeclinedConsentIsReportedAsRejected() = runTest {
        val repository = FakePaymentAuthRepository()
        repository.validationReturns(PaymentAuthValidation.AccessDenied)

        val state = assertIs<PaymentConsentUiState.Error>(viewModel(repository).stateFlow.value.uiState)
        assertEquals(PaymentConsentErrorKind.ConsentRejected, state.kind)
    }

    @Test
    fun aMissingCodeIsReportedAsExpired() = runTest {
        val repository = FakePaymentAuthRepository()
        repository.validationReturns(PaymentAuthValidation.MissingCode)

        val state = assertIs<PaymentConsentUiState.Error>(viewModel(repository).stateFlow.value.uiState)
        assertEquals(PaymentConsentErrorKind.CodeExpired, state.kind)
    }

    @Test
    fun aFailedExchangeSurfacesAsAnError() = runTest {
        val repository = FakePaymentAuthRepository()
        repository.exchangeReturns(NetworkResult.Error(NetworkError.Client.Unauthorized(null)))

        val state = assertIs<PaymentConsentUiState.Error>(viewModel(repository).stateFlow.value.uiState)
        assertEquals(PaymentConsentErrorKind.CodeExpired, state.kind)
        assertTrue(repository.statusChecks.isEmpty())
    }

    @Test
    fun aFailedStatusReadSurfacesAsAnError() = runTest {
        val repository = FakePaymentAuthRepository()
        repository.statusReturns(
            NetworkResult.Error(NetworkError.Network(cause = RuntimeException("offline"))),
        )

        val state = assertIs<PaymentConsentUiState.Error>(viewModel(repository).stateFlow.value.uiState)
        assertEquals(PaymentConsentErrorKind.NetworkError, state.kind)
    }

    // endregion

    // region — the two exits

    @Test
    fun restartingAsksTheHostToAuthoriseAgain() = runTest {
        val repository = FakePaymentAuthRepository()
        repository.validationReturns(PaymentAuthValidation.SecurityError)
        val vm = viewModel(repository)

        vm.trySendAction(PaymentConsentAction.RetryAuthorisation)

        assertEquals(PaymentConsentEvent.RestartAuthorisation, vm.eventFlow.first())
    }

    /** A stuck authorisation gets a clean exit rather than leaving someone to back out. */
    @Test
    fun abandoningReportsThePaymentAsAbandoned() = runTest {
        val repository = FakePaymentAuthRepository()
        repository.validationReturns(PaymentAuthValidation.SecurityError)
        val vm = viewModel(repository)

        vm.trySendAction(PaymentConsentAction.AbandonPayment)

        assertEquals(PaymentConsentEvent.Abandoned, vm.eventFlow.first())
    }

    /**
     * The regression test for a payment that succeeded and never showed its receipt.
     *
     * `EventsEffect` filters an event out — permanently, since the channel has already yielded it —
     * unless the screen is `RESUMED` or the event is a [BackgroundEvent]. This screen runs the whole
     * tail of the journey while the customer may still be in the bank's browser tab, so the emission
     * routinely lands while it is not resumed. Live, the payment was created, funds confirmed and the
     * instruction submitted over 25 seconds; `PaymentSubmitted` was then dropped and the screen sat
     * on a progress panel while the money had already moved.
     *
     * Asserted on the type rather than through a fake lifecycle because the type *is* the contract
     * `EventsEffect` checks. Removing the supertype reintroduces the bug and fails here.
     */
    @Test
    fun everyEventOptsOutOfTheResumedOnlyFilterThatWouldDiscardIt() {
        assertIs<BackgroundEvent>(PaymentConsentEvent.PaymentSubmitted(PaymentConsentFixtures.PAYMENT_ID))
        assertIs<BackgroundEvent>(PaymentConsentEvent.RestartAuthorisation)
        assertIs<BackgroundEvent>(PaymentConsentEvent.Abandoned)
    }

    // endregion
}
