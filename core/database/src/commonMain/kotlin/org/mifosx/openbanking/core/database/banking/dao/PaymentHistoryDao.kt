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

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow
import org.mifosx.openbanking.core.database.banking.entity.PaymentHistoryEntity

/**
 * DAO for the `payment_history` local snapshot table.
 *
 * Nothing lists these rows any more. `observeRecent` existed for the hub's Recent section and went
 * with it; what remains is written on every payment outcome and read back one row at a time by
 * [observeById], which answers two questions the bank cannot: which rail a payment id belongs to,
 * and when the app observed its approval and submission.
 *
 * [upsert] replaces a row by primary key, so a write for a payment already recorded is an in-place
 * update rather than a duplicate.
 */
@Dao
interface PaymentHistoryDao {

    @Query("SELECT * FROM payment_history WHERE paymentId = :paymentId")
    fun observeById(paymentId: String): Flow<PaymentHistoryEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PaymentHistoryEntity)

    @Query("DELETE FROM payment_history")
    suspend fun clear()
}
