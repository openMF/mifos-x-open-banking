/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.ui.payment

import org.mifosx.openbanking.core.model.banking.payment.ConsentType
import org.mifosx.openbanking.core.model.banking.payment.PaymentStatus
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaymentHistoryEntryTest {

    private fun entry(status: PaymentStatus, consentType: ConsentType) = PaymentHistoryEntry(
        paymentId = "19916",
        amountLabel = "£45.00",
        dateLabel = "14 Aug 2026",
        status = status,
        consentType = consentType,
    )

    @Test
    fun aSetUpStandingOrderIsSettled() {
        val row = entry(PaymentStatus.InitiationCompleted, ConsentType.DomesticStandingOrder)

        assertTrue(row.settled)
    }

    @Test
    fun aSetUpScheduledPaymentIsSettled() {
        val row = entry(PaymentStatus.InitiationCompleted, ConsentType.InternationalScheduledPayment)

        assertTrue(row.settled)
    }

    @Test
    fun aPendingStandingOrderIsNotSettled() {
        val row = entry(PaymentStatus.Pending, ConsentType.DomesticStandingOrder)

        assertFalse(row.settled)
    }

    @Test
    fun anAcceptedSinglePaymentIsNotSettledUntilTheBankCompletesIt() {
        val inFlight = entry(
            PaymentStatus.AcceptedSettlementInProcess,
            ConsentType.DomesticSinglePayment,
        )
        val completed = entry(
            PaymentStatus.AcceptedCreditSettlementCompleted,
            ConsentType.DomesticSinglePayment,
        )

        assertFalse(inFlight.settled)
        assertTrue(completed.settled)
    }

    @Test
    fun aRefusedPaymentIsSettled() {
        val row = entry(PaymentStatus.Rejected, ConsentType.DomesticSinglePayment)

        assertTrue(row.settled)
    }

    @Test
    fun anUnrecognisedStatusIsNotSettledSoItGetsReadAgain() {
        val row = entry(PaymentStatus.Unknown, ConsentType.DomesticStandingOrder)

        assertFalse(row.settled)
    }
}
