/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.request

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The domestic mandate on the wire.
 *
 * The round-trip case is only meaningful because `Risk` is an object rather than an empty class: an
 * empty class has no structural equality, so `assertEquals(original, decoded)` would compare
 * identities and pass without checking anything.
 */
class DomesticStandingOrderRequestSerializationTest {

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
        firstPaymentAmount = FirstPaymentAmount(amount = "250.00", currency = "GBP"),
        recurringPaymentAmount = RecurringPaymentAmount(amount = "300.00", currency = "GBP"),
        finalPaymentAmount = FinalPaymentAmount(amount = "125.00", currency = "GBP"),
        debtorAccount = DebtorAccount(
            schemeName = "UK.OBIE.SortCodeAccountNumber",
            identification = "80200110203349",
        ),
        creditorAccount = CreditorAccount(
            schemeName = "UK.OBIE.SortCodeAccountNumber",
            identification = "80200110203350",
            name = "Mr Dharani C",
        ),
        remittanceInformation = RemittanceInformation(unstructured = listOf("FLAT 4B RENT")),
    )

    private fun sampleConsent() =
        DomesticStandingOrderConsentRequest(data = Data(permission = "Create", initiation = initiation()), risk = Risk)

    @Test
    fun aFullConsentRequestRoundTripsThroughJson() {
        val original = sampleConsent()
        val encoded = json.encodeToString(DomesticStandingOrderConsentRequest.serializer(), original)

        assertEquals(original, json.decodeFromString(DomesticStandingOrderConsentRequest.serializer(), encoded))
    }

    @Test
    fun nestedFieldsSurviveTheRoundTrip() {
        val encoded = json.encodeToString(DomesticStandingOrderConsentRequest.serializer(), sampleConsent())
        val decoded = json.decodeFromString(DomesticStandingOrderConsentRequest.serializer(), encoded)

        assertEquals("Create", decoded.data?.permission)
        assertEquals("MNTH", decoded.data?.initiation?.mandateRelatedInformation?.frequency?.type)
        assertEquals("250.00", decoded.data?.initiation?.firstPaymentAmount?.amount)
        assertEquals("Mr Dharani C", decoded.data?.initiation?.creditorAccount?.name)
        assertEquals(listOf("FLAT 4B RENT"), decoded.data?.initiation?.remittanceInformation?.unstructured)
    }

    /** `Frequency` is an object in v4.0, not the v3 interval string. */
    @Test
    fun theFrequencyIsNestedInsideAnObject() {
        val encoded = json.encodeToString(DomesticStandingOrderConsentRequest.serializer(), sampleConsent())

        assertTrue(encoded.contains("\"Frequency\":{\"Type\":\"MNTH\"}"), encoded)
    }

    /** An empty `Risk` is what this product wants, and it must serialize as an object. */
    @Test
    fun theRiskBlockSerializesAsAnEmptyObject() {
        val encoded = json.encodeToString(DomesticStandingOrderConsentRequest.serializer(), sampleConsent())

        assertTrue(encoded.contains("\"Risk\":{}"), encoded)
        assertFalse(encoded.contains("PaymentContextCode"), "this product sends no payment context")
    }

    /** Unset fields must not reach the wire, or the two bodies would differ by their nulls. */
    @Test
    fun unsetFieldsAreOmittedRatherThanSentAsNull() {
        val minimal = DomesticStandingOrderConsentRequest(
            data = Data(
                permission = "Create",
                initiation = Initiation(
                    mandateRelatedInformation = MandateRelatedInformation(frequency = Frequency(type = "WEEK")),
                    firstPaymentAmount = FirstPaymentAmount(amount = "1.00", currency = "GBP"),
                ),
            ),
            risk = Risk,
        )

        val encoded = json.encodeToString(DomesticStandingOrderConsentRequest.serializer(), minimal)

        assertFalse(encoded.contains("null"), encoded)
        assertFalse(encoded.contains("DebtorAccount"), "an omitted payer means the bank chooses")
    }

    @Test
    fun theSubmissionRequestSharesTheInitiationShape() {
        val submission = DomesticStandingOrderRequest(
            data = Data(consentId = "45171", initiation = initiation()),
            risk = Risk,
        )
        val encoded = json.encodeToString(DomesticStandingOrderRequest.serializer(), submission)
        val decoded = json.decodeFromString(DomesticStandingOrderRequest.serializer(), encoded)

        assertEquals(submission, decoded)
        assertEquals("45171", decoded.data?.consentId)
        assertFalse(encoded.contains("Permission"), "the submission must not repeat it")
    }
}
