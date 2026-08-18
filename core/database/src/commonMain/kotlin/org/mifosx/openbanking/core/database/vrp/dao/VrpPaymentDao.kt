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

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow
import org.mifosx.openbanking.core.database.vrp.entity.VrpPaymentEntity

@Dao
interface VrpPaymentDao {

    /** Every payment attempted under a consent, newest first. */
    @Query("SELECT * FROM vrp_payment WHERE consentId = :consentId ORDER BY createdAt DESC")
    fun observeForConsent(consentId: String): Flow<List<VrpPaymentEntity>>

    /** A one-shot read of a consent's payments, for the usage calculation. */
    @Query("SELECT * FROM vrp_payment WHERE consentId = :consentId")
    suspend fun findForConsent(consentId: String): List<VrpPaymentEntity>

    @Query("SELECT * FROM vrp_payment WHERE localId = :localId")
    suspend fun findByLocalId(localId: String): VrpPaymentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(payment: VrpPaymentEntity)

    @Query("DELETE FROM vrp_payment")
    suspend fun clear()
}
