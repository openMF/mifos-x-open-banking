/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The account the mandate pays from.
 *
 * Optional on both rails, and omitting it is a sanctioned shape rather than an oversight: the bank
 * then asks the customer to pick at its own site. That is the only route to a mandate funded by a
 * credit card or a Global Money wallet, both of which are refused when named here.
 */
@Serializable
data class DebtorAccount(
    @SerialName("SchemeName")
    val schemeName: String? = null,
    @SerialName("Identification")
    val identification: String? = null,
    @SerialName("Name")
    val name: String? = null,
)
