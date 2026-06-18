/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.mifosx.openbanking.feature.sendmoney.ui

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
import org.mifosx.openbanking.core.datastore.UserPreferencesRepository
import org.mifosx.openbanking.core.model.obp.Account
import org.mifosx.openbanking.core.model.obp.AmountOfMoney
import org.mifosx.openbanking.core.model.obp.Bank
import org.mifosx.openbanking.core.model.obp.Counterparty
import org.mifosx.openbanking.core.model.obp.TransactionRequest
import org.mifosx.openbanking.core.model.obp.TransactionRequestDetails
import org.mifosx.openbanking.core.model.obp.TransactionRequestSummary
import org.mifosx.openbanking.core.model.obp.TransactionRequestToSandboxTan
import org.mifosx.openbanking.core.model.obp.TransactionRequestToSepa
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

private class HubFakeAccountsRepository(
    var accounts: Result<List<Account>>,
) : AccountsRepository {
    override fun accountsStream(scope: CoroutineScope): ScreenDataStream<List<Account>> = TODO()
    override suspend fun listAccounts(): Result<List<Account>> = accounts
    override suspend fun myAccounts(): Result<List<Account>> = accounts
    override suspend fun accountDetail(bankId: String, accountId: String): Result<Account> =
        accounts.map { list -> list.first { it.accountIdOrId == accountId } }
}

private class HubFakePaymentsRepository(
    var beneficiaries: Result<List<Counterparty>> = Result.success(emptyList()),
    var requests: Result<List<TransactionRequestSummary>> = Result.success(emptyList()),
) : PaymentsRepository {
    var listRequestsCalls = 0

    override fun beneficiariesStream(
        accountId: String,
        scope: CoroutineScope,
    ): ScreenDataStream<List<Counterparty>> = TODO()
    override suspend fun listBeneficiaries(bankId: String, accountId: String): Result<List<Counterparty>> =
        beneficiaries
    override suspend fun listTransactionRequests(
        bankId: String,
        accountId: String,
    ): Result<List<TransactionRequestSummary>> {
        listRequestsCalls++
        return requests
    }
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

private class HubFakeBanksRepository : BanksRepository {
    override suspend fun bankName(bankId: String): String = "Afternoon Coffee Bank"
    override suspend fun bank(bankId: String): Bank? = null
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
    override suspend fun setDefaultAccountId(accountId: String) = TODO()
    override val consumerKey: String = ""
    override suspend fun setConsumerKey(key: String) { }
    override suspend fun clearUserData() = TODO()
}

private fun rajAccount() = Account(
    id = "ac.raj.savings.001",
    bankId = "ac.bank.uk",
    label = "Raj Savings",
    balance = AmountOfMoney(currency = "EUR", amount = "500"),
)

/** External (IBAN) beneficiary — paid via the SEPA rail. */
private fun rajIbanPayee() = Counterparty(
    counterpartyId = "cp-iban",
    name = "SEPA Probe Hosted raj",
    otherAccountRoutingScheme = "IBAN",
    otherAccountRoutingAddress = "DE89370400440532099999",
    isBeneficiary = true,
)

/** In-bank OBP-hosted beneficiary — paid via the SANDBOX_TAN rail (routing address = OBP account id). */
private fun rajObpPayee() = Counterparty(
    counterpartyId = "cp-obp",
    name = "SEPA Probe Settleable",
    otherBankRoutingScheme = "OBP",
    otherBankRoutingAddress = "ac.bank.uk",
    otherAccountRoutingScheme = "OBP",
    otherAccountRoutingAddress = "ac.savings.001",
    isBeneficiary = true,
)

private fun sepaRequest(date: String) = TransactionRequestSummary(
    status = "COMPLETED",
    startDate = date,
    details = TransactionRequestDetails(toSepa = TransactionRequestToSepa(iban = "DE89370400440532099999")),
)

private fun sandboxTanRequest(date: String) = TransactionRequestSummary(
    status = "COMPLETED",
    startDate = date,
    details = TransactionRequestDetails(
        toSandboxTan = TransactionRequestToSandboxTan(bankId = "ac.bank.uk", accountId = "ac.savings.001"),
    ),
)

class SendMoneyHubViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(payments: HubFakePaymentsRepository) = SendMoneyHubViewModel(
        accountsRepository = HubFakeAccountsRepository(Result.success(listOf(rajAccount()))),
        paymentsRepository = payments,
        banksRepository = HubFakeBanksRepository(),
        userPreferencesRepository = FakeUserPreferencesRepository(),
    )

    private fun content(model: SendMoneyHubViewModel): SendMoneyHubContent =
        (model.uiState.value as ScreenState.Content).data

    /**
     * Regression for the real sandbox bug: raj paid two beneficiaries — one IBAN payee (SEPA rail)
     * and one OBP-hosted payee (SANDBOX_TAN rail) — but only the IBAN one appeared in "recent",
     * because recency matching ignored the SANDBOX_TAN rail. Both must now appear, sandbox-paid first.
     */
    @Test
    fun recentRecipients_includesSandboxTanInBankPayee() = runTest(dispatcher) {
        val payments = HubFakePaymentsRepository(
            beneficiaries = Result.success(listOf(rajIbanPayee(), rajObpPayee())),
            requests = Result.success(
                listOf(
                    sepaRequest("2026-06-06T00:00:00Z"),
                    sandboxTanRequest("2026-06-10T00:00:00Z"),
                ),
            ),
        )
        val model = vm(payments)
        backgroundScope.launch(dispatcher) { model.uiState.collect {} }
        advanceUntilIdle()

        val recents = content(model).recentRecipients
        assertEquals(2, recents.size)
        assertTrue(recents.any { it.counterpartyId == "cp-obp" }, "SANDBOX_TAN payee must appear in recents")
        assertEquals("cp-obp", recents.first().counterpartyId)
    }

    /** onRefresh re-fetches — the hub reloads when it re-enters after a completed payment. */
    @Test
    fun onRefresh_refetchesTransactionRequests() = runTest(dispatcher) {
        val payments = HubFakePaymentsRepository(
            beneficiaries = Result.success(listOf(rajIbanPayee())),
            requests = Result.success(listOf(sepaRequest("2026-06-06T00:00:00Z"))),
        )
        val model = vm(payments)
        backgroundScope.launch(dispatcher) { model.uiState.collect {} }
        advanceUntilIdle()
        val afterInit = payments.listRequestsCalls

        model.onRefresh()
        advanceUntilIdle()

        assertTrue(payments.listRequestsCalls > afterInit, "onRefresh should trigger another load")
    }
}
