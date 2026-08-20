/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.sendmoney.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.mifosx.openbanking.core.data.util.RemoteException
import org.mifosx.openbanking.feature.sendmoney.FakeAccountCapabilityRegistry
import org.mifosx.openbanking.feature.sendmoney.FakeAccountsOverviewRepository
import org.mifosx.openbanking.feature.sendmoney.FakeBeneficiariesRepository
import org.mifosx.openbanking.feature.sendmoney.FakeSinglePaymentInitiationRepository
import org.mifosx.openbanking.feature.sendmoney.SendMoneyFixtures
import template.core.base.common.screen.DataFreshness
import template.core.base.common.screen.ScreenState
import template.core.base.network.NetworkError
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * What a saved-payee read that did not succeed becomes on screen.
 *
 * Split from [SendMoneyViewModelTest] for the same reason [SendMoneyRailTest] was: it is one
 * coherent question. Three outcomes of one stream — a list, no list, and no answer — used to
 * collapse into two, and the missing one is the case the bank actually produced.
 *
 * The failure this exists for was first-hand: HSBC answered `403` on `aisp/…/beneficiaries` for
 * every account, every time, while `accounts`, `balances` and `transactions` all returned `200` on
 * the same token. `content()` mapped every non-`Content` state to `emptyList()` and `renderForm`
 * switches only on the **accounts** stream, so the refusal reached the customer as "No saved
 * payees" — a claim about their own bank that was not true.
 */
class SendMoneyPayeeLoadTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val registry = FakeAccountCapabilityRegistry()

    private fun viewModel(
        accounts: FakeAccountsOverviewRepository = FakeAccountsOverviewRepository(),
        beneficiaries: FakeBeneficiariesRepository = FakeBeneficiariesRepository(),
    ) = SendMoneyViewModel(accounts, beneficiaries, FakeSinglePaymentInitiationRepository(), registry)

    private fun content(vm: SendMoneyViewModel): SendMoneyUiState.Content =
        assertIs<SendMoneyUiState.Content>(vm.stateFlow.value.uiState)

    private fun failing(state: ScreenState<Nothing>) = FakeBeneficiariesRepository(initial = state)

    /** The refusal is carried through as a failure rather than flattened into an empty list. */
    @Test
    fun aRefusedPayeeReadIsDistinguishedFromAnEmptyList() = runTest {
        val vm = viewModel(
            beneficiaries = failing(ScreenState.Error(RemoteException(NetworkError.Client.Forbidden(null)))),
        )

        vm.trySendAction(SendMoneyAction.SelectDebtorAccount(SendMoneyFixtures.CURRENT_ACCOUNT_ID))

        val state = content(vm)
        assertTrue(state.payeesFailed)
        assertFalse(state.hasBeneficiaries)
        // Not the "choose a payer first" case either: a payer IS chosen, and the read failed anyway.
        assertFalse(state.payeesUnavailable)
    }

    /**
     * Losing the network mid-form is the same shape of answer and gets the same notice.
     *
     * Proves the flag is about the read not succeeding rather than about one error code.
     */
    @Test
    fun aPayeeReadThatCouldNotReachTheBankIsAlsoAFailure() = runTest {
        val vm = viewModel(beneficiaries = failing(ScreenState.NoNetwork()))

        vm.trySendAction(SendMoneyAction.SelectDebtorAccount(SendMoneyFixtures.CURRENT_ACCOUNT_ID))

        assertTrue(content(vm).payeesFailed)
    }

    /**
     * `403` is an authorisation refusal, which `DecisionEngine` maps to `Unauthenticated` — and it
     * stays an inline payee failure rather than escalating to the full-screen `TokenExpired` that
     * the accounts stream raises for the same state.
     *
     * The evidence says the token is fine: every other AIS read succeeded on it, so "your
     * authorisation expired" would be false and its recovery — a browser round-trip — would not fix
     * it. `ErrorCategory.categorize` folds `401` and `403` together, so this state cannot tell an
     * expiry from a narrowed consent and must not assert the more alarming reading. And a real
     * expiry escalates on its own: the accounts stream runs on the same credential and `renderForm`
     * turns its `Unauthenticated` into `TokenExpired`. Escalating here would only pre-empt that by
     * taking away a form the PSU can still finish by hand.
     */
    @Test
    fun anUnauthorisedPayeeReadStaysInlineRatherThanTakingTheWholeScreen() = runTest {
        val vm = viewModel(beneficiaries = failing(ScreenState.Unauthenticated))

        vm.trySendAction(SendMoneyAction.SelectDebtorAccount(SendMoneyFixtures.CURRENT_ACCOUNT_ID))

        val state = content(vm)
        assertTrue(state.payeesFailed)
        assertEquals(SendMoneyStep.Form, state.step)
    }

    /** And the accounts stream still escalates, so a genuine expiry is not swallowed by the above. */
    @Test
    fun anUnauthorisedAccountsReadStillTakesTheWholeScreen() = runTest {
        val vm = viewModel(accounts = FakeAccountsOverviewRepository(initial = ScreenState.Unauthenticated))

        val state = assertIs<SendMoneyUiState.Error>(vm.stateFlow.value.uiState)
        assertEquals(SendMoneyErrorKind.TokenExpired, state.kind)
    }

    /** A list that loaded and is genuinely empty is not a failure — that is the other notice. */
    @Test
    fun anEmptyPayeeListIsNotAFailure() = runTest {
        val beneficiaries = FakeBeneficiariesRepository(
            initial = ScreenState.Content(emptyList(), DataFreshness.FRESH),
        )
        val vm = viewModel(beneficiaries = beneficiaries)

        vm.trySendAction(SendMoneyAction.SelectDebtorAccount(SendMoneyFixtures.CURRENT_ACCOUNT_ID))

        val state = content(vm)
        assertFalse(state.payeesFailed)
        assertFalse(state.hasBeneficiaries)
    }

    /**
     * The read is running, and that is a third answer rather than the absence of one.
     *
     * Every assertion here is about what the screen must NOT say. An empty list with no
     * explanation renders as "No saved payees", so the in-flight window told the customer
     * something about their own account that the bank had not yet been asked.
     */
    @Test
    fun aReadStillInFlightIsLoadingRatherThanAnEmptyList() = runTest {
        val beneficiaries = failing(ScreenState.Loading)
        val vm = viewModel(beneficiaries = beneficiaries)

        vm.trySendAction(SendMoneyAction.SelectDebtorAccount(SendMoneyFixtures.CURRENT_ACCOUNT_ID))

        val state = content(vm)
        assertTrue(state.payeesLoading)
        // Neither of the other two notices, both of which are claims the bank has not made yet.
        assertFalse(state.payeesFailed)
        assertFalse(state.payeesUnavailable)
        // And no list, so nothing downstream can mistake this for a loaded empty account.
        assertFalse(state.hasBeneficiaries)
    }

    /**
     * **The trap.** No payer chosen is NOT loading, however the stream happens to be seeded.
     *
     * `beneficiariesScreen` starts at `Loading` and the blank-id branch short-circuits to
     * `Content(emptyList())`, so at first paint the stream can read `Loading` while nothing has
     * been asked for. The form already explains that case — "choose an account to pay from" — and a
     * shimmer under that notice would be the screen saying two different things at once, one of
     * them untrue. This is the pairing [retryingThePayeesDoesNothingWithoutAPayer] also guards.
     */
    @Test
    fun noPayerChosenIsNotTreatedAsLoading() = runTest {
        val vm = viewModel(beneficiaries = failing(ScreenState.Loading))

        val state = content(vm)
        assertTrue(state.payeesUnavailable)
        assertFalse(state.payeesLoading)
    }

    /**
     * Payees arriving ends the wait.
     *
     * The first of four terminal outcomes, each asserted on its own below: a spinner that never
     * stops is worse than the state it was added to fix.
     */
    @Test
    fun payeesArrivingClearsTheLoadingFlag() = runTest {
        val beneficiaries = failing(ScreenState.Loading)
        val vm = viewModel(beneficiaries = beneficiaries)
        vm.trySendAction(SendMoneyAction.SelectDebtorAccount(SendMoneyFixtures.CURRENT_ACCOUNT_ID))
        assertTrue(content(vm).payeesLoading)

        beneficiaries.emit(ScreenState.Content(SendMoneyFixtures.beneficiaries(), DataFreshness.FRESH))

        val state = content(vm)
        assertFalse(state.payeesLoading)
        assertTrue(state.hasBeneficiaries)
    }

    /** An account with nothing saved against it ends it too, and gets the "no payees" notice. */
    @Test
    fun anEmptyAnswerClearsTheLoadingFlag() = runTest {
        val beneficiaries = failing(ScreenState.Loading)
        val vm = viewModel(beneficiaries = beneficiaries)
        vm.trySendAction(SendMoneyAction.SelectDebtorAccount(SendMoneyFixtures.CURRENT_ACCOUNT_ID))

        beneficiaries.emit(ScreenState.Content(emptyList(), DataFreshness.FRESH))

        val state = content(vm)
        assertFalse(state.payeesLoading)
        assertFalse(state.payeesFailed)
        assertFalse(state.hasBeneficiaries)
    }

    /** And a refusal ends it, handing over to the failure card rather than sitting there. */
    @Test
    fun aRefusalClearsTheLoadingFlag() = runTest {
        val beneficiaries = failing(ScreenState.Loading)
        val vm = viewModel(beneficiaries = beneficiaries)
        vm.trySendAction(SendMoneyAction.SelectDebtorAccount(SendMoneyFixtures.CURRENT_ACCOUNT_ID))

        beneficiaries.emit(ScreenState.Error(RemoteException(NetworkError.Client.Forbidden(null))))

        val state = content(vm)
        assertFalse(state.payeesLoading)
        assertTrue(state.payeesFailed)
    }

    /**
     * Deselecting the payer ends it as well, which is the outcome with no answer at all.
     *
     * Letting the bank choose drops the account, and beneficiaries are account-scoped — so the read
     * that was in flight is no longer for anything. Without this the shimmer would outlive the
     * question it was asked, under a notice explaining that there is no account to ask about.
     */
    @Test
    fun losingThePayerEndsTheWaitRatherThanLeavingItRunning() = runTest {
        val beneficiaries = failing(ScreenState.Loading)
        val vm = viewModel(beneficiaries = beneficiaries)
        vm.trySendAction(SendMoneyAction.SelectDebtorAccount(SendMoneyFixtures.CURRENT_ACCOUNT_ID))
        assertTrue(content(vm).payeesLoading)

        vm.trySendAction(SendMoneyAction.LetBankChoosePayer)

        val state = content(vm)
        assertFalse(state.payeesLoading)
        assertTrue(state.payeesUnavailable)
    }
}
