/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.data.vrp

import org.mifosx.openbanking.core.model.vrp.FundsAvailability
import org.mifosx.openbanking.core.model.vrp.Money
import org.mifosx.openbanking.core.network.model.vrp.checkFunds.request.CheckFunds
import org.mifosx.openbanking.core.network.model.vrp.checkFunds.request.Data
import org.mifosx.openbanking.core.network.model.vrp.checkFunds.request.InstructedAmount
import org.mifosx.openbanking.core.network.model.vrp.checkFunds.response.CheckFundsResponse

/**
 * Builds a funds-confirmation body.
 *
 * The consent id in the body must be the one in the path.
 */
internal fun checkFundsRequest(
    consentId: String,
    amount: Money,
    reference: String? = null,
): CheckFunds = CheckFunds(
    data = Data(
        consentId = consentId,
        instructedAmount = InstructedAmount(
            amount = amount.toWireAmount(),
            currency = amount.currency,
        ),
        reference = reference,
    ),
)

/**
 * Reads a funds-confirmation response into the domain.
 *
 * Returns null when the response carries no answer, no amount or no time. A negative answer is a
 * successful call and maps to [FundsAvailability.available] being false.
 */
@Suppress("ReturnCount")
internal fun CheckFundsResponse.toFundsAvailability(): FundsAvailability? {
    val body = data ?: return null
    val result = body.fundsAvailableResult ?: return null
    val answer = result.fundsAvailable ?: return null
    val checkedAt = wireInstant(result.fundsAvailableDateTime)
        ?: wireInstant(body.creationDateTime)
        ?: return null
    val amount = wireMoney(
        body.instructedAmount?.amount,
        body.instructedAmount?.currency,
    ) ?: return null

    return FundsAvailability(
        available = answer.trim() == "Available",
        amount = amount,
        checkedAt = checkedAt,
    )
}
