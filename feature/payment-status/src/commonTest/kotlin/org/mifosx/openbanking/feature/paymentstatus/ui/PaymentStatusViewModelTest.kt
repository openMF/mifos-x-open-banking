/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.paymentstatus.ui

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import org.mifosx.openbanking.core.model.banking.payment.PaymentDisposition
import org.mifosx.openbanking.core.model.banking.payment.PaymentStageTimestamps
import org.mifosx.openbanking.core.model.banking.payment.PaymentStatus
import org.mifosx.openbanking.feature.paymentstatus.FakePaymentHistoryRepository
import org.mifosx.openbanking.feature.paymentstatus.FakePaymentStatusRepository
import org.mifosx.openbanking.feature.paymentstatus.PaymentStatusFixtures
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class PaymentStatusViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** A clock that can be wound forward, so "last checked" is assertable rather than wall-clock. */
    private class FixedClock(var instant: Instant) : Clock {
        override fun now(): Instant = instant
    }

    private val clock = FixedClock(Instant.parse("2026-08-03T14:25:00Z"))

    private fun viewModel(
        repository: FakePaymentStatusRepository = FakePaymentStatusRepository(),
        history: FakePaymentHistoryRepository = FakePaymentHistoryRepository(),
        paymentId: String = PaymentStatusFixtures.PAYMENT_ID,
    ) = PaymentStatusViewModel(
        savedStateHandle = SavedStateHandle(mapOf(PaymentStatusViewModel.PAYMENT_ID_ARG to paymentId)),
        repository = repository,
        paymentHistoryRepository = history,
        clock = clock,
        // Fixed, so these assertions do not change meaning on a machine in another zone.
        timeZone = TimeZone.UTC,
    )

    private fun timelineOf(vm: PaymentStatusViewModel): Map<PaymentTimelineStep, PaymentTimelineEntry> =
        content(vm).timeline.associateBy { it.step }

    private fun content(vm: PaymentStatusViewModel): PaymentStatusUiState.Content =
        assertIs<PaymentStatusUiState.Content>(vm.stateFlow.value.uiState)

    @Test
    fun readsTheStatusForTheRoutesPaymentId() = runTest {
        val repository = FakePaymentStatusRepository()

        viewModel(repository)

        assertEquals(listOf(PaymentStatusFixtures.PAYMENT_ID), repository.statusReads)
    }

    @Test
    fun rendersTheReceiptTheBankEchoed() = runTest {
        val vm = viewModel()

        val state = content(vm)
        assertEquals("£850.00", state.amountLabel)
        assertEquals("Jameson Lettings", state.creditorName)
        assertEquals("RENT-FLAT12", state.reference)
        assertEquals(PaymentStatusFixtures.PAYMENT_ID, state.paymentId)
    }

    /**
     * The defect this rewrite exists for. "Submitted" was fed from `StatusUpdateDateTime`, which is
     * when the status last moved — a different fact from when the payment was made, and one HSBC
     * happens to return equal, which is exactly why the mislabel went unnoticed.
     */
    @Test
    fun submittedComesFromCreationTimeNotTheStatusUpdateTime() = runTest {
        val repository = FakePaymentStatusRepository(
            receipt = PaymentStatusFixtures.receiptWithDistinctTimestamps(),
        )

        val state = content(viewModel(repository))

        assertEquals("3 Aug 2026, 14:22", state.submittedAt)
        assertEquals("3 Aug 2026, 16:40", state.statusChangedAt)
        assertEquals("4 Aug 2026, 09:00", state.settledAt)
    }

    @Test
    fun carriesTheChargeTheBankActuallyApplied() = runTest {
        val charge = content(viewModel()).charges.single()

        assertEquals("UK.OBIE.CHAPSOut", charge.typeLabel)
        assertEquals("£0.05", charge.amountLabel)
    }

    /** No charge is not the same claim as a zero charge, so nothing is invented to fill the gap. */
    @Test
    fun aPaymentWithNoChargesCarriesNone() = runTest {
        val repository = FakePaymentStatusRepository(
            receipt = PaymentStatusFixtures.receipt(charges = emptyList()),
        )

        assertTrue(content(viewModel(repository)).charges.isEmpty())
    }

    @Test
    fun aMissingSettlementTimeLeavesTheRowEmptyRatherThanFormattingNothing() = runTest {
        val repository = FakePaymentStatusRepository(
            receipt = PaymentStatusFixtures.receipt().copy(settlementDateTime = ""),
        )

        assertEquals("", content(viewModel(repository)).settledAt)
    }

    /**
     * The question that started this: a refresh returning the same status must still be visibly a
     * refresh. Without a moving "last checked" the button is indistinguishable from a dead one.
     */
    @Test
    fun refreshingUpdatesLastCheckedEvenWhenTheStatusHasNotMoved() = runTest {
        val repository = FakePaymentStatusRepository()
        val vm = viewModel(repository)
        assertEquals("14:25", content(vm).lastCheckedAt)

        clock.instant = Instant.parse("2026-08-03T14:31:00Z")
        vm.trySendAction(PaymentStatusAction.RefreshStatus)

        val state = content(vm)
        assertEquals("14:31", state.lastCheckedAt)
        assertEquals(PaymentStatus.AcceptedSettlementInProcess, state.status)
    }

    /** And when it has moved, both the status and the stamp advance. */
    @Test
    fun aSettledReadUpdatesBothTheStatusAndTheStamp() = runTest {
        val repository = FakePaymentStatusRepository()
        val vm = viewModel(repository)

        repository.receiptReturns(
            PaymentStatusFixtures.receipt(status = PaymentStatus.AcceptedCreditSettlementCompleted),
        )
        clock.instant = Instant.parse("2026-08-03T15:00:00Z")
        vm.trySendAction(PaymentStatusAction.RefreshStatus)

        val state = content(vm)
        assertEquals(PaymentStatus.AcceptedCreditSettlementCompleted, state.status)
        assertEquals(PaymentDisposition.TerminalSuccess, state.disposition)
        assertEquals("15:00", state.lastCheckedAt)
    }

    /** The wire carries fourteen unpunctuated digits; people read a sort code in pairs. */
    @Test
    fun formatsThePayingAccountTheWayItIsWrittenDown() = runTest {
        val vm = viewModel()

        assertEquals("40-05-15 12345678", content(vm).debtorLabel)
    }

    /**
     * Accepted-but-not-settled is the normal answer for a fresh payment. Rendering it as a fault
     * would send people chasing a problem that does not exist.
     */
    @Test
    fun treatsAnAcceptedPaymentAsInProgressRatherThanDone() = runTest {
        val vm = viewModel()

        val state = content(vm)
        assertEquals(PaymentDisposition.InProgress, state.disposition)
        assertTrue(state.inProgress)
    }

    @Test
    fun reportsASettledPaymentAsTerminalSuccess() = runTest {
        val repository = FakePaymentStatusRepository()
        repository.statusReturns(
            NetworkResult.Success(
                PaymentStatusFixtures.receipt(status = PaymentStatus.AcceptedSettlementCompleted),
            ),
        )

        val state = content(viewModel(repository))
        assertEquals(PaymentDisposition.TerminalSuccess, state.disposition)
        assertFalse(state.inProgress)
    }

    @Test
    fun reportsARejectedPaymentAsTerminalFailure() = runTest {
        val repository = FakePaymentStatusRepository()
        repository.statusReturns(
            NetworkResult.Success(PaymentStatusFixtures.receipt(status = PaymentStatus.Rejected)),
        )

        assertEquals(PaymentDisposition.TerminalFailure, content(viewModel(repository)).disposition)
    }

    @Test
    fun refreshingReReadsTheStatus() = runTest {
        val repository = FakePaymentStatusRepository()
        val vm = viewModel(repository)

        vm.trySendAction(PaymentStatusAction.RefreshStatus)

        assertEquals(2, repository.statusReads.size)
    }

    /** Replacing a known answer with a skeleton reads as losing it. */
    @Test
    fun refreshingKeepsTheCurrentStatusOnScreen() = runTest {
        val repository = FakePaymentStatusRepository()
        val vm = viewModel(repository)

        vm.trySendAction(PaymentStatusAction.RefreshStatus)

        assertIs<PaymentStatusUiState.Content>(vm.stateFlow.value.uiState)
    }

    /** A payment id that resolves to nothing is a failure to explain, not an empty set. */
    @Test
    fun anUnknownPaymentIsAnErrorRatherThanAnEmptyState() = runTest {
        val repository = FakePaymentStatusRepository()
        repository.statusReturns(NetworkResult.Error(NetworkError.Client.NotFound(null)))

        val state = assertIs<PaymentStatusUiState.Error>(viewModel(repository).stateFlow.value.uiState)
        assertEquals(PaymentStatusErrorKind.PaymentNotFound, state.kind)
    }

    @Test
    fun anExpiredTokenIsReportedAsSuch() = runTest {
        val repository = FakePaymentStatusRepository()
        repository.statusReturns(NetworkResult.Error(NetworkError.Client.Unauthorized(null)))

        val state = assertIs<PaymentStatusUiState.Error>(viewModel(repository).stateFlow.value.uiState)
        assertEquals(PaymentStatusErrorKind.TokenExpired, state.kind)
    }

    @Test
    fun aTransportFailureIsReportedAsANetworkError() = runTest {
        val repository = FakePaymentStatusRepository()
        repository.statusReturns(
            NetworkResult.Error(NetworkError.Network(cause = RuntimeException("offline"))),
        )

        val state = assertIs<PaymentStatusUiState.Error>(viewModel(repository).stateFlow.value.uiState)
        assertEquals(PaymentStatusErrorKind.NetworkError, state.kind)
    }

    /** Retry from the error state is the same read, so a recovered payment renders normally. */
    @Test
    fun retryingAfterAFailureRecoversTheContent() = runTest {
        val repository = FakePaymentStatusRepository()
        repository.statusReturns(
            NetworkResult.Error(NetworkError.Network(cause = RuntimeException("offline"))),
        )
        val vm = viewModel(repository)
        assertIs<PaymentStatusUiState.Error>(vm.stateFlow.value.uiState)

        repository.statusReturns(NetworkResult.Success(PaymentStatusFixtures.receipt()))
        vm.trySendAction(PaymentStatusAction.RefreshStatus)

        assertEquals("£850.00", content(vm).amountLabel)
    }

    // region — a failed refresh keeps what is already known

    /**
     * The defect: `load` replaced the whole state with `Error`, so one flaky read wiped a status
     * the user had already been shown. A read that could not reach the bank has not invalidated
     * the bank's last answer — it is still true, only older than asked for.
     */
    @Test
    fun aFailedRefreshKeepsTheContentOnScreen() = runTest {
        val repository = FakePaymentStatusRepository()
        val vm = viewModel(repository)

        repository.statusReturns(
            NetworkResult.Error(NetworkError.Network(cause = RuntimeException("offline"))),
        )
        vm.trySendAction(PaymentStatusAction.RefreshStatus)

        val state = content(vm)
        assertEquals("£850.00", state.amountLabel)
        assertEquals(PaymentStatus.AcceptedSettlementInProcess, state.status)
    }

    @Test
    fun aFailedRefreshSurfacesTheFailureAndStopsSpinning() = runTest {
        val repository = FakePaymentStatusRepository()
        val vm = viewModel(repository)

        repository.statusReturns(NetworkResult.Error(NetworkError.Client.Unauthorized(null)))
        vm.trySendAction(PaymentStatusAction.RefreshStatus)

        val state = content(vm)
        assertEquals(PaymentStatusErrorKind.TokenExpired, state.refreshFailure)
        assertFalse(state.refreshing)
    }

    /** The stamp must not advance on a read that never landed, or it would claim a check happened. */
    @Test
    fun aFailedRefreshLeavesLastCheckedWhereItWas() = runTest {
        val repository = FakePaymentStatusRepository()
        val vm = viewModel(repository)

        repository.statusReturns(
            NetworkResult.Error(NetworkError.Network(cause = RuntimeException("offline"))),
        )
        clock.instant = Instant.parse("2026-08-03T15:00:00Z")
        vm.trySendAction(PaymentStatusAction.RefreshStatus)

        assertEquals("14:25", content(vm).lastCheckedAt)
    }

    @Test
    fun aRecoveredRefreshClearsTheFailureNotice() = runTest {
        val repository = FakePaymentStatusRepository()
        val vm = viewModel(repository)
        repository.statusReturns(
            NetworkResult.Error(NetworkError.Network(cause = RuntimeException("offline"))),
        )
        vm.trySendAction(PaymentStatusAction.RefreshStatus)
        assertNotNull(content(vm).refreshFailure)

        repository.statusReturns(NetworkResult.Success(PaymentStatusFixtures.receipt()))
        vm.trySendAction(PaymentStatusAction.RefreshStatus)

        assertNull(content(vm).refreshFailure)
    }

    /** With nothing to preserve, the first load's failure is still the error page. */
    @Test
    fun aFailedFirstLoadIsStillTheErrorPage() = runTest {
        val repository = FakePaymentStatusRepository()
        repository.statusReturns(NetworkResult.Error(NetworkError.Client.NotFound(null)))

        assertIs<PaymentStatusUiState.Error>(viewModel(repository).stateFlow.value.uiState)
    }

    // endregion

    // region — the four-stage timeline

    @Test
    fun theTimelineRunsNewestFirst() = runTest {
        val steps = content(viewModel()).timeline.map { it.step }

        assertEquals(
            listOf(
                PaymentTimelineStep.Completed,
                PaymentTimelineStep.Submitted,
                PaymentTimelineStep.ApprovedAtBank,
                PaymentTimelineStep.RequestCreated,
            ),
            steps,
        )
    }

    /**
     * This screen is only reachable with a bank-issued payment id, so the first three stages are
     * facts rather than inferences: without them there would be no id to look up.
     */
    @Test
    fun theFirstThreeStagesAreDoneWheneverThereIsAPaymentId() = runTest {
        val timeline = timelineOf(viewModel())

        assertEquals(PaymentStepState.Done, timeline.getValue(PaymentTimelineStep.RequestCreated).state)
        assertEquals(PaymentStepState.Done, timeline.getValue(PaymentTimelineStep.ApprovedAtBank).state)
        assertEquals(PaymentStepState.Done, timeline.getValue(PaymentTimelineStep.Submitted).state)
    }

    /** OBIE returns one CreationDateTime, so these two can only come from the local row. */
    @Test
    fun approvalAndSubmissionTimesComeFromTheLocalRow() = runTest {
        val timeline = timelineOf(viewModel())

        assertEquals("3 Aug 2026, 14:20", timeline.getValue(PaymentTimelineStep.ApprovedAtBank).timestamp)
        assertEquals("3 Aug 2026, 14:22", timeline.getValue(PaymentTimelineStep.Submitted).timestamp)
    }

    /**
     * Nothing records when the consent was staged — v5 stores `approvedAt` and `submittedAt` and no
     * `stagedAt` — and the nearest field lies: on a payment resource OBIE's `CreationDateTime` is
     * when the bank created the *payment*, which is submission. Feeding it here dated the first
     * stage with the third stage's event, so a payment approved at 14:20 rendered as requested at
     * 14:22 — later than its own approval, in a list that claims to run newest first.
     */
    @Test
    fun theRequestStageIsUndatedRatherThanDatedFromTheSubmission() = runTest {
        val timeline = timelineOf(viewModel())

        assertEquals("", timeline.getValue(PaymentTimelineStep.RequestCreated).timestamp)
        assertEquals(PaymentStepState.Done, timeline.getValue(PaymentTimelineStep.RequestCreated).state)
    }

    /** The dated stages must never run backwards against the newest-first order they are drawn in. */
    @Test
    fun theDatedStagesRunNewestFirst() = runTest {
        val repository = FakePaymentStatusRepository(
            receipt = PaymentStatusFixtures.receiptWithDistinctTimestamps()
                .copy(status = PaymentStatus.AcceptedCreditSettlementCompleted),
        )

        val dated = content(viewModel(repository)).timeline.filter { it.timestamp.isNotBlank() }

        assertEquals(
            listOf("4 Aug 2026, 09:00", "3 Aug 2026, 14:22", "3 Aug 2026, 14:20"),
            dated.map { it.timestamp },
        )
    }

    @Test
    fun theStageTimesAreReadForTheRoutesPaymentId() = runTest {
        val history = FakePaymentHistoryRepository()

        viewModel(history = history)

        assertEquals(listOf(PaymentStatusFixtures.PAYMENT_ID), history.stageReads)
    }

    /**
     * A payment made on another device, or one the five-row cap evicted, has no local row. The
     * stages still happened, so they stay Done — they are simply undated, never back-filled from a
     * nearby timestamp.
     */
    @Test
    fun aPaymentWithNoLocalRowRendersItsMiddleStagesUndated() = runTest {
        val timeline = timelineOf(viewModel(history = FakePaymentHistoryRepository(stages = null)))

        assertEquals("", timeline.getValue(PaymentTimelineStep.ApprovedAtBank).timestamp)
        assertEquals("", timeline.getValue(PaymentTimelineStep.Submitted).timestamp)
        assertEquals(PaymentStepState.Done, timeline.getValue(PaymentTimelineStep.ApprovedAtBank).state)
    }

    @Test
    fun aHalfRecordedRowDatesOnlyTheStageItObserved() = runTest {
        val history = FakePaymentHistoryRepository(
            stages = PaymentStageTimestamps(approvedAt = null, submittedAt = "2026-08-03T14:22:00Z"),
        )

        val timeline = timelineOf(viewModel(history = history))

        assertEquals("", timeline.getValue(PaymentTimelineStep.ApprovedAtBank).timestamp)
        assertEquals("3 Aug 2026, 14:22", timeline.getValue(PaymentTimelineStep.Submitted).timestamp)
    }

    /**
     * Settlement is an asynchronous batch, so the final stage is never filled in on the strength of
     * a submission having succeeded. It arrives on a later refresh or not at all.
     */
    @Test
    fun anInFlightPaymentLeavesTheFinalStageDatelessRatherThanOptimistic() = runTest {
        val completed = timelineOf(viewModel()).getValue(PaymentTimelineStep.Completed)

        assertEquals(PaymentStepState.Current, completed.state)
        assertEquals("", completed.timestamp)
    }

    /**
     * "Received" is not "settling". Telling someone their money is moving on the strength of the
     * bank having taken the instruction would be a claim the bank has not made.
     */
    @Test
    fun aMerelyReceivedPaymentLeavesTheFinalStagePending() = runTest {
        val repository = FakePaymentStatusRepository(
            receipt = PaymentStatusFixtures.receipt(status = PaymentStatus.Received),
        )

        val completed = timelineOf(viewModel(repository)).getValue(PaymentTimelineStep.Completed)

        assertEquals(PaymentStepState.Pending, completed.state)
    }

    /** An unrecognised status is the same refusal to guess: pending, not settling. */
    @Test
    fun anUnrecognisedStatusLeavesTheFinalStagePending() = runTest {
        val repository = FakePaymentStatusRepository(
            receipt = PaymentStatusFixtures.receipt(status = PaymentStatus.Unknown),
        )

        assertEquals(
            PaymentStepState.Pending,
            timelineOf(viewModel(repository)).getValue(PaymentTimelineStep.Completed).state,
        )
    }

    @Test
    fun aSettledPaymentCompletesTheFinalStageWithTheSettlementTime() = runTest {
        val repository = FakePaymentStatusRepository(
            receipt = PaymentStatusFixtures.receiptWithDistinctTimestamps()
                .copy(status = PaymentStatus.AcceptedCreditSettlementCompleted),
        )

        val completed = timelineOf(viewModel(repository)).getValue(PaymentTimelineStep.Completed)

        assertEquals(PaymentStepState.Done, completed.state)
        assertEquals("4 Aug 2026, 09:00", completed.timestamp)
    }

    /** Everything up to the bank's refusal did happen, so only the last stage fails. */
    @Test
    fun aRejectedPaymentFailsOnlyItsFinalStage() = runTest {
        val repository = FakePaymentStatusRepository(
            receipt = PaymentStatusFixtures.receipt(status = PaymentStatus.Rejected),
        )

        val timeline = timelineOf(viewModel(repository))

        assertEquals(PaymentStepState.Done, timeline.getValue(PaymentTimelineStep.RequestCreated).state)
        assertEquals(PaymentStepState.Done, timeline.getValue(PaymentTimelineStep.ApprovedAtBank).state)
        assertEquals(PaymentStepState.Done, timeline.getValue(PaymentTimelineStep.Submitted).state)
        assertEquals(PaymentStepState.Failed, timeline.getValue(PaymentTimelineStep.Completed).state)
    }

    /** The rejection is dated from the bank's own status-update time, not from anything invented. */
    @Test
    fun aRejectedPaymentDatesItsFailureFromTheStatusUpdate() = runTest {
        val repository = FakePaymentStatusRepository(
            receipt = PaymentStatusFixtures.receiptWithDistinctTimestamps()
                .copy(status = PaymentStatus.Rejected),
        )

        assertEquals(
            "3 Aug 2026, 16:40",
            timelineOf(viewModel(repository)).getValue(PaymentTimelineStep.Completed).timestamp,
        )
    }

    // endregion

    /**
     * `settledAt` was guarded and this was not, so a blank wire value went through `formatDateTime`
     * — which returns an unparseable input verbatim — and whatever came back drove a conditional
     * row rather than the fact that the bank said nothing.
     */
    @Test
    fun aBlankStatusUpdateTimeLeavesTheRowEmptyRatherThanFormattingNothing() = runTest {
        val repository = FakePaymentStatusRepository(
            receipt = PaymentStatusFixtures.receipt().copy(statusUpdateDateTime = ""),
        )

        assertEquals("", content(viewModel(repository)).statusChangedAt)
    }
}
