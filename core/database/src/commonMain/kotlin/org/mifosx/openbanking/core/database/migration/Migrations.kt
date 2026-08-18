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

import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import org.mifosx.openbanking.core.database.AppDatabase

/**
 * The first real migration in this database.
 *
 * Every version up to 4 relied on `fallbackToDestructiveMigration(dropAllTables = true)`, which is
 * acceptable for caches — accounts, transactions and balances are all re-fetchable — but not for
 * `payment_history`. A payment that failed before reaching the bank exists in no other place, so
 * dropping the table destroys the only record that it was ever attempted.
 *
 * The destructive fallback stays configured. It is now the path for version gaps this file does not
 * cover, rather than the path for every upgrade.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override suspend fun migrate(connection: SQLiteConnection) {
        // Renamed because international payments have always been stored here too, so the old name
        // was never accurate. RENAME COLUMN needs SQLite 3.25+, which BundledSQLiteDriver ships on
        // every target this app builds for, and it leaves the rows themselves untouched.
        connection.runSql("ALTER TABLE payment_history RENAME COLUMN domesticPaymentId TO paymentId")

        // OBIE returns one CreationDateTime and no per-stage history, so the timeline can only be
        // built from what this app observed. Existing rows get NULL: their stages happened before
        // anything recorded them, and a made-up timestamp would read as fact.
        connection.runSql("ALTER TABLE payment_history ADD COLUMN approvedAt TEXT")
        connection.runSql("ALTER TABLE payment_history ADD COLUMN submittedAt TEXT")

        // International-only. NULL on a domestic row, and on every row written before v5.
        connection.runSql("ALTER TABLE payment_history ADD COLUMN chargeBearer TEXT")
        connection.runSql("ALTER TABLE payment_history ADD COLUMN currencyOfTransfer TEXT")
    }
}

/**
 * Adds the account description, which decides whether a wallet may fund a payment.
 *
 * `accounts` is a re-fetchable cache, so the destructive fallback would have been survivable here.
 * A real migration is still the right call: the column defaults to empty, and an empty description is
 * indistinguishable from "this account is not a Global Money wallet". Dropping the table would blank
 * every row until the next successful accounts fetch, and until then the wallet would again be
 * offered as a payer. Adding the column keeps the rows and lets the next fetch fill it in.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.runSql("ALTER TABLE accounts ADD COLUMN description TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override suspend fun migrate(connection: SQLiteConnection) {
        // Scheduled payments only. NULL on every existing row, and on every immediate payment
        // written after this: an immediate payment has no future date, and defaulting one would
        // make the hub claim a payment is due later than it was actually made.
        connection.runSql("ALTER TABLE payment_history ADD COLUMN requestedExecutionDateTime TEXT")
    }
}

/**
 * Adds the two columns a recurring mandate needs.
 *
 * Both NULL on every existing row and on every product that runs once. A standing order's *first*
 * payment date deliberately reuses `requestedExecutionDateTime` rather than taking a third column:
 * the two mean the same thing — the date the first movement is due — and splitting them would leave
 * two columns that must never both be set, which is a rule nothing enforces.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.runSql("ALTER TABLE payment_history ADD COLUMN frequency TEXT")
        connection.runSql("ALTER TABLE payment_history ADD COLUMN finalPaymentDateTime TEXT")
    }
}

/**
 * Adds the two tables variable recurring payments need.
 *
 * A consent carries its caps as one nullable column per period rather than a child table: each
 * period may appear at most once, so the six columns are the whole set. Payments reference their
 * consent and go with it.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.runSql(
            """
            CREATE TABLE IF NOT EXISTS `vrp_consent` (
                `consentId` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `createdAt` TEXT NOT NULL,
                `validFrom` TEXT,
                `validTo` TEXT,
                `maxIndividualAmountMinor` INTEGER NOT NULL,
                `currency` TEXT NOT NULL,
                `interactionType` TEXT,
                `payerScheme` TEXT,
                `payerIdentification` TEXT,
                `payerName` TEXT,
                `payeeScheme` TEXT NOT NULL,
                `payeeIdentification` TEXT NOT NULL,
                `payeeName` TEXT NOT NULL,
                `dayLimitMinor` INTEGER,
                `weekLimitMinor` INTEGER,
                `fortnightLimitMinor` INTEGER,
                `monthLimitMinor` INTEGER,
                `halfYearLimitMinor` INTEGER,
                `yearLimitMinor` INTEGER,
                `reference` TEXT,
                `revokedAt` TEXT,
                `syncedAt` TEXT,
                PRIMARY KEY(`consentId`)
            )
            """.trimIndent(),
        )
        connection.runSql(
            """
            CREATE TABLE IF NOT EXISTS `vrp_payment` (
                `localId` TEXT NOT NULL,
                `consentId` TEXT NOT NULL,
                `paymentId` TEXT,
                `amountMinor` INTEGER NOT NULL,
                `currency` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `createdAt` TEXT NOT NULL,
                `submittedAt` TEXT,
                `settledAt` TEXT,
                `reference` TEXT,
                `errorKind` TEXT,
                `errorDescription` TEXT,
                `supportReference` TEXT,
                `syncedAt` TEXT,
                PRIMARY KEY(`localId`),
                FOREIGN KEY(`consentId`) REFERENCES `vrp_consent`(`consentId`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        connection.runSql(
            "CREATE INDEX IF NOT EXISTS `index_vrp_payment_consentId` ON `vrp_payment` (`consentId`)",
        )
    }
}

/** Every migration the database knows about, in the order Room should consider them. */
val ALL_MIGRATIONS: Array<Migration> =
    arrayOf(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)

/**
 * Registers every migration on a builder.
 *
 * Each platform builds its own [AppDatabase], so without this the list would have to be spread into
 * five separate call sites and a new migration would mean editing all five — the kind of change that
 * gets applied to four of them.
 */
fun RoomDatabase.Builder<AppDatabase>.addAppMigrations(): RoomDatabase.Builder<AppDatabase> =
    @Suppress("SpreadOperator")
    addMigrations(*ALL_MIGRATIONS)
