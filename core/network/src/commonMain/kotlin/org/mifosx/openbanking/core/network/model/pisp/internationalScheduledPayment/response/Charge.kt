/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.network.model.pisp.internationalScheduledPayment.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One charge the ASPSP applies to an international scheduled payment.
 *
 * Unlike the domestic rail, this rail declares **nothing at consent staging** and returns the charge
 * only when the payment resource is created — `0.50 GBP UK.OBIE.CHAPSOut BorneByDebtor` in the
 * sandbox. Creation happens after the customer has authorised at the bank, so this figure cannot be
 * shown on the review screen; it is only knowable afterwards. Modelling it here is what stops the app
 * silently discarding a charge the bank did declare.
 *
 * @property chargeBearer Who pays it, e.g. `BorneByDebtor`.
 * @property type The OBIE charge type, e.g. `UK.OBIE.CHAPSOut`.
 */
@Serializable
data class Charge(
    @SerialName("ChargeBearer")
    val chargeBearer: String? = null,
    @SerialName("Type")
    val type: String? = null,
    @SerialName("Amount")
    val amount: ChargeAmount? = null,
)

@Serializable
data class ChargeAmount(
    @SerialName("Amount")
    val amount: String? = null,
    @SerialName("Currency")
    val currency: String? = null,
)
