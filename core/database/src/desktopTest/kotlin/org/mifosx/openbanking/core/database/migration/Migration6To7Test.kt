/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.database.migration

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Adding the scheduled execution date to `payment_history`.
 *
 * These cases matter more than the migration's one statement suggests: the database still configures
 * `fallbackToDestructiveMigration(dropAllTables = true)`, so a migration that is wrong or missing
 * does not fail — it silently **drops the table**. `payment_history` has no remote source, and it is
 * the only record this app keeps of payments that failed before submission.
 */
class Migration6To7Test {

    private lateinit var connection: SQLiteConnection

    /** The v6 table, transcribed from `6.json` rather than retyped from the entity. */
    private val createV6 = """
        CREATE TABLE IF NOT EXISTS `payment_history` (
            `id` TEXT NOT NULL, `paymentId` TEXT, `errorKind` TEXT, `errorDescription` TEXT,
            `status` TEXT, `debtorAccountId` TEXT NOT NULL, `debtorName` TEXT NOT NULL,
            `debtorIdentification` TEXT NOT NULL, `creditorName` TEXT NOT NULL,
            `creditorIdentification` TEXT NOT NULL, `amountMinorUnits` INTEGER NOT NULL,
            `currency` TEXT NOT NULL, `reference` TEXT, `creationDateTime` TEXT NOT NULL,
            `approvedAt` TEXT, `submittedAt` TEXT, `settlementDateTime` TEXT, `chargeBearer` TEXT,
            `currencyOfTransfer` TEXT, `paymentType` TEXT NOT NULL, `syncedAt` TEXT,
            PRIMARY KEY(`id`)
        )
    """.trimIndent()

    @BeforeTest
    fun setUp() {
        connection = BundledSQLiteDriver().open(":memory:")
        connection.execSQL(createV6)
    }

    @AfterTest
    fun tearDown() {
        connection.close()
    }

    /** A settled domestic payment, the shape every pre-v7 row has. */
    private fun insertV6Payment() {
        connection.execSQL(
            """
            INSERT INTO payment_history VALUES (
                'row-1', '19919', NULL, NULL, 'AcceptedSettlementInProcess',
                'acc-1', 'Current account', '80200110203349',
                'Mr Dharani C', '80200110203350', 25000, 'GBP', 'RENT-AUG',
                '2026-08-06T10:42:08+00:00', '2026-08-06T10:40:00+00:00', '2026-08-06T10:42:00+00:00',
                NULL, NULL, NULL, 'domestic_payment', NULL
            )
            """.trimIndent(),
        )
    }

    private fun columns(): List<String> = buildList {
        connection.prepare("PRAGMA table_info(payment_history)").use { stmt ->
            while (stmt.step()) add(stmt.getText(1))
        }
    }

    @Test
    fun addsTheRequestedExecutionDateColumn() = runTest {
        assertFalse("requestedExecutionDateTime" in columns(), "v6 should not have it yet")

        MIGRATION_6_7.migrate(connection)

        assertTrue("requestedExecutionDateTime" in columns())
    }

    @Test
    fun keepsExistingRowsAndTheirValues() = runTest {
        insertV6Payment()

        MIGRATION_6_7.migrate(connection)

        connection.prepare(
            "SELECT id, paymentId, amountMinorUnits, paymentType FROM payment_history",
        ).use { stmt ->
            assertTrue(stmt.step(), "the existing row was lost")
            assertEquals("row-1", stmt.getText(0))
            assertEquals("19919", stmt.getText(1))
            assertEquals(25_000L, stmt.getLong(2))
            assertEquals("domestic_payment", stmt.getText(3))
        }
    }

    /**
     * An existing row's date is NULL, not a stand-in.
     *
     * Every pre-v7 row is an immediate payment, which has no future execution date. Defaulting to
     * the creation date would make the hub state that a payment already made is due later.
     */
    @Test
    fun leavesTheNewColumnNullOnExistingRows() = runTest {
        insertV6Payment()

        MIGRATION_6_7.migrate(connection)

        connection.prepare("SELECT requestedExecutionDateTime FROM payment_history").use { stmt ->
            assertTrue(stmt.step())
            assertTrue(stmt.isNull(0), "an immediate payment must not be given an execution date")
        }
    }

    @Test
    fun migratesAnEmptyTableWithoutError() = runTest {
        MIGRATION_6_7.migrate(connection)

        connection.prepare("SELECT COUNT(*) FROM payment_history").use { stmt ->
            assertTrue(stmt.step())
            assertEquals(0L, stmt.getLong(0))
        }
    }

    @Test
    fun declaresTheVersionsItMovesBetween() {
        assertEquals(6, MIGRATION_6_7.startVersion)
        assertEquals(7, MIGRATION_6_7.endVersion)
    }

    /** Room only applies a migration it can find, so a missing registration is a silent wipe. */
    @Test
    fun isRegisteredInAllMigrations() {
        assertTrue(ALL_MIGRATIONS.any { it.startVersion == 6 && it.endVersion == 7 })
    }
}
