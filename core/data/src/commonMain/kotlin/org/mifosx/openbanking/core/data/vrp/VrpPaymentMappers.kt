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

import org.mifosx.openbanking.core.model.banking.payment.PaymentStatus
import org.mifosx.openbanking.core.model.vrp.Money
import org.mifosx.openbanking.core.model.vrp.VrpCharge
import org.mifosx.openbanking.core.model.vrp.VrpConsent
import org.mifosx.openbanking.core.model.vrp.VrpPayment
import org.mifosx.openbanking.core.network.model.vrp.initiatePayment.request.CreditorAccount
import org.mifosx.openbanking.core.network.model.vrp.initiatePayment.request.Data
import org.mifosx.openbanking.core.network.model.vrp.initiatePayment.request.DebtorAccount
import org.mifosx.openbanking.core.network.model.vrp.initiatePayment.request.InitiatePayment
import org.mifosx.openbanking.core.network.model.vrp.initiatePayment.request.Initiation
import org.mifosx.openbanking.core.network.model.vrp.initiatePayment.request.InstructedAmount
import org.mifosx.openbanking.core.network.model.vrp.initiatePayment.request.Instruction
import org.mifosx.openbanking.core.network.model.vrp.initiatePayment.request.RemittanceInformation
import org.mifosx.openbanking.core.network.model.vrp.initiatePayment.request.Risk
import org.mifosx.openbanking.core.network.model.vrp.initiatePayment.response.InitiatePaymentResponse
import kotlin.time.Instant

/**
 * Builds a payment body for [amount] under this consent.
 *
 * The initiation is taken from the stored consent rather than rebuilt, so it matches what the
 * customer approved. A reference set on the consent is carried in both the initiation and the
 * instruction.
 *
 * @param instructionIdentification Identifies the instruction between this app and the bank.
 * @param endToEndIdentification Travels unchanged with the payment. Faster Payments carries 31
 *   characters.
 */
internal fun VrpConsent.toInitiatePayment(
    amount: Money,
    instructionIdentification: String,
    endToEndIdentification: String,
): InitiatePayment {
    val remittance = reference?.let { RemittanceInformation(unstructured = listOf(it)) }
    val creditor = CreditorAccount(
        schemeName = payee.schemeName,
        identification = payee.identification,
        name = payee.name,
        secondaryIdentification = payee.secondaryIdentification,
    )

    return InitiatePayment(
        data = Data(
            consentId = consentId,
            psuAuthenticationMethod = SCA_NOT_REQUIRED,
            vrpType = VRP_TYPE_SWEEPING,
            initiation = Initiation(
                debtorAccount = payer?.let {
                    DebtorAccount(
                        schemeName = it.schemeName,
                        identification = it.identification,
                        name = it.name,
                        secondaryIdentification = it.secondaryIdentification,
                    )
                },
                creditorAccount = creditor,
                remittanceInformation = remittance,
            ),
            instruction = Instruction(
                instructionIdentification = instructionIdentification,
                endToEndIdentification = endToEndIdentification,
                instructedAmount = InstructedAmount(
                    amount = amount.toWireAmount(),
                    currency = amount.currency,
                ),
                creditorAccount = creditor,
                remittanceInformation = remittance,
            ),
        ),
        risk = Risk(),
    )
}

/**
 * Reads a payment response into the domain.
 *
 * Returns null when the response carries no identifier, no creation time or no amount.
 *
 * @param localId The app's own identifier for this attempt.
 * @param syncedAt When this response was received.
 */
@Suppress("ReturnCount")
internal fun InitiatePaymentResponse.toVrpPayment(
    localId: String,
    syncedAt: Instant,
): VrpPayment? {
    val body = data ?: return null
    val paymentId = body.domesticVRPId?.takeIf { it.isNotBlank() } ?: return null
    val consentId = body.consentId?.takeIf { it.isNotBlank() } ?: return null
    val createdAt = wireInstant(body.creationDateTime) ?: return null
    val amount = wireMoney(
        body.instruction?.instructedAmount?.amount,
        body.instruction?.instructedAmount?.currency,
    ) ?: return null

    return VrpPayment(
        localId = localId,
        consentId = consentId,
        amount = amount,
        status = PaymentStatus.fromWire(body.status),
        createdAt = createdAt,
        paymentId = paymentId,
        submittedAt = createdAt,
        charges = body.charges.orEmpty().mapNotNull { charge ->
            val chargeAmount = wireMoney(charge.amount?.amount, charge.amount?.currency)
                ?: return@mapNotNull null
            VrpCharge(
                bearer = charge.chargeBearer.orEmpty(),
                type = charge.type.orEmpty(),
                amount = chargeAmount,
            )
        },
        reference = body.initiation?.remittanceInformation?.unstructured?.firstOrNull(),
        syncedAt = syncedAt,
    )
}
