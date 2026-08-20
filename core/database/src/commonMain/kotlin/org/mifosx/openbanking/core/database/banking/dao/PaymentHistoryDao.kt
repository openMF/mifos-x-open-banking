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
 * DAO for the `payment_history` table.
 *
 * [observeById] reads one payment by the bank's id; [observeByType] backs a feature's history list.
 * [upsert] replaces a row by primary key.
 */
@Dao
interface PaymentHistoryDao {

    @Query("SELECT * FROM payment_history WHERE paymentId = :paymentId")
    fun observeById(paymentId: String): Flow<PaymentHistoryEntity?>

    /** The payments of the given [types] that reached the bank, newest first. */
    @Query(
        "SELECT * FROM payment_history " +
            "WHERE paymentId IS NOT NULL AND paymentType IN (:types) " +
            "ORDER BY submittedAt DESC LIMIT :limit",
    )
    fun observeByType(types: List<String>, limit: Int): Flow<List<PaymentHistoryEntity>>

    /** Writes back what a status read returned, leaving the rest of the row as submitted. */
    @Query(
        "UPDATE payment_history " +
            "SET status = :status, settlementDateTime = :settledAt, syncedAt = :syncedAt " +
            "WHERE paymentId = :paymentId",
    )
    suspend fun updateStatus(
        paymentId: String,
        status: String,
        settledAt: String?,
        syncedAt: String,
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PaymentHistoryEntity)

    @Query("DELETE FROM payment_history")
    suspend fun clear()
}
