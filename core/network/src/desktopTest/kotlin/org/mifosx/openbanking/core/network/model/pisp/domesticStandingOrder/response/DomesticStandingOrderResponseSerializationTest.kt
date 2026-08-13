/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.response

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The domestic mandate as the bank returns it, including the charge it declares at staging. */
class DomesticStandingOrderResponseSerializationTest {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    private fun sample() = DomesticStandingOrderConsentResponse(
        data = Data(
            consentId = "45171",
            status = "AWAU",
            creationDateTime = "2026-08-13T10:07:59+00:00",
            statusUpdateDateTime = "2026-08-13T10:07:59+00:00",
            permission = "Create",
            initiation = Initiation(
                mandateRelatedInformation = MandateRelatedInformation(
                    frequency = Frequency(type = "MNTH"),
                    firstPaymentDateTime = "2026-08-20T00:00:00+00:00",
                ),
                firstPaymentAmount = FirstPaymentAmount(amount = "250.00", currency = "GBP"),
            ),
            charges = listOf(
                Charge(
                    chargeBearer = "BorneByDebtor",
                    type = "UK.OBIE.CHAPSOut",
                    amount = ChargeAmount(amount = "0.05", currency = "GBP"),
                ),
            ),
        ),
        links = Links(self = "https://secure.sandbox.ob.hsbc.co.uk/obie/open-banking/v4.0/pisp/x"),
        meta = Meta(totalPages = 1),
        risk = Risk,
    )

    @Test
    fun aFullConsentResponseRoundTripsThroughJson() {
        val original = sample()
        val encoded = json.encodeToString(DomesticStandingOrderConsentResponse.serializer(), original)

        assertEquals(original, json.decodeFromString(DomesticStandingOrderConsentResponse.serializer(), encoded))
    }

    @Test
    fun theScheduleAndChargeSurviveTheRoundTrip() {
        val encoded = json.encodeToString(DomesticStandingOrderConsentResponse.serializer(), sample())
        val decoded = json.decodeFromString(DomesticStandingOrderConsentResponse.serializer(), encoded)

        assertEquals("AWAU", decoded.data?.status)
        assertEquals("MNTH", decoded.data?.initiation?.mandateRelatedInformation?.frequency?.type)
        assertEquals("UK.OBIE.CHAPSOut", decoded.data?.charges?.single()?.type)
        assertEquals("0.05", decoded.data?.charges?.single()?.amount?.amount)
        assertEquals(1, decoded.meta?.totalPages)
    }

    /**
     * The real sandbox body decodes, unknown keys and all.
     *
     * `CutOffDateTime` is deliberately unmodelled — it comes back equal to `CreationDateTime` and says
     * nothing about when the mandate runs — so this proves ignoring it does not break the parse.
     */
    @Test
    fun aRealSandboxBodyDecodes() {
        val body = """{"Data":{"ConsentId":"45128","CreationDateTime":"2026-08-06T10:08:00+00:00",""" +
            """"Status":"AWAU","StatusUpdateDateTime":"2026-08-06T10:08:00+00:00","Permission":"Create",""" +
            """"CutOffDateTime":"2026-08-06T10:08:00+00:00","Charges":[{"ChargeBearer":"BorneByDebtor",""" +
            """"Type":"UK.OBIE.CHAPSOut","Amount":{"Amount":"0.05","Currency":"GBP"}}],""" +
            """"Initiation":{"FirstPaymentAmount":{"Amount":"1.00","Currency":"GBP"},""" +
            """"MandateRelatedInformation":{"Frequency":{"Type":"WEEK"},""" +
            """"FirstPaymentDateTime":"2026-08-13T10:07:59+00:00"}}},"Risk":{},""" +
            """"Meta":{"TotalPages":1}}"""

        val decoded = json.decodeFromString(DomesticStandingOrderConsentResponse.serializer(), body)

        assertEquals("45128", decoded.data?.consentId)
        assertEquals("WEEK", decoded.data?.initiation?.mandateRelatedInformation?.frequency?.type)
        assertTrue(decoded.data?.charges?.isNotEmpty() == true, "the charge must not be dropped")
    }

    @Test
    fun theCreatedResourceCarriesItsOwnId() {
        val body = """{"Data":{"DomesticStandingOrderId":"19916","ConsentId":"45171","Status":"PDNG"}}"""

        val decoded = json.decodeFromString(DomesticStandingOrderResponse.serializer(), body)

        assertEquals("19916", decoded.data?.domesticStandingOrderId)
        assertEquals("PDNG", decoded.data?.status)
    }
}
