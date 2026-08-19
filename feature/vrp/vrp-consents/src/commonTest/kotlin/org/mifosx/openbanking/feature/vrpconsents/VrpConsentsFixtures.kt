/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.vrpconsents

import org.mifosx.openbanking.core.model.callback.ConsentStatus
import org.mifosx.openbanking.core.model.vrp.PeriodType
import org.mifosx.openbanking.feature.vrpconsents.consentList.ConsentRowUi
import org.mifosx.openbanking.feature.vrpconsents.consentList.VrpConsentListErrorKind
import org.mifosx.openbanking.feature.vrpconsents.consentList.VrpConsentListState
import org.mifosx.openbanking.feature.vrpconsents.consentList.VrpConsentListUiState

/** Fixtures shared by the list's unit, UI and screenshot suites. */
object VrpConsentsFixtures {

    fun row(
        consentId: String = "45411",
        payeeName: String = "Sarah Chen",
        limitAmountLabel: String = "£500",
        period: PeriodType? = PeriodType.Month,
        status: ConsentStatus = ConsentStatus.Authorised,
        validUntil: String? = "18 Mar 2027",
        isRevoked: Boolean = false,
    ) = ConsentRowUi(
        consentId = consentId,
        payeeName = payeeName,
        limitAmountLabel = limitAmountLabel,
        period = period,
        status = status,
        validUntil = validUntil,
        isRevoked = isRevoked,
    )

    /** The four rows the mockup renders, in its order. */
    fun rows(): List<ConsentRowUi> = listOf(
        row(),
        row(
            consentId = "45412",
            payeeName = "Oakwood Property Ltd",
            limitAmountLabel = "£1,200",
            validUntil = null,
        ),
        row(
            consentId = "45413",
            payeeName = "My Savings Pot",
            limitAmountLabel = "£250",
            period = PeriodType.Week,
            validUntil = "2 Dec 2026",
        ),
        row(
            consentId = "45414",
            payeeName = "James Whitfield",
            limitAmountLabel = "£75",
            period = PeriodType.Day,
            validUntil = null,
        ),
    )

    fun listContentState() = VrpConsentListState(VrpConsentListUiState.Content(rows()))

    fun listLoadingState() = VrpConsentListState(VrpConsentListUiState.Loading)

    fun listEmptyState() = VrpConsentListState(VrpConsentListUiState.Empty)

    fun listErrorState(
        kind: VrpConsentListErrorKind = VrpConsentListErrorKind.StorageUnavailable,
    ) = VrpConsentListState(VrpConsentListUiState.Error(kind))
}
