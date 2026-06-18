/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.mifosx.openbanking.feature.pfm.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.mifosx.openbanking.core.data.accounts.AccountsRepository
import org.mifosx.openbanking.core.data.accounts.PfmAccountsService
import org.mifosx.openbanking.core.data.fx.FxConverter
import org.mifosx.openbanking.core.data.fx.FxRepository
import org.mifosx.openbanking.core.data.pfm.BudgetsRepository
import org.mifosx.openbanking.core.data.pfm.PfmBudgets
import org.mifosx.openbanking.core.data.transactions.CounterpartyNameResolver
import org.mifosx.openbanking.core.data.transactions.TransactionsRepository
import org.mifosx.openbanking.core.datastore.UserPreferencesRepository
import org.mifosx.openbanking.core.model.obp.Account
import org.mifosx.openbanking.core.model.obp.AmountOfMoney
import org.mifosx.openbanking.core.model.obp.CounterpartyHolder
import org.mifosx.openbanking.core.model.obp.FxRate
import org.mifosx.openbanking.core.model.obp.Transaction
import org.mifosx.openbanking.core.model.obp.TransactionAttribute
import org.mifosx.openbanking.core.model.obp.TransactionCounterparty
import org.mifosx.openbanking.core.model.obp.TransactionDetails
import org.mifosx.openbanking.core.model.pfm.PfmPeriod
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val TODAY = LocalDate(2026, 6, 6)

/** Resolution is exercised by the engine's own tests; the dashboard tests fold raw names. */
private object NoopCounterpartyNameResolver : CounterpartyNameResolver {
    override suspend fun resolve(
        bankId: String,
        accountId: String,
        transactions: List<Transaction>,
    ): Map<String, String> = emptyMap()

    override suspend fun placeholderHolder(): String = ""
}

private fun pfmTxn(
    amount: String,
    date: String = "2026-06-03",
    description: String = "Payment",
    holder: String = "Acme Ltd",
    typeCode: String? = "POS",
    currency: String = "EUR",
) = Transaction(
    id = "$holder-$date-$amount",
    otherAccount = TransactionCounterparty(holder = CounterpartyHolder(name = holder)),
    details = TransactionDetails(
        description = description,
        completed = "${date}T10:00:00Z",
        value = AmountOfMoney(currency = currency, amount = amount),
    ),
    transactionAttributes = typeCode?.let { listOf(TransactionAttribute("TXN_TYPE", "STRING", it)) }.orEmpty(),
)

private fun account(
    id: String,
    type: String = "CURRENT",
    label: String = "Account $id",
    currency: String = "EUR",
) = Account(
    id = id,
    label = label,
    bankId = "ac.bank.uk",
    accountType = type,
    balance = AmountOfMoney(currency = currency, amount = "1000.00"),
)

private class PfmFakeAccountsRepository(
    var accounts: Result<List<Account>> = Result.success(listOf(account("acc-1"))),
) : AccountsRepository {
    override fun accountsStream(scope: CoroutineScope): ScreenDataStream<List<Account>> = TODO()
    override suspend fun listAccounts(): Result<List<Account>> = TODO()
    override suspend fun myAccounts(): Result<List<Account>> = accounts
    override suspend fun accountDetail(bankId: String, accountId: String): Result<Account> =
        Result.failure(IllegalStateException("no detail in fake — list shape carries the type"))
}

private class PfmFakeTransactionsRepository(
    var result: Result<List<Transaction>> = Result.success(emptyList()),
    var resultsByAccount: Map<String, Result<List<Transaction>>> = emptyMap(),
) : TransactionsRepository {
    var fetches = 0
    var fetchedAccountIds = mutableListOf<String>()
    override fun transactionsStream(
        bankId: String,
        accountId: String,
        scope: CoroutineScope,
    ): ScreenDataStream<List<Transaction>> = TODO()
    override suspend fun listTransactions(bankId: String, accountId: String, limit: Int?) = TODO()
    override suspend fun listTransactionsWithAttributes(
        bankId: String,
        accountId: String,
        limit: Int?,
    ): Result<List<Transaction>> {
        fetches++
        fetchedAccountIds += accountId
        return resultsByAccount[accountId] ?: result
    }
    override suspend fun getTransaction(bankId: String, accountId: String, transactionId: String) = TODO()
}

private class PfmFakeFxRepository(
    var rates: Map<Pair<String, String>, Double> = mapOf(("GBP" to "EUR") to 1.16278),
) : FxRepository {
    override suspend fun getRate(from: String, to: String): Result<FxRate> =
        rates[from to to]?.let { Result.success(FxRate(conversionValue = it)) }
            ?: Result.failure(IllegalStateException("no rate $from->$to"))

    override suspend fun supportedCurrencies(): List<String> = listOf("GBP", "EUR")
}

private class PfmFakeBudgetsRepository(
    var result: Result<PfmBudgets> = Result.success(PfmBudgets()),
    var saveResult: Result<Unit> = Result.success(Unit),
    var baseCurrencyResult: Result<String?> = Result.success(null),
) : BudgetsRepository {
    var saved = mutableListOf<Pair<String, Double>>()
    var savedBaseCurrencies = mutableListOf<String>()
    override suspend fun budgets(): Result<PfmBudgets> = result
    override suspend fun saveBudget(categoryId: String, amount: Double): Result<Unit> {
        saved += categoryId to amount
        return saveResult
    }
    override suspend fun baseCurrency(): Result<String?> = baseCurrencyResult
    override suspend fun saveBaseCurrency(code: String): Result<Unit> {
        savedBaseCurrencies += code
        return Result.success(Unit)
    }
}

private class PfmFakeUserPreferencesRepository(
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

class PfmDashboardViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(
        transactions: PfmFakeTransactionsRepository = PfmFakeTransactionsRepository(),
        accounts: PfmFakeAccountsRepository = PfmFakeAccountsRepository(),
        budgets: PfmFakeBudgetsRepository = PfmFakeBudgetsRepository(),
        fx: PfmFakeFxRepository = PfmFakeFxRepository(),
        defaultAccountId: String = "",
    ) = PfmDashboardViewModel(
        transactionsRepository = transactions,
        pfmAccountsService = PfmAccountsService(accounts),
        fxConverter = FxConverter(fx),
        budgetsRepository = budgets,
        userPreferencesRepository = PfmFakeUserPreferencesRepository(defaultAccountId),
        counterpartyNameResolver = NoopCounterpartyNameResolver,
        todayProvider = { TODAY },
    )

    private suspend fun TestScope.content(model: PfmDashboardViewModel): PfmContent {
        backgroundScope.launch { model.uiState.collect {} }
        advanceUntilIdle()
        val s = model.uiState.value
        assertTrue(s is ScreenState.Content, "expected Content, was $s")
        return s.data
    }

    @Test
    fun load_derivesSummaryCategoriesAndMerchants() = runTest(dispatcher) {
        val transactions = PfmFakeTransactionsRepository(
            Result.success(
                listOf(
                    pfmTxn("-75.00", description = "Tesco groceries", holder = "Tesco"),
                    pfmTxn("-25.00", holder = "Netflix"),
                    pfmTxn("3200.00", holder = "Employer", typeCode = "SAL"),
                ),
            ),
        )
        val c = content(vm(transactions))
        assertEquals(100.0, c.summary.spent)
        assertEquals(3200.0, c.summary.received)
        assertEquals("EUR", c.currency)
        assertEquals(listOf("food-dining", "entertainment"), c.categories.map { it.id })
        assertEquals("Tesco", c.topMerchants.first().name)
        assertEquals(PfmPeriod.THIS_MONTH, c.period)
        assertEquals("June 2026", c.periodLabel)
        assertTrue(c.hasActivity)
    }

    @Test
    fun load_aggregatesAcrossAllPersonalAccounts() = runTest(dispatcher) {
        val transactions = PfmFakeTransactionsRepository(
            resultsByAccount = mapOf(
                "acc-1" to Result.success(listOf(pfmTxn("-40.00", holder = "Tesco", description = "Tesco"))),
                "sav-1" to Result.success(listOf(pfmTxn("-60.00", holder = "Netflix"))),
            ),
        )
        val accounts = PfmFakeAccountsRepository(
            Result.success(listOf(account("acc-1"), account("sav-1", type = "SAVINGS"))),
        )
        val c = content(vm(transactions, accounts))
        assertEquals(2, c.accountCount)
        assertEquals(2, transactions.fetches)
        assertEquals(100.0, c.summary.spent)
    }

    @Test
    fun load_excludesBusinessAccountsFromPersonalInsights() = runTest(dispatcher) {
        val transactions = PfmFakeTransactionsRepository(
            resultsByAccount = mapOf(
                "acc-1" to Result.success(listOf(pfmTxn("-40.00"))),
                "biz-1" to Result.success(listOf(pfmTxn("-9999.00", description = "Payroll April"))),
            ),
        )
        val accounts = PfmFakeAccountsRepository(
            Result.success(listOf(account("acc-1"), account("biz-1", type = "BUSINESS"))),
        )
        val c = content(vm(transactions, accounts))
        assertEquals(1, c.accountCount)
        assertEquals(listOf("acc-1"), transactions.fetchedAccountIds)
        assertEquals(40.0, c.summary.spent)
    }

    @Test
    fun load_convertsForeignAmountsIntoBaseCurrency() = runTest(dispatcher) {
        val rate = 1.16278
        val transactions = PfmFakeTransactionsRepository(
            resultsByAccount = mapOf(
                "acc-1" to Result.success(listOf(pfmTxn("-50.00"))),
                "gbp-1" to Result.success(listOf(pfmTxn("-100.00", currency = "GBP"))),
            ),
        )
        val accounts = PfmFakeAccountsRepository(
            Result.success(listOf(account("acc-1"), account("gbp-1", currency = "GBP"))),
        )
        val c = content(vm(transactions, accounts))
        assertEquals("EUR", c.currency)
        assertEquals(50.0 + 100.0 * rate, c.summary.spent, absoluteTolerance = 0.001)
    }

    @Test
    fun load_missingFxRateFallsBackToNativeWithNotice() = runTest(dispatcher) {
        val transactions = PfmFakeTransactionsRepository(
            Result.success(listOf(pfmTxn("-100.00", currency = "GBP"))),
        )
        val fx = PfmFakeFxRepository(rates = emptyMap())
        val model = vm(transactions, fx = fx)
        val c = content(model)
        assertEquals(100.0, c.summary.spent, absoluteTolerance = 0.001)
        assertNotNull(model.notice.value)
    }

    @Test
    fun load_persistedBaseCurrencyWins() = runTest(dispatcher) {
        val budgets = PfmFakeBudgetsRepository(baseCurrencyResult = Result.success("GBP"))
        val fx = PfmFakeFxRepository(rates = mapOf(("EUR" to "GBP") to 0.860011))
        val transactions = PfmFakeTransactionsRepository(
            Result.success(listOf(pfmTxn("-100.00"))),
        )
        val c = content(vm(transactions, budgets = budgets, fx = fx))
        assertEquals("GBP", c.currency)
        assertEquals(86.0011, c.summary.spent, absoluteTolerance = 0.001)
    }

    @Test
    fun load_prefersDefaultAccountForNavigation() = runTest(dispatcher) {
        val transactions = PfmFakeTransactionsRepository()
        val accounts = PfmFakeAccountsRepository(
            Result.success(listOf(account("acc-1"), account("acc-2"))),
        )
        val c = content(vm(transactions, accounts, defaultAccountId = "acc-2"))
        assertEquals("acc-2", c.navAccountId)
    }

    @Test
    fun accountFilter_reprojectsSingleAccountWithoutRefetch() = runTest(dispatcher) {
        val transactions = PfmFakeTransactionsRepository(
            resultsByAccount = mapOf(
                "acc-1" to Result.success(listOf(pfmTxn("-40.00", holder = "Tesco", description = "Tesco"))),
                "acc-2" to Result.success(listOf(pfmTxn("-60.00", holder = "Netflix"))),
            ),
        )
        val accounts = PfmFakeAccountsRepository(
            Result.success(listOf(account("acc-1"), account("acc-2"))),
        )
        val model = vm(transactions, accounts)
        content(model)
        model.onAccountFilterSelected("acc-2")
        advanceUntilIdle()
        val c = (model.uiState.value as ScreenState.Content).data
        assertEquals("acc-2", c.selectedAccountId)
        assertEquals("Account acc-2", c.scopeLabel)
        assertEquals(60.0, c.summary.spent)
        assertEquals("acc-2", c.navAccountId)
        assertEquals(2, transactions.fetches)

        model.onAccountFilterSelected(null)
        advanceUntilIdle()
        val all = (model.uiState.value as ScreenState.Content).data
        assertNull(all.selectedAccountId)
        assertEquals(100.0, all.summary.spent)
        assertEquals(2, transactions.fetches)
    }

    @Test
    fun transfers_appearInCategoriesButNeverInBudgetRows() = runTest(dispatcher) {
        val transactions = PfmFakeTransactionsRepository(
            Result.success(listOf(pfmTxn("-500.00", typeCode = "TFR", description = "To savings"))),
        )
        val c = content(vm(transactions))
        assertEquals(listOf("transfers"), c.categories.map { it.id })
        assertTrue(c.budgetRows.none { it.categoryId == "transfers" })
    }

    @Test
    fun periodSwitch_reprojectsWithoutRefetch() = runTest(dispatcher) {
        val transactions = PfmFakeTransactionsRepository(
            Result.success(
                listOf(
                    pfmTxn("-10.00", date = "2026-06-03"),
                    pfmTxn("-99.00", date = "2026-05-10"),
                ),
            ),
        )
        val model = vm(transactions)
        content(model)
        model.onPeriodSelected(PfmPeriod.LAST_MONTH)
        advanceUntilIdle()
        val c = (model.uiState.value as ScreenState.Content).data
        assertEquals(99.0, c.summary.spent)
        assertEquals("May 2026", c.periodLabel)
        assertEquals(1, transactions.fetches)
    }

    @Test
    fun customRange_projectsWindow() = runTest(dispatcher) {
        val transactions = PfmFakeTransactionsRepository(
            Result.success(
                listOf(
                    pfmTxn("-10.00", date = "2026-06-03"),
                    pfmTxn("-99.00", date = "2026-05-10"),
                ),
            ),
        )
        val model = vm(transactions)
        content(model)
        model.onCustomRangeSelected(LocalDate(2026, 5, 1), LocalDate(2026, 5, 31))
        advanceUntilIdle()
        val c = (model.uiState.value as ScreenState.Content).data
        assertEquals(PfmPeriod.CUSTOM, c.period)
        assertEquals(99.0, c.summary.spent)
    }

    @Test
    fun budgets_mapToOverallAndRows() = runTest(dispatcher) {
        val transactions = PfmFakeTransactionsRepository(
            Result.success(listOf(pfmTxn("-75.00", description = "Tesco groceries"))),
        )
        val budgets = PfmFakeBudgetsRepository(
            Result.success(
                PfmBudgets(categoryLimits = mapOf("food-dining" to 150.0), overallLimit = 1500.0),
            ),
        )
        val c = content(vm(transactions, budgets = budgets))
        val overall = assertNotNull(c.overallBudget)
        assertEquals(1500.0, overall.limit)
        assertEquals(75.0, overall.spent)
        assertEquals(5, overall.percent)
        val food = c.budgetRows.first { it.categoryId == "food-dining" }
        assertEquals(150.0, food.limit)
        assertEquals(50, food.percent)
    }

    @Test
    fun budgetMeter_ignoresTransfersAndUntaggedButCountsCash() = runTest(dispatcher) {
        val transactions = PfmFakeTransactionsRepository(
            Result.success(
                listOf(
                    pfmTxn("-75.00", description = "Tesco groceries"),
                    pfmTxn("-500.00", typeCode = "TFR", description = "To savings"),
                    pfmTxn("-40.00", typeCode = null, description = "Mystery payment", holder = "zz"),
                    pfmTxn("-60.00", typeCode = "ATM", description = "Cash withdrawal", holder = "zz"),
                ),
            ),
        )
        val budgets = PfmFakeBudgetsRepository(
            Result.success(PfmBudgets(overallLimit = 1000.0)),
        )
        val c = content(vm(transactions, budgets = budgets))
        assertEquals(675.0, c.summary.spent)
        assertEquals(135.0, c.overallBudget?.spent)
        assertEquals(14, c.overallBudget?.percent)
    }

    @Test
    fun noOverallBudget_isNull() = runTest(dispatcher) {
        val c = content(vm())
        assertNull(c.overallBudget)
    }

    @Test
    fun saveBudget_persistsAndRefreshes() = runTest(dispatcher) {
        val budgets = PfmFakeBudgetsRepository()
        val model = vm(budgets = budgets)
        content(model)
        budgets.result = Result.success(PfmBudgets(overallLimit = 2000.0))
        model.onSaveBudget("overall", "2000")
        advanceUntilIdle()
        assertEquals(listOf("overall" to 2000.0), budgets.saved)
        val c = (model.uiState.value as ScreenState.Content).data
        assertEquals(2000.0, c.overallBudget?.limit)
    }

    @Test
    fun saveBudget_invalidAmountShowsNoticeWithoutCall() = runTest(dispatcher) {
        val budgets = PfmFakeBudgetsRepository()
        val model = vm(budgets = budgets)
        content(model)
        model.onSaveBudget("overall", "abc")
        advanceUntilIdle()
        assertNotNull(model.notice.value)
        assertTrue(budgets.saved.isEmpty())
    }

    @Test
    fun load_accountsFailureIsErrorThenRetryRecovers() = runTest(dispatcher) {
        val accounts = PfmFakeAccountsRepository(Result.failure(RuntimeException("boom")))
        val model = vm(accounts = accounts)
        backgroundScope.launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertTrue(model.uiState.value is ScreenState.Error)

        accounts.accounts = Result.success(listOf(account("acc-1")))
        model.onRetry()
        advanceUntilIdle()
        assertTrue(model.uiState.value is ScreenState.Content)
    }

    @Test
    fun budgetsFailure_stillLoadsContent() = runTest(dispatcher) {
        val budgets = PfmFakeBudgetsRepository(Result.failure(RuntimeException("boom")))
        val c = content(vm(budgets = budgets))
        assertNull(c.overallBudget)
    }
}
