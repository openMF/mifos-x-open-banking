/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.mifosx.openbanking.feature.beneficiaries.ui

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
import org.mifosx.openbanking.core.data.banks.BanksRepository
import org.mifosx.openbanking.core.data.payments.PaymentsRepository
import org.mifosx.openbanking.core.data.transactions.TransactionsRepository
import org.mifosx.openbanking.core.datastore.UserPreferencesRepository
import org.mifosx.openbanking.core.model.obp.Account
import org.mifosx.openbanking.core.model.obp.AmountOfMoney
import org.mifosx.openbanking.core.model.obp.Bank
import org.mifosx.openbanking.core.model.obp.Counterparty
import org.mifosx.openbanking.core.model.obp.Transaction
import org.mifosx.openbanking.core.model.obp.TransactionDetails
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

private class FakeAccountsRepository(
    var accounts: Result<List<Account>> = Result.success(emptyList()),
) : AccountsRepository {
    override fun accountsStream(scope: CoroutineScope): ScreenDataStream<List<Account>> = TODO()
    override suspend fun listAccounts(): Result<List<Account>> = accounts
    override suspend fun myAccounts(): Result<List<Account>> = accounts
    override suspend fun accountDetail(bankId: String, accountId: String): Result<Account> =
        accounts.map { list -> list.firstOrNull { it.accountIdOrId == accountId } ?: account() }
}

private class FakePaymentsRepository(
    var beneficiaries: Result<List<Counterparty>> = Result.success(emptyList()),
    var lastBankId: String = "",
    var lastAccountId: String = "",
) : PaymentsRepository {
    override fun beneficiariesStream(
        accountId: String,
        scope: CoroutineScope,
    ): ScreenDataStream<List<Counterparty>> = TODO()
    override suspend fun listBeneficiaries(bankId: String, accountId: String): Result<List<Counterparty>> {
        lastBankId = bankId
        lastAccountId = accountId
        return beneficiaries
    }
    override suspend fun listTransactionRequests(
        bankId: String,
        accountId: String,
    ): Result<List<TransactionRequestSummary>> = Result.success(emptyList())
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

private class FakeTransactionsRepository(
    var transactions: Result<List<Transaction>> = Result.success(emptyList()),
) : TransactionsRepository {
    override fun transactionsStream(
        bankId: String,
        accountId: String,
        scope: CoroutineScope,
    ): ScreenDataStream<List<Transaction>> = TODO()
    override suspend fun listTransactions(bankId: String, accountId: String, limit: Int?): Result<List<Transaction>> =
        transactions
    override suspend fun listTransactionsWithAttributes(
        bankId: String,
        accountId: String,
        limit: Int?,
    ): Result<List<Transaction>> = transactions
    override suspend fun getTransaction(bankId: String, accountId: String, transactionId: String): Result<Transaction> =
        TODO()
}

private class FakeBanksRepository(
    private val names: Map<String, String> = emptyMap(),
) : BanksRepository {
    override suspend fun bankName(bankId: String): String = names[bankId] ?: bankId
    override suspend fun bank(bankId: String): Bank? = null
}

private fun account(id: String = "ac.checking.001") =
    Account(id = id, bankId = "ac.bank.uk", balance = AmountOfMoney(currency = "GBP"))

private fun beneficiary(
    id: String,
    name: String,
    bankAddr: String = "ac.bank.uk",
    isBeneficiary: Boolean = true,
) = Counterparty(
    counterpartyId = id,
    name = name,
    otherBankRoutingScheme = "OBP",
    otherBankRoutingAddress = bankAddr,
    otherAccountRoutingAddress = "GB29NWBK60161331926819",
    isBeneficiary = isBeneficiary,
)

private fun txn(description: String, posted: String, amount: String) =
    Transaction(
        details = TransactionDetails(
            description = description,
            posted = posted,
            value = AmountOfMoney(amount = amount, currency = "GBP"),
        ),
    )

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

class BeneficiariesViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(
        accounts: Result<List<Account>> = Result.success(listOf(account())),
        beneficiaries: Result<List<Counterparty>> = Result.success(emptyList()),
        transactions: Result<List<Transaction>> = Result.success(emptyList()),
        bankNames: Map<String, String> = emptyMap(),
        payments: FakePaymentsRepository = FakePaymentsRepository(beneficiaries),
        prefs: FakeUserPreferencesRepository = FakeUserPreferencesRepository(),
    ) = BeneficiariesViewModel(
        paymentsRepository = payments,
        banksRepository = FakeBanksRepository(bankNames),
        transactionsRepository = FakeTransactionsRepository(transactions),
        accountsRepository = FakeAccountsRepository(accounts),
        userPreferencesRepository = prefs,
    )

    @Test
    fun load_prefersPersistedDefaultAccount() = runTest(dispatcher) {
        val payments = FakePaymentsRepository(Result.success(emptyList()))
        val model = vm(
            accounts = Result.success(listOf(account(), account("acc-2"))),
            payments = payments,
            prefs = FakeUserPreferencesRepository(defaultAccountId = "acc-2"),
        )
        backgroundScope.launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertEquals("acc-2", payments.lastAccountId)
    }

    @Test
    fun load_success_emitsContentWithResolvedBankName() = runTest(dispatcher) {
        val model = vm(
            beneficiaries = Result.success(listOf(beneficiary("b1", "TechStart Ltd"))),
            bankNames = mapOf("ac.bank.uk" to "Afternoon Coffee Bank"),
        )
        backgroundScope.launch { model.uiState.collect {} }
        advanceUntilIdle()

        val state = model.uiState.value
        assertTrue(state is ScreenState.Content)
        val row = state.data.all.single()
        assertEquals("TechStart Ltd", row.name)
        assertEquals("Afternoon Coffee Bank", row.bankName)
    }

    @Test
    fun load_filtersOutNonBeneficiaries() = runTest(dispatcher) {
        val model = vm(
            beneficiaries = Result.success(
                listOf(
                    beneficiary("b1", "Real Payee", isBeneficiary = true),
                    beneficiary("b2", "Not A Payee", isBeneficiary = false),
                ),
            ),
        )
        backgroundScope.launch { model.uiState.collect {} }
        advanceUntilIdle()
        val content = model.uiState.value as ScreenState.Content
        assertEquals(1, content.data.all.size)
        assertEquals("Real Payee", content.data.all.single().name)
    }

    @Test
    fun load_accountWithNoBeneficiaries_emitsContentNotEmpty() = runTest(dispatcher) {
        val model = vm(beneficiaries = Result.success(emptyList()))
        backgroundScope.launch { model.uiState.collect {} }
        advanceUntilIdle()
        val content = model.uiState.value
        assertTrue(content is ScreenState.Content)
        assertTrue(content.data.all.isEmpty())
        assertEquals("ac.checking.001", content.data.selectedAccountId)
    }

    @Test
    fun load_noAccounts_emitsEmpty() = runTest(dispatcher) {
        val model = vm(accounts = Result.success(emptyList()))
        backgroundScope.launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertTrue(model.uiState.value is ScreenState.Empty)
    }

    @Test
    fun onAccountSelected_reloadsSelectedAccountBeneficiaries() = runTest(dispatcher) {
        val payments = FakePaymentsRepository(Result.success(listOf(beneficiary("b1", "First Acct Payee"))))
        val model = vm(
            accounts = Result.success(
                listOf(
                    account(id = "acc-1"),
                    Account(id = "acc-2", bankId = "mifos-x-openbank", balance = AmountOfMoney(currency = "GBP")),
                ),
            ),
            payments = payments,
        )
        backgroundScope.launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertEquals("acc-1", (model.uiState.value as ScreenState.Content).data.selectedAccountId)

        payments.beneficiaries = Result.success(listOf(beneficiary("b2", "Second Acct Payee")))
        model.onAccountSelected("acc-2")
        advanceUntilIdle()

        val content = model.uiState.value as ScreenState.Content
        assertEquals("acc-2", content.data.selectedAccountId)
        assertEquals("mifos-x-openbank", payments.lastBankId)
        assertEquals("acc-2", payments.lastAccountId)
        assertEquals("Second Acct Payee", content.data.all.single().name)
    }

    @Test
    fun load_accountsFailure_emitsError() = runTest(dispatcher) {
        val model = vm(accounts = Result.failure(RuntimeException("boom")))
        backgroundScope.launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertTrue(model.uiState.value is ScreenState.Error)
    }

    @Test
    fun lastPayment_derivedFromTransactions() = runTest(dispatcher) {
        val model = vm(
            beneficiaries = Result.success(listOf(beneficiary("b1", "Costa Coffee"))),
            transactions = Result.success(
                listOf(
                    txn("Costa Coffee", "2026-05-01T10:00:00Z", "-3.00"),
                    txn("Costa Coffee", "2026-06-01T10:00:00Z", "-5.40"),
                ),
            ),
        )
        backgroundScope.launch { model.uiState.collect {} }
        advanceUntilIdle()
        val row = (model.uiState.value as ScreenState.Content).data.all.single()
        assertEquals("£5.40", row.lastPayment?.amount)
        assertEquals("1 Jun 2026", row.lastPayment?.date)
        assertEquals(1, (model.uiState.value as ScreenState.Content).data.recentlyUsed.size)
    }

    @Test
    fun search_filtersAllList_butKeepsContent() = runTest(dispatcher) {
        val model = vm(
            beneficiaries = Result.success(
                listOf(beneficiary("b1", "Costa Coffee"), beneficiary("b2", "Tesco Express")),
            ),
        )
        backgroundScope.launch { model.uiState.collect {} }
        advanceUntilIdle()
        model.onSearchQueryChanged("tesco")
        advanceUntilIdle()
        val content = model.uiState.value as ScreenState.Content
        assertEquals(1, content.data.all.size)
        assertEquals("Tesco Express", content.data.all.single().name)
    }

    @Test
    fun sort_descendingReversesOrder() = runTest(dispatcher) {
        val model = vm(
            beneficiaries = Result.success(
                listOf(beneficiary("b1", "Alpha"), beneficiary("b2", "Zulu")),
            ),
        )
        backgroundScope.launch { model.uiState.collect {} }
        advanceUntilIdle()
        model.onSortChanged(SortOrder.AlphaDescending)
        advanceUntilIdle()
        val content = model.uiState.value as ScreenState.Content
        assertEquals("Zulu", content.data.all.first().name)
    }
}
