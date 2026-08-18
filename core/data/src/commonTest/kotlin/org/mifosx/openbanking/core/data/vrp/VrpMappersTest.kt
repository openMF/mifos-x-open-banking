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

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import org.mifosx.openbanking.core.model.callback.ConsentStatus
import org.mifosx.openbanking.core.model.vrp.AccountIdentity
import org.mifosx.openbanking.core.model.vrp.Money
import org.mifosx.openbanking.core.model.vrp.PeriodType
import org.mifosx.openbanking.core.model.vrp.PeriodicLimit
import org.mifosx.openbanking.core.model.vrp.ValidityWindow
import org.mifosx.openbanking.core.model.vrp.VrpConsent
import org.mifosx.openbanking.core.model.vrp.VrpConsentDraft
import org.mifosx.openbanking.core.model.vrp.VrpControlParameters
import org.mifosx.openbanking.core.network.model.vrp.createConsent.response.CreateConsentResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class VrpMappersTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val syncedAt = Instant.parse("2026-08-15T18:00:00Z")

    private val payer = AccountIdentity(
        schemeName = "UK.OBIE.SortCodeAccountNumber",
        identification = "80200110203348",
        name = "Mr Robert",
    )

    private val payee = AccountIdentity(
        schemeName = "UK.OBIE.SortCodeAccountNumber",
        identification = "80200110203350",
        name = "Mr Dharani C",
    )

    private fun gbp(minorUnits: Long) = Money(minorUnits = minorUnits, currency = "GBP")

    private fun draft(
        payerAccount: AccountIdentity? = payer,
        reference: String? = null,
        validity: ValidityWindow? = null,
        limits: List<PeriodicLimit> = listOf(PeriodicLimit(PeriodType.Month, gbp(500_00))),
    ) = VrpConsentDraft(
        payee = payee,
        controlParameters = VrpControlParameters(
            maximumIndividualAmount = gbp(10_00),
            periodicLimits = limits,
        ),
        idempotencyKey = "idem-1",
        payer = payerAccount,
        validity = validity,
        reference = reference,
    )

    // region — amounts

    @Test
    fun rendersMinorUnitsAsATwoPlaceDecimal() {
        assertEquals("10.00", gbp(10_00).toWireAmount())
        assertEquals("0.05", gbp(5).toWireAmount())
        assertEquals("0.00", gbp(0).toWireAmount())
        assertEquals("1000.00", gbp(1000_00).toWireAmount())
        assertEquals("100000.00", gbp(100000_00).toWireAmount())
    }

    @Test
    fun readsAWireAmountBackToMinorUnits() {
        assertEquals(1000L, wireMoney("10.00", "GBP")?.minorUnits)
        assertEquals(5L, wireMoney("0.05", "GBP")?.minorUnits)
        assertEquals(1000L, wireMoney("10", "GBP")?.minorUnits)
        assertEquals(1050L, wireMoney("10.5", "GBP")?.minorUnits)
        assertEquals("GBP", wireMoney("10.00", "GBP")?.currency)
    }

    @Test
    fun refusesAnAmountItCannotRead() {
        assertNull(wireMoney("ten", "GBP"))
        assertNull(wireMoney("1.2.3", "GBP"))
        assertNull(wireMoney(null, "GBP"))
        assertNull(wireMoney("10.00", null))
    }

    @Test
    fun survivesARoundTripThroughTheWire() {
        listOf(0L, 1L, 5L, 99L, 100L, 12345L, 100000_00L).forEach { minorUnits ->
            val rendered = gbp(minorUnits).toWireAmount()
            assertEquals(minorUnits, wireMoney(rendered, "GBP")?.minorUnits, rendered)
        }
    }

    // endregion

    // region — consent request

    @Test
    fun sendsSweepingWithConsentAlignmentAndNoScaRequired() {
        val control = draft().toCreateConsent().data.controlParameters

        assertEquals(listOf("UK.OBIE.VRPType.Sweeping"), control.vrpType)
        assertEquals(listOf("UK.OBIE.SCANotRequired"), control.psuAuthenticationMethods)
        assertEquals(listOf("OffSession"), control.psuInteractionTypes)
        assertTrue(control.periodicLimits.all { it.periodAlignment == "Consent" })
    }

    @Test
    fun sendsEveryLimitTheDraftCarries() {
        val control = draft(
            limits = listOf(
                PeriodicLimit(PeriodType.Day, gbp(50_00)),
                PeriodicLimit(PeriodType.HalfYear, gbp(800_00)),
            ),
        ).toCreateConsent().data.controlParameters

        assertEquals(listOf("Day", "Half-year"), control.periodicLimits.map { it.periodType })
        assertEquals(listOf("50.00", "800.00"), control.periodicLimits.map { it.amount })
    }

    @Test
    fun omitsTheDebtorWhenThePayerIsChosenAtTheBank() {
        val withPayer = draft(payerAccount = payer).toCreateConsent()
        val withoutPayer = draft(payerAccount = null).toCreateConsent()

        assertEquals("80200110203348", withPayer.data.initiation.debtorAccount?.identification)
        assertNull(withoutPayer.data.initiation.debtorAccount)
    }

    @Test
    fun sendsValidityDatesWithAnOffsetAndNoTime() {
        val control = draft(
            validity = ValidityWindow(
                validFrom = LocalDate.parse("2026-08-20"),
                validTo = LocalDate.parse("2027-12-31"),
            ),
        ).toCreateConsent().data.controlParameters

        assertEquals("2026-08-20T00:00:00+00:00", control.validFromDateTime)
        assertEquals("2027-12-31T00:00:00+00:00", control.validToDateTime)
    }

    @Test
    fun omitsValidityWhenTheConsentHasNoEndDate() {
        val control = draft(validity = null).toCreateConsent().data.controlParameters

        assertNull(control.validFromDateTime)
        assertNull(control.validToDateTime)
    }

    @Test
    fun omitsRemittanceWhenThereIsNoReference() {
        assertNull(draft(reference = null).toCreateConsent().data.initiation.remittanceInformation)
        assertEquals(
            listOf("BFRS.RFRNC.545"),
            draft(reference = "BFRS.RFRNC.545").toCreateConsent()
                .data.initiation.remittanceInformation?.unstructured,
        )
    }

    // endregion

    // region — consent response

    private val consentResponseJson = """
        {
          "Data": {
            "ConsentId": "45365",
            "Status": "AWAU",
            "CreationDateTime": "2026-08-15T17:51:00+00:00",
            "StatusUpdateDateTime": "2026-08-15T17:51:00+00:00",
            "ControlParameters": {
              "ValidToDateTime": "2027-12-31T00:00:00+00:00",
              "MaximumIndividualAmount": { "Amount": "10.00", "Currency": "GBP" },
              "PeriodicLimits": [
                { "PeriodType": "Day", "PeriodAlignment": "Consent",
                  "Amount": "50.00", "Currency": "GBP" },
                { "PeriodType": "Month", "PeriodAlignment": "Calendar",
                  "Amount": "500.00", "Currency": "GBP" }
              ],
              "VRPType": ["UK.OBIE.VRPType.Sweeping"],
              "PSUAuthenticationMethods": ["UK.OBIE.SCANotRequired"],
              "PSUInteractionTypes": ["OffSession"]
            },
            "Initiation": {
              "DebtorAccount": {
                "SchemeName": "UK.OBIE.SortCodeAccountNumber",
                "Identification": "80200110203348",
                "Name": "Mr Robert"
              },
              "CreditorAccount": {
                "SchemeName": "UK.OBIE.SortCodeAccountNumber",
                "Identification": "80200110203350",
                "Name": "Mr Dharani C"
              },
              "RemittanceInformation": { "Unstructured": ["BFRS.RFRNC.545"] }
            }
          },
          "Risk": {},
          "Links": { "Self": "https://example.test/domestic-vrp-consents/45365" },
          "Meta": { "TotalPages": 1 }
        }
    """.trimIndent()

    private fun consentResponse(raw: String = consentResponseJson) =
        json.decodeFromString(CreateConsentResponse.serializer(), raw)

    @Test
    fun readsAConsentIdThatIsNotAUuid() {
        val consent = consentResponse().toVrpConsent(syncedAt)

        assertNotNull(consent)
        assertEquals("45365", consent.consentId)
    }

    @Test
    fun readsEveryLimitBack() {
        val consent = assertNotNull(consentResponse().toVrpConsent(syncedAt))

        assertEquals(2, consent.controlParameters.periodicLimits.size)
        assertEquals(
            listOf(PeriodType.Day, PeriodType.Month),
            consent.controlParameters.periodicLimits.map { it.periodType },
        )
        assertEquals(50_00, consent.controlParameters.periodicLimits[0].amount.minorUnits)
        assertEquals(500_00, consent.controlParameters.periodicLimits[1].amount.minorUnits)
        assertEquals(10_00, consent.controlParameters.maximumIndividualAmount.minorUnits)
    }

    @Test
    fun readsTheRestOfTheConsent() {
        val consent = assertNotNull(consentResponse().toVrpConsent(syncedAt))

        assertEquals(ConsentStatus.AwaitingAuthorisation, consent.status)
        assertEquals("80200110203348", consent.payer?.identification)
        assertEquals("80200110203350", consent.payee.identification)
        assertEquals("BFRS.RFRNC.545", consent.reference)
        assertEquals(LocalDate.parse("2027-12-31"), consent.validity?.validTo)
        assertEquals(syncedAt, consent.syncedAt)
    }

    /** A card selected at the bank comes back outside both the declared enum and length rule. */
    @Test
    fun toleratesAPayerOutsideTheDeclaredSchemeAndLength() {
        val raw = """
            {
              "Data": {
                "ConsentId": "45372",
                "Status": "AUTH",
                "CreationDateTime": "2026-08-15T17:51:00+00:00",
                "ControlParameters": {
                  "MaximumIndividualAmount": { "Amount": "10.00", "Currency": "GBP" },
                  "PeriodicLimits": [
                    { "PeriodType": "Day", "PeriodAlignment": "Consent",
                      "Amount": "50.00", "Currency": "GBP" }
                  ],
                  "VRPType": ["UK.OBIE.VRPType.Sweeping"],
                  "PSUAuthenticationMethods": ["UK.OBIE.SCANotRequired"]
                },
                "Initiation": {
                  "CreditorAccount": {
                    "SchemeName": "UK.OBIE.SortCodeAccountNumber",
                    "Identification": "80200110203350",
                    "Name": "Mr Dharani C"
                  }
                },
                "DebtorAccount": {
                  "SchemeName": "UK.OBIE.PAN",
                  "Identification": "1234567890123456",
                  "Name": "Mr Bantu"
                }
              },
              "Risk": {},
              "Links": { "Self": "https://example.test/domestic-vrp-consents/45372" },
              "Meta": { "TotalPages": 1 }
            }
        """.trimIndent()

        val consent = assertNotNull(consentResponse(raw).toVrpConsent(syncedAt))

        assertEquals("UK.OBIE.PAN", consent.payer?.schemeName)
        assertEquals("1234567890123456", consent.payer?.identification)
    }

    @Test
    fun refusesAConsentWithNoLimits() {
        val raw = consentResponseJson.replace(
            Regex(""""PeriodicLimits": \[[\s\S]*?\],"""),
            """"PeriodicLimits": [],""",
        )

        assertNull(consentResponse(raw).toVrpConsent(syncedAt))
    }

    // endregion

    // region — payment request

    @Test
    fun carriesTheReferenceInBothInitiationAndInstruction() {
        val consent = VrpConsent(
            consentId = "45365",
            status = ConsentStatus.Authorised,
            createdAt = syncedAt,
            controlParameters = VrpControlParameters(
                maximumIndividualAmount = gbp(10_00),
                periodicLimits = listOf(PeriodicLimit(PeriodType.Month, gbp(500_00))),
            ),
            payee = payee,
            payer = payer,
            reference = "BFRS.RFRNC.545",
        )

        val payment = consent.toInitiatePayment(
            amount = gbp(2_00),
            instructionIdentification = "INSTR-1",
            endToEndIdentification = "E2E-1",
        )

        assertEquals(
            listOf("BFRS.RFRNC.545"),
            payment.data.initiation.remittanceInformation?.unstructured,
        )
        assertEquals(
            listOf("BFRS.RFRNC.545"),
            payment.data.instruction.remittanceInformation?.unstructured,
        )
    }

    @Test
    fun sendsTheSingularFieldNamesOnAPayment() {
        val consent = VrpConsent(
            consentId = "45365",
            status = ConsentStatus.Authorised,
            createdAt = syncedAt,
            controlParameters = VrpControlParameters(
                maximumIndividualAmount = gbp(10_00),
                periodicLimits = listOf(PeriodicLimit(PeriodType.Month, gbp(500_00))),
            ),
            payee = payee,
            payer = payer,
        )

        val payment = consent.toInitiatePayment(
            amount = gbp(2_00),
            instructionIdentification = "INSTR-1",
            endToEndIdentification = "E2E-1",
        )

        assertEquals("UK.OBIE.VRPType.Sweeping", payment.data.vrpType)
        assertEquals("UK.OBIE.SCANotRequired", payment.data.psuAuthenticationMethod)
        assertEquals("2.00", payment.data.instruction.instructedAmount.amount)
        assertEquals("80200110203350", payment.data.instruction.creditorAccount.identification)
    }

    // endregion
}
