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
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import org.mifosx.openbanking.core.model.banking.payment.PaymentDisposition
import org.mifosx.openbanking.core.model.banking.payment.PaymentStatus
import org.mifosx.openbanking.feature.paymentstatus.FakePaymentHistoryRepository
import org.mifosx.openbanking.feature.paymentstatus.FakePaymentStatusRepository
import org.mifosx.openbanking.feature.paymentstatus.PaymentStatusFixtures
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * How this screen reads a **scheduled** payment, which is a different thing from a slow one.
 *
 * The distinction matters because the screen's whole vocabulary — settling, settled, completed — was
 * written for a payment already on its way. A scheduled payment has been accepted and is doing
 * nothing at all until its date, and the app has no way to observe the moment it executes: OBIE
 * publishes no per-execution status, and the AIS fallback refuses the account types these land on.
 *
 * So every assertion here is about restraint: state the date, state the last status the bank gave,
 * and claim nothing further.
 */
class PaymentStatusScheduledTest {

    private val clock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-08-06T14:25:00Z")
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = PaymentStatusViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(PaymentStatusViewModel.PAYMENT_ID_ARG to PaymentStatusFixtures.PAYMENT_ID),
        ),
        repository = FakePaymentStatusRepository(receipt = PaymentStatusFixtures.scheduledReceipt()),
        paymentHistoryRepository = FakePaymentHistoryRepository(),
        clock = clock,
        // Fixed, so the date assertions below do not change meaning on a machine in another zone.
        timeZone = TimeZone.UTC,
    )

    private fun content(): PaymentStatusUiState.Content =
        assertIs(viewModel().stateFlow.value.uiState)

    /**
     * The assertion this suite exists for.
     *
     * `Current` renders as "Settling now". A payment whose date is a week away is not settling, and
     * saying so would be the screen's most consequential lie — it is the difference between "your
     * money is moving" and "nothing has happened yet".
     */
    @Test
    fun aScheduledPaymentIsNotDescribedAsSettling() {
        val completed = content().timeline.first { it.step == PaymentTimelineStep.Completed }

        assertEquals(PaymentStepState.Pending, completed.state)
        assertNotEquals(PaymentStepState.Current, completed.state, "nothing is settling before the date")
    }

    /** And the final stage carries no timestamp, because nothing has happened to stamp. */
    @Test
    fun theFinalStageIsUndated() {
        val completed = content().timeline.first { it.step == PaymentTimelineStep.Completed }

        assertTrue(completed.timestamp.isEmpty())
    }

    /**
     * `INCO` resolves rather than falling through to `Unknown`.
     *
     * Before it was added, it landed on `Unknown` — also `InProgress`, so nothing looked broken —
     * and the hub would have re-read every scheduled payment on every refresh for up to a year while
     * the status never moved.
     */
    @Test
    fun theInitiationCompletedStatusResolves() {
        val state = content()

        assertEquals(PaymentStatus.InitiationCompleted, state.status)
        assertEquals(PaymentDisposition.InProgress, state.disposition)
    }

    /**
     * The date is the one the customer chose, rendered without a time.
     *
     * It comes from `requestedExecutionDateTime`. The obvious-looking source, `settlementDateTime`,
     * is returned by both scheduled rails equal to the creation timestamp — so reading it would
     * report a payment due next week as having settled today.
     */
    @Test
    fun theRequestedDateIsShownAsADateAndNotAnInstant() {
        assertEquals("14 Aug 2026", content().scheduledForAt)
    }

    /** And no settlement row at all, because the bank has stated no settlement. */
    @Test
    fun noSettlementDateIsClaimed() {
        assertTrue(content().settledAt.isEmpty(), "the scheduled rails never state a real settlement date")
    }
}
