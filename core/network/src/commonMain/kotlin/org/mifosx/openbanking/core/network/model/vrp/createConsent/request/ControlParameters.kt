/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.network.model.vrp.createConsent.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ControlParameters(
    @SerialName("ValidFromDateTime")
    val validFromDateTime: String? = null,
    @SerialName("ValidToDateTime")
    val validToDateTime: String? = null,
    @SerialName("MaximumIndividualAmount")
    val maximumIndividualAmount: MaximumIndividualAmount,
    @SerialName("PSUAuthenticationMethods")
    val psuAuthenticationMethods: List<String>,
    @SerialName("PSUInteractionTypes")
    val psuInteractionTypes: List<String>? = null,
    @SerialName("PeriodicLimits")
    val periodicLimits: List<PeriodicLimit>,
    @SerialName("SupplementaryData")
    val supplementaryData: SupplementaryData? = null,
    @SerialName("VRPType")
    val vrpType: List<String>,
)
