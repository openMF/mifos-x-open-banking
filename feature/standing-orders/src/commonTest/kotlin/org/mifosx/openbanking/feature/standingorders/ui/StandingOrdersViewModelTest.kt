/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.mifosx.openbanking.feature.standingorders.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.mifosx.openbanking.core.data.accounts.AccountsRepository
import org.mifosx.openbanking.core.data.payments.PaymentsRepository
import org.mifosx.openbanking.core.data.standingorders.StandingOrdersRepository
import org.mifosx.openbanking.core.datastore.UserPreferencesRepository
import org.mifosx.openbanking.core.model.obp.Account
import org.mifosx.openbanking.core.model.obp.AmountOfMoney
import org.mifosx.openbanking.core.model.obp.Counterparty
import org.mifosx.openbanking.core.model.obp.CreateStandingOrderRequest
import org.mifosx.openbanking.core.model.obp.StandingOrder
import org.mifosx.openbanking.core.model.obp.StandingOrderDetail
import org.mifosx.openbanking.core.model.obp.TransactionRequest
import org.mifosx.openbanking.core.model.obp.TransactionRequestSummary
import org.mifosx.openbanking.core.model.user.DarkThemeConfig
import org.mifosx.openbanking.core.model.user.LanguageConfig
import org.mifosx.openbanking.core.model.user.ThemeBrand
import org.mifosx.openbanking.core.model.user.UserData
import template.core.base.store.screen.ScreenDataStream
import template.core.base.store.screen.ScreenState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun account(id: String = "ac.checking.001") = Account(
    id = id,
    bankId = "ac.bank.uk",
    accountType = "checking",
    balance = AmountOfMoney(currency = "EUR", amount = "1000.00"),
)

private fun order(name: String, status: String = StandingOrder.STATUS_ACTIVE) = StandingOrder(
    id = "so-derived-${name.lowercase()}",
    name = name,
    amountValue = "45.00",
    amountCurrency = "EUR",
    frequency = "MONTHLY",
    lastPaymentDate = "2026-06-01",
    nextPaymentDate = "2026-07-01",
    status = status,
)

private class FakeStandingOrdersRepository(
    var orders: Result<List<StandingOrder>> = Result.success(emptyList()),
) : StandingOrdersRepository {
    var lastAccountId: String = ""
    override suspend fun detail(
        bankId: String,
        accountId: String,
        standingOrderId: String,
    ): Result<StandingOrderDetail> = TODO("not used")
    override suspend fun listRecurring(bankId: String, accountId: String): Result<List<StandingOrder>> {
        lastAccountId = accountId
        return orders
    }
    override suspend fun create(
        bankId: String,
        accountId: String,
        name: String,
        request: CreateStandingOrderRequest,
    ): Result<StandingOrder> = TODO("not used")
}

private class FakeAccountsRepository(
    var accounts: Result<List<Account>> = Result.success(listOf(account())),
) : AccountsRepository {
    override fun accountsStream(scope: CoroutineScope): ScreenDataStream<List<Account>> = TODO()
    override suspend fun listAccounts(): Result<List<Account>> = accounts
    override suspend fun myAccounts(): Result<List<Account>> = accounts
    override suspend fun accountDetail(bankId: String, accountId: String): Result<Account> = TODO()
}

private class FakePaymentsRepository(
    var beneficiaries: Result<List<Counterparty>> = Result.success(emptyList()),
) : PaymentsRepository {
    override fun beneficiariesStream(accountId: String, scope: CoroutineScope): ScreenDataStream<List<Counterparty>> =
        TODO()
    override suspend fun listBeneficiaries(bankId: String, accountId: String): Result<List<Counterparty>> =
        beneficiaries
    override suspend fun listTransactionRequests(
        bankId: String,
        accountId: String,
    ): Result<List<TransactionRequestSummary>> = TODO()
    override suspend fun sendSepaPayment(
        bankId: String,
        accountId: String,
        iban: String,
        amount: String,
        currency: String,
        reference: String,
    ): Result<TransactionRequest> = TODO()
    override suspend fun sendToCounterparty(
        bankId: String,
        accountId: String,
        counterpartyId: String,
        amount: String,
        currency: String,
        reference: String,
    ): Result<TransactionRequest> = TODO()
    override suspend fun fundsAvailable(
        bankId: String,
        accountId: String,
        amount: String,
        currency: String,
    ): Result<Boolean> = TODO()
    override suspend fun sendToSandboxTan(
        bankId: String,
        accountId: String,
        toBankId: String,
        toAccountId: String,
        amount: String,
        currency: String,
        reference: String,
    ): Result<TransactionRequest> = TODO()
    override suspend fun answerChallenge(
        bankId: String,
        accountId: String,
        type: String,
        requestId: String,
        challengeId: String,
        answer: String,
    ): Result<TransactionRequest> = TODO()
}

private class FakeUserPreferencesRepository(
    defaultAccountId: String = "",
) : UserPreferencesRepository {
    private val _userData = MutableStateFlow(UserData.DEFAULT.copy(defaultAccountId = defaultAccountId))
    override val userData: StateFlow<UserData> = _userData
    override val authToken: String? = null
    override val passcode: String = ""
    override val observeLanguage: Flow<LanguageConfig> get() = TODO()
    override val observeDarkThemeConfig: Flow<DarkThemeConfig> get() = TODO()
    override val observeDynamicColorPreference: Flow<Boolean> get() = TODO()
    override val observeScreenCapturePreference: Flow<Boolean> get() = TODO()
    override val observePushNotificationsEnabled: Flow<Boolean> get() = TODO()
    override val observeTransactionAlertsEnabled: Flow<Boolean> get() = TODO()
    override val observeMarketingEnabled: Flow<Boolean> get() = TODO()
    override val observeDefaultAccountId: Flow<String> = _userData.map { it.defaultAccountId }
    override suspend fun setLanguage(language: LanguageConfig) = TODO()
    override suspend fun setThemeBrand(themeBrand: ThemeBrand) = TODO()
    override suspend fun setDarkThemeConfig(darkThemeConfig: DarkThemeConfig) = TODO()
    override suspend fun setDynamicColorPreference(useDynamicColor: Boolean) = TODO()
    override suspend fun setIsAuthenticated(isAuthenticated: Boolean) = TODO()
    override suspend fun setIsUnlocked(isUnlocked: Boolean) = TODO()
    override suspend fun setIsPasscodeEnabled(isPasscodeEnabled: Boolean) = TODO()
    override suspend fun setIsBiometricsEnabled(isBiometricsEnabled: Boolean) = TODO()
    override suspend fun setPushNotificationsEnabled(isEnabled: Boolean) = TODO()
    override suspend fun setTransactionAlertsEnabled(isEnabled: Boolean) = TODO()
    override suspend fun setMarketingEnabled(isEnabled: Boolean) = TODO()
    override suspend fun setShowOnboarding(showOnboarding: Boolean) = TODO()
    override suspend fun setFirstTimeState(firstTimeState: Boolean) = TODO()
    override suspend fun setPasscode(passcode: String) = TODO()
    override suspend fun setScreenCapturePreference(isScreenCaptureEnabled: Boolean) = TODO()
    override suspend fun setAuthToken(token: String?) = TODO()
    override suspend fun setDefaultAccountId(accountId: String) {
        _userData.value = _userData.value.copy(defaultAccountId = accountId)
    }
    override val consumerKey: String = ""
    override suspend fun setConsumerKey(key: String) { }
    override suspend fun clearUserData() = TODO()
}

class StandingOrdersViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(
        repo: FakeStandingOrdersRepository = FakeStandingOrdersRepository(),
        accounts: FakeAccountsRepository = FakeAccountsRepository(),
        payments: FakePaymentsRepository = FakePaymentsRepository(),
        prefs: FakeUserPreferencesRepository = FakeUserPreferencesRepository(),
    ) = StandingOrdersViewModel(
        standingOrdersRepository = repo,
        accountsRepository = accounts,
        paymentsRepository = payments,
        userPreferencesRepository = prefs,
    )

    @Test
    fun load_prefersPersistedDefaultAccount() = runTest(dispatcher) {
        val repo = FakeStandingOrdersRepository()
        val model = vm(
            repo = repo,
            accounts = FakeAccountsRepository(
                Result.success(
                    listOf(account(), account("ac.business.001").copy(accountType = "business")),
                ),
            ),
            prefs = FakeUserPreferencesRepository(defaultAccountId = "ac.business.001"),
        )
        backgroundScope.launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertEquals("ac.business.001", repo.lastAccountId)
    }

    @Test
    fun load_success_emitsContentWithStats() = runTest(dispatcher) {
        val model = vm(
            repo = FakeStandingOrdersRepository(
                orders = Result.success(
                    listOf(
                        order("Rent"),
                        order("Netflix"),
                        order("Gym", status = StandingOrder.STATUS_PAUSED),
                    ),
                ),
            ),
        )
        backgroundScope.launch { model.uiState.collect {} }
        backgroundScope.launch { model.header.collect {} }
        advanceUntilIdle()
        val state = model.uiState.value
        assertTrue(state is ScreenState.Content)
        assertEquals(3, state.data.size)
        val header = model.header.value
        assertEquals(2, header.activeCount)
        assertEquals(1, header.pausedCount)
        assertEquals("€90.00", header.monthlyTotal)
    }

    @Test
    fun filter_pausedShowsOnlyPausedRows() = runTest(dispatcher) {
        val model = vm(
            repo = FakeStandingOrdersRepository(
                orders = Result.success(
                    listOf(
                        order("Rent"),
                        order("Gym", status = StandingOrder.STATUS_PAUSED),
                        order("Old loan", status = StandingOrder.STATUS_CANCELLED),
                    ),
                ),
            ),
        )
        backgroundScope.launch { model.uiState.collect {} }
        backgroundScope.launch { model.header.collect {} }
        advanceUntilIdle()
        model.onFilterChanged(StandingOrderFilter.Paused)
        advanceUntilIdle()
        val state = model.uiState.value as ScreenState.Content
        assertEquals(listOf("Gym"), state.data.map { it.name })
        val header = model.header.value
        assertEquals(StandingOrderFilter.Paused, header.filter)
        assertEquals(1, header.activeCount)
        assertEquals(1, header.pausedCount)
    }

    @Test
    fun onAccountSelected_reloadsOrdersForThatAccount() = runTest(dispatcher) {
        val repo = FakeStandingOrdersRepository(orders = Result.success(listOf(order("Rent"))))
        val model = vm(
            repo = repo,
            accounts = FakeAccountsRepository(
                accounts = Result.success(listOf(account(), account(id = "ac.savings.001"))),
            ),
        )
        backgroundScope.launch { model.uiState.collect {} }
        backgroundScope.launch { model.header.collect {} }
        advanceUntilIdle()
        assertEquals("ac.checking.001", repo.lastAccountId)

        model.onAccountSelected("ac.savings.001")
        advanceUntilIdle()

        assertEquals("ac.savings.001", repo.lastAccountId)
        val header = model.header.value
        assertEquals("ac.savings.001", header.selectedAccountId)
        assertEquals(2, header.accounts.size)
    }

    @Test
    fun header_totalIsPlainSumOfActiveAmounts() = runTest(dispatcher) {
        val model = vm(
            repo = FakeStandingOrdersRepository(
                orders = Result.success(
                    listOf(
                        order("Rent").copy(amountValue = "450.00"),
                        order("Savings").copy(amountValue = "200.00"),
                        order("Coffee club").copy(amountValue = "2.00", frequency = "WEEKLY"),
                        order("Gym", status = StandingOrder.STATUS_PAUSED).copy(amountValue = "12.50"),
                    ),
                ),
            ),
        )
        backgroundScope.launch { model.uiState.collect {} }
        backgroundScope.launch { model.header.collect {} }
        advanceUntilIdle()
        assertEquals("€652.00", model.header.value.monthlyTotal)
    }

    @Test
    fun header_zerosStatsWhenLoadFails() = runTest(dispatcher) {
        val model = vm(
            repo = FakeStandingOrdersRepository(orders = Result.failure(RuntimeException("boom"))),
        )
        backgroundScope.launch { model.uiState.collect {} }
        backgroundScope.launch { model.header.collect {} }
        advanceUntilIdle()
        assertTrue(model.uiState.value is ScreenState.Error)
        val header = model.header.value
        assertEquals(1, header.accounts.size)
        assertEquals(0, header.activeCount)
        assertEquals(0, header.pausedCount)
        assertEquals("€0.00", header.monthlyTotal)
    }

    @Test
    fun load_noOrders_emitsEmpty() = runTest(dispatcher) {
        val model = vm()
        backgroundScope.launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertTrue(model.uiState.value is ScreenState.Empty)
    }

    @Test
    fun onCreateClicked_noPayees_gatesWithDialog() = runTest(dispatcher) {
        val model = vm(payments = FakePaymentsRepository(beneficiaries = Result.success(emptyList())))
        backgroundScope.launch { model.uiState.collect {} }
        advanceUntilIdle()
        model.onCreateClicked()
        advanceUntilIdle()
        assertTrue(model.createGate.value is CreateGate.NoPayees)
        model.onCreateGateConsumed()
        assertTrue(model.createGate.value is CreateGate.Idle)
    }

    @Test
    fun onCreateClicked_withPayees_signalsReadyWithAccount() = runTest(dispatcher) {
        val model = vm(
            payments = FakePaymentsRepository(
                beneficiaries = Result.success(
                    listOf(Counterparty(counterpartyId = "cp-1", name = "Payee", isBeneficiary = true)),
                ),
            ),
        )
        backgroundScope.launch { model.uiState.collect {} }
        advanceUntilIdle()
        model.onCreateClicked()
        advanceUntilIdle()
        val gate = model.createGate.value
        assertTrue(gate is CreateGate.Ready)
        assertEquals("ac.checking.001", gate.accountId)
    }

    @Test
    fun load_failure_emitsError() = runTest(dispatcher) {
        val model = vm(
            repo = FakeStandingOrdersRepository(orders = Result.failure(RuntimeException("boom"))),
        )
        backgroundScope.launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertTrue(model.uiState.value is ScreenState.Error)
    }
}
