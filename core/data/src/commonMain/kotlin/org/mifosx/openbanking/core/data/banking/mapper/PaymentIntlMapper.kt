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
import org.mifosx.openbanking.core.model.banking.payment.PaymentDraft
import org.mifosx.openbanking.core.model.banking.payment.PaymentReceipt
import org.mifosx.openbanking.core.model.banking.payment.PaymentStatus
import org.mifosx.openbanking.core.network.model.pisp.internationalPayment.request.InternationalPaymentConsentRequest
import org.mifosx.openbanking.core.network.model.pisp.internationalPayment.request.InternationalPaymentRequest
import org.mifosx.openbanking.core.network.model.pisp.internationalPayment.response.InternationalPaymentConsentResponse
import org.mifosx.openbanking.core.network.model.pisp.internationalPayment.response.InternationalPaymentResponse
import org.mifosx.openbanking.core.network.model.pisp.internationalPayment.request.CreditorAccount as IntlCreditorAccountReq
import org.mifosx.openbanking.core.network.model.pisp.internationalPayment.request.Data as IntlDataReq
import org.mifosx.openbanking.core.network.model.pisp.internationalPayment.request.DebtorAccount as IntlDebtorAccount
import org.mifosx.openbanking.core.network.model.pisp.internationalPayment.request.Initiation as IntlInitiationReq
import org.mifosx.openbanking.core.network.model.pisp.internationalPayment.request.InstructedAmount as IntlInstructedAmountReq
import org.mifosx.openbanking.core.network.model.pisp.internationalPayment.request.Risk as IntlRiskReq

/**
 * The international rail's half of the OBIE mapping, split out from [PaymentMapper.kt] so neither
 * file carries both.
 *
 * The two rails are not variations of one shape: international carries `CurrencyOfTransfer` and
 * `ChargeBearer` and must omit `RemittanceInformation`, while domestic is the reverse. Keeping them
 * apart means a field can only be added to the rail that accepts it. The shared minor-unit and
 * scheme-name helpers stay in [PaymentMapper.kt] and are used from here.
 */

/** Shared with [ScheduledPaymentInitiationIntlMapper.kt] — the international scheduled rail sends the same. */
internal const val RISK_CATEGORY_EPAY = "EPAY"

internal fun PaymentDraft.toIntlInitiation(): IntlInitiationReq = IntlInitiationReq(
    instructionIdentification = instructionIdentification,
    endToEndIdentification = endToEndIdentification,
    currencyOfTransfer = currencyOfTransfer,
    instructedAmount = IntlInstructedAmountReq(
        amount = amountMinorUnits.toMajorUnitString(),
        currency = currency,
    ),
    creditorAccount = creditor.toIntlObieCreditor(),
    chargeBearer = chargeBearer?.wireValue,
    // Absent when the PSU chose to pick their account at the bank instead.
    debtorAccount = debtorAccount.toIntlObieDebtor(),
)

private fun BankAccount?.toIntlObieDebtor(): IntlDebtorAccount? = this?.let { account ->
    account.rawIdentification
        .takeIf { it.isNotBlank() }
        ?.let { identification ->
            IntlDebtorAccount(
                schemeName = SCHEME_SORT_CODE,
                identification = identification,
                name = account.nickname.takeIf { it.isNotBlank() },
            )
        }
}

internal fun PaymentDraft.toIntlRisk(): IntlRiskReq = IntlRiskReq(
    categoryPurposeCode = RISK_CATEGORY_EPAY,
)

internal fun PaymentDraft.toIntlConsentRequest(): InternationalPaymentConsentRequest =
    InternationalPaymentConsentRequest(
        data = IntlDataReq(initiation = toIntlInitiation()),
        risk = toIntlRisk(),
    )

internal fun PaymentDraft.toIntlPaymentRequest(consentId: String): InternationalPaymentRequest =
    InternationalPaymentRequest(
        data = IntlDataReq(consentId = consentId, initiation = toIntlInitiation()),
        risk = toIntlRisk(),
    )

private fun CreditorSelection.toIntlObieCreditor(): IntlCreditorAccountReq =
    IntlCreditorAccountReq(
        schemeName = SCHEME_IBAN,
        identification = identification,
        name = name.takeIf { it.isNotBlank() },
    )

internal fun InternationalPaymentConsentResponse.intlConsentIdOrNull(): String? =
    data?.consentId?.takeIf { it.isNotBlank() }

internal fun InternationalPaymentConsentResponse.intlStatusOrEmpty(): String =
    data?.status.orEmpty()

internal fun InternationalPaymentResponse.toIntlPaymentReceipt(): PaymentReceipt {
    val initiation = data?.initiation
    val amount = initiation?.instructedAmount
    return PaymentReceipt(
        domesticPaymentId = data?.internationalPaymentId.orEmpty(),
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
        reference = "",
        debtorIdentification = initiation?.debtorAccount?.identification.orEmpty(),
        charges = data?.charges.orEmpty().map { c ->
            PaymentCharge(
                bearer = c.chargeBearer.orEmpty(),
                typeLabel = c.type.orEmpty(),
                amountLabel = formatMinorUnits(
                    minorUnits = c.amount?.amount.toMinorUnits(),
                    currency = c.amount?.currency.orEmpty(),
                ),
            )
        },
    )
}
