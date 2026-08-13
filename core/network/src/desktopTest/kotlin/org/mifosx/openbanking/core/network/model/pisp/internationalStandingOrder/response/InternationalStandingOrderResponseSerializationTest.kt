/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.network.model.pisp.internationalStandingOrder.response

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The international mandate as the bank returns it. */
class InternationalStandingOrderResponseSerializationTest {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    private fun sample() = InternationalStandingOrderConsentResponse(
        data = Data(
            consentId = "45173",
            status = "AWAU",
            creationDateTime = "2026-08-13T10:09:12+00:00",
            permission = "Create",
            initiation = Initiation(
                mandateRelatedInformation = MandateRelatedInformation(
                    frequency = Frequency(type = "MNTH"),
                    firstPaymentDateTime = "2026-08-20T00:00:00+00:00",
                ),
                instructedAmount = InstructedAmount(amount = "250.00", currency = "GBP"),
                currencyOfTransfer = "USD",
                chargeBearer = "BorneByCreditor",
            ),
        ),
        links = Links(self = "https://secure.sandbox.ob.hsbc.co.uk/obie/open-banking/v4.0/pisp/y"),
        meta = Meta(totalPages = 1),
        risk = Risk,
    )

    @Test
    fun aFullConsentResponseRoundTripsThroughJson() {
        val original = sample()
        val encoded = json.encodeToString(InternationalStandingOrderConsentResponse.serializer(), original)

        assertEquals(
            original,
            json.decodeFromString(InternationalStandingOrderConsentResponse.serializer(), encoded),
        )
    }

    /** The consent on this rail declares no charge; the figure only exists once the resource is made. */
    @Test
    fun theConsentCarriesNoCharges() {
        val encoded = json.encodeToString(InternationalStandingOrderConsentResponse.serializer(), sample())
        val decoded = json.decodeFromString(InternationalStandingOrderConsentResponse.serializer(), encoded)

        assertNull(decoded.data?.charges)
    }

    /** And the created resource carries both the charge and its own id. */
    @Test
    fun theCreatedResourceCarriesItsIdAndTheCharge() {
        val body = """{"Data":{"InternationalStandingOrderId":"19917","ConsentId":"45173",""" +
            """"Status":"INCO","Charges":[{"ChargeBearer":"BorneByDebtor","Type":"UK.OBIE.CHAPSOut",""" +
            """"Amount":{"Amount":"0.50","Currency":"GBP"}}]}}"""

        val decoded = json.decodeFromString(InternationalStandingOrderResponse.serializer(), body)

        assertEquals("19917", decoded.data?.internationalStandingOrderId)
        assertEquals("INCO", decoded.data?.status)
        assertEquals("0.50", decoded.data?.charges?.single()?.amount?.amount)
    }
}
