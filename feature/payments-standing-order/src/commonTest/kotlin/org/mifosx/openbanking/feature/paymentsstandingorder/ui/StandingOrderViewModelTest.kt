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
import org.mifosx.openbanking.feature.paymentsstandingorder.FakeStandingOrderInitiationRepository
import org.mifosx.openbanking.feature.paymentsstandingorder.StandingOrderFixtures
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
        val tomorrow = LocalDate(2026, 8, 13)
        vm.trySendAction(StandingOrderAction.SelectDate(StandingOrderDateRole.First, tomorrow))
        assertEquals(tomorrow, content(vm).firstPaymentDate)

        // The chosen date is now today, which the bank counts as past.
        current = tomorrow
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
        assertFalse(state.recurringAmountEnabled, "and the fields stay visible, disabled")
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

    /** Abandoning drops the draft: the next attempt is a fresh instruction under fresh keys. */
    @Test
    fun abandoningAuthorisationDropsTheDraft() = runTest {
        val vm = viewModel()
        vm.completeForm()
        vm.trySendAction(StandingOrderAction.ConfirmAndStageConsent)

        vm.trySendAction(StandingOrderAction.AbandonAuthorisation)

        assertNull(vm.stateFlow.value.draft)
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
