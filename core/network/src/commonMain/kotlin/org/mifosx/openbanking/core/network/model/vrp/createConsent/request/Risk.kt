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
data class Risk(
    @SerialName("BeneficiaryAccountType")
    val beneficiaryAccountType: String? = null,
    @SerialName("BeneficiaryPrepopulatedIndicator")
    val beneficiaryPrepopulatedIndicator: Boolean? = null,
    @SerialName("DeliveryAddress")
    val deliveryAddress: DeliveryAddress? = null,
    @SerialName("MerchantCategoryCode")
    val merchantCategoryCode: String? = null,
    @SerialName("MerchantCustomerIdentification")
    val merchantCustomerIdentification: String? = null,
    @SerialName("PaymentContextCode")
    val paymentContextCode: String? = null,
    @SerialName("PaymentPurposeCode")
    val paymentPurposeCode: String? = null,
)
