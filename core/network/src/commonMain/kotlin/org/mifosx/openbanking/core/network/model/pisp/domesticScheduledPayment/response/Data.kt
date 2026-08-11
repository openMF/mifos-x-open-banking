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
    @SerialName("DomesticScheduledPaymentId")
    val domesticScheduledPaymentId: String? = null,
    /**
     * The charges the bank applies. Present from consent staging onwards on this rail.
     *
     * `ExpectedSettlementDateTime` is deliberately NOT modelled: the sandbox returns it equal to
     * `CreationDateTime` — today — not the requested execution date, so mapping it would render a
     * payment due next week as settled today. The only truthful date is
     * [Initiation.requestedExecutionDateTime].
     */
    @SerialName("Charges")
    val charges: List<Charge>? = null,
    @SerialName("ExpectedExecutionDateTime")
    val expectedExecutionDateTime: String? = null,
)
