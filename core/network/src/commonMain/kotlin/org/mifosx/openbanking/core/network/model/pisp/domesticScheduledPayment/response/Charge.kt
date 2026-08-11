/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.network.model.pisp.domesticScheduledPayment.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One charge the ASPSP applies to a scheduled payment.
 *
 * The domestic rail declares this at consent staging — `0.05 GBP UK.OBIE.CHAPSOut` in the sandbox —
 * so it is known before the customer authorises, and the review screen may state it.
 *
 * @property chargeBearer Who pays it, e.g. `BorneByDebtor`.
 * @property type The OBIE charge type, e.g. `UK.OBIE.CHAPSOut`. It need not correspond to any
 *   `LocalInstrument`: the scheduled rails send none at all, and the sandbox still returns a CHAPS
 *   charge.
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
