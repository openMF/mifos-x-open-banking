/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The domestic standing-order instruction.
 *
 * Nine body leaves where a single payment has thirty-four, and the omissions are the product rather
 * than an oversight: a mandate carries no `InstructionIdentification`, no `EndToEndIdentification`
 * and no `LocalInstrument`, because it is not one payment and does not pick a rail — the bank picks
 * one per instalment.
 *
 * Four fields an OBIE-shaped instinct will suggest are refused and must never be added here:
 * `MandateRelatedInformation.Reference` (not a v4.0 field at all), `Frequency.PointInTime`,
 * `MandateRelatedInformation.RecurringPaymentDateTime`, and `InstructedAmount` — this rail's amount
 * is [firstPaymentAmount], and sending the international rail's field is `U005`.
 */
@Serializable
data class Initiation(
    @SerialName("MandateRelatedInformation")
    val mandateRelatedInformation: MandateRelatedInformation? = null,
    @SerialName("FirstPaymentAmount")
    val firstPaymentAmount: FirstPaymentAmount? = null,
    @SerialName("RecurringPaymentAmount")
    val recurringPaymentAmount: RecurringPaymentAmount? = null,
    @SerialName("FinalPaymentAmount")
    val finalPaymentAmount: FinalPaymentAmount? = null,
    @SerialName("DebtorAccount")
    val debtorAccount: DebtorAccount? = null,
    @SerialName("CreditorAccount")
    val creditorAccount: CreditorAccount? = null,
    @SerialName("RemittanceInformation")
    val remittanceInformation: RemittanceInformation? = null,
)
