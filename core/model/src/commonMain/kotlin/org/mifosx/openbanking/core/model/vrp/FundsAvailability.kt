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
import kotlin.time.Instant

/**
 * Whether an amount was in the account at the moment it was checked.
 *
 * The check reads the balance alone. It does not test the consent's limits or the currency, so an
 * available answer does not mean the payment will be accepted.
 *
 * @property available Whether the bank found the amount available.
 * @property amount The amount that was asked about.
 * @property checkedAt When the bank generated the answer.
 */
@Serializable
data class FundsAvailability(
    val available: Boolean,
    val amount: Money,
    val checkedAt: Instant,
)
