/*
 * Copyright 2024 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.database.di

import org.koin.core.module.Module
import org.koin.dsl.module
import org.mifosx.openbanking.core.database.AppDatabase

/**
 * Koin module that provides the [AppDatabase] instance and all DAO singletons.
 *
 * Delegates platform-specific database construction to [platformModule], which each
 * source set (`androidMain`, `desktopMain`, `nativeMain`, `jsMain`, `wasmJsMain`)
 * implements using the appropriate [AppDatabaseFactory][template.core.base.database.AppDatabaseFactory]
 * and SQLite driver.
 *
 * OBP banking DAOs (accounts, transactions, …) are exposed here in Phase 3 as their
 * Room entities land.
 */
val DatabaseModule = module {
    includes(platformModule)
    single { get<AppDatabase>().sampleDao }
    single { get<AppDatabase>().bookkeeperDao }
    single { get<AppDatabase>().accountDao }
    single { get<AppDatabase>().transactionDao }
    single { get<AppDatabase>().paymentHistoryDao }
    single { get<AppDatabase>().vrpConsentDao }
    single { get<AppDatabase>().vrpPaymentDao }
}

/**
 * Platform-specific Koin module that provides the [AppDatabase] singleton.
 *
 * Each platform actual configures the database builder with the correct
 * [SQLiteDriver][androidx.sqlite.SQLiteDriver] and [CoroutineDispatcher][kotlinx.coroutines.CoroutineDispatcher]:
 * - **Android/Desktop**: [BundledSQLiteDriver][androidx.sqlite.driver.bundled.BundledSQLiteDriver] + `Dispatchers.IO`
 * - **Native (iOS)**: [BundledSQLiteDriver][androidx.sqlite.driver.bundled.BundledSQLiteDriver] + `Dispatchers.Default`
 * - **JS/WasmJS**: SQLiteWeb driver (OPFS-backed) + `Dispatchers.Default`
 */
expect val platformModule: Module
