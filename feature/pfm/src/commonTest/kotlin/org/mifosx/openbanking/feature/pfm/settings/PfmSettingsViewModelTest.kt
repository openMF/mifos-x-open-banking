/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.mifosx.openbanking.feature.pfm.settings

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
import org.mifosx.openbanking.core.data.accounts.AccountsRepository
import org.mifosx.openbanking.core.data.accounts.PfmAccountsService
import org.mifosx.openbanking.core.data.pfm.BudgetsRepository
import org.mifosx.openbanking.core.data.pfm.PfmBudgets
import org.mifosx.openbanking.core.datastore.UserPreferencesRepository
import org.mifosx.openbanking.core.model.obp.Account
import org.mifosx.openbanking.core.model.obp.AmountOfMoney
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
import kotlin.test.assertTrue

private fun settingsAccount(id: String, type: String = "CURRENT", currency: String = "EUR") = Account(
    id = id,
    label = "Account $id",
    bankId = "ac.bank.uk",
    accountType = type,
    balance = AmountOfMoney(currency = currency, amount = "1000.00"),
)

private class SettingsFakeAccountsRepository(
    var accounts: Result<List<Account>> = Result.success(listOf(settingsAccount("acc-1"))),
) : AccountsRepository {
    override fun accountsStream(scope: CoroutineScope): ScreenDataStream<List<Account>> = TODO()
    override suspend fun listAccounts(): Result<List<Account>> = TODO()
    override suspend fun myAccounts(): Result<List<Account>> = accounts
    override suspend fun accountDetail(bankId: String, accountId: String): Result<Account> =
        Result.failure(IllegalStateException("no detail in fake"))
}

private class SettingsFakeBudgetsRepository(
    var baseCurrencyResult: Result<String?> = Result.success(null),
    var saveBaseResult: Result<Unit> = Result.success(Unit),
) : BudgetsRepository {
    var savedBaseCurrencies = mutableListOf<String>()
    override suspend fun budgets(): Result<PfmBudgets> = Result.success(PfmBudgets())
    override suspend fun saveBudget(categoryId: String, amount: Double): Result<Unit> = TODO()
    override suspend fun baseCurrency(): Result<String?> = baseCurrencyResult
    override suspend fun saveBaseCurrency(code: String): Result<Unit> {
        savedBaseCurrencies += code
        return saveBaseResult
    }
}

private class SettingsFakeUserPreferencesRepository(
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
    override suspend fun setDefaultAccountId(accountId: String) = TODO()
    override val consumerKey: String = ""
    override suspend fun setConsumerKey(key: String) { }
    override suspend fun clearUserData() = TODO()
}

class PfmSettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(
        accounts: SettingsFakeAccountsRepository = SettingsFakeAccountsRepository(),
        budgets: SettingsFakeBudgetsRepository = SettingsFakeBudgetsRepository(),
        defaultAccountId: String = "",
    ) = PfmSettingsViewModel(
        pfmAccountsService = PfmAccountsService(accounts),
        budgetsRepository = budgets,
        userPreferencesRepository = SettingsFakeUserPreferencesRepository(defaultAccountId),
    )

    private suspend fun TestScope.content(model: PfmSettingsViewModel): PfmSettingsContent {
        backgroundScope.launch { model.uiState.collect {} }
        advanceUntilIdle()
        val s = model.uiState.value
        assertTrue(s is ScreenState.Content, "expected Content, was $s")
        return s.data
    }

    @Test
    fun load_optionsAreDistinctPersonalCurrencies() = runTest(dispatcher) {
        val accounts = SettingsFakeAccountsRepository(
            Result.success(
                listOf(
                    settingsAccount("acc-1"),
                    settingsAccount("acc-2", currency = "GBP"),
                    settingsAccount("biz-1", type = "BUSINESS", currency = "USD"),
                ),
            ),
        )
        val c = content(vm(accounts))
        assertEquals(listOf("EUR", "GBP"), c.options)
    }

    @Test
    fun load_selectionFallsBackToDefaultAccountCurrency() = runTest(dispatcher) {
        val accounts = SettingsFakeAccountsRepository(
            Result.success(
                listOf(settingsAccount("acc-1"), settingsAccount("acc-2", currency = "GBP")),
            ),
        )
        val c = content(vm(accounts, defaultAccountId = "acc-2"))
        assertEquals("GBP", c.selected)
    }

    @Test
    fun load_persistedBaseCurrencyWins() = runTest(dispatcher) {
        val budgets = SettingsFakeBudgetsRepository(baseCurrencyResult = Result.success("GBP"))
        val c = content(vm(budgets = budgets))
        assertEquals("GBP", c.selected)
    }

    @Test
    fun selectCurrency_persistsAndUpdatesSelection() = runTest(dispatcher) {
        val accounts = SettingsFakeAccountsRepository(
            Result.success(
                listOf(settingsAccount("acc-1"), settingsAccount("acc-2", currency = "GBP")),
            ),
        )
        val budgets = SettingsFakeBudgetsRepository()
        val model = vm(accounts, budgets)
        content(model)
        model.onCurrencySelected("GBP")
        advanceUntilIdle()
        assertEquals(listOf("GBP"), budgets.savedBaseCurrencies)
        assertEquals("GBP", (model.uiState.value as ScreenState.Content).data.selected)
        assertNotNull(model.notice.value)
    }

    @Test
    fun selectCurrency_failureShowsNotice() = runTest(dispatcher) {
        val budgets = SettingsFakeBudgetsRepository(saveBaseResult = Result.failure(RuntimeException("boom")))
        val model = vm(budgets = budgets)
        val before = content(model).selected
        model.onCurrencySelected("GBP")
        advanceUntilIdle()
        assertEquals(before, (model.uiState.value as ScreenState.Content).data.selected)
        assertNotNull(model.notice.value)
    }
}
