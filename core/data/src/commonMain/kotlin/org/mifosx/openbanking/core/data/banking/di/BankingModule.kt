/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.data.banking.di

import org.koin.core.module.Module
import org.koin.dsl.module
import org.mifosx.openbanking.core.data.banking.AccountCapabilityRegistry
import org.mifosx.openbanking.core.data.banking.AccountDetailRepository
import org.mifosx.openbanking.core.data.banking.AccountsOverviewRepository
import org.mifosx.openbanking.core.data.banking.AccountsRepository
import org.mifosx.openbanking.core.data.banking.BalancesRepository
import org.mifosx.openbanking.core.data.banking.BeneficiariesRepository
import org.mifosx.openbanking.core.data.banking.ConsentDetailRepository
import org.mifosx.openbanking.core.data.banking.ConsentRevokeRepository
import org.mifosx.openbanking.core.data.banking.DirectDebitsRepository
import org.mifosx.openbanking.core.data.banking.InMemoryAccountCapabilityRegistry
import org.mifosx.openbanking.core.data.banking.ProductRepository
import org.mifosx.openbanking.core.data.banking.ProfileRepository
import org.mifosx.openbanking.core.data.banking.ScheduledPaymentsRepository
import org.mifosx.openbanking.core.data.banking.StandingOrdersRepository
import org.mifosx.openbanking.core.data.banking.StatementDetailRepository
import org.mifosx.openbanking.core.data.banking.StatementFileRepository
import org.mifosx.openbanking.core.data.banking.StatementsRepository
import org.mifosx.openbanking.core.data.banking.TransactionDetailRepository
import org.mifosx.openbanking.core.data.banking.TransactionsRepository
import org.mifosx.openbanking.core.data.banking.impl.AccountDetailRepositoryImpl
import org.mifosx.openbanking.core.data.banking.impl.AccountsOverviewRepositoryImpl
import org.mifosx.openbanking.core.data.banking.impl.AccountsRepositoryImpl
import org.mifosx.openbanking.core.data.banking.impl.BalancesRepositoryImpl
import org.mifosx.openbanking.core.data.banking.impl.BeneficiariesRepositoryImpl
import org.mifosx.openbanking.core.data.banking.impl.ConsentDetailRepositoryImpl
import org.mifosx.openbanking.core.data.banking.impl.ConsentRevokeRepositoryImpl
import org.mifosx.openbanking.core.data.banking.impl.DirectDebitsRepositoryImpl
import org.mifosx.openbanking.core.data.banking.impl.ProductRepositoryImpl
import org.mifosx.openbanking.core.data.banking.impl.ProfileRepositoryImpl
import org.mifosx.openbanking.core.data.banking.impl.ScheduledPaymentsRepositoryImpl
import org.mifosx.openbanking.core.data.banking.impl.StandingOrdersRepositoryImpl
import org.mifosx.openbanking.core.data.banking.impl.StatementDetailRepositoryImpl
import org.mifosx.openbanking.core.data.banking.impl.StatementFileRepositoryImpl
import org.mifosx.openbanking.core.data.banking.impl.StatementsRepositoryImpl
import org.mifosx.openbanking.core.data.banking.impl.TransactionDetailRepositoryImpl
import org.mifosx.openbanking.core.data.banking.impl.TransactionsRepositoryImpl
import org.mifosx.openbanking.core.data.banking.store.BankingStores
import org.mifosx.openbanking.core.data.banking.store.ConsentStores
import org.mifosx.openbanking.core.model.banking.AccountBalance
import org.mifosx.openbanking.core.model.banking.AccountBalanceLine
import org.mifosx.openbanking.core.model.banking.AccountDetail
import org.mifosx.openbanking.core.model.banking.BankAccount
import org.mifosx.openbanking.core.model.banking.BeneficiaryItem
import org.mifosx.openbanking.core.model.banking.ConsentSummary
import org.mifosx.openbanking.core.model.banking.DirectDebitsSummary
import org.mifosx.openbanking.core.model.banking.PartyProfile
import org.mifosx.openbanking.core.model.banking.ScheduledPaymentItem
import org.mifosx.openbanking.core.model.banking.StandingOrdersSummary
import org.mifosx.openbanking.core.model.banking.StatementDetail
import org.mifosx.openbanking.core.model.banking.StatementPeriod
import org.mifosx.openbanking.core.model.banking.TransactionDetail
import org.mifosx.openbanking.core.model.banking.TransactionItem
import org.mifosx.openbanking.core.store.AppStoreRegistry
import org.mifosx.openbanking.core.store.infra.StoreCacheManager
import org.mifosx.openbanking.core.store.infra.impl.StoreCacheManagerImpl
import org.mobilenativefoundation.store.store5.Store

/**
 * Wires the banking Store5 stores and repositories.
 *
 * Each store is built with [BankingStores], bound under its [AppStoreRegistry] qualifier, and
 * registered with the [StoreCacheManager] so it is cleared on logout. Repositories wrap those
 * stores; only the repository interfaces are exposed to feature modules.
 *
 * Stores and repositories are singletons.
 */
val BankingModule: Module = module {

    single<AccountCapabilityRegistry> { InMemoryAccountCapabilityRegistry() }

    single<Store<String, List<BankAccount>>>(AppStoreRegistry.Accounts) {
        BankingStores.accountsStore(aisp = get(), accountDao = get()).registerForLogout(get())
    }

    single<Store<String, AccountBalance>>(AppStoreRegistry.Balances) {
        BankingStores.balancesStore(aisp = get()).registerForLogout(get())
    }

    single<Store<String, List<TransactionItem>>>(AppStoreRegistry.Transactions) {
        BankingStores.transactionsStore(aisp = get(), transactionDao = get()).registerForLogout(get())
    }

    single<Store<String, List<TransactionDetail>>>(AppStoreRegistry.TransactionDetails) {
        BankingStores.transactionDetailsStore(aisp = get()).registerForLogout(get())
    }

    single<Store<String, AccountDetail>>(AppStoreRegistry.AccountDetail) {
        BankingStores.accountDetailStore(aisp = get()).registerForLogout(get())
    }

    single<Store<String, List<AccountBalanceLine>>>(AppStoreRegistry.BalanceLines) {
        BankingStores.balanceLinesStore(aisp = get()).registerForLogout(get())
    }

    single<Store<String, DirectDebitsSummary>>(AppStoreRegistry.DirectDebits) {
        BankingStores.directDebitsStore(aisp = get(), capabilityRegistry = get())
            .registerForLogout(get())
    }

    single<Store<String, StandingOrdersSummary>>(AppStoreRegistry.StandingOrders) {
        BankingStores.standingOrdersStore(aisp = get(), capabilityRegistry = get())
            .registerForLogout(get())
    }

    single<Store<String, List<ScheduledPaymentItem>>>(AppStoreRegistry.ScheduledPayments) {
        BankingStores.scheduledPaymentsStore(aisp = get(), capabilityRegistry = get())
            .registerForLogout(get())
    }

    single<Store<String, List<BeneficiaryItem>>>(AppStoreRegistry.Beneficiaries) {
        BankingStores.beneficiariesStore(aisp = get()).registerForLogout(get())
    }

    single<Store<String, ConsentSummary>>(AppStoreRegistry.ConsentDetail) {
        ConsentStores.consentDetailStore(aisp = get()).registerForLogout(get())
    }

    single<Store<String, PartyProfile>>(AppStoreRegistry.Party) {
        BankingStores.partyStore(aisp = get()).registerForLogout(get())
    }

    single<Store<String, List<StatementPeriod>>>(AppStoreRegistry.Statements) {
        BankingStores.statementsStore(aisp = get()).registerForLogout(get())
    }

    single<Store<String, StatementDetail>>(AppStoreRegistry.StatementDetail) {
        BankingStores.statementDetailStore(aisp = get()).registerForLogout(get())
    }

    single<Store<String, List<TransactionItem>>>(AppStoreRegistry.StatementTransactions) {
        BankingStores.statementTransactionsStore(aisp = get()).registerForLogout(get())
    }

    single<AccountsRepository> {
        AccountsRepositoryImpl(
            store = get(AppStoreRegistry.Accounts),
            networkMonitor = get(),
            fetchedAtRepository = get(),
        )
    }

    single<BalancesRepository> {
        BalancesRepositoryImpl(
            store = get(AppStoreRegistry.Balances),
            networkMonitor = get(),
            fetchedAtRepository = get(),
        )
    }

    single<AccountDetailRepository> {
        AccountDetailRepositoryImpl(
            detailStore = get(AppStoreRegistry.AccountDetail),
            balanceLinesStore = get(AppStoreRegistry.BalanceLines),
            networkMonitor = get(),
            fetchedAtRepository = get(),
        )
    }

    single<BeneficiariesRepository> {
        BeneficiariesRepositoryImpl(
            store = get(AppStoreRegistry.Beneficiaries),
            networkMonitor = get(),
            fetchedAtRepository = get(),
        )
    }

    single<ConsentDetailRepository> {
        ConsentDetailRepositoryImpl(
            store = get(AppStoreRegistry.ConsentDetail),
            networkMonitor = get(),
            fetchedAtRepository = get(),
        )
    }

    single<ConsentRevokeRepository> {
        ConsentRevokeRepositoryImpl(aisp = get())
    }

    single<DirectDebitsRepository> {
        DirectDebitsRepositoryImpl(
            store = get(AppStoreRegistry.DirectDebits),
            networkMonitor = get(),
            fetchedAtRepository = get(),
        )
    }

    single<ScheduledPaymentsRepository> {
        ScheduledPaymentsRepositoryImpl(
            store = get(AppStoreRegistry.ScheduledPayments),
            networkMonitor = get(),
            fetchedAtRepository = get(),
        )
    }

    single<StatementsRepository> {
        StatementsRepositoryImpl(
            store = get(AppStoreRegistry.Statements),
            networkMonitor = get(),
            fetchedAtRepository = get(),
        )
    }

    single<StatementFileRepository> {
        StatementFileRepositoryImpl(aisp = get())
    }

    // One-shot, storeless: product terms declare cache_strategy: none, so there is no store to
    // register for logout — every mount re-reads from the network.
    single<ProductRepository> {
        ProductRepositoryImpl(aisp = get())
    }

    single<StatementDetailRepository> {
        StatementDetailRepositoryImpl(
            detailStore = get(AppStoreRegistry.StatementDetail),
            transactionsStore = get(AppStoreRegistry.StatementTransactions),
            networkMonitor = get(),
            fetchedAtRepository = get(),
        )
    }

    single<StandingOrdersRepository> {
        StandingOrdersRepositoryImpl(
            store = get(AppStoreRegistry.StandingOrders),
            networkMonitor = get(),
            fetchedAtRepository = get(),
        )
    }

    single<ProfileRepository> {
        ProfileRepositoryImpl(
            store = get(AppStoreRegistry.Party),
            networkMonitor = get(),
            fetchedAtRepository = get(),
        )
    }

    single<TransactionDetailRepository> {
        TransactionDetailRepositoryImpl(
            store = get(AppStoreRegistry.TransactionDetails),
            networkMonitor = get(),
            fetchedAtRepository = get(),
        )
    }

    single<TransactionsRepository> {
        TransactionsRepositoryImpl(
            store = get(AppStoreRegistry.Transactions),
            aisp = get(),
            networkMonitor = get(),
            fetchedAtRepository = get(),
        )
    }

    single<AccountsOverviewRepository> {
        AccountsOverviewRepositoryImpl(
            get(AppStoreRegistry.Accounts),
            get(AppStoreRegistry.Balances),
            get(),
            get(),
        )
    }
}

/**
 * Registers a store with the [StoreCacheManager] so logout clears its cache, returning the store for
 * fluent use in a `single { … }` binding. `register` lives only on [StoreCacheManagerImpl] (not the
 * interface) per the framework's documented usage; the safe cast is a no-op if the binding is faked.
 */
private fun <K : Any, V : Any> Store<K, V>.registerForLogout(cacheManager: StoreCacheManager): Store<K, V> {
    (cacheManager as? StoreCacheManagerImpl)?.register(this)
    return this
}
