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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.mifosx.openbanking.core.model.banking.payment.PaymentRail
import org.mifosx.openbanking.feature.paymentsstandingorder.FakeAccountCapabilityRegistry
import org.mifosx.openbanking.feature.paymentsstandingorder.FakeAccountsOverviewRepository
import org.mifosx.openbanking.feature.paymentsstandingorder.FakeBeneficiariesRepository
import org.mifosx.openbanking.feature.paymentsstandingorder.FakePaymentHistoryRepository
import org.mifosx.openbanking.feature.paymentsstandingorder.FakePaymentStatusRepository
import org.mifosx.openbanking.feature.paymentsstandingorder.FakeStandingOrderInitiationRepository
import org.mifosx.openbanking.feature.paymentsstandingorder.StandingOrderFixtures
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The scheduling journey, as far as the browser hand-off.
 *
 * Focused on what this screen does that send-money does not: hold an execution date, keep it valid as
 * the rail and the calendar move underneath it, and carry it into the draft. The parts the two share
 * — the payer picker, the payee list, the amount ladder — are covered by send-money's own suite and
 * are not re-asserted here.
 *
 * Every case runs against a fixed clock. The rules under test are all relative to today, so a suite
 * on the real clock would assert a different window every day and would pass or fail depending on
 * which day of the week it ran.
 */
class StandingOrderViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** 2026-08-12, a Wednesday. Matches [StandingOrderFixtures.TODAY]. */
    private fun clockAt(date: LocalDate = StandingOrderFixtures.TODAY): Clock =
        object : Clock {
            override fun now(): Instant = Instant.parse("${date}T09:00:00Z")
        }

    private fun viewModel(
        accounts: FakeAccountsOverviewRepository = FakeAccountsOverviewRepository(),
        payees: FakeBeneficiariesRepository = FakeBeneficiariesRepository(),
        payments: FakeStandingOrderInitiationRepository = FakeStandingOrderInitiationRepository(),
        clock: Clock = clockAt(),
    ) = StandingOrderViewModel(
        accountsOverviewRepository = accounts,
        beneficiariesRepository = payees,
        paymentInitiationRepository = payments,
        capabilityRegistry = FakeAccountCapabilityRegistry(),
        paymentHistoryRepository = FakePaymentHistoryRepository(),
        paymentStatusRepository = FakePaymentStatusRepository(),
        clock = clock,
    )

    private fun content(vm: StandingOrderViewModel): StandingOrderUiState.Content =
        assertIs(vm.stateFlow.value.uiState)

    /** Fills everything except the date, so the date's own effect on `canReview` is isolated. */
    /** The default start date, as an action. Named because four cases needed the same line. */
    private fun StandingOrderViewModel.chooseFirstPaymentDate(
        date: LocalDate = StandingOrderFixtures.EXECUTION_DATE,
    ) {
        trySendAction(StandingOrderAction.SelectDate(StandingOrderDateRole.First, date))
    }

    private fun StandingOrderViewModel.completeFormWithoutDate() {
        trySendAction(StandingOrderAction.SelectDebtorAccount(StandingOrderFixtures.CURRENT_ACCOUNT_ID))
        trySendAction(StandingOrderAction.SelectCreditor(StandingOrderFixtures.JAMESON_ID))
        trySendAction(StandingOrderAction.EnterAmount("850"))
    }

    private fun StandingOrderViewModel.completeForm(
        date: LocalDate = StandingOrderFixtures.EXECUTION_DATE,
    ) {
        completeFormWithoutDate()
        trySendAction(StandingOrderAction.SelectDate(StandingOrderDateRole.First, date))
    }

    // region — The date itself

    @Test
    fun opensWithNoDateChosen() = runTest {
        val state = content(viewModel())

        assertNull(state.firstPaymentDate)
        assertTrue(state.firstPaymentDateLabel.isEmpty())
    }

    /**
     * The date is mandatory, and this is the guard that makes it so.
     *
     * Omitting `RequestedExecutionDateTime` is refused `U004` outright, so a review reachable without
     * one would build a request that can only fail — after the customer had filled in everything else.
     */
    @Test
    fun reviewIsUnreachableUntilADateIsChosen() = runTest {
        val vm = viewModel()

        vm.completeFormWithoutDate()

        assertFalse(content(vm).canReview, "a payment with no date cannot be reviewed")
    }

    @Test
    fun reviewBecomesReachableOnceADateIsChosen() = runTest {
        val vm = viewModel()

        vm.completeForm()

        assertTrue(content(vm).canReview)
    }

    @Test
    fun choosingADateWritesItAndItsLabel() = runTest {
        val vm = viewModel()

        vm.chooseFirstPaymentDate()

        val state = content(vm)
        assertEquals(StandingOrderFixtures.EXECUTION_DATE, state.firstPaymentDate)
        assertEquals(StandingOrderFixtures.EXECUTION_DATE_LABEL, state.firstPaymentDateLabel)
    }

    /** The rule is re-checked here, not trusted from the control that produced it. */
    @Test
    fun aDateOutsideTheWindowIsRefusedEvenIfThePickerOffersIt() = runTest {
        val vm = viewModel()

        vm.trySendAction(StandingOrderAction.SelectDate(StandingOrderDateRole.First, StandingOrderFixtures.TODAY))

        assertNull(content(vm).firstPaymentDate, "today is refused U003 and must not be accepted here")
    }

    // endregion

    // region — The dialog

    @Test
    fun openingThePickerShowsIt() = runTest {
        val vm = viewModel()

        vm.trySendAction(StandingOrderAction.OpenDatePicker(StandingOrderDateRole.First))

        assertTrue(content(vm).datePickerVisible)
    }

    /** Dismissing is not clearing: a date already chosen survives a cancel. */
    @Test
    fun dismissingThePickerKeepsAnyDateAlreadyChosen() = runTest {
        val vm = viewModel()
        vm.chooseFirstPaymentDate()

        vm.trySendAction(StandingOrderAction.OpenDatePicker(StandingOrderDateRole.First))
        vm.trySendAction(StandingOrderAction.DismissDatePicker)

        val state = content(vm)
        assertFalse(state.datePickerVisible)
        assertEquals(StandingOrderFixtures.EXECUTION_DATE, state.firstPaymentDate)
    }

    @Test
    fun choosingADateClosesThePicker() = runTest {
        val vm = viewModel()
        vm.trySendAction(StandingOrderAction.OpenDatePicker(StandingOrderDateRole.First))

        vm.chooseFirstPaymentDate()

        assertFalse(content(vm).datePickerVisible)
    }

    /**
     * The window is measured when the dialog opens, not when the screen was built.
     *
     * An app can sit open across midnight. With the window captured once, the picker would go on
     * offering yesterday's tomorrow — a date the bank refuses `U003` — and the customer would only
     * find out after filling the form.
     */
    @Test
    fun openingThePickerRestampsTheWindow() = runTest {
        val nextDay = LocalDate(2026, 8, 13)
        var current = StandingOrderFixtures.TODAY
        val movingClock = object : Clock {
            override fun now(): Instant = Instant.parse("${current}T09:00:00Z")
        }
        val vm = viewModel(clock = movingClock)
        assertEquals(StandingOrderFixtures.TODAY, content(vm).today)

        current = nextDay
        vm.trySendAction(StandingOrderAction.OpenDatePicker(StandingOrderDateRole.First))

        assertEquals(nextDay, content(vm).today, "the window must be measured again on every open")
    }

    /** And a date the moved window no longer allows is dropped rather than carried silently. */
    @Test
    fun aDateThatMidnightInvalidatedIsClearedWhenThePickerReopens() = runTest {
        var current = StandingOrderFixtures.TODAY
        val movingClock = object : Clock {
            override fun now(): Instant = Instant.parse("${current}T09:00:00Z")
        }
        val vm = viewModel(clock = movingClock)
        // The earliest the bank allows: today + 2. Not today + 1 — the bank refuses a first payment
        // that falls today or tomorrow, so T+1 would never have been selectable to begin with.
        val earliestAllowed = LocalDate(2026, 8, 14)
        vm.trySendAction(StandingOrderAction.SelectDate(StandingOrderDateRole.First, earliestAllowed))
        assertEquals(earliestAllowed, content(vm).firstPaymentDate)

        // One midnight passes. The held date is now only a day away, which the bank refuses.
        current = LocalDate(2026, 8, 13)
        vm.trySendAction(StandingOrderAction.OpenDatePicker(StandingOrderDateRole.First))

        assertNull(content(vm).firstPaymentDate)
    }

    // endregion

    // region — The rail, which changes which dates are legal

    /**
     * A weekend start date survives a rail switch — the deliberate inversion of the scheduled module.
     *
     * There, a Saturday is selectable domestically and refused internationally, so switching has to
     * clear it. Here both rails accept weekends: every weekend first-payment date probed was accepted
     * on both, and each was echoed back unchanged rather than shifted to the next working day.
     * Clearing it would make the customer re-pick a date the bank would have taken.
     */
    @Test
    fun switchingToInternationalKeepsAWeekendDate() = runTest {
        val vm = viewModel()
        val saturday = LocalDate(2026, 8, 15)
        vm.trySendAction(StandingOrderAction.SelectDate(StandingOrderDateRole.First, saturday))
        assertEquals(saturday, content(vm).firstPaymentDate)

        vm.trySendAction(StandingOrderAction.SelectRail(PaymentRail.International))

        assertEquals(saturday, content(vm).firstPaymentDate, "both rails accept weekends")
    }

    /**
     * Switching to international empties the three fields that rail has no wire member for.
     *
     * Emptying is half of the guarantee; the draft builder reading only enabled fields is the other.
     * Both are needed — this one is what the customer sees, and the builder is what the bank sees.
     */
    @Test
    fun switchingToInternationalEmptiesTheFieldsThatRailCannotCarry() = runTest {
        val vm = viewModel()
        vm.trySendAction(StandingOrderAction.EnterRecurringAmount("300"))
        vm.trySendAction(StandingOrderAction.EnterFinalAmount("125"))
        vm.trySendAction(StandingOrderAction.EnterReference("FLAT 4B RENT"))

        vm.trySendAction(StandingOrderAction.SelectRail(PaymentRail.International))

        val state = content(vm)
        assertEquals("", state.recurringAmountInput)
        assertEquals("", state.finalAmountInput)
        assertEquals("", state.reference)
        assertFalse(state.recurringAmountEnabled, "and the fields are not offered on this rail")
        assertFalse(state.referenceEnabled)
    }

    /** A date both rails accept survives the switch — re-picking it would be a cost with no reason. */
    @Test
    fun switchingRailKeepsADateBothRailsAccept() = runTest {
        val vm = viewModel()
        vm.chooseFirstPaymentDate()

        vm.trySendAction(StandingOrderAction.SelectRail(PaymentRail.International))

        assertEquals(StandingOrderFixtures.EXECUTION_DATE, content(vm).firstPaymentDate)
    }

    // endregion

    // region — Staging

    /** The date reaches the wire as a bare ISO date, which is what keeps the two bodies identical. */
    @Test
    fun stagingCarriesTheChosenDateAsAnIsoDate() = runTest {
        val payments = FakeStandingOrderInitiationRepository()
        val vm = viewModel(payments = payments)
        vm.completeForm()

        vm.trySendAction(StandingOrderAction.ConfirmAndStageConsent)

        assertEquals("2026-08-14", payments.stagedDrafts.single().firstPaymentDate)
    }

    /**
     * The two optional overrides reach the wire on the rail that has members for them.
     *
     * Until the fields were built, this could not be asserted at all: the actions, the state and the
     * draft mapping all existed, and nothing on screen could produce a value for any of them.
     */
    @Test
    fun stagingCarriesTheRecurringAndFinalAmountsOnTheDomesticRail() = runTest {
        val payments = FakeStandingOrderInitiationRepository()
        val vm = viewModel(payments = payments)
        vm.completeForm()

        vm.trySendAction(StandingOrderAction.EnterRecurringAmount("300"))
        vm.trySendAction(StandingOrderAction.EnterFinalAmount("50"))
        vm.trySendAction(StandingOrderAction.ConfirmAndStageConsent)

        val draft = payments.stagedDrafts.single()
        assertEquals(85000L, draft.firstPaymentAmountMinorUnits)
        assertEquals(30000L, draft.recurringPaymentAmountMinorUnits)
        assertEquals(5000L, draft.finalPaymentAmountMinorUnits)
    }

    /**
     * And cannot on the rail that has none — even when they were typed before the switch.
     *
     * `OBInternationalStandingOrder4` carries a single `InstructedAmount`; sending either override is
     * `U005`. The fields are disabled there, but the guarantee that matters is this one, because it
     * holds whether or not the disabling does.
     */
    @Test
    fun stagingDropsBothOverridesOnTheInternationalRail() = runTest {
        val payments = FakeStandingOrderInitiationRepository()
        val vm = viewModel(payments = payments)
        vm.completeForm()
        vm.trySendAction(StandingOrderAction.EnterRecurringAmount("300"))
        vm.trySendAction(StandingOrderAction.EnterFinalAmount("50"))

        vm.trySendAction(StandingOrderAction.SelectRail(PaymentRail.International))
        vm.completeForm()
        vm.trySendAction(StandingOrderAction.ConfirmAndStageConsent)

        val draft = payments.stagedDrafts.single()
        assertNull(draft.recurringPaymentAmountMinorUnits, "this rail has no member for it")
        assertNull(draft.finalPaymentAmountMinorUnits, "nor for this one")
    }

    @Test
    fun stagingMintsTwoDistinctIdempotencyKeys() = runTest {
        val payments = FakeStandingOrderInitiationRepository()
        val vm = viewModel(payments = payments)
        vm.completeForm()

        vm.trySendAction(StandingOrderAction.ConfirmAndStageConsent)

        val draft = payments.stagedDrafts.single()
        assertNotEquals(draft.consentIdempotencyKey, draft.paymentIdempotencyKey)
    }

    /**
     * The key is only worth minting if a retry replays it.
     *
     * A staging failure does not say whether the bank saw the request. If it did and only the reply
     * was lost, retrying under a fresh key stages a *second* consent rather than returning the
     * first — and a standing-order consent cannot be withdrawn, because the bank answers `405` to a
     * delete. Every such retry would leave a permanent orphan.
     */
    @Test
    fun retryingAfterAFailedStagingReplaysTheSameKeys() = runTest {
        val payments = FakeStandingOrderInitiationRepository()
        payments.stageReturns(NetworkResult.Error(NetworkError.Client.BadRequest("lost")))
        val vm = viewModel(payments = payments)
        vm.completeForm()
        vm.trySendAction(StandingOrderAction.ConfirmAndStageConsent)

        vm.trySendAction(StandingOrderAction.RetryStaging)

        assertEquals(2, payments.stagedDrafts.size, "the retry must reach the repository")
        val (first, second) = payments.stagedDrafts
        assertEquals(
            first.consentIdempotencyKey,
            second.consentIdempotencyKey,
            "a retry of the same mandate must replay its consent key, not mint a new one",
        )
        assertEquals(first.paymentIdempotencyKey, second.paymentIdempotencyKey)
    }

    /**
     * The other half: an edited mandate is a different mandate, so it must not inherit the keys.
     *
     * Replaying them would ask the bank to treat a changed instruction as the one it already has.
     */
    @Test
    fun editingTheAmountAfterAFailureMintsFreshKeys() = runTest {
        val payments = FakeStandingOrderInitiationRepository()
        payments.stageReturns(NetworkResult.Error(NetworkError.Client.BadRequest("lost")))
        val vm = viewModel(payments = payments)
        vm.completeForm()
        vm.trySendAction(StandingOrderAction.ConfirmAndStageConsent)

        vm.trySendAction(StandingOrderAction.EnterAmount("900"))
        vm.trySendAction(StandingOrderAction.ConfirmAndStageConsent)

        val (first, second) = payments.stagedDrafts
        assertNotEquals(
            first.consentIdempotencyKey,
            second.consentIdempotencyKey,
            "an edited mandate must not reuse the previous mandate's key",
        )
        assertEquals(90000L, second.firstPaymentAmountMinorUnits)
    }

    /**
     * This screen stages and stops.
     *
     * Submitting belongs to the leg that returns from the bank, which is a different ViewModel
     * entirely. A submission recorded here has caught it drifting back to the wrong screen.
     */
    @Test
    fun stagingNeverSubmits() = runTest {
        val payments = FakeStandingOrderInitiationRepository()
        val vm = viewModel(payments = payments)
        vm.completeForm()

        vm.trySendAction(StandingOrderAction.ConfirmAndStageConsent)

        assertTrue(payments.submittedDrafts.isEmpty())
    }

    @Test
    fun stagingRaisesTheAuthorisationEvent() = runTest {
        val vm = viewModel()
        vm.completeForm()

        vm.trySendAction(StandingOrderAction.ConfirmAndStageConsent)

        assertIs<StandingOrderUiState.Submitting>(vm.stateFlow.value.uiState)
    }

    /**
     * Nothing is staged without a date, even if the action is dispatched directly.
     *
     * `canReview` keeps the button disabled, but the guard is repeated in `buildDraft` because a
     * disabled control is a UI property and this is the last point before the instruction is fixed.
     */
    @Test
    fun confirmingWithNoDateStagesNothing() = runTest {
        val payments = FakeStandingOrderInitiationRepository()
        val vm = viewModel(payments = payments)
        vm.completeFormWithoutDate()

        vm.trySendAction(StandingOrderAction.ConfirmAndStageConsent)

        assertTrue(payments.stagedDrafts.isEmpty())
    }

    // endregion

    // region — Getting back out

    /**
     * The waiting state has a way out, unlike the immediate rail's.
     *
     * A customer who returns through the task switcher rather than the redirect delivers no callback
     * at all, and without this the screen waits on one forever.
     */
    @Test
    fun abandoningAuthorisationReturnsToTheReview() = runTest {
        val vm = viewModel()
        vm.completeForm()
        vm.trySendAction(StandingOrderAction.ConfirmAndStageConsent)
        assertIs<StandingOrderUiState.Submitting>(vm.stateFlow.value.uiState)

        vm.trySendAction(StandingOrderAction.AbandonAuthorisation)

        assertEquals(StandingOrderStep.Review, content(vm).step)
    }

    /**
     * Abandoning keeps the draft, and with it the keys.
     *
     * Walking away from the browser changes nothing about the mandate, and the consent it already
     * staged cannot be withdrawn. Confirming again must therefore land on that same consent rather
     * than stage a second un-withdrawable one. The consent id is dropped because staging returns it
     * afresh.
     */
    @Test
    fun abandoningAuthorisationKeepsTheDraftAndItsKeys() = runTest {
        val payments = FakeStandingOrderInitiationRepository()
        val vm = viewModel(payments = payments)
        vm.completeForm()
        vm.trySendAction(StandingOrderAction.ConfirmAndStageConsent)

        vm.trySendAction(StandingOrderAction.AbandonAuthorisation)
        assertNull(vm.stateFlow.value.consentId, "the id is dropped; staging returns it afresh")

        vm.trySendAction(StandingOrderAction.ConfirmAndStageConsent)

        val (first, second) = payments.stagedDrafts
        assertEquals(first.consentIdempotencyKey, second.consentIdempotencyKey)
    }

    /** Editing keeps the date — going back to change the amount is not a reason to re-pick a day. */
    @Test
    fun goingBackToEditKeepsTheDate() = runTest {
        val vm = viewModel()
        vm.completeForm()
        vm.trySendAction(StandingOrderAction.ReviewStandingOrder)

        vm.trySendAction(StandingOrderAction.BackStep)

        val state = content(vm)
        assertEquals(StandingOrderStep.Form, state.step)
        assertEquals(StandingOrderFixtures.EXECUTION_DATE, state.firstPaymentDate)
    }

    // endregion
}
