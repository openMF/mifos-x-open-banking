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
 * How much of a consent's recurring limit the current period has used.
 *
 * Computed by the app from the payments it recorded, so it is an estimate: the bank's own evaluation
 * is what decides a payment, and it can refuse an amount this allows.
 *
 * @property limit The cap being measured against.
 * @property consumed Total of the settled payments in the current period.
 * @property remaining [limit] less [consumed].
 * @property periodStart When the current period began.
 */
@Serializable
data class PeriodUsage(
    val limit: PeriodicLimit,
    val consumed: Money,
    val remaining: Money,
    val periodStart: Instant,
)
