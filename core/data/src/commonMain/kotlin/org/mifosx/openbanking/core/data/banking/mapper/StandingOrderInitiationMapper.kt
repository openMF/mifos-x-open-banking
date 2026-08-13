/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.data.banking.mapper

import org.mifosx.openbanking.core.common.formatMinorUnits
import org.mifosx.openbanking.core.model.banking.BankAccount
import org.mifosx.openbanking.core.model.banking.payment.CreditorSelection
import org.mifosx.openbanking.core.model.banking.payment.PaymentCharge
import org.mifosx.openbanking.core.model.banking.payment.PaymentReceipt
import org.mifosx.openbanking.core.model.banking.payment.PaymentStatus
import org.mifosx.openbanking.core.model.banking.payment.StandingOrderDraft
import org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.request.CreditorAccount
import org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.request.Data
import org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.request.DebtorAccount
import org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.request.DomesticStandingOrderConsentRequest
import org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.request.DomesticStandingOrderRequest
import org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.request.FinalPaymentAmount
import org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.request.FirstPaymentAmount
import org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.request.Frequency
import org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.request.Initiation
import org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.request.MandateRelatedInformation
import org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.request.RecurringPaymentAmount
import org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.request.RemittanceInformation
import org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.request.Risk
import org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.response.Charge
import org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.response.DomesticStandingOrderConsentResponse
import org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.response.DomesticStandingOrderResponse

/**
 * The domestic standing-order rail's half of the OBIE mapping.
 *
 * A separate file from [ScheduledPaymentInitiationMapper.kt] for the same reason that one is separate
 * from [PaymentMapper.kt]: this rail's `Initiation`, `Data`, `Risk`, `CreditorAccount` and
 * `DebtorAccount` are distinct classes in a distinct package, structurally similar and not
 * interchangeable. Only the leaf helpers are shared — [toMajorUnitString], [toMinorUnits] and
 * [SCHEME_SORT_CODE].
 *
 * Four differences from the scheduled rail, each proven against the sandbox and each easy to get
 * wrong by copying:
 *  - The schedule lives in `MandateRelatedInformation`, not in a single `RequestedExecutionDateTime`.
 *  - The amount is `FirstPaymentAmount`, **not** `InstructedAmount` — sending the latter is `U005`.
 *  - `Risk` is empty. There is no `PaymentContextCode` on this product; the bank echoes `{}` back
 *    whatever is sent, so nothing is derived from whether the payee is the customer's own account.
 *  - No `InstructionIdentification`, `EndToEndIdentification` or `LocalInstrument`. A mandate is not
 *    one payment and does not pick a rail.
 */

/** Midnight UTC. Appended rather than carried on the draft so no timezone can enter the wire body. */
private const val MANDATE_TIME_SUFFIX = "T00:00:00+00:00"

/** OBIE restricts a standing-order consent's permission to this single value. */
private const val PERMISSION_CREATE = "Create"

/**
 * Builds the OBIE `Initiation` for [this] draft.
 *
 * Pure and total, for the same reason both sibling mappers are: the mandate is staged once and
 * submitted against the consent, and the two bodies must agree exactly or the bank refuses `U008`.
 * Nothing here reads a clock — both dates were fixed when the draft was made.
 */
internal fun StandingOrderDraft.toStandingOrderInitiation(): Initiation = Initiation(
    mandateRelatedInformation = MandateRelatedInformation(
        frequency = Frequency(type = frequency.wireValue),
        firstPaymentDateTime = firstPaymentDate + MANDATE_TIME_SUFFIX,
        // Omitted entirely for an open-ended mandate, which both rails accept.
        finalPaymentDateTime = finalPaymentDate?.let { it + MANDATE_TIME_SUFFIX },
    ),
    firstPaymentAmount = FirstPaymentAmount(
        amount = firstPaymentAmountMinorUnits.toMajorUnitString(),
        currency = currency,
    ),
    recurringPaymentAmount = recurringPaymentAmountMinorUnits?.let {
        RecurringPaymentAmount(amount = it.toMajorUnitString(), currency = currency)
    },
    finalPaymentAmount = finalPaymentAmountMinorUnits?.let {
        FinalPaymentAmount(amount = it.toMajorUnitString(), currency = currency)
    },
    // Absent when the customer chose to pick their account at the bank. On this product that is the
    // only route to a mandate funded by a card or a Global Money wallet, both refused when named.
    debtorAccount = debtorAccount?.toStandingOrderObieDebtor(),
    creditorAccount = creditor.toStandingOrderObieCreditor(),
    remittanceInformation = reference
        ?.takeIf { it.isNotBlank() }
        ?.let { RemittanceInformation(unstructured = listOf(it)) },
)

/** The staging body. Carries `Permission`, which the submission must not. */
internal fun StandingOrderDraft.toStandingOrderConsentRequest(): DomesticStandingOrderConsentRequest =
    DomesticStandingOrderConsentRequest(
        data = Data(permission = PERMISSION_CREATE, initiation = toStandingOrderInitiation()),
        risk = Risk,
    )

/**
 * The submission body: the same initiation, now bound to the authorised [consentId].
 *
 * `Permission` is deliberately absent. The request `Data` class is shared between the consent and the
 * resource on this rail, so the field is reachable here and sending it would diverge the two bodies —
 * the one thing the byte-identity rule forbids.
 */
internal fun StandingOrderDraft.toStandingOrderRequest(consentId: String): DomesticStandingOrderRequest =
    DomesticStandingOrderRequest(
        data = Data(consentId = consentId, initiation = toStandingOrderInitiation()),
        risk = Risk,
    )

private fun BankAccount.toStandingOrderObieDebtor(): DebtorAccount = DebtorAccount(
    schemeName = SCHEME_SORT_CODE,
    identification = rawIdentification.takeIf { it.isNotBlank() } ?: (sortCode + accountNumber),
    name = nickname.takeIf { it.isNotBlank() },
)

/**
 * Always a sort code and account number.
 *
 * This rail refuses an IBAN creditor with `U027`, so the scheme is stated rather than derived from the
 * beneficiary's own — mapping that would let a Paym or card payee reach an endpoint that cannot
 * accept one.
 */
private fun CreditorSelection.toStandingOrderObieCreditor(): CreditorAccount = CreditorAccount(
    schemeName = SCHEME_SORT_CODE,
    identification = identification,
    name = name.takeIf { it.isNotBlank() },
)

internal fun DomesticStandingOrderConsentResponse.standingOrderConsentIdOrNull(): String? =
    data?.consentId?.takeIf { it.isNotBlank() }

internal fun DomesticStandingOrderConsentResponse.standingOrderStatusOrEmpty(): String =
    data?.status.orEmpty()

/** The charges the bank declared at staging, which on this rail it does before the customer authorises. */
internal fun DomesticStandingOrderConsentResponse.standingOrderConsentCharges(): List<PaymentCharge> =
    data?.charges.orEmpty().map { it.toStandingOrderPaymentCharge() }

/**
 * The created mandate as a receipt.
 *
 * [PaymentReceipt.settlementDateTime] is left empty, and here that is not a workaround but the truth:
 * a mandate has no single settlement. [PaymentReceipt.requestedExecutionDateTime] carries the first
 * payment date, which is the one date on this product the bank states and the customer chose.
 */
internal fun DomesticStandingOrderResponse.toStandingOrderReceipt(): PaymentReceipt {
    val initiation = data?.initiation
    val amount = initiation?.firstPaymentAmount
    return PaymentReceipt(
        domesticPaymentId = data?.domesticStandingOrderId.orEmpty(),
        consentId = data?.consentId.orEmpty(),
        status = PaymentStatus.fromWire(data?.status),
        creationDateTime = data?.creationDateTime.orEmpty(),
        statusUpdateDateTime = data?.statusUpdateDateTime.orEmpty(),
        amountLabel = formatMinorUnits(
            minorUnits = amount?.amount.toMinorUnits(),
            currency = amount?.currency.orEmpty(),
        ),
        creditorName = initiation?.creditorAccount?.name.orEmpty(),
        settlementDateTime = "",
        requestedExecutionDateTime = initiation
            ?.mandateRelatedInformation
            ?.firstPaymentDateTime
            .orEmpty(),
        reference = initiation?.remittanceInformation?.unstructured?.firstOrNull().orEmpty(),
        debtorIdentification = initiation?.debtorAccount?.identification.orEmpty(),
        charges = data?.charges.orEmpty().map { it.toStandingOrderPaymentCharge() },
    )
}

private fun Charge.toStandingOrderPaymentCharge(): PaymentCharge = PaymentCharge(
    bearer = chargeBearer.orEmpty(),
    typeLabel = type.orEmpty(),
    amountLabel = formatMinorUnits(
        minorUnits = amount?.amount.toMinorUnits(),
        currency = amount?.currency.orEmpty(),
    ),
)
