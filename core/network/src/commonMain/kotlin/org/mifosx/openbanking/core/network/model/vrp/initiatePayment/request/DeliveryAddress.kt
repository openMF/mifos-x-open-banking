/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.network.model.vrp.initiatePayment.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeliveryAddress(
    @SerialName("AddressLine")
    val addressLine: List<String>? = null,
    @SerialName("BuildingNumber")
    val buildingNumber: String? = null,
    @SerialName("Country")
    val country: String? = null,
    @SerialName("CountrySubDivision")
    val countrySubDivision: String? = null,
    @SerialName("PostCode")
    val postCode: String? = null,
    @SerialName("StreetName")
    val streetName: String? = null,
    @SerialName("TownName")
    val townName: String? = null,
)
