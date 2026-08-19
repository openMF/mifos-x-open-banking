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
import org.mifosx.openbanking.core.database.vrp.entity.VrpConsentEntity

@Dao
interface VrpConsentDao {

    /** Consents the app has not revoked, newest first. */
    @Query("SELECT * FROM vrp_consent WHERE revokedAt IS NULL ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<VrpConsentEntity>>

    /** One consent, revoked or not. */
    @Query("SELECT * FROM vrp_consent WHERE consentId = :consentId")
    fun observeById(consentId: String): Flow<VrpConsentEntity?>

    /** Consents the app has not revoked, newest first. */
    @Query("SELECT * FROM vrp_consent WHERE revokedAt IS NULL ORDER BY createdAt DESC")
    suspend fun findActive(): List<VrpConsentEntity>

    @Query("SELECT * FROM vrp_consent WHERE consentId = :consentId")
    suspend fun findById(consentId: String): VrpConsentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(consent: VrpConsentEntity)

    @Query("UPDATE vrp_consent SET status = :status, syncedAt = :syncedAt WHERE consentId = :consentId")
    suspend fun updateStatus(consentId: String, status: String, syncedAt: String)

    /** Records that the app revoked this consent. The row is kept. */
    @Query("UPDATE vrp_consent SET revokedAt = :revokedAt WHERE consentId = :consentId")
    suspend fun markRevoked(consentId: String, revokedAt: String)

    @Query("DELETE FROM vrp_consent")
    suspend fun clear()
}
