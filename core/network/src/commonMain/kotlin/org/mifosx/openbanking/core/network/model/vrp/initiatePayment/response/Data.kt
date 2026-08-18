/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.network.model.vrp.initiatePayment.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Data(
    @SerialName("DomesticVRPId")
    val domesticVRPId: String? = null,
    @SerialName("ConsentId")
    val consentId: String? = null,
    @SerialName("CreationDateTime")
    val creationDateTime: String? = null,
    @SerialName("Status")
    val status: String? = null,
    @SerialName("StatusUpdateDateTime")
    val statusUpdateDateTime: String? = null,
    @SerialName("StatusReason")
    val statusReason: List<StatusReason>? = null,
    @SerialName("ExpectedExecutionDateTime")
    val expectedExecutionDateTime: String? = null,
    @SerialName("ExpectedSettlementDateTime")
    val expectedSettlementDateTime: String? = null,
    @SerialName("Refund")
    val refund: DebtorAccount? = null,
    @SerialName("Charges")
    val charges: List<Charge>? = null,
    @SerialName("Initiation")
    val initiation: Initiation? = null,
    @SerialName("Instruction")
    val instruction: Instruction? = null,
)
