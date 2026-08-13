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

import org.mifosx.openbanking.core.model.banking.BankAccount
import org.mifosx.openbanking.core.model.banking.BeneficiaryScheme
import org.mifosx.openbanking.core.model.banking.payment.CreditorSelection
import org.mifosx.openbanking.core.model.banking.payment.PaymentStatus
import org.mifosx.openbanking.core.model.banking.payment.StandingOrderDraft
import org.mifosx.openbanking.core.model.banking.payment.StandingOrderFrequency
import org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.response.Charge
import org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.response.ChargeAmount
import org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.response.DomesticStandingOrderResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.response.Data as SoRespData
import org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.response.FirstPaymentAmount as SoRespAmount
import org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.response.Frequency as SoRespFrequency
import org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.response.Initiation as SoRespInitiation
import org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.response.MandateRelatedInformation as SoRespMandate

/**
 * The domestic standing-order mapper, with the same recurring question its siblings ask: **does the
 * submission say exactly what the consent said?**
 *
 * The bank enforces that with `U008`, and this rail shares the scheduled one's way of breaking it —
 * the request `Data` class serves both bodies, so `Permission` is reachable on the submission and
 * sending it twice would diverge them.
 *
 * The cases about fields that must NOT appear are as load-bearing as the ones about fields that must.
 * Each names a refusal observed against the sandbox, and every one of them is a field an OBIE-shaped
 * instinct suggests adding.
 */
class StandingOrderInitiationMapperTest {

    private fun draft(
        reference: String? = "FLAT 4B RENT",
        debtor: BankAccount? = BankAccount(
            accountId = "acc-1",
            nickname = "",
            accountSubType = "CurrentAccount",
            currency = "GBP",
            sortCode = "802001",
            accountNumber = "10203349",
            rawIdentification = "80200110203349",
        ),
        finalPaymentDate: String? = "2026-12-11",
        recurring: Long? = null,
        final: Long? = null,
    ) = StandingOrderDraft(
        debtorAccount = debtor,
        creditor = CreditorSelection(
            name = "Mr Dharani C",
            scheme = BeneficiaryScheme.SortCode,
            identification = "80200110203350",
        ),
        frequency = StandingOrderFrequency.Monthly,
        firstPaymentDate = "2026-08-20",
        finalPaymentDate = finalPaymentDate,
        firstPaymentAmountMinorUnits = 25_000L,
        recurringPaymentAmountMinorUnits = recurring,
        finalPaymentAmountMinorUnits = final,
        currency = "GBP",
        reference = reference,
        consentIdempotencyKey = "so-consent-key-1",
        paymentIdempotencyKey = "so-payment-key-1",
    )

    // region — the two bodies must agree

    @Test
    fun theConsentBodyCarriesTheCreatePermission() {
        assertEquals("Create", draft().toStandingOrderConsentRequest().data?.permission)
    }

    @Test
    fun theSubmissionBodyDoesNotRepeatThePermission() {
        val submission = draft().toStandingOrderRequest("45171")

        assertNull(submission.data?.permission)
        assertEquals("45171", submission.data?.consentId)
    }

    /** The property the whole staging-then-submitting design rests on. */
    @Test
    fun theConsentAndSubmissionInitiationsAreIdentical() {
        val d = draft()

        assertEquals(
            d.toStandingOrderConsentRequest().data?.initiation,
            d.toStandingOrderRequest("45171").data?.initiation,
        )
    }

    /** Building the same draft twice must not drift — nothing here may read a clock. */
    @Test
    fun theSameDraftAlwaysYieldsAnEqualInitiation() {
        val d = draft()

        assertEquals(d.toStandingOrderInitiation(), d.toStandingOrderInitiation())
    }

    /**
     * `Risk` compares equal across the two bodies.
     *
     * It is an empty object, which is what the bank wants on this product — but an empty *class*
     * would have no structural equality, and this assertion would pass vacuously while the byte
     * identity it stands for went unchecked.
     */
    @Test
    fun theRiskBlockIsEmptyAndComparable() {
        val d = draft()

        assertEquals(d.toStandingOrderConsentRequest().risk, d.toStandingOrderRequest("45171").risk)
    }

    // endregion

    // region — the schedule

    @Test
    fun theFrequencyIsSentAsItsObieCode() {
        assertEquals(
            "MNTH",
            draft().toStandingOrderInitiation().mandateRelatedInformation?.frequency?.type,
        )
    }

    /** Dates go out at midnight UTC, so the two bodies agree by construction. */
    @Test
    fun bothDatesAreSentAtMidnightUtc() {
        val mandate = draft().toStandingOrderInitiation().mandateRelatedInformation

        assertEquals("2026-08-20T00:00:00+00:00", mandate?.firstPaymentDateTime)
        assertEquals("2026-12-11T00:00:00+00:00", mandate?.finalPaymentDateTime)
    }

    /** An open-ended mandate omits the final date rather than sending a sentinel. */
    @Test
    fun anOpenEndedMandateSendsNoFinalDate() {
        val mandate = draft(finalPaymentDate = null).toStandingOrderInitiation().mandateRelatedInformation

        assertNull(mandate?.finalPaymentDateTime)
        assertEquals("2026-08-20T00:00:00+00:00", mandate?.firstPaymentDateTime)
    }

    // endregion

    // region — amounts

    @Test
    fun amountsConvertToMajorUnitsWithoutFloatingPoint() {
        assertEquals("250.00", draft().toStandingOrderInitiation().firstPaymentAmount?.amount)
    }

    /** This rail's amount is FirstPaymentAmount; `InstructedAmount` here is refused `U005`. */
    @Test
    fun noInstructedAmountIsSentOnThisRail() {
        val initiation = draft().toStandingOrderInitiation()

        assertEquals("250.00", initiation.firstPaymentAmount?.amount)
        assertNull(initiation.recurringPaymentAmount, "absent unless the customer set a different one")
        assertNull(initiation.finalPaymentAmount)
    }

    @Test
    fun aDifferentRecurringOrFinalAmountIsSentWhenGiven() {
        val initiation = draft(recurring = 30_000L, final = 12_500L).toStandingOrderInitiation()

        assertEquals("300.00", initiation.recurringPaymentAmount?.amount)
        assertEquals("125.00", initiation.finalPaymentAmount?.amount)
    }

    // endregion

    // region — payer, payee and reference

    /**
     * No debtor at all when the customer asked the bank to choose.
     *
     * The only route to a mandate funded by a card or a Global Money wallet, both of which the bank
     * refuses when they are named and accepts when it picks them itself.
     */
    @Test
    fun lettingTheBankChooseSendsNoDebtorAccount() {
        assertNull(draft(debtor = null).toStandingOrderInitiation().debtorAccount)
    }

    @Test
    fun aNamedDebtorIsSentAsSortCodeAndAccountNumber() {
        val debtor = draft().toStandingOrderInitiation().debtorAccount

        assertEquals("UK.OBIE.SortCodeAccountNumber", debtor?.schemeName)
        assertEquals("80200110203349", debtor?.identification)
    }

    /** This rail refuses an IBAN creditor `U027`, so the scheme is stated, not derived from the payee. */
    @Test
    fun theCreditorIsAlwaysSortCodeAndAccountNumber() {
        assertEquals(
            "UK.OBIE.SortCodeAccountNumber",
            draft().toStandingOrderInitiation().creditorAccount?.schemeName,
        )
    }

    @Test
    fun theDomesticRailSendsRemittanceInformation() {
        assertEquals(
            listOf("FLAT 4B RENT"),
            draft().toStandingOrderInitiation().remittanceInformation?.unstructured,
        )
    }

    @Test
    fun aBlankReferenceIsOmittedEntirely() {
        assertNull(draft(reference = "  ").toStandingOrderInitiation().remittanceInformation)
        assertNull(draft(reference = null).toStandingOrderInitiation().remittanceInformation)
    }

    // endregion

    // region — reading the mandate back

    private fun response(charges: List<Charge>? = null) = DomesticStandingOrderResponse(
        data = SoRespData(
            domesticStandingOrderId = "19916",
            consentId = "45171",
            status = "INCO",
            creationDateTime = "2026-08-13T10:42:08+00:00",
            statusUpdateDateTime = "2026-08-13T10:42:08+00:00",
            initiation = SoRespInitiation(
                mandateRelatedInformation = SoRespMandate(
                    frequency = SoRespFrequency(type = "MNTH"),
                    firstPaymentDateTime = "2026-08-20T00:00:00+00:00",
                ),
                firstPaymentAmount = SoRespAmount(amount = "250.00", currency = "GBP"),
            ),
            charges = charges,
        ),
    )

    /** `INCO` is what both rails return on creation, and it must not fall through to Unknown. */
    @Test
    fun theCreatedMandateResolvesTheInitiationCompletedStatus() {
        assertEquals(PaymentStatus.InitiationCompleted, response().toStandingOrderReceipt().status)
    }

    /**
     * The first payment date is carried and no settlement date is invented.
     *
     * Empty here is the plain truth rather than a workaround for a bank quirk: a mandate has no single
     * settlement, so there is no date that could go in that field honestly.
     */
    @Test
    fun theReceiptCarriesTheFirstPaymentDateAndNoSettlementDate() {
        val receipt = response().toStandingOrderReceipt()

        assertEquals("2026-08-20T00:00:00+00:00", receipt.requestedExecutionDateTime)
        assertTrue(receipt.settlementDateTime.isEmpty())
    }

    /**
     * The charge must reach the receipt rather than being dropped.
     *
     * It was dropped until the response DTO grew a `Charges` member — the field simply had nowhere to
     * land, and nothing failed.
     */
    @Test
    fun theReceiptCarriesTheBanksCharge() {
        val charges = listOf(
            Charge(
                chargeBearer = "BorneByDebtor",
                type = "UK.OBIE.CHAPSOut",
                amount = ChargeAmount(amount = "0.05", currency = "GBP"),
            ),
        )

        val mapped = response(charges).toStandingOrderReceipt().charges.single()

        assertEquals("BorneByDebtor", mapped.bearer)
        assertEquals("UK.OBIE.CHAPSOut", mapped.typeLabel)
    }

    @Test
    fun aResponseWithNoChargesYieldsAnEmptyListNotAZeroCharge() {
        assertTrue(response().toStandingOrderReceipt().charges.isEmpty())
    }

    // endregion
}
