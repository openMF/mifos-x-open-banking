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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.mifosx.openbanking.core.model.banking.payment.PaymentRail
import org.mifosx.openbanking.feature.paymentsschedulepayment.FakeAccountCapabilityRegistry
import org.mifosx.openbanking.feature.paymentsschedulepayment.FakeAccountsOverviewRepository
import org.mifosx.openbanking.feature.paymentsschedulepayment.FakeBeneficiariesRepository
import org.mifosx.openbanking.feature.paymentsschedulepayment.FakePaymentHistoryRepository
import org.mifosx.openbanking.feature.paymentsschedulepayment.FakePaymentStatusRepository
import org.mifosx.openbanking.feature.paymentsschedulepayment.FakeScheduledPaymentInitiationRepository
import org.mifosx.openbanking.feature.paymentsschedulepayment.SchedulePaymentFixtures
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
class SchedulePaymentViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** 2026-08-12, a Wednesday. Matches [SchedulePaymentFixtures.TODAY]. */
    private fun clockAt(date: LocalDate = SchedulePaymentFixtures.TODAY): Clock =
        object : Clock {
            override fun now(): Instant = Instant.parse("${date}T09:00:00Z")
        }

    private fun viewModel(
        accounts: FakeAccountsOverviewRepository = FakeAccountsOverviewRepository(),
        payees: FakeBeneficiariesRepository = FakeBeneficiariesRepository(),
        payments: FakeScheduledPaymentInitiationRepository = FakeScheduledPaymentInitiationRepository(),
        clock: Clock = clockAt(),
    ) = SchedulePaymentViewModel(
        accountsOverviewRepository = accounts,
        beneficiariesRepository = payees,
        paymentInitiationRepository = payments,
        capabilityRegistry = FakeAccountCapabilityRegistry(),
        paymentHistoryRepository = FakePaymentHistoryRepository(),
        paymentStatusRepository = FakePaymentStatusRepository(),
        clock = clock,
    )

    private fun content(vm: SchedulePaymentViewModel): SchedulePaymentUiState.Content =
        assertIs(vm.stateFlow.value.uiState)

    /** Fills everything except the date, so the date's own effect on `canReview` is isolated. */
    private fun SchedulePaymentViewModel.completeFormWithoutDate() {
        trySendAction(SchedulePaymentAction.SelectDebtorAccount(SchedulePaymentFixtures.CURRENT_ACCOUNT_ID))
        trySendAction(SchedulePaymentAction.SelectCreditor(SchedulePaymentFixtures.JAMESON_ID))
        trySendAction(SchedulePaymentAction.EnterAmount("850"))
    }

    private fun SchedulePaymentViewModel.completeForm(
        date: LocalDate = SchedulePaymentFixtures.EXECUTION_DATE,
    ) {
        completeFormWithoutDate()
        trySendAction(SchedulePaymentAction.SelectExecutionDate(date))
    }

    // region — The date itself

    @Test
    fun opensWithNoDateChosen() = runTest {
        val state = content(viewModel())

        assertNull(state.executionDate)
        assertTrue(state.executionDateLabel.isEmpty())
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

        vm.trySendAction(SchedulePaymentAction.SelectExecutionDate(SchedulePaymentFixtures.EXECUTION_DATE))

        val state = content(vm)
        assertEquals(SchedulePaymentFixtures.EXECUTION_DATE, state.executionDate)
        assertEquals(SchedulePaymentFixtures.EXECUTION_DATE_LABEL, state.executionDateLabel)
    }

    /** The rule is re-checked here, not trusted from the control that produced it. */
    @Test
    fun aDateOutsideTheWindowIsRefusedEvenIfThePickerOffersIt() = runTest {
        val vm = viewModel()

        vm.trySendAction(SchedulePaymentAction.SelectExecutionDate(SchedulePaymentFixtures.TODAY))

        assertNull(content(vm).executionDate, "today is refused U003 and must not be accepted here")
    }

    // endregion

    // region — The dialog

    @Test
    fun openingThePickerShowsIt() = runTest {
        val vm = viewModel()

        vm.trySendAction(SchedulePaymentAction.OpenDatePicker)

        assertTrue(content(vm).datePickerVisible)
    }

    /** Dismissing is not clearing: a date already chosen survives a cancel. */
    @Test
    fun dismissingThePickerKeepsAnyDateAlreadyChosen() = runTest {
        val vm = viewModel()
        vm.trySendAction(SchedulePaymentAction.SelectExecutionDate(SchedulePaymentFixtures.EXECUTION_DATE))

        vm.trySendAction(SchedulePaymentAction.OpenDatePicker)
        vm.trySendAction(SchedulePaymentAction.DismissDatePicker)

        val state = content(vm)
        assertFalse(state.datePickerVisible)
        assertEquals(SchedulePaymentFixtures.EXECUTION_DATE, state.executionDate)
    }

    @Test
    fun choosingADateClosesThePicker() = runTest {
        val vm = viewModel()
        vm.trySendAction(SchedulePaymentAction.OpenDatePicker)

        vm.trySendAction(SchedulePaymentAction.SelectExecutionDate(SchedulePaymentFixtures.EXECUTION_DATE))

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
        var current = SchedulePaymentFixtures.TODAY
        val movingClock = object : Clock {
            override fun now(): Instant = Instant.parse("${current}T09:00:00Z")
        }
        val vm = viewModel(clock = movingClock)
        assertEquals(SchedulePaymentFixtures.TODAY, content(vm).today)

        current = nextDay
        vm.trySendAction(SchedulePaymentAction.OpenDatePicker)

        assertEquals(nextDay, content(vm).today, "the window must be measured again on every open")
    }

    /** And a date the moved window no longer allows is dropped rather than carried silently. */
    @Test
    fun aDateThatMidnightInvalidatedIsClearedWhenThePickerReopens() = runTest {
        var current = SchedulePaymentFixtures.TODAY
        val movingClock = object : Clock {
            override fun now(): Instant = Instant.parse("${current}T09:00:00Z")
        }
        val vm = viewModel(clock = movingClock)
        val tomorrow = LocalDate(2026, 8, 13)
        vm.trySendAction(SchedulePaymentAction.SelectExecutionDate(tomorrow))
        assertEquals(tomorrow, content(vm).executionDate)

        // The chosen date is now today, which the bank counts as past.
        current = tomorrow
        vm.trySendAction(SchedulePaymentAction.OpenDatePicker)

        assertNull(content(vm).executionDate)
    }

    // endregion

    // region — The rail, which changes which dates are legal

    /**
     * The trap this feature adds.
     *
     * A Saturday is selectable on the domestic rail and refused on the international one, so a date
     * chosen under one rail can be illegal under the other. Carrying it silently would stage a date
     * the customer was shown as acceptable.
     */
    @Test
    fun switchingToInternationalClearsAWeekendDate() = runTest {
        val vm = viewModel()
        val saturday = LocalDate(2026, 8, 15)
        vm.trySendAction(SchedulePaymentAction.SelectExecutionDate(saturday))
        assertEquals(saturday, content(vm).executionDate)

        vm.trySendAction(SchedulePaymentAction.SelectRail(PaymentRail.International))

        assertNull(content(vm).executionDate, "a Saturday is refused on the international rail")
    }

    /** A date both rails accept survives the switch — re-picking it would be a cost with no reason. */
    @Test
    fun switchingRailKeepsADateBothRailsAccept() = runTest {
        val vm = viewModel()
        vm.trySendAction(SchedulePaymentAction.SelectExecutionDate(SchedulePaymentFixtures.EXECUTION_DATE))

        vm.trySendAction(SchedulePaymentAction.SelectRail(PaymentRail.International))

        assertEquals(SchedulePaymentFixtures.EXECUTION_DATE, content(vm).executionDate)
    }

    // endregion

    // region — Staging

    /** The date reaches the wire as a bare ISO date, which is what keeps the two bodies identical. */
    @Test
    fun stagingCarriesTheChosenDateAsAnIsoDate() = runTest {
        val payments = FakeScheduledPaymentInitiationRepository()
        val vm = viewModel(payments = payments)
        vm.completeForm()

        vm.trySendAction(SchedulePaymentAction.ConfirmAndStageConsent)

        assertEquals("2026-08-14", payments.stagedDrafts.single().requestedExecutionDate)
    }

    @Test
    fun stagingMintsTwoDistinctIdempotencyKeys() = runTest {
        val payments = FakeScheduledPaymentInitiationRepository()
        val vm = viewModel(payments = payments)
        vm.completeForm()

        vm.trySendAction(SchedulePaymentAction.ConfirmAndStageConsent)

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
        val payments = FakeScheduledPaymentInitiationRepository()
        val vm = viewModel(payments = payments)
        vm.completeForm()

        vm.trySendAction(SchedulePaymentAction.ConfirmAndStageConsent)

        assertTrue(payments.submittedDrafts.isEmpty())
    }

    @Test
    fun stagingRaisesTheAuthorisationEvent() = runTest {
        val vm = viewModel()
        vm.completeForm()

        vm.trySendAction(SchedulePaymentAction.ConfirmAndStageConsent)

        assertIs<SchedulePaymentUiState.Submitting>(vm.stateFlow.value.uiState)
    }

    /**
     * Nothing is staged without a date, even if the action is dispatched directly.
     *
     * `canReview` keeps the button disabled, but the guard is repeated in `buildDraft` because a
     * disabled control is a UI property and this is the last point before the instruction is fixed.
     */
    @Test
    fun confirmingWithNoDateStagesNothing() = runTest {
        val payments = FakeScheduledPaymentInitiationRepository()
        val vm = viewModel(payments = payments)
        vm.completeFormWithoutDate()

        vm.trySendAction(SchedulePaymentAction.ConfirmAndStageConsent)

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
        vm.trySendAction(SchedulePaymentAction.ConfirmAndStageConsent)
        assertIs<SchedulePaymentUiState.Submitting>(vm.stateFlow.value.uiState)

        vm.trySendAction(SchedulePaymentAction.AbandonAuthorisation)

        assertEquals(SchedulePaymentStep.Review, content(vm).step)
    }

    /** Abandoning drops the draft: the next attempt is a fresh instruction under fresh keys. */
    @Test
    fun abandoningAuthorisationDropsTheDraft() = runTest {
        val vm = viewModel()
        vm.completeForm()
        vm.trySendAction(SchedulePaymentAction.ConfirmAndStageConsent)

        vm.trySendAction(SchedulePaymentAction.AbandonAuthorisation)

        assertNull(vm.stateFlow.value.draft)
    }

    /** Editing keeps the date — going back to change the amount is not a reason to re-pick a day. */
    @Test
    fun goingBackToEditKeepsTheDate() = runTest {
        val vm = viewModel()
        vm.completeForm()
        vm.trySendAction(SchedulePaymentAction.ReviewPayment)

        vm.trySendAction(SchedulePaymentAction.BackStep)

        val state = content(vm)
        assertEquals(SchedulePaymentStep.Form, state.step)
        assertEquals(SchedulePaymentFixtures.EXECUTION_DATE, state.executionDate)
    }

    // endregion
}
