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
 * Adding the mandate columns to `payment_history`.
 *
 * The same stakes as every migration on this table, and one more specific to this product: the hub
 * row is the **only** record the app keeps that a standing order was ever set up. A mandate cannot be
 * found again through the AIS read side, which returns no `StandingOrderId` on any element, so a
 * dropped table is not a cache miss here — it is the loss of the only evidence.
 *
 * `fallbackToDestructiveMigration(dropAllTables = true)` remains configured, so a migration that is
 * wrong or missing does not fail; it silently drops the table.
 */
class Migration7To8Test {

    private lateinit var connection: SQLiteConnection

    /** The v7 table, transcribed from `7.json` rather than retyped from the entity. */
    private val createV7 = """
        CREATE TABLE IF NOT EXISTS `payment_history` (
            `id` TEXT NOT NULL, `paymentId` TEXT, `errorKind` TEXT, `errorDescription` TEXT,
            `status` TEXT, `debtorAccountId` TEXT NOT NULL, `debtorName` TEXT NOT NULL,
            `debtorIdentification` TEXT NOT NULL, `creditorName` TEXT NOT NULL,
            `creditorIdentification` TEXT NOT NULL, `amountMinorUnits` INTEGER NOT NULL,
            `currency` TEXT NOT NULL, `reference` TEXT, `creationDateTime` TEXT NOT NULL,
            `approvedAt` TEXT, `submittedAt` TEXT, `settlementDateTime` TEXT, `chargeBearer` TEXT,
            `currencyOfTransfer` TEXT, `requestedExecutionDateTime` TEXT,
            `paymentType` TEXT NOT NULL, `syncedAt` TEXT,
            PRIMARY KEY(`id`)
        )
    """.trimIndent()

    @BeforeTest
    fun setUp() {
        connection = BundledSQLiteDriver().open(":memory:")
        connection.execSQL(createV7)
    }

    @AfterTest
    fun tearDown() {
        connection.close()
    }

    /** A scheduled payment, the richest shape a pre-v8 row can have. */
    private fun insertV7Payment() {
        connection.execSQL(
            """
            INSERT INTO payment_history VALUES (
                'row-1', '19919', NULL, NULL, 'InitiationCompleted',
                'acc-1', 'Current account', '80200110203349',
                'Mr Dharani C', '80200110203350', 25000, 'GBP', 'RENT-AUG',
                '2026-08-06T10:42:08+00:00', '2026-08-06T10:40:00+00:00', '2026-08-06T10:42:00+00:00',
                NULL, NULL, NULL, '2026-09-08',
                'domestic_scheduled_payment', NULL
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
    fun addsBothMandateColumns() = runTest {
        assertFalse("frequency" in columns(), "v7 should not have it yet")
        assertFalse("finalPaymentDateTime" in columns(), "v7 should not have it yet")

        MIGRATION_7_8.migrate(connection)

        assertTrue("frequency" in columns())
        assertTrue("finalPaymentDateTime" in columns())
    }

    @Test
    fun keepsExistingRowsAndTheirValues() = runTest {
        insertV7Payment()

        MIGRATION_7_8.migrate(connection)

        connection.prepare(
            "SELECT id, paymentId, amountMinorUnits, paymentType, requestedExecutionDateTime " +
                "FROM payment_history",
        ).use { stmt ->
            assertTrue(stmt.step(), "the existing row was lost")
            assertEquals("row-1", stmt.getText(0))
            assertEquals("19919", stmt.getText(1))
            assertEquals(25_000L, stmt.getLong(2))
            assertEquals("domestic_scheduled_payment", stmt.getText(3))
            assertEquals("2026-09-08", stmt.getText(4))
        }
    }

    /**
     * Both new columns are NULL on an existing row, and neither is defaulted.
     *
     * Every pre-v8 row runs once. Giving one a frequency would make the hub claim it repeats, and the
     * customer has no way to check that against anything — the mandate list at the bank would simply
     * not contain it.
     */
    @Test
    fun leavesBothNewColumnsNullOnExistingRows() = runTest {
        insertV7Payment()

        MIGRATION_7_8.migrate(connection)

        connection.prepare("SELECT frequency, finalPaymentDateTime FROM payment_history").use { stmt ->
            assertTrue(stmt.step())
            assertTrue(stmt.isNull(0), "a one-off payment must not be given a frequency")
            assertTrue(stmt.isNull(1), "nor an end date")
        }
    }

    /** The columns accept what a mandate actually writes, rather than merely existing. */
    @Test
    fun acceptsAMandateRowAfterMigrating() = runTest {
        MIGRATION_7_8.migrate(connection)

        connection.execSQL(
            """
            INSERT INTO payment_history VALUES (
                'row-2', '19916', NULL, NULL, 'InitiationCompleted',
                'acc-1', 'Current account', '80200110203349',
                'Mr Dharani C', '80200110203350', 25000, 'GBP', 'FLAT 4B RENT',
                '2026-08-13T10:42:08+00:00', NULL, NULL, NULL, NULL, NULL, '2026-08-20',
                'domestic_standing_order', NULL, 'MNTH', '2026-12-11'
            )
            """.trimIndent(),
        )

        connection.prepare(
            "SELECT frequency, finalPaymentDateTime, paymentType FROM payment_history WHERE id = 'row-2'",
        ).use { stmt ->
            assertTrue(stmt.step())
            assertEquals("MNTH", stmt.getText(0))
            assertEquals("2026-12-11", stmt.getText(1))
            assertEquals("domestic_standing_order", stmt.getText(2))
        }
    }

    /** An open-ended mandate stores a frequency and no end date, which must remain distinguishable. */
    @Test
    fun anOpenEndedMandateKeepsItsFrequencyWithNoEndDate() = runTest {
        MIGRATION_7_8.migrate(connection)

        connection.execSQL(
            """
            INSERT INTO payment_history VALUES (
                'row-3', '19918', NULL, NULL, 'InitiationCompleted',
                'acc-1', 'Current account', '80200110203349',
                'Mr Dharani C', '80200110203350', 25000, 'GBP', NULL,
                '2026-08-13T10:44:00+00:00', NULL, NULL, NULL, NULL, NULL, '2026-08-20',
                'domestic_standing_order', NULL, 'WEEK', NULL
            )
            """.trimIndent(),
        )

        connection.prepare(
            "SELECT frequency, finalPaymentDateTime FROM payment_history WHERE id = 'row-3'",
        ).use { stmt ->
            assertTrue(stmt.step())
            assertEquals("WEEK", stmt.getText(0), "the row must still say it repeats")
            assertTrue(stmt.isNull(1), "and that it has no end")
        }
    }

    @Test
    fun migratesAnEmptyTableWithoutError() = runTest {
        MIGRATION_7_8.migrate(connection)

        connection.prepare("SELECT COUNT(*) FROM payment_history").use { stmt ->
            assertTrue(stmt.step())
            assertEquals(0L, stmt.getLong(0))
        }
    }

    @Test
    fun declaresTheVersionsItMovesBetween() {
        assertEquals(7, MIGRATION_7_8.startVersion)
        assertEquals(8, MIGRATION_7_8.endVersion)
    }

    /** Room only applies a migration it can find, so a missing registration is a silent wipe. */
    @Test
    fun isRegisteredInAllMigrations() {
        assertTrue(ALL_MIGRATIONS.any { it.startVersion == 7 && it.endVersion == 8 })
    }
}
