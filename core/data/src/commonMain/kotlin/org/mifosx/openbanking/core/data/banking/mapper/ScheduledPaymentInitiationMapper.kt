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
import org.mifosx.openbanking.core.network.model.pisp.domesticScheduledPayment.request.CreditorAccount
import org.mifosx.openbanking.core.network.model.pisp.domesticScheduledPayment.request.Data
import org.mifosx.openbanking.core.network.model.pisp.domesticScheduledPayment.request.DebtorAccount
import org.mifosx.openbanking.core.network.model.pisp.domesticScheduledPayment.request.DomesticScheduledPaymentConsentRequest
import org.mifosx.openbanking.core.network.model.pisp.domesticScheduledPayment.request.DomesticScheduledPaymentRequest
import org.mifosx.openbanking.core.network.model.pisp.domesticScheduledPayment.request.Initiation
import org.mifosx.openbanking.core.network.model.pisp.domesticScheduledPayment.request.InstructedAmount
import org.mifosx.openbanking.core.network.model.pisp.domesticScheduledPayment.request.RemittanceInformation
import org.mifosx.openbanking.core.network.model.pisp.domesticScheduledPayment.request.Risk
import org.mifosx.openbanking.core.network.model.pisp.domesticScheduledPayment.response.Charge
import org.mifosx.openbanking.core.network.model.pisp.domesticScheduledPayment.response.DomesticScheduledPaymentConsentResponse
import org.mifosx.openbanking.core.network.model.pisp.domesticScheduledPayment.response.DomesticScheduledPaymentResponse

/**
 * The domestic scheduled rail's half of the OBIE mapping.
 *
 * A separate file from [PaymentMapper.kt] because none of the bodies can be shared: this rail's
 * `Initiation`, `Data`, `Risk`, `CreditorAccount` and `DebtorAccount` are distinct classes in a
 * distinct package, structurally similar and not interchangeable. Only the leaf helpers are reused —
 * [toMajorUnitString], [toMinorUnits], [SCHEME_SORT_CODE] and the two payment-context constants.
 *
 * Three differences from the immediate rail, each proven against the sandbox and each easy to get
 * wrong by copying:
 *  - `Data.Permission` is mandatory and must be `"Create"`. The immediate rail has no such field, so
 *    copying it omits this and earns `400 U004`.
 *  - **No `LocalInstrument`.** The proven consent bodies carry no such key.
 *  - `RequestedExecutionDateTime` is mandatory, and is built here from the draft's plain date so the
 *    consent and the submission are byte-identical by construction.
 */

/** Midnight UTC. Appended rather than carried on the draft so no timezone can enter the wire body. */
private const val EXECUTION_TIME_SUFFIX = "T00:00:00+00:00"

/** OBIE restricts a scheduled consent's permission to this single value. */
private const val PERMISSION_CREATE = "Create"

/**
 * Builds the OBIE `Initiation` for [this] draft.
 *
 * Pure and total, for the same reason the immediate rail's is: the instruction is staged once and
 * submitted against the consent, and the two bodies must agree exactly or the bank refuses `U008`.
 * Nothing here reads a clock — the execution date was fixed when the draft was made.
 */
internal fun ScheduledPaymentDraft.toScheduledInitiation(): Initiation = Initiation(
    instructionIdentification = instructionIdentification,
    endToEndIdentification = endToEndIdentification,
    requestedExecutionDateTime = requestedExecutionDate + EXECUTION_TIME_SUFFIX,
    instructedAmount = InstructedAmount(
        amount = amountMinorUnits.toMajorUnitString(),
        currency = currency,
    ),
    // Absent when the PSU chose to pick their account at the bank. On this rail that is the only
    // route to paying from a card or a Global Money wallet, both of which are refused when named.
    debtorAccount = debtorAccount?.toScheduledObieDebtor(),
    creditorAccount = creditor.toScheduledObieCreditor(),
    remittanceInformation = reference
        ?.takeIf { it.isNotBlank() }
        ?.let { RemittanceInformation(unstructured = listOf(it)) },
)

/** Payment context and nothing else, exactly as on the immediate domestic rail. */
internal fun ScheduledPaymentDraft.toScheduledRisk(): Risk = Risk(
    paymentContextCode = if (creditor.isOwnAccount) {
        CONTEXT_TRANSFER_TO_SELF
    } else {
        CONTEXT_TRANSFER_TO_THIRD_PARTY
    },
)

/** The staging body. Carries `Permission`, which the submission must not. */
internal fun ScheduledPaymentDraft.toScheduledConsentRequest(): DomesticScheduledPaymentConsentRequest =
    DomesticScheduledPaymentConsentRequest(
        data = Data(permission = PERMISSION_CREATE, initiation = toScheduledInitiation()),
        risk = toScheduledRisk(),
    )

/**
 * The submission body: the same initiation, now bound to the authorised [consentId].
 *
 * `Permission` is deliberately absent. The request `Data` class is shared between the consent and
 * the payment on this rail, so the field is reachable here and sending it would diverge the two
 * bodies — which is the one thing the byte-identity rule forbids.
 */
internal fun ScheduledPaymentDraft.toScheduledPaymentRequest(consentId: String): DomesticScheduledPaymentRequest =
    DomesticScheduledPaymentRequest(
        data = Data(consentId = consentId, initiation = toScheduledInitiation()),
        risk = toScheduledRisk(),
    )

private fun BankAccount.toScheduledObieDebtor(): DebtorAccount = DebtorAccount(
    schemeName = SCHEME_SORT_CODE,
    identification = rawIdentification.takeIf { it.isNotBlank() } ?: (sortCode + accountNumber),
    name = nickname.takeIf { it.isNotBlank() },
)

/**
 * Always a sort code and account number.
 *
 * This rail refuses an IBAN creditor with `U027`, so the scheme is stated rather than derived from
 * the beneficiary's own. Mapping the beneficiary's scheme here would let a Paym or card payee reach
 * an endpoint that cannot accept one.
 */
private fun CreditorSelection.toScheduledObieCreditor(): CreditorAccount =
    CreditorAccount(
        schemeName = SCHEME_SORT_CODE,
        identification = identification,
        name = name.takeIf { it.isNotBlank() },
    )

internal fun DomesticScheduledPaymentConsentResponse.scheduledConsentIdOrNull(): String? =
    data?.consentId?.takeIf { it.isNotBlank() }

internal fun DomesticScheduledPaymentConsentResponse.scheduledStatusOrEmpty(): String = data?.status.orEmpty()

/** The charges the bank declared at staging, which on this rail it does before the PSU authorises. */
internal fun DomesticScheduledPaymentConsentResponse.scheduledConsentCharges(): List<PaymentCharge> =
    data?.charges.orEmpty().map { it.toScheduledPaymentCharge() }

/**
 * The created payment as a receipt.
 *
 * [PaymentReceipt.settlementDateTime] is left empty on purpose. The bank does return an
 * `ExpectedSettlementDateTime`, but it equals `CreationDateTime` — today — rather than the requested
 * date, so mapping it would render a payment due next week as settling now. The only truthful date
 * is the requested execution date, which travels in its own field.
 */
internal fun DomesticScheduledPaymentResponse.toScheduledPaymentReceipt(): PaymentReceipt {
    val initiation = data?.initiation
    val amount = initiation?.instructedAmount
    return PaymentReceipt(
        domesticPaymentId = data?.domesticScheduledPaymentId.orEmpty(),
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
        reference = initiation?.remittanceInformation?.unstructured?.firstOrNull().orEmpty(),
        debtorIdentification = initiation?.debtorAccount?.identification.orEmpty(),
        charges = data?.charges.orEmpty().map { it.toScheduledPaymentCharge() },
    )
}

private fun Charge.toScheduledPaymentCharge(): PaymentCharge = PaymentCharge(
    bearer = chargeBearer.orEmpty(),
    typeLabel = type.orEmpty(),
    amountLabel = formatMinorUnits(
        minorUnits = amount?.amount.toMinorUnits(),
        currency = amount?.currency.orEmpty(),
    ),
)
