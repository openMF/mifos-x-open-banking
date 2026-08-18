/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.database.vrp.dao

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.mifosx.openbanking.core.database.AppDatabase
import org.mifosx.openbanking.core.database.vrp.entity.VrpConsentEntity
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VrpConsentDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: VrpConsentDao

    private fun consent(
        consentId: String,
        createdAt: String = "2026-08-15T17:51:00Z",
        status: String = "AWAU",
        revokedAt: String? = null,
        monthLimitMinor: Long? = 500_00,
    ) = VrpConsentEntity(
        consentId = consentId,
        status = status,
        createdAt = createdAt,
        maxIndividualAmountMinor = 10_00,
        currency = "GBP",
        payeeScheme = "UK.OBIE.SortCodeAccountNumber",
        payeeIdentification = "80200110203350",
        payeeName = "Mr Dharani C",
        monthLimitMinor = monthLimitMinor,
        revokedAt = revokedAt,
    )

    @BeforeTest
    fun setup() {
        database = Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        dao = database.vrpConsentDao
    }

    @AfterTest
    fun teardown() {
        database.close()
    }

    @Test
    fun observeActiveFromEmptyDatabaseReturnsEmptyList() = runTest {
        dao.observeActive().test {
            assertEquals(emptyList(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun storesAndReadsBackAConsent() = runTest {
        dao.upsert(consent("45365"))

        val stored = dao.findById("45365")

        assertEquals("45365", stored?.consentId)
        assertEquals(10_00, stored?.maxIndividualAmountMinor)
        assertEquals(500_00, stored?.monthLimitMinor)
        assertNull(stored?.dayLimitMinor)
    }

    @Test
    fun keepsEveryLimitThatWasSetAndLeavesTheRestNull() = runTest {
        dao.upsert(
            consent("45365").copy(
                dayLimitMinor = 50_00,
                weekLimitMinor = 100_00,
                fortnightLimitMinor = 200_00,
                monthLimitMinor = 400_00,
                halfYearLimitMinor = 800_00,
                yearLimitMinor = 1600_00,
            ),
        )

        val stored = dao.findById("45365")

        assertEquals(50_00, stored?.dayLimitMinor)
        assertEquals(1600_00, stored?.yearLimitMinor)
    }

    @Test
    fun ordersActiveConsentsNewestFirst() = runTest {
        dao.upsert(consent("45365", createdAt = "2026-08-15T17:51:00Z"))
        dao.upsert(consent("45366", createdAt = "2026-08-16T09:00:00Z"))

        dao.observeActive().test {
            assertEquals(listOf("45366", "45365"), awaitItem().map { it.consentId })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun hidesRevokedConsentsFromTheActiveListButKeepsTheRow() = runTest {
        dao.upsert(consent("45365"))

        dao.markRevoked("45365", revokedAt = "2026-08-16T10:00:00Z")

        dao.observeActive().test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("2026-08-16T10:00:00Z", dao.findById("45365")?.revokedAt)
    }

    @Test
    fun updatesStatusAndSyncedAtTogether() = runTest {
        dao.upsert(consent("45365", status = "AWAU"))

        dao.updateStatus("45365", status = "AUTH", syncedAt = "2026-08-16T10:00:00Z")

        val stored = dao.findById("45365")
        assertEquals("AUTH", stored?.status)
        assertEquals("2026-08-16T10:00:00Z", stored?.syncedAt)
    }

    @Test
    fun replacesAConsentOnUpsert() = runTest {
        dao.upsert(consent("45365", status = "AWAU"))
        dao.upsert(consent("45365", status = "AUTH"))

        assertEquals("AUTH", dao.findById("45365")?.status)
        dao.observeActive().test {
            assertEquals(1, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun clearRemovesEveryConsent() = runTest {
        dao.upsert(consent("45365"))
        dao.upsert(consent("45366"))

        dao.clear()

        assertNull(dao.findById("45365"))
        assertNull(dao.findById("45366"))
    }
}
