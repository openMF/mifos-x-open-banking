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
import org.mifosx.openbanking.core.model.banking.payment.ScheduledPaymentDraft
import org.mifosx.openbanking.core.network.model.pisp.internationalScheduledPayment.request.CreditorAccount
import org.mifosx.openbanking.core.network.model.pisp.internationalScheduledPayment.request.Data
import org.mifosx.openbanking.core.network.model.pisp.internationalScheduledPayment.request.DebtorAccount
import org.mifosx.openbanking.core.network.model.pisp.internationalScheduledPayment.request.Initiation
import org.mifosx.openbanking.core.network.model.pisp.internationalScheduledPayment.request.InstructedAmount
import org.mifosx.openbanking.core.network.model.pisp.internationalScheduledPayment.request.InternationalScheduledPaymentConsentRequest
import org.mifosx.openbanking.core.network.model.pisp.internationalScheduledPayment.request.InternationalScheduledPaymentRequest
import org.mifosx.openbanking.core.network.model.pisp.internationalScheduledPayment.request.Risk
import org.mifosx.openbanking.core.network.model.pisp.internationalScheduledPayment.response.Charge
import org.mifosx.openbanking.core.network.model.pisp.internationalScheduledPayment.response.InternationalScheduledPaymentConsentResponse
import org.mifosx.openbanking.core.network.model.pisp.internationalScheduledPayment.response.InternationalScheduledPaymentResponse

/**
 * The international scheduled rail's half of the OBIE mapping.
 *
 * Mirrors [ScheduledPaymentInitiationMapper.kt] and inverts three of its rules, each proven:
 *  - **No `RemittanceInformation`.** This rail refuses it with `U005`; the domestic one accepts it.
 *  - **`ChargeBearer` is mandatory** — omitting it is `400 U004`. Only `BorneByCreditor` has ever
 *    been sent on a scheduled consent, so nothing else is offered.
 *  - **The creditor is an IBAN.** A sort-code creditor is refused `U027` here, exactly as an IBAN is
 *    refused on the domestic rail.
 *
 * `Risk` carries only `CategoryPurposeCode` on this rail — no payment context code, which is the
 * domestic shape.
 *
 * No `ExchangeRateInformation` is ever sent: every `RateType` is refused `U005`, so no rate can be
 * requested and no "recipient receives" figure exists to display.
 */

private const val EXECUTION_TIME_SUFFIX = "T00:00:00+00:00"
private const val PERMISSION_CREATE = "Create"

internal fun ScheduledPaymentDraft.toIntlScheduledInitiation(): Initiation = Initiation(
    instructionIdentification = instructionIdentification,
    endToEndIdentification = endToEndIdentification,
    requestedExecutionDateTime = requestedExecutionDate + EXECUTION_TIME_SUFFIX,
    currencyOfTransfer = currencyOfTransfer,
    chargeBearer = chargeBearer?.wireValue,
    instructedAmount = InstructedAmount(
        amount = amountMinorUnits.toMajorUnitString(),
        currency = currency,
    ),
    debtorAccount = debtorAccount?.toIntlScheduledObieDebtor(),
    creditorAccount = creditor.toIntlScheduledObieCreditor(),
)

internal fun ScheduledPaymentDraft.toIntlScheduledRisk(): Risk = Risk(
    categoryPurposeCode = RISK_CATEGORY_EPAY,
)

internal fun ScheduledPaymentDraft.toIntlScheduledConsentRequest(): InternationalScheduledPaymentConsentRequest =
    InternationalScheduledPaymentConsentRequest(
        data = Data(permission = PERMISSION_CREATE, initiation = toIntlScheduledInitiation()),
        risk = toIntlScheduledRisk(),
    )

internal fun ScheduledPaymentDraft.toIntlScheduledPaymentRequest(
    consentId: String,
): InternationalScheduledPaymentRequest =
    InternationalScheduledPaymentRequest(
        data = Data(consentId = consentId, initiation = toIntlScheduledInitiation()),
        risk = toIntlScheduledRisk(),
    )

/**
 * Null when the account carries no raw identification.
 *
 * Matches the immediate international rail: a debtor with nothing to identify it is omitted rather
 * than sent half-formed, which the bank reads as "you choose" instead of refusing the consent.
 */
private fun BankAccount.toIntlScheduledObieDebtor(): DebtorAccount? =
    rawIdentification.takeIf { it.isNotBlank() }?.let {
        DebtorAccount(schemeName = SCHEME_SORT_CODE, identification = it)
    }

/** Always an IBAN — a sort-code creditor is refused `U027` on this rail. */
private fun CreditorSelection.toIntlScheduledObieCreditor(): CreditorAccount = CreditorAccount(
    schemeName = SCHEME_IBAN,
    identification = identification,
    name = name.takeIf { it.isNotBlank() },
)

internal fun InternationalScheduledPaymentConsentResponse.intlScheduledConsentIdOrNull(): String? =
    data?.consentId?.takeIf { it.isNotBlank() }

internal fun InternationalScheduledPaymentConsentResponse.intlScheduledStatusOrEmpty(): String =
    data?.status.orEmpty()

/**
 * The created payment as a receipt.
 *
 * This is the first point at which the bank states a charge on this rail — the consent response
 * carries none — and it is already after the PSU authorised. So the figure exists here and could not
 * have been shown on the review screen, which is why that screen says the bank will confirm it
 * rather than quoting a number.
 *
 * [PaymentReceipt.settlementDateTime] is left empty for the same reason as the domestic rail.
 */
internal fun InternationalScheduledPaymentResponse.toIntlScheduledPaymentReceipt(): PaymentReceipt {
    val initiation = data?.initiation
    val amount = initiation?.instructedAmount
    return PaymentReceipt(
        domesticPaymentId = data?.internationalScheduledPaymentId.orEmpty(),
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
        requestedExecutionDateTime = initiation?.requestedExecutionDateTime.orEmpty(),
        reference = "",
        debtorIdentification = initiation?.debtorAccount?.identification.orEmpty(),
        charges = data?.charges.orEmpty().map { it.toIntlScheduledPaymentCharge() },
    )
}

private fun Charge.toIntlScheduledPaymentCharge(): PaymentCharge = PaymentCharge(
    bearer = chargeBearer.orEmpty(),
    typeLabel = type.orEmpty(),
    amountLabel = formatMinorUnits(
        minorUnits = amount?.amount.toMinorUnits(),
        currency = amount?.currency.orEmpty(),
    ),
)
