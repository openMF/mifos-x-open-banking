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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.mifosx.openbanking.core.model.banking.payment.ConsentType
import org.mifosx.openbanking.feature.paymentconsent.FakePaymentAuthRepository
import org.mifosx.openbanking.feature.paymentconsent.FakePaymentHistoryRepository
import org.mifosx.openbanking.feature.paymentconsent.FakeScheduledPaymentInitiationRepository
import org.mifosx.openbanking.feature.paymentconsent.FakeSinglePaymentInitiationRepository
import org.mifosx.openbanking.feature.paymentconsent.FakeStandingOrderInitiationRepository
import org.mifosx.openbanking.feature.paymentconsent.PaymentConsentFixtures
import org.mifosx.openbanking.feature.paymentconsent.standingOrderDraftFixture
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The return leg when the authorisation that comes back is a **standing order**.
 *
 * The third product to arrive through this screen, and the one that forced the branch to stop being
 * an ordered probe of draft slots. That shape let call order stand in for the answer: whichever
 * repository was asked first and had something staged won, whether or not it was the product that
 * actually authorised. With two products it was right by luck; with three it is a coin toss.
 *
 * Nothing here fails to compile if it is wrong. It surfaces only against the live bank, after the
 * customer has already approved at the browser — which is the whole reason these cases exist.
 */
class PaymentConsentStandingOrderTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun standingOrderAuthRepository(): FakePaymentAuthRepository =
        FakePaymentAuthRepository().apply { pendingType = ConsentType.DomesticStandingOrder }

    private fun viewModel(
        repository: FakePaymentAuthRepository = standingOrderAuthRepository(),
        payments: FakeSinglePaymentInitiationRepository = FakeSinglePaymentInitiationRepository(),
        scheduled: FakeScheduledPaymentInitiationRepository = FakeScheduledPaymentInitiationRepository(),
        history: FakePaymentHistoryRepository = FakePaymentHistoryRepository(),
        standingOrder: FakeStandingOrderInitiationRepository =
            FakeStandingOrderInitiationRepository(staged = standingOrderDraftFixture()),
    ) = PaymentConsentViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(PaymentConsentViewModel.REDIRECT_URL_ARG to PaymentConsentFixtures.REDIRECT_URL),
        ),
        repository = repository,
        paymentInitiationRepository = payments,
        scheduledPaymentInitiationRepository = scheduled,
        standingOrderInitiationRepository = standingOrder,
        paymentHistoryRepository = history,
    )

    /**
     * The single most important assertion in this file.
     *
     * An empty `fundsChecks` log is the only observable proof that the standing-order branch was
     * taken. The absence is more absolute than on the scheduled rails, where an endpoint at least
     * exists and is unsupported: OBIE defines no funds-confirmation sub-resource for either
     * standing-order consent, so a call here would be a request the bank has no route for at all.
     */
    @Test
    fun aStandingOrderConsentNeverConfirmsFunds() = runTest {
        val payments = FakeSinglePaymentInitiationRepository()

        viewModel(payments = payments)

        assertTrue(payments.fundsChecks.isEmpty(), "a standing order has no funds confirmation to make")
    }

    @Test
    fun theStagedMandateIsTheOneSubmitted() = runTest {
        val standingOrder = FakeStandingOrderInitiationRepository(staged = standingOrderDraftFixture())

        viewModel(standingOrder = standingOrder)

        assertEquals(1, standingOrder.submittedDrafts.size)
        assertEquals("2026-08-20", standingOrder.submittedDrafts.single().firstPaymentDate)
        assertEquals(listOf(PaymentConsentFixtures.CONSENT_ID), standingOrder.submittedConsentIds)
    }

    /**
     * The mandate must not reach either sibling repository.
     *
     * This is the assertion the ordered probe could never make. Submitting a standing order through
     * the scheduled path would send it to `domestic-scheduled-payments`, which would either refuse it
     * or accept something that is not the instruction the customer authorised.
     */
    @Test
    fun noOtherProductIsSubmitted() = runTest {
        val payments = FakeSinglePaymentInitiationRepository()
        val scheduled = FakeScheduledPaymentInitiationRepository()

        viewModel(payments = payments, scheduled = scheduled)

        assertTrue(payments.submittedDrafts.isEmpty(), "the immediate repository must not be used")
        assertTrue(scheduled.submittedDrafts.isEmpty(), "the scheduled repository must not be used")
    }

    /**
     * A mandate whose type is recorded but whose draft is gone is terminal, not a prompt to rebuild.
     *
     * The consent was granted against a specific `Initiation`; anything reconstructed here would be a
     * different mandate wearing the same consent.
     */
    @Test
    fun aMissingMandateFailsRatherThanFallingBackToAnotherProduct() = runTest {
        val payments = FakeSinglePaymentInitiationRepository()

        val vm = viewModel(
            payments = payments,
            standingOrder = FakeStandingOrderInitiationRepository(staged = null),
        )

        val state = vm.stateFlow.value.uiState
        assertTrue(state is PaymentConsentUiState.Error, "a missing mandate is terminal")
        assertEquals(PaymentConsentErrorKind.NoStagedPayment, state.kind)
        assertTrue(payments.fundsChecks.isEmpty(), "and must not fall through to the immediate path")
    }

    /**
     * A refused submission still leaves a row behind, recorded under the mandate's own shape.
     *
     * The hub is the only record the app keeps that the customer tried: a standing order cannot be
     * found again through the AIS read side, which returns no `StandingOrderId` to correlate on. A
     * failure written under a payment's shape would also give the hub the wrong endpoint to refresh
     * it from.
     */
    @Test
    fun aFailedMandateIsRecordedUnderItsOwnShape() = runTest {
        val history = FakePaymentHistoryRepository()
        val standingOrder = FakeStandingOrderInitiationRepository(
            staged = standingOrderDraftFixture(),
        ).apply {
            submissionReturns(NetworkResult.Error(NetworkError.Client.BadRequest("refused")))
        }

        viewModel(history = history, standingOrder = standingOrder)

        assertEquals(1, history.standingOrderFailures.size, "a failed mandate must leave a row behind")
        assertTrue(history.failures.isEmpty(), "and not under the immediate shape")
        assertTrue(history.scheduledFailures.isEmpty(), "nor the scheduled one")
    }

    /**
     * A successful submission writes no history row from here.
     *
     * Deliberate, and worth pinning: the repository records the row as it submits, so that both
     * rails agree on what is saved. Asserting a row here would pass only against a ViewModel that had
     * taken that responsibility back — which is the duplication this arrangement removed.
     */
    @Test
    fun aSubmittedMandateIsRecordedByTheRepositoryNotThisScreen() = runTest {
        val history = FakePaymentHistoryRepository()
        val standingOrder = FakeStandingOrderInitiationRepository(staged = standingOrderDraftFixture())

        viewModel(history = history, standingOrder = standingOrder)

        assertEquals(1, standingOrder.submittedDrafts.size, "the mandate reached the repository")
        assertTrue(history.submittedStandingOrders.isEmpty(), "and this screen wrote no row itself")
    }
}
