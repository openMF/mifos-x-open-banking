/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.network.model.pisp.internationalStandingOrder.request

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The international mandate on the wire — one amount, a transfer currency and a charge bearer. */
class InternationalStandingOrderRequestSerializationTest {

    private val json = Json {
        prettyPrint = false
        explicitNulls = false
    }

    private fun initiation() = Initiation(
        mandateRelatedInformation = MandateRelatedInformation(
            frequency = Frequency(type = "MNTH"),
            firstPaymentDateTime = "2026-08-20T00:00:00+00:00",
            finalPaymentDateTime = "2026-12-11T00:00:00+00:00",
        ),
        instructedAmount = InstructedAmount(amount = "250.00", currency = "GBP"),
        creditorAccount = CreditorAccount(
            schemeName = "UK.OBIE.IBAN",
            identification = "DE89370400440532013000",
            name = "Klara Weiss",
        ),
        currencyOfTransfer = "USD",
        chargeBearer = "BorneByCreditor",
    )

    private fun sampleConsent() = InternationalStandingOrderConsentRequest(
        data = Data(permission = "Create", initiation = initiation()),
        risk = Risk,
    )

    @Test
    fun aFullConsentRequestRoundTripsThroughJson() {
        val original = sampleConsent()
        val encoded = json.encodeToString(InternationalStandingOrderConsentRequest.serializer(), original)

        assertEquals(original, json.decodeFromString(InternationalStandingOrderConsentRequest.serializer(), encoded))
    }

    @Test
    fun theTransferCurrencyAndChargeBearerReachTheWire() {
        val encoded = json.encodeToString(InternationalStandingOrderConsentRequest.serializer(), sampleConsent())

        assertTrue(encoded.contains("\"CurrencyOfTransfer\":\"USD\""), encoded)
        assertTrue(encoded.contains("\"ChargeBearer\":\"BorneByCreditor\""), encoded)
    }

    /**
     * This rail carries one amount and no reference.
     *
     * There is no `RemittanceInformation`, `RecurringPaymentAmount` or `FinalPaymentAmount` member to
     * set, which is what makes the form's disabled fields safe: they have nowhere to go even if a
     * value survived a rail switch.
     */
    @Test
    fun thereIsNoReferenceOrRecurringAmountToSend() {
        val encoded = json.encodeToString(InternationalStandingOrderConsentRequest.serializer(), sampleConsent())

        assertFalse(encoded.contains("RemittanceInformation"), encoded)
        assertFalse(encoded.contains("RecurringPaymentAmount"), encoded)
        assertFalse(encoded.contains("FirstPaymentAmount"), encoded)
    }

    @Test
    fun theSubmissionBindsTheConsentAndDropsThePermission() {
        val submission = InternationalStandingOrderRequest(
            data = Data(consentId = "45173", initiation = initiation()),
            risk = Risk,
        )
        val encoded = json.encodeToString(InternationalStandingOrderRequest.serializer(), submission)

        assertEquals(submission, json.decodeFromString(InternationalStandingOrderRequest.serializer(), encoded))
        assertFalse(encoded.contains("Permission"), encoded)
    }
}
