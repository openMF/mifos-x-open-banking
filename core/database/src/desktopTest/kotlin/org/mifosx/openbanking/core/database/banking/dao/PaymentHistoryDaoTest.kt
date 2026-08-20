/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.database.banking.dao

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.mifosx.openbanking.core.database.AppDatabase
import org.mifosx.openbanking.core.database.banking.entity.PaymentHistoryEntity
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PaymentHistoryDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: PaymentHistoryDao

    private fun row(
        id: String,
        paymentId: String? = id,
        paymentType: String = DOMESTIC_SINGLE,
        submittedAt: String? = "2026-08-15T18:00:00Z",
        status: String? = "AcceptedSettlementInProcess",
    ) = PaymentHistoryEntity(
        id = id,
        paymentId = paymentId,
        errorKind = null,
        errorDescription = null,
        status = status,
        debtorAccountId = "acc-1",
        debtorName = "Current account",
        debtorIdentification = "80200110203349",
        creditorName = "Mr Dharani C",
        creditorIdentification = "80200110203350",
        amountMinorUnits = 2_50,
        currency = "GBP",
        reference = null,
        creationDateTime = "2026-08-15T18:00:00Z",
        submittedAt = submittedAt,
        settlementDateTime = null,
        paymentType = paymentType,
        syncedAt = null,
    )

    @BeforeTest
    fun setup() {
        database = Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        dao = database.paymentHistoryDao
    }

    @AfterTest
    fun teardown() {
        database.close()
    }

    @Test
    fun `observeByType returns only the requested types`() = runTest {
        dao.upsert(row("1", paymentType = DOMESTIC_SINGLE))
        dao.upsert(row("2", paymentType = DOMESTIC_STANDING_ORDER))
        dao.upsert(row("3", paymentType = INTERNATIONAL_SINGLE))

        dao.observeByType(listOf(DOMESTIC_SINGLE, INTERNATIONAL_SINGLE), LIMIT).test {
            assertEquals(setOf("1", "3"), awaitItem().map { it.id }.toSet())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeByType orders by submittedAt, newest first`() = runTest {
        dao.upsert(row("old", submittedAt = "2026-08-01T09:00:00Z"))
        dao.upsert(row("new", submittedAt = "2026-08-20T09:00:00Z"))
        dao.upsert(row("mid", submittedAt = "2026-08-10T09:00:00Z"))

        dao.observeByType(listOf(DOMESTIC_SINGLE), LIMIT).test {
            assertEquals(listOf("new", "mid", "old"), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeByType omits payments that never reached the bank`() = runTest {
        dao.upsert(row("sent"))
        dao.upsert(row("failed", paymentId = null, status = null))

        dao.observeByType(listOf(DOMESTIC_SINGLE), LIMIT).test {
            assertEquals(listOf("sent"), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeByType honours the limit`() = runTest {
        repeat(7) { index ->
            dao.upsert(row("p$index", submittedAt = "2026-08-0${index + 1}T09:00:00Z"))
        }

        dao.observeByType(listOf(DOMESTIC_SINGLE), limit = 5).test {
            assertEquals(5, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateStatus writes the status back and leaves the rest of the row`() = runTest {
        dao.upsert(row("1"))

        dao.updateStatus(
            paymentId = "1",
            status = "AcceptedCreditSettlementCompleted",
            settledAt = "2026-08-16T10:00:00Z",
            syncedAt = "2026-08-16T10:00:01Z",
        )

        val stored = dao.observeById("1").first()
        assertEquals("AcceptedCreditSettlementCompleted", stored?.status)
        assertEquals("2026-08-16T10:00:00Z", stored?.settlementDateTime)
        assertEquals("2026-08-16T10:00:01Z", stored?.syncedAt)
        assertEquals("Mr Dharani C", stored?.creditorName)
        assertEquals(2_50, stored?.amountMinorUnits)
    }

    @Test
    fun `updateStatus touches nothing when no row carries that payment id`() = runTest {
        dao.upsert(row("1"))

        dao.updateStatus("absent", "Rejected", settledAt = null, syncedAt = "2026-08-16T10:00:01Z")

        assertNull(dao.observeById("absent").first())
        assertEquals("AcceptedSettlementInProcess", dao.observeById("1").first()?.status)
    }

    private companion object {
        const val DOMESTIC_SINGLE = "domestic_payment"
        const val INTERNATIONAL_SINGLE = "international_payment"
        const val DOMESTIC_STANDING_ORDER = "domestic_standing_order"
        const val LIMIT = 20
    }
}
