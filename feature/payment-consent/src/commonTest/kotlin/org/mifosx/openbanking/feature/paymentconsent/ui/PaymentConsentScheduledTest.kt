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
import org.mifosx.openbanking.feature.paymentconsent.scheduledDraftFixture
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The return leg when the authorisation that comes back is a **scheduled** payment.
 *
 * Split into its own file rather than added to [PaymentConsentViewModelTest], which sits at detekt's
 * `LargeClass` bound, and because this is a distinct journey: authorised straight to created, with
 * no funds confirmation in between.
 *
 * Nothing in this branch fails to compile if it is wrong. Before these cases existed, a scheduled
 * consent reaching this screen would have called a funds-confirmation endpoint that does not exist on
 * either scheduled rail — and it would only have shown up against the live bank, after the customer
 * had already authorised at the browser.
 */
class PaymentConsentScheduledTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        repository: FakePaymentAuthRepository = scheduledAuthRepository(),
        payments: FakeSinglePaymentInitiationRepository = FakeSinglePaymentInitiationRepository(),
        history: FakePaymentHistoryRepository = FakePaymentHistoryRepository(),
        scheduled: FakeScheduledPaymentInitiationRepository =
            FakeScheduledPaymentInitiationRepository(staged = scheduledDraftFixture()),
        standingOrder: FakeStandingOrderInitiationRepository = FakeStandingOrderInitiationRepository(),
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
     * The return leg now chooses its repository from the recorded consent type, so a scheduled test
     * has to say it is scheduled.
     *
     * That is the change this suite exists to protect. The previous shape asked each repository in
     * turn and took whichever answered first, which happened to be right here and would have been
     * wrong the moment a third product staged a draft.
     */
    private fun scheduledAuthRepository(): FakePaymentAuthRepository =
        FakePaymentAuthRepository().apply { pendingType = ConsentType.DomesticScheduledPayment }

    /**
     * The single most important assertion in this feature.
     *
     * An empty `fundsChecks` log is the only observable proof that the scheduled branch was taken.
     * There is no endpoint to confirm funds against on either scheduled rail, so a call here would be
     * a request the bank has no answer for — made after the customer had already approved.
     */
    @Test
    fun aScheduledConsentNeverConfirmsFunds() = runTest {
        val payments = FakeSinglePaymentInitiationRepository()

        viewModel(payments = payments)

        assertTrue(
            payments.fundsChecks.isEmpty(),
            "a scheduled payment must not ask for funds confirmation — no such endpoint exists",
        )
    }

    @Test
    fun aScheduledConsentSubmitsTheStagedScheduledDraft() = runTest {
        val scheduled = FakeScheduledPaymentInitiationRepository(staged = scheduledDraftFixture())

        viewModel(scheduled = scheduled)

        assertEquals(1, scheduled.submittedDrafts.size)
        assertEquals("2026-08-14", scheduled.submittedDrafts.single().requestedExecutionDate)
        assertEquals(listOf(PaymentConsentFixtures.CONSENT_ID), scheduled.submittedConsentIds)
    }

    @Test
    fun aScheduledConsentNeverSubmitsThroughTheImmediateRepository() = runTest {
        val payments = FakeSinglePaymentInitiationRepository()

        viewModel(payments = payments)

        assertTrue(payments.submittedDrafts.isEmpty(), "the immediate rail must not see this payment")
    }

    @Test
    fun aSubmittedScheduledPaymentRaisesTheHandoffEvent() = runTest {
        val vm = viewModel()

        val event = vm.eventFlow.first()

        assertEquals(
            PaymentConsentEvent.PaymentSubmitted(PaymentConsentFixtures.PAYMENT_ID),
            event,
        )
    }

    /**
     * A failed scheduled payment still writes a history row.
     *
     * `fail()` reads both draft shapes before discarding the session. Reading only the immediate one
     * would leave this row unwritten — silently, with no error and nothing in the hub where a failure
     * belongs.
     */
    @Test
    fun aFailedScheduledSubmissionIsStillRecorded() = runTest {
        val history = FakePaymentHistoryRepository()
        val scheduled = FakeScheduledPaymentInitiationRepository(staged = scheduledDraftFixture()).apply {
            submissionReturns(NetworkResult.Error(NetworkError.Client.BadRequest("refused")))
        }

        viewModel(history = history, scheduled = scheduled)

        assertEquals(1, history.scheduledFailures.size, "a scheduled failure must leave a row behind")
        assertTrue(history.failures.isEmpty(), "and must not be recorded against the immediate shape")
    }

    /**
     * The regression guard: an immediate consent must still confirm funds.
     *
     * The pending type is what selects the branch now, so this states it rather than relying on the
     * scheduled repository having nothing staged. Under the previous ordered probe those two were
     * the same thing; they are not, and conflating them is what let a third product break the chain.
     */
    @Test
    fun anImmediateConsentStillConfirmsFundsBeforeSubmitting() = runTest {
        val payments = FakeSinglePaymentInitiationRepository()

        viewModel(
            repository = FakePaymentAuthRepository()
                .apply { pendingType = ConsentType.DomesticSinglePayment },
            payments = payments,
            scheduled = FakeScheduledPaymentInitiationRepository(staged = null),
        )

        assertEquals(1, payments.fundsChecks.size)
        assertEquals(1, payments.submittedDrafts.size)
    }
}
