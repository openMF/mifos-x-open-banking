/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.network.model.pisp.domesticPayment.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.mifosx.openbanking.core.network.model.ais.standingOrders.Structured

@Serializable
data class RemittanceInformation(
    @SerialName("Unstructured")
    val unstructured: List<String>? = null,
    // HSBC refuses a domestic consent without Structured[0].CreditorReferenceInformation.Reference (U004).
    @SerialName("Structured")
    val structured: List<Structured>? = null,
)
