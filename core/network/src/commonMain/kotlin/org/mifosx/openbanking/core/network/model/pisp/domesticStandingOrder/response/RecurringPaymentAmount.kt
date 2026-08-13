/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The amount of every payment after the first, when it differs.
 *
 * Domestic only — the international initiation carries a single `InstructedAmount` and defines no
 * such member, so sending it there is refused `U005`.
 */
@Serializable
data class RecurringPaymentAmount(
    @SerialName("Amount")
    val amount: String? = null,
    @SerialName("Currency")
    val currency: String? = null,
)
