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

import org.mifosx.openbanking.core.model.banking.payment.PaymentStatus
import org.mifosx.openbanking.core.model.callback.ConsentStatus
import org.mifosx.openbanking.core.model.vrp.PeriodType
import org.mifosx.openbanking.feature.vrpconsents.consentDetail.LimitRowUi
import org.mifosx.openbanking.feature.vrpconsents.consentDetail.PayerAccountUi
import org.mifosx.openbanking.feature.vrpconsents.consentDetail.PaymentRowUi
import org.mifosx.openbanking.feature.vrpconsents.consentDetail.PeriodicLimitUsageUi
import org.mifosx.openbanking.feature.vrpconsents.consentDetail.RevokePhase
import org.mifosx.openbanking.feature.vrpconsents.consentDetail.VrpConsentDetailState
import org.mifosx.openbanking.feature.vrpconsents.consentDetail.VrpConsentDetailUiState
import org.mifosx.openbanking.feature.vrpconsents.consentList.ConsentRowUi
import org.mifosx.openbanking.feature.vrpconsents.consentList.VrpConsentListErrorKind
import org.mifosx.openbanking.feature.vrpconsents.consentList.VrpConsentListState
import org.mifosx.openbanking.feature.vrpconsents.consentList.VrpConsentListUiState
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Fixtures shared by the list's unit, UI and screenshot suites. */
object VrpConsentsFixtures {

    fun row(
        consentId: String = "45411",
        payeeName: String = "Sarah Chen",
        ceilingAmount: String = "£500",
        periodType: PeriodType? = PeriodType.Month,
        status: ConsentStatus = ConsentStatus.Authorised,
        validUntil: String? = "18 Mar 2027",
        isRevoked: Boolean = false,
    ) = ConsentRowUi(
        consentId = consentId,
        payeeName = payeeName,
        ceilingAmount = ceilingAmount,
        periodType = periodType,
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
            ceilingAmount = "£1,200",
            validUntil = null,
        ),
        row(
            consentId = "45413",
            payeeName = "My Savings Pot",
            ceilingAmount = "£250",
            periodType = PeriodType.Week,
            validUntil = "2 Dec 2026",
        ),
        row(
            consentId = "45414",
            payeeName = "James Whitfield",
            ceilingAmount = "£75",
            periodType = PeriodType.Day,
            validUntil = null,
        ),
    )

    fun listContentState() = VrpConsentListState(VrpConsentListUiState.Content(rows()))

    fun listLoadingState() = VrpConsentListState(VrpConsentListUiState.Loading)

    fun listEmptyState() = VrpConsentListState(VrpConsentListUiState.Empty)

    fun listErrorState(
        kind: VrpConsentListErrorKind = VrpConsentListErrorKind.StorageUnavailable,
    ) = VrpConsentListState(VrpConsentListUiState.Error(kind))

    /** The four payments the mockup renders, newest first, one of them refused. */
    fun paymentRows(): List<PaymentRowUi> = listOf(
        PaymentRowUi(
            localId = "pay-1",
            sentAmount = "£45.00",
            status = PaymentStatus.AcceptedCreditSettlementCompleted,
            sentOn = "14 Aug 2026",
        ),
        PaymentRowUi(
            localId = "pay-2",
            sentAmount = "£30.00",
            status = PaymentStatus.AcceptedCreditSettlementCompleted,
            sentOn = "9 Aug 2026",
        ),
        PaymentRowUi(
            localId = "pay-3",
            sentAmount = "£20.00",
            status = PaymentStatus.Rejected,
            sentOn = "6 Aug 2026",
        ),
        PaymentRowUi(
            localId = "pay-4",
            sentAmount = "£45.00",
            status = PaymentStatus.AcceptedCreditSettlementCompleted,
            sentOn = "1 Aug 2026",
        ),
    )

    fun detailContent(
        revoke: RevokePhase = RevokePhase.Idle,
        payments: List<PaymentRowUi> = paymentRows(),
    ) = VrpConsentDetailUiState.Content(
        payeeName = "Sarah Chen",
        payer = PayerAccountUi(maskedAccountNumber = "XXXX4021", accountSubType = "CACC"),
        status = ConsentStatus.Authorised,
        validUntil = "18 Mar 2027",
        syncedAt = checkedAt,
        perPaymentCeilingAmount = "£200.00",
        limits = listOf(LimitRowUi(PeriodType.Month, "£500.00")),
        periodicLimitUsage = PeriodicLimitUsageUi(
            periodType = PeriodType.Month,
            sentAmount = "£120.00",
            ceilingAmount = "£500.00",
            remainingAmount = "£380.00",
            sentAmountFraction = 0.24f,
        ),
        payments = payments,
        revoke = revoke,
    )

    fun detailState(uiState: VrpConsentDetailUiState) =
        VrpConsentDetailState(consentId = "45411", uiState = uiState)

    fun detailContentState(revoke: RevokePhase = RevokePhase.Idle) =
        detailState(detailContent(revoke = revoke))

    fun detailLoadingState() = detailState(VrpConsentDetailUiState.Loading)

    fun detailUnusableState() = detailState(VrpConsentDetailUiState.Unusable)

    fun detailEndedState() =
        detailState(VrpConsentDetailUiState.Ended("Sarah Chen", paymentRows()))

    fun detailRevokeRefusedState() = detailState(
        VrpConsentDetailUiState.Ended("Sarah Chen", paymentRows(), bankRefusedRemoval = true),
    )

    fun detailNotFoundState() = detailState(VrpConsentDetailUiState.NotFound)
}

/** Always two minutes ago, so the rendered phrase does not drift as the fixture ages. */
private val checkedAt: Instant get() = Clock.System.now() - 2.minutes
