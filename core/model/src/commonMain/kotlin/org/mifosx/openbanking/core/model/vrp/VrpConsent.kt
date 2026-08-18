/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.model.vrp

import kotlinx.serialization.Serializable
import org.mifosx.openbanking.core.model.callback.ConsentStatus
import kotlin.time.Instant

/**
 * A standing authority to pay, and the app's only handle on it.
 *
 * @property consentId The bank's identifier. A plain string, not a UUID.
 * @property status The bank's state for the consent, as of [syncedAt].
 * @property createdAt When the bank created the consent. Also the start of the first limit period.
 * @property controlParameters The limits payments under this authority must stay within.
 * @property payee The account being paid.
 * @property payer The account being debited. Null until the customer picks one at the bank.
 * @property validity When the authority starts and stops. Null when no dates were set.
 * @property reference Payment reference agreed at setup. When set, every payment must carry it.
 * @property revokedAt When the app revoked this authority. Null while it is still live.
 * @property syncedAt When [status] was last read from the bank. Null if never read since creation.
 */
@Serializable
data class VrpConsent(
    val consentId: String,
    val status: ConsentStatus,
    val createdAt: Instant,
    val controlParameters: VrpControlParameters,
    val payee: AccountIdentity,
    val payer: AccountIdentity? = null,
    val validity: ValidityWindow? = null,
    val reference: String? = null,
    val revokedAt: Instant? = null,
    val syncedAt: Instant? = null,
)
