/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.store

import template.core.base.store.infra.StoreRegistry

/**
 * Application-level [StoreRegistry] — the single named-qualifier registry for every
 * `org.mobilenativefoundation.store.store5.Store` the app exposes.
 *
 * OBP banking stores are registered in Phase 3 (one Store5 qualifier per OBP service),
 * e.g.:
 *
 * ```kotlin
 * object AppStoreRegistry : StoreRegistry() {
 *     val Accounts     = store("accounts")
 *     val Transactions = store("transactions")
 * }
 * ```
 *
 * Then reference the qualifier from Koin DI in [appStoreModule]:
 *
 * ```kotlin
 * single<Store<AccountId, Account>>(qualifier = AppStoreRegistry.Accounts) { ... }
 * ```
 *
 * Centralizing here gives a one-place audit of every Store the app owns and prevents
 * qualifier-name collisions across feature modules.
 */
object AppStoreRegistry : StoreRegistry() {
    val Accounts = store("accounts")
    val AccountDetail = store("accountDetail")
    val Balances = store("balances")
    val BalanceLines = store("balanceLines")
    val Transactions = store("transactions")
    val TransactionDetails = store("transactionDetails")
    val Beneficiaries = store("beneficiaries")
    val ConsentDetail = store("consentDetail")
    val DirectDebits = store("directDebits")
    val ScheduledPayments = store("scheduledPayments")
    val StandingOrders = store("standingOrders")
    val Party = store("party")
    val Statements = store("statements")
    val StatementDetail = store("statementDetail")
    val StatementTransactions = store("statementTransactions")

    val OpenSourceLicence = store("openSourceLicence")
}
