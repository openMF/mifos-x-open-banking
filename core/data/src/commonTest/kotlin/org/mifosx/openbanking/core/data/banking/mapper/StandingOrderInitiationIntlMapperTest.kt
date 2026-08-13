/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.data.banking.mapper

import org.mifosx.openbanking.core.model.banking.BeneficiaryScheme
import org.mifosx.openbanking.core.model.banking.payment.ChargeBearer
import org.mifosx.openbanking.core.model.banking.payment.CreditorSelection
import org.mifosx.openbanking.core.model.banking.payment.PaymentStatus
import org.mifosx.openbanking.core.model.banking.payment.StandingOrderDraft
import org.mifosx.openbanking.core.model.banking.payment.StandingOrderFrequency
import org.mifosx.openbanking.core.network.model.pisp.internationalStandingOrder.response.Charge
import org.mifosx.openbanking.core.network.model.pisp.internationalStandingOrder.response.ChargeAmount
import org.mifosx.openbanking.core.network.model.pisp.internationalStandingOrder.response.InternationalStandingOrderResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.mifosx.openbanking.core.network.model.pisp.internationalStandingOrder.response.Data as IntlSoData
import org.mifosx.openbanking.core.network.model.pisp.internationalStandingOrder.response.Frequency as IntlSoFrequency
import org.mifosx.openbanking.core.network.model.pisp.internationalStandingOrder.response.Initiation as IntlSoInitiation
import org.mifosx.openbanking.core.network.model.pisp.internationalStandingOrder.response.InstructedAmount as IntlSoAmount
import org.mifosx.openbanking.core.network.model.pisp.internationalStandingOrder.response.MandateRelatedInformation as IntlSoMandate

/**
 * The international standing-order mapper, which inverts four of its domestic sibling's rules.
 *
 * Each inversion is a refusal observed against the sandbox, and each is the kind of thing that gets
 * introduced by copying the other rail: one amount rather than three, no reference, a mandatory
 * charge bearer, and an IBAN creditor.
 */
class StandingOrderInitiationIntlMapperTest {

    private fun draft(reference: String? = "IGNORED ON THIS RAIL") = StandingOrderDraft(
        debtorAccount = null,
        creditor = CreditorSelection(
            name = "Klara Weiss",
            scheme = BeneficiaryScheme.Iban,
            identification = "DE89370400440532013000",
        ),
        frequency = StandingOrderFrequency.Monthly,
        firstPaymentDate = "2026-08-20",
        finalPaymentDate = "2026-12-11",
        firstPaymentAmountMinorUnits = 25_000L,
        currency = "GBP",
        reference = reference,
        consentIdempotencyKey = "so-consent-key-1",
        paymentIdempotencyKey = "so-payment-key-1",
        currencyOfTransfer = "USD",
        chargeBearer = ChargeBearer.BorneByCreditor,
    )

    @Test
    fun theConsentBodyCarriesTheCreatePermission() {
        assertEquals("Create", draft().toIntlStandingOrderConsentRequest().data?.permission)
    }

    @Test
    fun theSubmissionBodyDoesNotRepeatThePermission() {
        val submission = draft().toIntlStandingOrderRequest("45173")

        assertNull(submission.data?.permission)
        assertEquals("45173", submission.data?.consentId)
    }

    @Test
    fun theConsentAndSubmissionInitiationsAreIdentical() {
        val d = draft()

        assertEquals(
            d.toIntlStandingOrderConsentRequest().data?.initiation,
            d.toIntlStandingOrderRequest("45173").data?.initiation,
        )
    }

    /** Mandatory here; omitting it is `400 U004`. */
    @Test
    fun theTransferCurrencyAndChargeBearerAreSent() {
        val initiation = draft().toIntlStandingOrderInitiation()

        assertEquals("USD", initiation.currencyOfTransfer)
        assertEquals("BorneByCreditor", initiation.chargeBearer)
    }

    /**
     * One amount, and the domestic rail's three have nowhere to go.
     *
     * `OBInternationalStandingOrder4` defines no first, recurring or final amount member at all, so
     * the form disables those fields rather than hiding them — and the builder reads only enabled
     * ones, which is what makes this assertion hold even after a rail switch.
     */
    @Test
    fun theAmountIsTheSingleInstructedAmount() {
        assertEquals("250.00", draft().toIntlStandingOrderInitiation().instructedAmount?.amount)
    }

    /** Refused `U005` on this rail; accepted on the domestic one. */
    @Test
    fun theReferenceIsNeverSentOnThisRail() {
        val consent = draft().toIntlStandingOrderConsentRequest()

        assertEquals("Klara Weiss", consent.data?.initiation?.creditorAccount?.name)
        assertEquals(draft(reference = null).toIntlStandingOrderInitiation(), consent.data?.initiation)
    }

    /** A sort-code creditor is refused `U027` here, exactly as an IBAN is on the domestic rail. */
    @Test
    fun theCreditorIsAlwaysAnIban() {
        assertEquals(
            "UK.OBIE.IBAN",
            draft().toIntlStandingOrderInitiation().creditorAccount?.schemeName,
        )
    }

    @Test
    fun theScheduleIsSentTheSameWayOnBothRails() {
        val mandate = draft().toIntlStandingOrderInitiation().mandateRelatedInformation

        assertEquals("MNTH", mandate?.frequency?.type)
        assertEquals("2026-08-20T00:00:00+00:00", mandate?.firstPaymentDateTime)
        assertEquals("2026-12-11T00:00:00+00:00", mandate?.finalPaymentDateTime)
    }

    private fun response(charges: List<Charge>? = null) = InternationalStandingOrderResponse(
        data = IntlSoData(
            internationalStandingOrderId = "19917",
            consentId = "45173",
            status = "INCO",
            creationDateTime = "2026-08-13T10:44:02+00:00",
            statusUpdateDateTime = "2026-08-13T10:44:02+00:00",
            initiation = IntlSoInitiation(
                mandateRelatedInformation = IntlSoMandate(
                    frequency = IntlSoFrequency(type = "MNTH"),
                    firstPaymentDateTime = "2026-08-20T00:00:00+00:00",
                ),
                instructedAmount = IntlSoAmount(amount = "250.00", currency = "GBP"),
            ),
            charges = charges,
        ),
    )

    @Test
    fun theCreatedMandateResolvesTheInitiationCompletedStatus() {
        assertEquals(PaymentStatus.InitiationCompleted, response().toIntlStandingOrderReceipt().status)
    }

    /**
     * The charge exists here and could not have been shown on the review screen.
     *
     * The consent response on this rail carries none, so this is the first moment the figure is
     * knowable — and by then the customer has already authorised.
     */
    @Test
    fun theReceiptCarriesTheChargeDeclaredOnlyAtCreation() {
        val charges = listOf(
            Charge(
                chargeBearer = "BorneByDebtor",
                type = "UK.OBIE.CHAPSOut",
                amount = ChargeAmount(amount = "0.50", currency = "GBP"),
            ),
        )

        val mapped = response(charges).toIntlStandingOrderReceipt().charges.single()

        assertEquals("UK.OBIE.CHAPSOut", mapped.typeLabel)
    }

    @Test
    fun theReceiptCarriesNoReferenceOnThisRail() {
        assertTrue(response().toIntlStandingOrderReceipt().reference.isEmpty())
    }
}
