/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.mifosx.openbanking.feature.directdebits.ui

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
import org.mifosx.openbanking.core.data.directdebits.DirectDebitsRepository
import org.mifosx.openbanking.core.datastore.UserPreferencesRepository
import org.mifosx.openbanking.core.model.obp.Account
import org.mifosx.openbanking.core.model.obp.DirectDebitMandate
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun mandate(
    id: String,
    merchant: String,
    status: String = DirectDebitMandate.STATUS_ACTIVE,
) = DirectDebitMandate(
    id = id,
    merchantName = merchant,
    amountValue = "15.99",
    amountCurrency = "EUR",
    frequency = "MONTHLY",
    lastCollectionDate = "2026-06-04",
    nextCollectionDate = if (status == DirectDebitMandate.STATUS_ACTIVE) "2026-07-04" else "",
    status = status,
    mandateReference = "DD-XX-20260604",
)

private class DdFakeRepository(
    var mandates: List<DirectDebitMandate> = listOf(
        mandate("dd-netflix", "Netflix Subscription"),
        mandate("dd-ee", "EE Mobile"),
        mandate("dd-gym", "PureGym", status = DirectDebitMandate.STATUS_CANCELLED),
    ),
    var failure: Throwable? = null,
) : DirectDebitsRepository {
    var requestedAccountId: String = ""
    val cancelledIds = mutableListOf<String>()

    override suspend fun listMandates(bankId: String, accountId: String): Result<List<DirectDebitMandate>> {
        requestedAccountId = accountId
        failure?.let { return Result.failure(it) }
        return Result.success(
            mandates.map { m ->
                if (m.id in cancelledIds) {
                    m.copy(status = DirectDebitMandate.STATUS_CANCELLED, nextCollectionDate = "")
                } else {
                    m
                }
            },
        )
    }

    override suspend fun cancel(accountId: String, mandateId: String): Result<Unit> {
        cancelledIds += mandateId
        return Result.success(Unit)
    }
}

private class DdFakeAccountsRepository(
    private val accounts: List<Account> = listOf(
        Account(id = "ac.savings.001", bankId = "ac.bank.uk", accountType = "SAVINGS"),
        Account(id = "ac.checking.001", bankId = "ac.bank.uk", accountType = "CHECKING"),
    ),
) : AccountsRepository {
    override fun accountsStream(scope: CoroutineScope): ScreenDataStream<List<Account>> = TODO("not used")
    override suspend fun listAccounts(): Result<List<Account>> = TODO("not used")
    override suspend fun myAccounts(): Result<List<Account>> = Result.success(accounts)
    override suspend fun accountDetail(bankId: String, accountId: String): Result<Account> = TODO("not used")
}

private class DdFakePreferencesRepository(
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

class DirectDebitsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(
        repository: DdFakeRepository = DdFakeRepository(),
        defaultAccountId: String = "",
        bankId: String = "",
        accountId: String = "",
    ) = DirectDebitsViewModel(
        directDebitsRepository = repository,
        accountsRepository = DdFakeAccountsRepository(),
        userPreferencesRepository = DdFakePreferencesRepository(defaultAccountId),
        bankId = bankId,
        accountId = accountId,
    )

    private suspend fun TestScope.content(model: DirectDebitsViewModel): DirectDebitsContent {
        backgroundScope.launch { model.uiState.collect {} }
        advanceUntilIdle()
        val s = model.uiState.value
        assertTrue(s is ScreenState.Content, "expected Content, was $s")
        return s.data
    }

    @Test
    fun load_projectsMandatesAndActiveCount() = runTest(dispatcher) {
        val c = content(vm())
        assertEquals(3, c.mandates.size)
        assertEquals(2, c.activeCount)
        val netflix = c.mandates.first { it.merchantName == "Netflix Subscription" }
        assertEquals("€15.99 / month", netflix.amountLabel)
        assertEquals("Next: 4 Jul 2026", netflix.nextCollectionLabel)
        assertEquals("Ref: DD-XX-20260604", netflix.referenceLabel)
        assertTrue(netflix.isActive)
        val gym = c.mandates.first { it.merchantName == "PureGym" }
        assertEquals("Cancelled", gym.statusLabel)
        assertEquals("", gym.nextCollectionLabel)
    }

    @Test
    fun accountResolution_prefersPersistedDefaultOverChecking() = runTest(dispatcher) {
        val repository = DdFakeRepository()
        content(vm(repository, defaultAccountId = "ac.savings.001"))
        assertEquals("ac.savings.001", repository.requestedAccountId)
    }

    @Test
    fun accountResolution_fallsBackToCheckingAccount() = runTest(dispatcher) {
        val repository = DdFakeRepository()
        content(vm(repository))
        assertEquals("ac.checking.001", repository.requestedAccountId)
    }

    @Test
    fun routeArguments_bypassAccountResolution() = runTest(dispatcher) {
        val repository = DdFakeRepository()
        content(vm(repository, bankId = "neon.bank.eu", accountId = "neon.wallet.001"))
        assertEquals("neon.wallet.001", repository.requestedAccountId)
    }

    @Test
    fun cancelFlow_opensDialogConfirmsAndReprojects() = runTest(dispatcher) {
        val repository = DdFakeRepository()
        val model = vm(repository)
        content(model)

        model.onCancelRequested("dd-netflix")
        advanceUntilIdle()
        var c = (model.uiState.value as ScreenState.Content).data
        assertNotNull(c.cancelDialogFor)
        assertEquals("Netflix Subscription", c.cancelDialogFor?.merchantName)

        model.onCancelConfirmed()
        advanceUntilIdle()
        c = (model.uiState.value as ScreenState.Content).data
        assertNull(c.cancelDialogFor)
        assertEquals(listOf("dd-netflix"), repository.cancelledIds)
        assertEquals(1, c.activeCount)
        assertFalse(c.mandates.first { it.id == "dd-netflix" }.isActive)
    }

    @Test
    fun cancelRequestOnCancelledMandate_isIgnored() = runTest(dispatcher) {
        val model = vm()
        content(model)
        model.onCancelRequested("dd-gym")
        advanceUntilIdle()
        val c = (model.uiState.value as ScreenState.Content).data
        assertNull(c.cancelDialogFor)
    }

    @Test
    fun cancelDismissed_closesDialogWithoutSideEffects() = runTest(dispatcher) {
        val repository = DdFakeRepository()
        val model = vm(repository)
        content(model)
        model.onCancelRequested("dd-netflix")
        advanceUntilIdle()
        model.onCancelDismissed()
        advanceUntilIdle()
        val c = (model.uiState.value as ScreenState.Content).data
        assertNull(c.cancelDialogFor)
        assertTrue(repository.cancelledIds.isEmpty())
        assertEquals(2, c.activeCount)
    }

    @Test
    fun noMandates_isEmptyState() = runTest(dispatcher) {
        val model = vm(DdFakeRepository(mandates = emptyList()))
        backgroundScope.launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertTrue(model.uiState.value is ScreenState.Empty)
    }

    @Test
    fun loadFailure_isErrorAndRetryRecovers() = runTest(dispatcher) {
        val repository = DdFakeRepository(failure = IllegalStateException("boom"))
        val model = vm(repository)
        backgroundScope.launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertTrue(model.uiState.value is ScreenState.Error)

        repository.failure = null
        model.onRetry()
        advanceUntilIdle()
        assertTrue(model.uiState.value is ScreenState.Content)
    }
}
