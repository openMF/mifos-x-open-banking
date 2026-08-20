/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.model.banking.payment

import kotlin.test.Test
import kotlin.test.assertEquals

class PaymentStatusDispositionTest {

    private val instructionTypes = listOf(
        ConsentType.DomesticScheduledPayment,
        ConsentType.InternationalScheduledPayment,
        ConsentType.DomesticStandingOrder,
        ConsentType.InternationalStandingOrder,
    )

    private val singlePaymentTypes = listOf(
        ConsentType.DomesticSinglePayment,
        ConsentType.InternationalSinglePayment,
    )

    @Test
    fun `INCO is terminal success on the scheduled and standing order rails`() {
        instructionTypes.forEach { type ->
            assertEquals(
                PaymentDisposition.TerminalSuccess,
                PaymentStatus.InitiationCompleted.dispositionFor(type),
                "INCO should be terminal on $type",
            )
        }
    }

    @Test
    fun `INCO stays in progress on the single payment rails`() {
        singlePaymentTypes.forEach { type ->
            assertEquals(
                PaymentDisposition.InProgress,
                PaymentStatus.InitiationCompleted.dispositionFor(type),
                "INCO should not be terminal on $type",
            )
        }
    }

    @Test
    fun `every other status keeps its declared disposition on every rail`() {
        val others = PaymentStatus.entries - PaymentStatus.InitiationCompleted

        ConsentType.ALL.forEach { type ->
            others.forEach { status ->
                assertEquals(
                    status.disposition,
                    status.dispositionFor(type),
                    "$status should be unchanged on $type",
                )
            }
        }
    }

    @Test
    fun `the observed sandbox statuses resolve as the bank means them`() {
        assertEquals(
            PaymentDisposition.InProgress,
            PaymentStatus.fromWire("ACSP").dispositionFor(ConsentType.DomesticSinglePayment),
        )
        assertEquals(
            PaymentDisposition.TerminalSuccess,
            PaymentStatus.fromWire("ACCC").dispositionFor(ConsentType.DomesticSinglePayment),
        )
        assertEquals(
            PaymentDisposition.InProgress,
            PaymentStatus.fromWire("PDNG").dispositionFor(ConsentType.DomesticStandingOrder),
        )
        assertEquals(
            PaymentDisposition.TerminalSuccess,
            PaymentStatus.fromWire("INCO").dispositionFor(ConsentType.DomesticStandingOrder),
        )
    }
}
