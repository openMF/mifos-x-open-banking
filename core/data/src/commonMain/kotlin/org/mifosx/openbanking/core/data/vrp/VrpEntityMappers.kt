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

import kotlinx.datetime.LocalDate
import org.mifosx.openbanking.core.database.vrp.entity.VrpConsentEntity
import org.mifosx.openbanking.core.database.vrp.entity.VrpPaymentEntity
import org.mifosx.openbanking.core.model.banking.payment.PaymentStatus
import org.mifosx.openbanking.core.model.callback.ConsentStatus
import org.mifosx.openbanking.core.model.vrp.AccountIdentity
import org.mifosx.openbanking.core.model.vrp.Money
import org.mifosx.openbanking.core.model.vrp.PeriodType
import org.mifosx.openbanking.core.model.vrp.PeriodicLimit
import org.mifosx.openbanking.core.model.vrp.ValidityWindow
import org.mifosx.openbanking.core.model.vrp.VrpConsent
import org.mifosx.openbanking.core.model.vrp.VrpControlParameters
import org.mifosx.openbanking.core.model.vrp.VrpPayment
import kotlin.time.Instant

/** The row for this consent, with each cap in the column for its period. */
internal fun VrpConsent.toEntity(): VrpConsentEntity {
    val limits = controlParameters.periodicLimits.associate { it.periodType to it.amount.minorUnits }
    return VrpConsentEntity(
        consentId = consentId,
        status = status.name,
        createdAt = createdAt.toString(),
        validFrom = validity?.validFrom?.toString(),
        validTo = validity?.validTo?.toString(),
        maxIndividualAmountMinor = controlParameters.maximumIndividualAmount.minorUnits,
        currency = controlParameters.maximumIndividualAmount.currency,
        interactionType = controlParameters.interactionType,
        payerScheme = payer?.schemeName,
        payerIdentification = payer?.identification,
        payerName = payer?.name,
        payeeScheme = payee.schemeName,
        payeeIdentification = payee.identification,
        payeeName = payee.name,
        dayLimitMinor = limits[PeriodType.Day],
        weekLimitMinor = limits[PeriodType.Week],
        fortnightLimitMinor = limits[PeriodType.Fortnight],
        monthLimitMinor = limits[PeriodType.Month],
        halfYearLimitMinor = limits[PeriodType.HalfYear],
        yearLimitMinor = limits[PeriodType.Year],
        reference = reference,
        revokedAt = revokedAt?.toString(),
        syncedAt = syncedAt?.toString(),
    )
}

/** The caps this row holds, in period order. A column left null carries no cap. */
private fun VrpConsentEntity.periodicLimits(): List<PeriodicLimit> = listOfNotNull(
    dayLimitMinor?.let { PeriodicLimit(PeriodType.Day, Money(it, currency)) },
    weekLimitMinor?.let { PeriodicLimit(PeriodType.Week, Money(it, currency)) },
    fortnightLimitMinor?.let { PeriodicLimit(PeriodType.Fortnight, Money(it, currency)) },
    monthLimitMinor?.let { PeriodicLimit(PeriodType.Month, Money(it, currency)) },
    halfYearLimitMinor?.let { PeriodicLimit(PeriodType.HalfYear, Money(it, currency)) },
    yearLimitMinor?.let { PeriodicLimit(PeriodType.Year, Money(it, currency)) },
)

/** The consent this row holds, with its caps in period order. */
internal fun VrpConsentEntity.toVrpConsent(): VrpConsent {
    val limits = periodicLimits()

    return VrpConsent(
        consentId = consentId,
        status = ConsentStatus.entries.firstOrNull { it.name == status } ?: ConsentStatus.Rejected,
        createdAt = Instant.parse(createdAt),
        controlParameters = VrpControlParameters(
            maximumIndividualAmount = Money(maxIndividualAmountMinor, currency),
            periodicLimits = limits,
            interactionType = interactionType,
        ),
        payee = AccountIdentity(
            schemeName = payeeScheme,
            identification = payeeIdentification,
            name = payeeName,
        ),
        payer = payerScheme?.let { scheme ->
            payerIdentification?.let { identification ->
                AccountIdentity(
                    schemeName = scheme,
                    identification = identification,
                    name = payerName.orEmpty(),
                )
            }
        },
        validity = if (validFrom == null && validTo == null) {
            null
        } else {
            ValidityWindow(
                validFrom = validFrom?.let { LocalDate.parse(it) },
                validTo = validTo?.let { LocalDate.parse(it) },
            )
        },
        reference = reference,
        revokedAt = revokedAt?.let { Instant.parse(it) },
        syncedAt = syncedAt?.let { Instant.parse(it) },
    )
}

/** The row for this payment. */
internal fun VrpPayment.toEntity(): VrpPaymentEntity = VrpPaymentEntity(
    localId = localId,
    consentId = consentId,
    paymentId = paymentId,
    amountMinor = amount.minorUnits,
    currency = amount.currency,
    status = status.name,
    createdAt = createdAt.toString(),
    submittedAt = submittedAt?.toString(),
    settledAt = settledAt?.toString(),
    reference = reference,
    errorKind = errorKind,
    errorDescription = errorDescription,
    supportReference = supportReference,
    syncedAt = syncedAt?.toString(),
)

/** The payment this row holds. */
internal fun VrpPaymentEntity.toVrpPayment(): VrpPayment = VrpPayment(
    localId = localId,
    consentId = consentId,
    amount = Money(amountMinor, currency),
    status = PaymentStatus.entries.firstOrNull { it.name == status } ?: PaymentStatus.Unknown,
    createdAt = Instant.parse(createdAt),
    paymentId = paymentId,
    submittedAt = submittedAt?.let { Instant.parse(it) },
    settledAt = settledAt?.let { Instant.parse(it) },
    reference = reference,
    errorKind = errorKind,
    errorDescription = errorDescription,
    supportReference = supportReference,
    syncedAt = syncedAt?.let { Instant.parse(it) },
)
