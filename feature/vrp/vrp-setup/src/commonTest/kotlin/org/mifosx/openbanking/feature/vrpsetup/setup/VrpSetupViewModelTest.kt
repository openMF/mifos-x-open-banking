/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.vrpsetup.setup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.mifosx.openbanking.core.model.banking.AccountBalance
import org.mifosx.openbanking.core.model.banking.AccountWithBalance
import org.mifosx.openbanking.core.model.banking.BankAccount
import org.mifosx.openbanking.core.model.banking.BeneficiaryItem
import org.mifosx.openbanking.core.model.banking.BeneficiaryScheme
import org.mifosx.openbanking.core.model.callback.ConsentStatus
import org.mifosx.openbanking.core.model.hsbcProduct.AccountEndpoint
import org.mifosx.openbanking.core.model.vrp.AccountIdentity
import org.mifosx.openbanking.core.model.vrp.Money
import org.mifosx.openbanking.core.model.vrp.PeriodType
import org.mifosx.openbanking.core.model.vrp.PeriodicLimit
import org.mifosx.openbanking.core.model.vrp.VrpConsent
import org.mifosx.openbanking.core.model.vrp.VrpControlParameters
import org.mifosx.openbanking.feature.vrpsetup.AmountProblem
import org.mifosx.openbanking.feature.vrpsetup.FakeAccountCapabilityRegistry
import org.mifosx.openbanking.feature.vrpsetup.FakeAccountsOverviewRepository
import org.mifosx.openbanking.feature.vrpsetup.FakeBeneficiariesRepository
import org.mifosx.openbanking.feature.vrpsetup.FakeVrpAuthRepository
import org.mifosx.openbanking.feature.vrpsetup.FakeVrpConsentRepository
import template.core.base.common.screen.DataFreshness
import template.core.base.common.screen.ScreenState
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** A loaded screen state, at the freshness a fixture never needs to vary. */
private fun <T> contentOf(data: T): ScreenState<T> =
    ScreenState.Content(data = data, freshness = DataFreshness.FRESH)

private const val CURRENT_ACCOUNT_ID = "123456791"
private const val CREDIT_CARD_ID = "1123456842"
private const val SAVINGS_ACCOUNT_ID = "1123456841"
private const val PAYEE_IDENTIFICATION = "40478412345678"

class VrpSetupViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var accounts: FakeAccountsOverviewRepository
    private lateinit var beneficiaries: FakeBeneficiariesRepository
    private lateinit var registry: FakeAccountCapabilityRegistry
    private lateinit var consents: FakeVrpConsentRepository
    private lateinit var auth: FakeVrpAuthRepository

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        accounts = FakeAccountsOverviewRepository(contentOf(listOf(currentAccount())))
        beneficiaries = FakeBeneficiariesRepository(contentOf(listOf(payee())))
        registry = FakeAccountCapabilityRegistry()
        consents = FakeVrpConsentRepository()
        auth = FakeVrpAuthRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = VrpSetupViewModel(
        accountsOverviewRepository = accounts,
        beneficiariesRepository = beneficiaries,
        capabilityRegistry = registry,
        consents = consents,
        auth = auth,
    )

    private fun content(vm: VrpSetupViewModel) =
        assertIs<VrpSetupUiState.Content>(vm.stateFlow.value.uiState)

    // observePayers

    @Test
    fun theAccountsThatCanPayAreOffered() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        assertContentEquals(
            listOf(CURRENT_ACCOUNT_ID),
            content(vm).form.payerOptions.map { it.accountId },
        )
    }

    /** A credit card cannot express a sort code and account number, so it is never a payer. */
    @Test
    fun aCreditCardIsNotOfferedAsAPayer() = runTest {
        accounts.emit(contentOf(listOf(currentAccount(), creditCard())))
        val vm = viewModel()
        advanceUntilIdle()

        assertContentEquals(
            listOf(CURRENT_ACCOUNT_ID),
            content(vm).form.payerOptions.map { it.accountId },
        )
    }

    /** The registry is the correction: whatever the bank refused this session is dropped too. */
    @Test
    fun anAccountTheBankRefusedIsNotOfferedAsAPayer() = runTest {
        accounts.emit(contentOf(listOf(currentAccount(), savingsAccount())))
        registry.markUnsupported(SAVINGS_ACCOUNT_ID, AccountEndpoint.VrpPayer)
        val vm = viewModel()
        advanceUntilIdle()

        assertContentEquals(
            listOf(CURRENT_ACCOUNT_ID),
            content(vm).form.payerOptions.map { it.accountId },
        )
    }

    @Test
    fun noEligibleAccountIsItsOwnState() = runTest {
        accounts.emit(contentOf(listOf(creditCard())))
        val vm = viewModel()
        advanceUntilIdle()

        assertIs<VrpSetupUiState.NoEligiblePayers>(vm.stateFlow.value.uiState)
    }

    @Test
    fun retryAsksTheAccountsToBeReadAgain() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.trySendAction(VrpSetupAction.RetryLoad)
        advanceUntilIdle()

        assertEquals(1, accounts.refreshCount)
    }

    // observePayees

    @Test
    fun noPayeesAreReadUntilAPayerIsChosen() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(beneficiaries.requestedAccountIds.isEmpty())
        assertTrue(content(vm).form.payeeOptions.isEmpty())
    }

    @Test
    fun choosingAPayerReadsThatAccountsPayees() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.trySendAction(VrpSetupAction.PayerSelected(CURRENT_ACCOUNT_ID))
        advanceUntilIdle()

        assertContentEquals(listOf(CURRENT_ACCOUNT_ID), beneficiaries.requestedAccountIds)
        assertContentEquals(
            listOf(PAYEE_IDENTIFICATION),
            content(vm).form.payeeOptions.map { it.payeeId },
        )
    }

    /** Deferring to the bank leaves no payer named here, so its payees are no longer the answer. */
    @Test
    fun choosingAtTheBankEmptiesThePayees() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.trySendAction(VrpSetupAction.PayerSelected(CURRENT_ACCOUNT_ID))
        advanceUntilIdle()

        vm.trySendAction(VrpSetupAction.ChooseAtBankSelected)
        advanceUntilIdle()

        assertTrue(content(vm).form.payeeOptions.isEmpty())
    }

    @Test
    fun aPayeeIsKeyedByItsAccount() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.trySendAction(VrpSetupAction.PayerSelected(CURRENT_ACCOUNT_ID))
        advanceUntilIdle()

        assertEquals(PAYEE_IDENTIFICATION, content(vm).form.payeeOptions.single().payeeId)
    }

    // the form

    @Test
    fun payNewOpensEmptyFieldsRatherThanCarryingAPayeeAcross() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.trySendAction(VrpSetupAction.PayerSelected(CURRENT_ACCOUNT_ID))
        advanceUntilIdle()
        vm.trySendAction(VrpSetupAction.PayeeSelected(PAYEE_IDENTIFICATION))
        advanceUntilIdle()

        vm.trySendAction(VrpSetupAction.PayNewSelected)
        advanceUntilIdle()

        val form = content(vm).form
        assertTrue(form.payNewSelected)
        assertNull(form.selectedPayeeId)
        assertEquals("", form.newPayeeName)
        assertEquals("", form.newPayeeSortCode)
        assertEquals("", form.newPayeeAccountNumber)
    }

    @Test
    fun editingEitherCeilingRechecksTheOrderingRule() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.trySendAction(VrpSetupAction.PerPaymentAmountChanged("500.00"))
        vm.trySendAction(VrpSetupAction.PeriodicAmountChanged("200.00"))
        advanceUntilIdle()

        assertEquals(AmountProblem.NotBelowPeriodic, content(vm).form.perPaymentProblem)
    }

    @Test
    fun anIncompleteFormCannotContinue() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.trySendAction(VrpSetupAction.Continue)
        advanceUntilIdle()

        assertEquals(SetupPhase.Form, content(vm).phase)
    }

    @Test
    fun aCompleteFormReachesTheReview() = runTest {
        val vm = completeForm()

        vm.trySendAction(VrpSetupAction.Continue)
        advanceUntilIdle()

        assertEquals(SetupPhase.Review, content(vm).phase)
    }

    @Test
    fun goingBackFromTheReviewKeepsEveryTypedValue() = runTest {
        val vm = completeForm()
        vm.trySendAction(VrpSetupAction.Continue)
        advanceUntilIdle()

        vm.trySendAction(VrpSetupAction.BackToForm)
        advanceUntilIdle()

        val form = content(vm).form
        assertEquals(SetupPhase.Form, content(vm).phase)
        assertEquals("200.00", form.perPaymentAmount)
        assertEquals("500.00", form.periodicAmount)
        assertEquals(PAYEE_IDENTIFICATION, form.selectedPayeeId)
        assertEquals(CURRENT_ACCOUNT_ID, form.selectedPayerId)
    }

    // staging

    @Test
    fun stagingCarriesTheEnteredCeilingsAndThenAuthorises() = runTest {
        val vm = completeForm()
        consents.stageReturns(NetworkResult.Success(stagedConsent()))
        vm.trySendAction(VrpSetupAction.Continue)
        advanceUntilIdle()

        vm.trySendAction(VrpSetupAction.StageConsent)
        advanceUntilIdle()

        val draft = consents.stagedDrafts.single()
        assertEquals(20_000L, draft.controlParameters.maximumIndividualAmount.minorUnits)
        assertEquals(50_000L, draft.controlParameters.periodicLimits.single().amount.minorUnits)
        assertEquals(PAYEE_IDENTIFICATION, draft.payee.identification)
        assertContentEquals(listOf("45411"), auth.begunFor)
    }

    @Test
    fun aFailedStagingUnlocksTheReview() = runTest {
        val vm = completeForm()
        consents.stageReturns(NetworkResult.Error(NetworkError.Network(IllegalStateException("offline"))))
        vm.trySendAction(VrpSetupAction.Continue)
        advanceUntilIdle()

        vm.trySendAction(VrpSetupAction.StageConsent)
        advanceUntilIdle()

        assertEquals(false, content(vm).isStaging)
    }

    @Test
    fun stagingTwiceDoesNotCreateTwoConsents() = runTest {
        val vm = completeForm()
        consents.stageReturns(NetworkResult.Success(stagedConsent()))
        vm.trySendAction(VrpSetupAction.Continue)
        advanceUntilIdle()

        vm.trySendAction(VrpSetupAction.StageConsent)
        vm.trySendAction(VrpSetupAction.StageConsent)
        advanceUntilIdle()

        assertEquals(1, consents.stagedDrafts.size)
    }

    private fun TestScope.completeForm(): VrpSetupViewModel {
        val vm = viewModel()
        advanceUntilIdle()
        vm.trySendAction(VrpSetupAction.PayerSelected(CURRENT_ACCOUNT_ID))
        advanceUntilIdle()
        vm.trySendAction(VrpSetupAction.PayeeSelected(PAYEE_IDENTIFICATION))
        vm.trySendAction(VrpSetupAction.PerPaymentAmountChanged("200.00"))
        vm.trySendAction(VrpSetupAction.PeriodicAmountChanged("500.00"))
        advanceUntilIdle()
        return vm
    }

    private fun currentAccount() = AccountWithBalance(
        account = BankAccount(
            accountId = CURRENT_ACCOUNT_ID,
            nickname = "Everyday Current Account",
            accountSubType = "CurrentAccount",
            currency = "GBP",
            sortCode = "802001",
            accountNumber = "10204021",
            rawIdentification = "80200110204021",
        ),
        balance = AccountBalance(
            accountId = CURRENT_ACCOUNT_ID,
            currency = "GBP",
            currentAmount = "3482.19",
            availableAmount = "3482.19",
        ),
    )

    private fun savingsAccount() = AccountWithBalance(
        account = BankAccount(
            accountId = SAVINGS_ACCOUNT_ID,
            nickname = "",
            accountSubType = "Savings",
            currency = "GBP",
            sortCode = "801225",
            accountNumber = "90953695",
            rawIdentification = "80122590953695",
        ),
        balance = null,
    )

    private fun creditCard() = AccountWithBalance(
        account = BankAccount(
            accountId = CREDIT_CARD_ID,
            nickname = "",
            accountSubType = "CARD",
            currency = "GBP",
            sortCode = "",
            accountNumber = "xxxx-xxxx-xxxx-3456",
            rawIdentification = "xxxx-xxxx-xxxx-3456",
        ),
        balance = null,
    )

    private fun payee() = BeneficiaryItem(
        beneficiaryId = "",
        accountId = CURRENT_ACCOUNT_ID,
        creditorName = "Sarah Chen",
        scheme = BeneficiaryScheme.SortCode,
        identification = PAYEE_IDENTIFICATION,
        reference = "RENT",
    )

    private fun stagedConsent() = VrpConsent(
        consentId = "45411",
        status = ConsentStatus.AwaitingAuthorisation,
        createdAt = Instant.parse("2026-08-19T12:00:00Z"),
        controlParameters = VrpControlParameters(
            maximumIndividualAmount = Money(20_000L, "GBP"),
            periodicLimits = listOf(PeriodicLimit(PeriodType.Month, Money(50_000L, "GBP"))),
            interactionType = "UK.OBIE.VRPType.Sweeping",
        ),
        payee = AccountIdentity(
            schemeName = "UK.OBIE.SortCodeAccountNumber",
            identification = PAYEE_IDENTIFICATION,
            name = "Sarah Chen",
        ),
    )
}
