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
    @SerialName("ReadRefundAccount")
    val readRefundAccount: String? = null,
    @SerialName("Initiation")
    val initiation: Initiation? = null,
    @SerialName("Authorisation")
    val authorisation: Authorisation? = null,
    @SerialName("SCASupportData")
    val scaSupportData: SCASupportData? = null,
    @SerialName("InternationalScheduledPaymentId")
    val internationalScheduledPaymentId: String? = null,
    /**
     * The charges the bank applies. **Absent at consent staging on this rail** and present only once
     * the payment resource is created — which is after the customer has authorised. The review screen
     * therefore cannot state a figure, and must say the bank will confirm its charge instead.
     *
     * `ExpectedSettlementDateTime` is deliberately NOT modelled — see the domestic sibling.
     */
    @SerialName("Charges")
    val charges: List<Charge>? = null,
    @SerialName("ExpectedExecutionDateTime")
    val expectedExecutionDateTime: String? = null,
)
