/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.database.vrp.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * One payment attempted under a consent.
 *
 * A row exists for every attempt, including one that failed before the bank saw it. The usage
 * calculation reads this table.
 *
 * @property localId The app's own identifier, present from the moment the attempt is recorded.
 * @property consentId The authority the payment was made under. Deleting that consent removes
 *   this row with it.
 * @property paymentId The bank's identifier, or null when the attempt never reached it.
 * @property amountMinor The amount in minor units.
 * @property currency ISO 4217 code.
 * @property status The bank's state for the payment as of [syncedAt].
 * @property createdAt ISO-8601 when the app recorded the attempt.
 * @property submittedAt ISO-8601 when the bank accepted the instruction.
 * @property settledAt ISO-8601 when the payment reached a terminal state.
 * @property reference The payment reference sent with the instruction.
 * @property errorKind Classification of a failure.
 * @property errorDescription The bank's own message for a failure.
 * @property supportReference The bank's error identifier, quotable to support.
 * @property syncedAt ISO-8601 when [status] was last read from the bank.
 */
@Entity(
    tableName = "vrp_payment",
    foreignKeys = [
        ForeignKey(
            entity = VrpConsentEntity::class,
            parentColumns = ["consentId"],
            childColumns = ["consentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("consentId")],
)
data class VrpPaymentEntity(
    @PrimaryKey val localId: String,
    val consentId: String,
    val paymentId: String? = null,
    val amountMinor: Long,
    val currency: String,
    val status: String,
    val createdAt: String,
    val submittedAt: String? = null,
    val settledAt: String? = null,
    val reference: String? = null,
    val errorKind: String? = null,
    val errorDescription: String? = null,
    val supportReference: String? = null,
    val syncedAt: String? = null,
)
