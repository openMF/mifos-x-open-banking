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
import androidx.room3.PrimaryKey

/**
 * A standing authority the app created.
 *
 * No endpoint lists a customer's consents, so this row is the only way back to one.
 *
 * @property consentId The bank's identifier. A plain string, not a UUID.
 * @property status The bank's state for the consent as of [syncedAt].
 * @property createdAt ISO-8601. Also the start of the first limit period.
 * @property validFrom ISO-8601 date, or null when no start date was set.
 * @property validTo ISO-8601 date, or null when the authority has no end date.
 * @property maxIndividualAmountMinor The most any single payment may be, in minor units.
 * @property currency ISO 4217 code covering every amount on this consent.
 * @property interactionType `InSession` or `OffSession`, as echoed by the bank.
 * @property payerScheme Identification scheme of the account being debited. Null until chosen.
 * @property payerIdentification Identifier of the account being debited. Null until chosen.
 * @property payerName Holder name of the account being debited. Null until chosen.
 * @property payeeScheme Identification scheme of the account being paid.
 * @property payeeIdentification Identifier of the account being paid.
 * @property payeeName Holder name of the account being paid.
 * @property dayLimitMinor Cap per day in minor units, or null when no daily cap was set.
 * @property weekLimitMinor Cap per week, or null when none was set.
 * @property fortnightLimitMinor Cap per fortnight, or null when none was set.
 * @property monthLimitMinor Cap per month, or null when none was set.
 * @property halfYearLimitMinor Cap per half-year, or null when none was set.
 * @property yearLimitMinor Cap per year, or null when none was set.
 * @property reference Payment reference carried on every payment under this consent.
 * @property revokedAt ISO-8601 when the app revoked it, or null while live. The row is kept so a
 *   revoked consent stays distinguishable from an unknown one.
 * @property syncedAt ISO-8601 when [status] was last read from the bank.
 */
@Entity(tableName = "vrp_consent")
data class VrpConsentEntity(
    @PrimaryKey val consentId: String,
    val status: String,
    val createdAt: String,
    val validFrom: String? = null,
    val validTo: String? = null,
    val maxIndividualAmountMinor: Long,
    val currency: String,
    val interactionType: String? = null,
    val payerScheme: String? = null,
    val payerIdentification: String? = null,
    val payerName: String? = null,
    val payeeScheme: String,
    val payeeIdentification: String,
    val payeeName: String,
    val dayLimitMinor: Long? = null,
    val weekLimitMinor: Long? = null,
    val fortnightLimitMinor: Long? = null,
    val monthLimitMinor: Long? = null,
    val halfYearLimitMinor: Long? = null,
    val yearLimitMinor: Long? = null,
    val reference: String? = null,
    val revokedAt: String? = null,
    val syncedAt: String? = null,
)
