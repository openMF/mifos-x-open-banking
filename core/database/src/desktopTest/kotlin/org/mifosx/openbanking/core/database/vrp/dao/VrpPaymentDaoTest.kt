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
import org.mifosx.openbanking.core.database.vrp.entity.VrpPaymentEntity
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VrpPaymentDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var consentDao: VrpConsentDao
    private lateinit var dao: VrpPaymentDao

    private fun consent(consentId: String) = VrpConsentEntity(
        consentId = consentId,
        status = "AUTH",
        createdAt = "2026-08-15T17:51:00Z",
        maxIndividualAmountMinor = 10_00,
        currency = "GBP",
        payeeScheme = "UK.OBIE.SortCodeAccountNumber",
        payeeIdentification = "80200110203350",
        payeeName = "Mr Dharani C",
    )

    private fun payment(
        localId: String,
        consentId: String = "45365",
        createdAt: String = "2026-08-15T18:00:00Z",
        paymentId: String? = "19959",
        status: String = "ACSP",
    ) = VrpPaymentEntity(
        localId = localId,
        consentId = consentId,
        paymentId = paymentId,
        amountMinor = 2_00,
        currency = "GBP",
        status = status,
        createdAt = createdAt,
    )

    @BeforeTest
    fun setup() {
        database = Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        consentDao = database.vrpConsentDao
        dao = database.vrpPaymentDao
    }

    @AfterTest
    fun teardown() {
        database.close()
    }

    @Test
    fun observeForConsentFromEmptyDatabaseReturnsEmptyList() = runTest {
        dao.observeForConsent("45365").test {
            assertEquals(emptyList(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun storesAndReadsBackAPayment() = runTest {
        consentDao.upsert(consent("45365"))
        dao.upsert(payment("local-1"))

        val stored = dao.findByLocalId("local-1")

        assertEquals("19959", stored?.paymentId)
        assertEquals(2_00, stored?.amountMinor)
        assertEquals("ACSP", stored?.status)
    }

    @Test
    fun keepsAnAttemptThatNeverReachedTheBank() = runTest {
        consentDao.upsert(consent("45365"))
        dao.upsert(
            payment("local-1", paymentId = null).copy(
                errorKind = "SignatureRejected",
                errorDescription = "Invalid signature",
            ),
        )

        val stored = dao.findByLocalId("local-1")

        assertNull(stored?.paymentId)
        assertEquals("SignatureRejected", stored?.errorKind)
    }

    @Test
    fun ordersAConsentsPaymentsNewestFirst() = runTest {
        consentDao.upsert(consent("45365"))
        dao.upsert(payment("local-1", createdAt = "2026-08-15T18:00:00Z"))
        dao.upsert(payment("local-2", createdAt = "2026-08-15T19:00:00Z"))

        dao.observeForConsent("45365").test {
            assertEquals(listOf("local-2", "local-1"), awaitItem().map { it.localId })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun keepsOneConsentsPaymentsApartFromAnothers() = runTest {
        consentDao.upsert(consent("45365"))
        consentDao.upsert(consent("45366"))
        dao.upsert(payment("local-1", consentId = "45365"))
        dao.upsert(payment("local-2", consentId = "45366"))

        assertEquals(listOf("local-1"), dao.findForConsent("45365").map { it.localId })
        assertEquals(listOf("local-2"), dao.findForConsent("45366").map { it.localId })
    }

    @Test
    fun removesAConsentsPaymentsWithTheConsent() = runTest {
        consentDao.upsert(consent("45365"))
        dao.upsert(payment("local-1"))

        consentDao.clear()

        assertTrue(dao.findForConsent("45365").isEmpty())
    }

    @Test
    fun replacesAPaymentOnUpsert() = runTest {
        consentDao.upsert(consent("45365"))
        dao.upsert(payment("local-1", status = "ACSP"))
        dao.upsert(payment("local-1", status = "AcceptedCreditSettlementCompleted"))

        assertEquals("AcceptedCreditSettlementCompleted", dao.findByLocalId("local-1")?.status)
        assertEquals(1, dao.findForConsent("45365").size)
    }
}
