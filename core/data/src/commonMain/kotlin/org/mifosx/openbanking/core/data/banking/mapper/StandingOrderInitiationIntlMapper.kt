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
import org.mifosx.openbanking.core.network.model.pisp.internationalStandingOrder.request.CreditorAccount
import org.mifosx.openbanking.core.network.model.pisp.internationalStandingOrder.request.Data
import org.mifosx.openbanking.core.network.model.pisp.internationalStandingOrder.request.DebtorAccount
import org.mifosx.openbanking.core.network.model.pisp.internationalStandingOrder.request.Frequency
import org.mifosx.openbanking.core.network.model.pisp.internationalStandingOrder.request.Initiation
import org.mifosx.openbanking.core.network.model.pisp.internationalStandingOrder.request.InstructedAmount
import org.mifosx.openbanking.core.network.model.pisp.internationalStandingOrder.request.InternationalStandingOrderConsentRequest
import org.mifosx.openbanking.core.network.model.pisp.internationalStandingOrder.request.InternationalStandingOrderRequest
import org.mifosx.openbanking.core.network.model.pisp.internationalStandingOrder.request.MandateRelatedInformation
import org.mifosx.openbanking.core.network.model.pisp.internationalStandingOrder.request.Risk
import org.mifosx.openbanking.core.network.model.pisp.internationalStandingOrder.response.Charge
import org.mifosx.openbanking.core.network.model.pisp.internationalStandingOrder.response.InternationalStandingOrderConsentResponse
import org.mifosx.openbanking.core.network.model.pisp.internationalStandingOrder.response.InternationalStandingOrderResponse

/**
 * The international standing-order rail's half of the OBIE mapping.
 *
 * Mirrors [StandingOrderInitiationMapper.kt] and inverts four of its rules, each proven:
 *  - **One amount, not three.** `InstructedAmount` replaces `FirstPaymentAmount`, and there is no
 *    recurring or final amount member at all — the form disables those fields rather than hiding them.
 *  - **No `RemittanceInformation`.** Refused `U005` here; accepted on the domestic rail.
 *  - **`ChargeBearer` is mandatory** — omitting it is `400 U004`.
 *  - **The creditor is an IBAN.** A sort-code creditor is refused `U027`, exactly as an IBAN is on
 *    the domestic rail.
 *
 * `Risk` is empty on both standing-order rails, so — unlike the scheduled rails, which differ —
 * there is nothing to invert here.
 *
 * No `ExchangeRateInformation` is ever sent: it is not a member of `OBInternationalStandingOrder4`,
 * and every `RateType` tried was refused `U005`. There is therefore no rate to request and no
 * "recipient receives" figure that could honestly be displayed.
 */

private const val MANDATE_TIME_SUFFIX = "T00:00:00+00:00"
private const val PERMISSION_CREATE = "Create"

internal fun StandingOrderDraft.toIntlStandingOrderInitiation(): Initiation = Initiation(
    mandateRelatedInformation = MandateRelatedInformation(
        frequency = Frequency(type = frequency.wireValue),
        firstPaymentDateTime = firstPaymentDate + MANDATE_TIME_SUFFIX,
        finalPaymentDateTime = finalPaymentDate?.let { it + MANDATE_TIME_SUFFIX },
    ),
    instructedAmount = InstructedAmount(
        amount = firstPaymentAmountMinorUnits.toMajorUnitString(),
        currency = currency,
    ),
    currencyOfTransfer = currencyOfTransfer,
    chargeBearer = chargeBearer?.wireValue,
    debtorAccount = debtorAccount?.toIntlStandingOrderObieDebtor(),
    creditorAccount = creditor.toIntlStandingOrderObieCreditor(),
)

internal fun StandingOrderDraft.toIntlStandingOrderConsentRequest(): InternationalStandingOrderConsentRequest =
    InternationalStandingOrderConsentRequest(
        data = Data(permission = PERMISSION_CREATE, initiation = toIntlStandingOrderInitiation()),
        risk = Risk,
    )

internal fun StandingOrderDraft.toIntlStandingOrderRequest(
    consentId: String,
): InternationalStandingOrderRequest =
    InternationalStandingOrderRequest(
        data = Data(consentId = consentId, initiation = toIntlStandingOrderInitiation()),
        risk = Risk,
    )

/**
 * Null when the account carries no raw identification.
 *
 * Matches the international scheduled rail: a debtor with nothing to identify it is omitted rather
 * than sent half-formed, which the bank reads as "you choose" instead of refusing the consent.
 */
private fun BankAccount.toIntlStandingOrderObieDebtor(): DebtorAccount? =
    rawIdentification.takeIf { it.isNotBlank() }?.let {
        DebtorAccount(schemeName = SCHEME_SORT_CODE, identification = it)
    }

/** Always an IBAN — a sort-code creditor is refused `U027` on this rail. */
private fun CreditorSelection.toIntlStandingOrderObieCreditor(): CreditorAccount = CreditorAccount(
    schemeName = SCHEME_IBAN,
    identification = identification,
    name = name.takeIf { it.isNotBlank() },
)

internal fun InternationalStandingOrderConsentResponse.intlStandingOrderConsentIdOrNull(): String? =
    data?.consentId?.takeIf { it.isNotBlank() }

internal fun InternationalStandingOrderConsentResponse.intlStandingOrderStatusOrEmpty(): String =
    data?.status.orEmpty()

/**
 * The created mandate as a receipt.
 *
 * This is the first point at which the bank states a charge on this rail — the consent response
 * carries none — and it is already after the customer authorised. So the figure exists here and could
 * not have been shown on the review screen, which is why that screen says a fee may apply rather than
 * quoting a number.
 */
internal fun InternationalStandingOrderResponse.toIntlStandingOrderReceipt(): PaymentReceipt {
    val initiation = data?.initiation
    val amount = initiation?.instructedAmount
    return PaymentReceipt(
        domesticPaymentId = data?.internationalStandingOrderId.orEmpty(),
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
        reference = "",
        debtorIdentification = initiation?.debtorAccount?.identification.orEmpty(),
        charges = data?.charges.orEmpty().map { it.toIntlStandingOrderPaymentCharge() },
        frequency = initiation?.mandateRelatedInformation?.frequency?.type.orEmpty(),
        finalPaymentDateTime = initiation
            ?.mandateRelatedInformation
            ?.finalPaymentDateTime
            .orEmpty(),
    )
}

private fun Charge.toIntlStandingOrderPaymentCharge(): PaymentCharge = PaymentCharge(
    bearer = chargeBearer.orEmpty(),
    typeLabel = type.orEmpty(),
    amountLabel = formatMinorUnits(
        minorUnits = amount?.amount.toMinorUnits(),
        currency = amount?.currency.orEmpty(),
    ),
)
