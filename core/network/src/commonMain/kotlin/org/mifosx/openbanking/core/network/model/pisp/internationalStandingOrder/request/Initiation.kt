/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.network.model.pisp.internationalStandingOrder.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The international standing-order instruction.
 *
 * One amount, not three: this rail defines no `FirstPaymentAmount`, `RecurringPaymentAmount` or
 * `FinalPaymentAmount`, and sending the domestic rail's field is `U005`. It also refuses
 * `RemittanceInformation` and `ExchangeRateInformation` — the latter is not a member of
 * `OBInternationalStandingOrder4` at all, so no rate can be requested and none can be displayed.
 */
@Serializable
data class Initiation(
    @SerialName("MandateRelatedInformation")
    val mandateRelatedInformation: MandateRelatedInformation? = null,
    @SerialName("InstructedAmount")
    val instructedAmount: InstructedAmount? = null,
    @SerialName("DebtorAccount")
    val debtorAccount: DebtorAccount? = null,
    @SerialName("CreditorAccount")
    val creditorAccount: CreditorAccount? = null,
    @SerialName("CurrencyOfTransfer")
    val currencyOfTransfer: String? = null,
    @SerialName("ChargeBearer")
    val chargeBearer: String? = null,
)
