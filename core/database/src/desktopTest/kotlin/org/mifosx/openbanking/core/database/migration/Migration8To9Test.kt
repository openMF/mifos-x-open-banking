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

class Migration8To9Test {

    private lateinit var connection: SQLiteConnection

    private val createV8 = """
        CREATE TABLE IF NOT EXISTS `payment_history` (
            `id` TEXT NOT NULL, `paymentId` TEXT, `errorKind` TEXT, `errorDescription` TEXT,
            `status` TEXT, `debtorAccountId` TEXT NOT NULL, `debtorName` TEXT NOT NULL,
            `debtorIdentification` TEXT NOT NULL, `creditorName` TEXT NOT NULL,
            `creditorIdentification` TEXT NOT NULL, `amountMinorUnits` INTEGER NOT NULL,
            `currency` TEXT NOT NULL, `reference` TEXT, `creationDateTime` TEXT NOT NULL,
            `approvedAt` TEXT, `submittedAt` TEXT, `settlementDateTime` TEXT, `chargeBearer` TEXT,
            `currencyOfTransfer` TEXT, `requestedExecutionDateTime` TEXT,
            `paymentType` TEXT NOT NULL, `syncedAt` TEXT,
            `frequency` TEXT, `finalPaymentDateTime` TEXT,
            PRIMARY KEY(`id`)
        )
    """.trimIndent()

    @BeforeTest
    fun setUp() {
        connection = BundledSQLiteDriver().open(":memory:")
        connection.execSQL(createV8)
        connection.execSQL("PRAGMA foreign_keys = ON")
    }

    @AfterTest
    fun tearDown() {
        connection.close()
    }

    private fun tables(): List<String> = buildList {
        connection.prepare("SELECT name FROM sqlite_master WHERE type = 'table'").use { stmt ->
            while (stmt.step()) add(stmt.getText(0))
        }
    }

    private fun columns(table: String): List<String> = buildList {
        connection.prepare("PRAGMA table_info($table)").use { stmt ->
            while (stmt.step()) add(stmt.getText(1))
        }
    }

    private fun insertConsent(consentId: String) {
        connection.execSQL(
            """
            INSERT INTO vrp_consent (
                consentId, status, createdAt, validFrom, validTo,
                maxIndividualAmountMinor, currency, interactionType,
                payerScheme, payerIdentification, payerName,
                payeeScheme, payeeIdentification, payeeName,
                dayLimitMinor, weekLimitMinor, fortnightLimitMinor,
                monthLimitMinor, halfYearLimitMinor, yearLimitMinor,
                reference, revokedAt, syncedAt
            ) VALUES (
                '$consentId', 'AWAU', '2026-08-15T17:51:00Z', NULL, '2027-12-31',
                1000, 'GBP', 'OffSession',
                'UK.OBIE.SortCodeAccountNumber', '80200110203348', 'Mr Robert',
                'UK.OBIE.SortCodeAccountNumber', '80200110203350', 'Mr Dharani C',
                5000, NULL, NULL, 50000, NULL, NULL,
                NULL, NULL, NULL
            )
            """.trimIndent(),
        )
    }

    @Test
    fun addsBothVrpTables() = runTest {
        assertFalse("vrp_consent" in tables(), "v8 should not have it yet")
        assertFalse("vrp_payment" in tables(), "v8 should not have it yet")

        MIGRATION_8_9.migrate(connection)

        assertTrue("vrp_consent" in tables())
        assertTrue("vrp_payment" in tables())
    }

    @Test
    fun leavesTheExistingPaymentHistoryAlone() = runTest {
        val before = columns("payment_history")

        MIGRATION_8_9.migrate(connection)

        assertEquals(before, columns("payment_history"))
    }

    @Test
    fun givesAConsentOneNullableColumnPerPeriod() = runTest {
        MIGRATION_8_9.migrate(connection)

        val columns = columns("vrp_consent")
        listOf(
            "dayLimitMinor",
            "weekLimitMinor",
            "fortnightLimitMinor",
            "monthLimitMinor",
            "halfYearLimitMinor",
            "yearLimitMinor",
        ).forEach { assertTrue(it in columns, "missing $it") }
    }

    @Test
    fun acceptsAConsentWithOnlySomeLimitsSet() = runTest {
        MIGRATION_8_9.migrate(connection)

        insertConsent("45365")

        connection.prepare(
            "SELECT dayLimitMinor, weekLimitMinor, monthLimitMinor FROM vrp_consent",
        ).use { stmt ->
            assertTrue(stmt.step())
            assertEquals(5000L, stmt.getLong(0))
            assertTrue(stmt.isNull(1))
            assertEquals(50000L, stmt.getLong(2))
        }
    }

    @Test
    fun removesAConsentsPaymentsWithTheConsent() = runTest {
        MIGRATION_8_9.migrate(connection)
        insertConsent("45365")
        connection.execSQL(
            """
            INSERT INTO vrp_payment (
                localId, consentId, paymentId, amountMinor, currency, status, createdAt,
                submittedAt, settledAt, reference, errorKind, errorDescription,
                supportReference, syncedAt
            ) VALUES (
                'local-1', '45365', '19959', 200, 'GBP', 'ACSP', '2026-08-15T18:00:00Z',
                NULL, NULL, NULL, NULL, NULL, NULL, NULL
            )
            """.trimIndent(),
        )

        connection.execSQL("DELETE FROM vrp_consent WHERE consentId = '45365'")

        connection.prepare("SELECT COUNT(*) FROM vrp_payment").use { stmt ->
            assertTrue(stmt.step())
            assertEquals(0L, stmt.getLong(0))
        }
    }

    @Test
    fun indexesPaymentsByConsent() = runTest {
        MIGRATION_8_9.migrate(connection)

        val indices = buildList {
            connection.prepare(
                "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'vrp_payment'",
            ).use { stmt ->
                while (stmt.step()) add(stmt.getText(0))
            }
        }

        assertTrue("index_vrp_payment_consentId" in indices, "actual: $indices")
    }
}
