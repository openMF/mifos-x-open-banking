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

/**
 * An amount on a VRP.
 *
 * @property minorUnits Pence. Integer, never floating point — this value is compared against limits
 *   and summed into a running total. `core/common`'s `parseMinorUnits` and `formatMinorUnits`
 *   convert to and from the decimal string the wire carries.
 * @property currency ISO 4217, e.g. `GBP`. The bank accepts only GBP on a VRP.
 */
@Serializable
data class Money(
    val minorUnits: Long,
    val currency: String,
)
