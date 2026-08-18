/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.network.model.vrp.checkFunds.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Data(
    @SerialName("FundsConfirmationId")
    val fundsConfirmationId: String? = null,
    @SerialName("ConsentId")
    val consentId: String? = null,
    @SerialName("CreationDateTime")
    val creationDateTime: String? = null,
    @SerialName("Reference")
    val reference: String? = null,
    @SerialName("FundsAvailableResult")
    val fundsAvailableResult: FundsAvailableResult? = null,
    @SerialName("InstructedAmount")
    val instructedAmount: InstructedAmount? = null,
)
