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

import org.mifosx.openbanking.core.model.callback.ConsentStatus
import org.mifosx.openbanking.core.model.vrp.AccountIdentity
import org.mifosx.openbanking.core.model.vrp.PeriodicLimit
import org.mifosx.openbanking.core.model.vrp.ValidityWindow
import org.mifosx.openbanking.core.model.vrp.VrpConsent
import org.mifosx.openbanking.core.model.vrp.VrpConsentDraft
import org.mifosx.openbanking.core.model.vrp.VrpControlParameters
import org.mifosx.openbanking.core.network.model.vrp.createConsent.request.ControlParameters
import org.mifosx.openbanking.core.network.model.vrp.createConsent.request.CreateConsent
import org.mifosx.openbanking.core.network.model.vrp.createConsent.request.CreditorAccount
import org.mifosx.openbanking.core.network.model.vrp.createConsent.request.Data
import org.mifosx.openbanking.core.network.model.vrp.createConsent.request.DebtorAccount
import org.mifosx.openbanking.core.network.model.vrp.createConsent.request.Initiation
import org.mifosx.openbanking.core.network.model.vrp.createConsent.request.MaximumIndividualAmount
import org.mifosx.openbanking.core.network.model.vrp.createConsent.request.RemittanceInformation
import org.mifosx.openbanking.core.network.model.vrp.createConsent.request.Risk
import org.mifosx.openbanking.core.network.model.vrp.createConsent.response.CreateConsentResponse
import kotlin.time.Instant
import org.mifosx.openbanking.core.network.model.vrp.createConsent.request.PeriodicLimit as WirePeriodicLimit
import org.mifosx.openbanking.core.network.model.vrp.createConsent.response.CreditorAccount as ResponseCreditorAccount
import org.mifosx.openbanking.core.network.model.vrp.createConsent.response.DebtorAccount as ResponseDebtorAccount

/** Builds the create-consent body for this draft. */
internal fun VrpConsentDraft.toCreateConsent(): CreateConsent = CreateConsent(
    data = Data(
        controlParameters = ControlParameters(
            validFromDateTime = validity?.validFrom?.toWireDateTime(),
            validToDateTime = validity?.validTo?.toWireDateTime(),
            maximumIndividualAmount = MaximumIndividualAmount(
                amount = controlParameters.maximumIndividualAmount.toWireAmount(),
                currency = controlParameters.maximumIndividualAmount.currency,
            ),
            periodicLimits = controlParameters.periodicLimits.map {
                WirePeriodicLimit(
                    periodType = it.periodType.toWireValue(),
                    periodAlignment = PERIOD_ALIGNMENT_CONSENT,
                    amount = it.amount.toWireAmount(),
                    currency = it.amount.currency,
                )
            },
            vrpType = listOf(VRP_TYPE_SWEEPING),
            psuAuthenticationMethods = listOf(SCA_NOT_REQUIRED),
            psuInteractionTypes = listOf(INTERACTION_OFF_SESSION),
        ),
        initiation = Initiation(
            debtorAccount = payer?.let {
                DebtorAccount(
                    schemeName = it.schemeName,
                    identification = it.identification,
                    name = it.name,
                    secondaryIdentification = it.secondaryIdentification,
                )
            },
            creditorAccount = CreditorAccount(
                schemeName = payee.schemeName,
                identification = payee.identification,
                name = payee.name,
                secondaryIdentification = payee.secondaryIdentification,
            ),
            remittanceInformation = reference?.let { RemittanceInformation(unstructured = listOf(it)) },
        ),
    ),
    risk = Risk(),
)

/**
 * Reads a consent response into the domain.
 *
 * Returns null when the response carries no identifier, no creation time, no limits or no payee —
 * a consent missing any of those cannot be paid under or displayed.
 *
 * @param syncedAt When this response was received.
 */
@Suppress("ReturnCount")
internal fun CreateConsentResponse.toVrpConsent(syncedAt: Instant): VrpConsent? {
    val body = data ?: return null
    val consentId = body.consentId?.takeIf { it.isNotBlank() } ?: return null
    val createdAt = wireInstant(body.creationDateTime) ?: return null
    val control = body.controlParameters ?: return null

    val maximumIndividualAmount = wireMoney(
        control.maximumIndividualAmount?.amount,
        control.maximumIndividualAmount?.currency,
    ) ?: return null

    val limits = control.periodicLimits.orEmpty().mapNotNull { limit ->
        val periodType = periodTypeFromWire(limit.periodType) ?: return@mapNotNull null
        val amount = wireMoney(limit.amount, limit.currency) ?: return@mapNotNull null
        PeriodicLimit(periodType = periodType, amount = amount)
    }
    if (limits.isEmpty()) return null

    val payee = body.initiation?.creditorAccount?.toAccountIdentity() ?: return null

    return VrpConsent(
        consentId = consentId,
        status = ConsentStatus.fromString(body.status.orEmpty()),
        createdAt = createdAt,
        controlParameters = VrpControlParameters(
            maximumIndividualAmount = maximumIndividualAmount,
            periodicLimits = limits,
            interactionType = control.psuInteractionTypes?.firstOrNull(),
        ),
        payee = payee,
        payer = (body.debtorAccount ?: body.initiation?.debtorAccount)?.toAccountIdentity(),
        validity = ValidityWindow(
            validFrom = wireDate(control.validFromDateTime),
            validTo = wireDate(control.validToDateTime),
        ),
        reference = body.initiation?.remittanceInformation?.unstructured?.firstOrNull(),
        syncedAt = syncedAt,
    )
}

private fun ResponseDebtorAccount.toAccountIdentity(): AccountIdentity? = accountIdentity(
    schemeName = schemeName,
    identification = identification,
    name = name,
    secondaryIdentification = secondaryIdentification,
)

private fun ResponseCreditorAccount.toAccountIdentity(): AccountIdentity? = accountIdentity(
    schemeName = schemeName,
    identification = identification,
    name = name,
    secondaryIdentification = secondaryIdentification,
)

private fun accountIdentity(
    schemeName: String?,
    identification: String?,
    name: String?,
    secondaryIdentification: String?,
): AccountIdentity? {
    if (schemeName == null || identification == null) return null
    return AccountIdentity(
        schemeName = schemeName,
        identification = identification,
        name = name.orEmpty(),
        secondaryIdentification = secondaryIdentification,
    )
}
