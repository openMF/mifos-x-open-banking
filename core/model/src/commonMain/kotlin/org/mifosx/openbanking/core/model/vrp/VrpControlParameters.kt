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
 * A cap on the total paid within one period.
 *
 * @property periodType The period the cap is measured over.
 * @property amount The most that may be paid within one such period. Enforced on the running total,
 *   so a small payment is refused once the period is nearly full.
 */
@Serializable
data class PeriodicLimit(
    val periodType: PeriodType,
    val amount: Money,
)

/**
 * The limits the customer approved. They bound this consent, not the account behind it.
 *
 * @property maximumIndividualAmount The most any single payment may be. Checked before
 *   [periodicLimit].
 * @property periodicLimit The recurring cap. Exactly one: the API permits up to six, one per period
 *   type, but this feature offers a single cap.
 * @property interactionType Whether the customer is expected to be present, as echoed by the bank —
 *   `InSession` or `OffSession`. Fixed at staging; there is no amend endpoint.
 */
@Serializable
data class VrpControlParameters(
    val maximumIndividualAmount: Money,
    val periodicLimit: PeriodicLimit,
    val interactionType: String? = null,
)
