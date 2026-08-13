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
 * One charge the ASPSP applies to a standing order.
 *
 * Declared at consent staging on the domestic rail — `0.05 GBP UK.OBIE.CHAPSOut` in the sandbox — so
 * the review screen may state it. The international rail returns none until the resource is created,
 * which is after the customer has authorised.
 *
 * @property type The OBIE charge type. It need not describe the settlement rail: the sandbox returns
 *   a CHAPS charge on a weekly standing order, which is not a CHAPS payment.
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
