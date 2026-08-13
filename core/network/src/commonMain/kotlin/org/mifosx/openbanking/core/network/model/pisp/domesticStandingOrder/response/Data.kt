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

@Serializable
data class Data(
    @SerialName("ConsentId")
    val consentId: String? = null,
    @SerialName("Status")
    val status: String? = null,
    @SerialName("CreationDateTime")
    val creationDateTime: String? = null,
    @SerialName("StatusUpdateDateTime")
    val statusUpdateDateTime: String? = null,
    @SerialName("Permission")
    val permission: String? = null,
    @SerialName("Initiation")
    val initiation: Initiation? = null,
    @SerialName("DomesticStandingOrderId")
    val domesticStandingOrderId: String? = null,
    /**
     * The charges the bank applies. Present from consent staging onwards on this rail, so the review
     * screen may state the fee before the customer commits.
     *
     * `CutOffDateTime` is deliberately not modelled: it is returned equal to `CreationDateTime` and
     * says nothing about when the mandate runs.
     */
    @SerialName("Charges")
    val charges: List<Charge>? = null,
)
