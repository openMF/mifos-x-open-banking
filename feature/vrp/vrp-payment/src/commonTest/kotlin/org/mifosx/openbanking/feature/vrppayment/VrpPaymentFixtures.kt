/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.vrppayment

import org.mifosx.openbanking.core.model.banking.payment.PaymentStatus
import org.mifosx.openbanking.core.model.vrp.Money
import org.mifosx.openbanking.core.model.vrp.PeriodType
import org.mifosx.openbanking.core.model.vrp.VrpPayment
import org.mifosx.openbanking.feature.vrppayment.payment.AmountProblem
import org.mifosx.openbanking.feature.vrppayment.payment.PaymentFailureKind
import org.mifosx.openbanking.feature.vrppayment.payment.PaymentFormUi
import org.mifosx.openbanking.feature.vrppayment.payment.PaymentPhase
import org.mifosx.openbanking.feature.vrppayment.payment.SubmissionUi
import org.mifosx.openbanking.feature.vrppayment.payment.VrpPaymentErrorKind
import org.mifosx.openbanking.feature.vrppayment.payment.VrpPaymentState
import org.mifosx.openbanking.feature.vrppayment.payment.VrpPaymentUiState
import kotlin.time.Instant

/** Fixtures shared by the payment unit, UI and screenshot suites. */
object VrpPaymentFixtures {

    const val CONSENT_ID = "45411"
    const val SUPPORT_REFERENCE = "9b7e4d20-1a6c-4f88-9d3a-2c5b7e10f4a6"

    fun form(
        amount: String = "45.00",
        problem: AmountProblem? = null,
    ) = PaymentFormUi(
        payeeName = "Sarah Chen",
        payerName = "Everyday Current Account",
        amount = amount,
        problem = problem,
        perPaymentCeilingAmount = "£200.00",
        remainingAmount = "£380.00",
        periodType = PeriodType.Month,
    )

    fun state(uiState: VrpPaymentUiState) =
        VrpPaymentState(consentId = CONSENT_ID, uiState = uiState)

    fun amountState(
        amount: String = "45.00",
        problem: AmountProblem? = null,
    ) = state(
        VrpPaymentUiState.Content(
            phase = PaymentPhase.Amount,
            form = form(amount, problem),
        ),
    )

    fun reviewState(outcome: SubmissionUi = SubmissionUi.NotStarted) = state(
        VrpPaymentUiState.Content(
            phase = PaymentPhase.Review,
            form = form(),
            outcome = outcome,
        ),
    )

    fun sentState(settled: Boolean) = reviewState(SubmissionUi.Sent(payment(settled)))

    fun failedState(
        kind: PaymentFailureKind,
        supportReference: String = "",
    ) = reviewState(SubmissionUi.Failed(kind, supportReference))

    fun loadingState() = state(VrpPaymentUiState.Loading)

    fun unusableState() = state(VrpPaymentUiState.Unusable)

    fun errorState(
        kind: VrpPaymentErrorKind = VrpPaymentErrorKind.ConsentUnavailable,
    ) = state(VrpPaymentUiState.Error(kind))

    private fun payment(settled: Boolean) = VrpPayment(
        localId = "pay-1",
        consentId = CONSENT_ID,
        amount = Money(4_500L, "GBP"),
        status = if (settled) {
            PaymentStatus.AcceptedCreditSettlementCompleted
        } else {
            PaymentStatus.AcceptedSettlementInProcess
        },
        createdAt = Instant.parse("2026-08-19T12:00:00Z"),
        paymentId = "19975",
    )
}
