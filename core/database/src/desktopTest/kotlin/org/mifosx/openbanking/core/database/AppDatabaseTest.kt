/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.database

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import org.mifosx.openbanking.core.database.migration.ALL_MIGRATIONS
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AppDatabaseTest {

    private var database: AppDatabase? = null

    @AfterTest
    fun teardown() {
        database?.close()
    }

    @Test
    fun inMemoryDatabaseCanBeCreated() {
        database = Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

        assertNotNull(database)
    }

    @Test
    fun databaseExposeSampleDao() {
        database = Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

        assertNotNull(database!!.sampleDao)
    }

    @Test
    fun databaseVersionIsCurrent() {
        // Update this constant when bumping AppDatabase.VERSION so the guardrail stays meaningful.
        // 4 added payment_history; 5 renamed its id column and added the timeline and international
        // fields — and is the first version reached by a real migration rather than a table drop;
        // 6 added accounts.description, without which a Global Money wallet reads back as a plain
        // current account and is offered as a payer the bank refuses; 7 added the scheduled execution
        // date; 8 added the standing-order frequency and end date, without which a mandate row is
        // indistinguishable from a one-off payment — and that row is the only record the app keeps,
        // since a mandate cannot be found again through the AIS read side; 9 added the VRP consent
        // and payment tables — a consent is the only handle on a standing authority, because no
        // endpoint lists them.
        assertEquals(9, AppDatabase.VERSION)
    }

    /**
     * Every version above the oldest must be reachable without dropping tables.
     *
     * The destructive fallback is still configured, so a missing migration does not fail the build
     * or throw at runtime — it silently wipes `payment_history`, which is the one table holding data
     * that exists nowhere else. This asserts the path exists rather than trusting that it does.
     */
    @Test
    fun everyVersionSincePaymentHistoryHasAMigrationPath() {
        val covered = ALL_MIGRATIONS.map { it.startVersion to it.endVersion }.toSet()

        for (from in 4 until AppDatabase.VERSION) {
            assertTrue(
                (from to from + 1) in covered,
                "no migration from $from to ${from + 1}; payment history would be dropped",
            )
        }
    }

    @Test
    fun databaseNameIsCorrect() {
        assertEquals("mifos_database.db", AppDatabase.DATABASE_NAME)
    }
}
