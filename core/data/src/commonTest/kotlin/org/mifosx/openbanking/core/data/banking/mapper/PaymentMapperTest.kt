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

import org.mifosx.openbanking.core.common.AccountScheme
import org.mifosx.openbanking.core.model.banking.BankAccount
import org.mifosx.openbanking.core.model.banking.BeneficiaryScheme
import org.mifosx.openbanking.core.model.banking.payment.CreditorSelection
import org.mifosx.openbanking.core.model.banking.payment.PaymentDisposition
import org.mifosx.openbanking.core.model.banking.payment.PaymentDraft
import org.mifosx.openbanking.core.model.banking.payment.PaymentStatus
import org.mifosx.openbanking.core.network.model.pisp.domesticPayment.response.Data
import org.mifosx.openbanking.core.network.model.pisp.domesticPayment.response.DomesticPaymentResponse
import org.mifosx.openbanking.core.network.model.pisp.domesticPayment.response.Initiation
import org.mifosx.openbanking.core.network.model.pisp.domesticPayment.response.InstructedAmount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins the two properties the payment feature exists to protect, plus the amount arithmetic.
 *
 * The fixtures are the sandbox capture used throughout the spec: £850.00 from the PSU's current
 * account to Jameson Lettings.
 */
class PaymentMapperTest {

    private fun draft(
        amountMinorUnits: Long = 85_000L,
        reference: String? = "RENT-FLAT12",
        isOwnAccount: Boolean = false,
    ) = PaymentDraft(
        debtorAccount = BankAccount(
            accountId = "123456791",
            accountHolderName = "Mr Nico",
            accountTypeCode = "CACC",
            currency = "GBP",
            identification = "80200110203349",
            scheme = AccountScheme.SortCode,
        ),
        creditor = CreditorSelection(
            name = "Jameson Lettings",
            scheme = BeneficiaryScheme.SortCode,
            identification = "40120965872310",
            beneficiaryId = "BEN-001",
            isOwnAccount = isOwnAccount,
        ),
        amountMinorUnits = amountMinorUnits,
        currency = "GBP",
        reference = reference,
        instructionIdentification = "MFX20260805T1042330001",
        endToEndIdentification = "E2E-RENT-FLAT12-202608",
        consentIdempotencyKey = "MFX-20260805-0001-consent",
        paymentIdempotencyKey = "MFX-20260805-0001-payment",
    )

    /**
     * The `U008` guard. Staging and submitting build the `Initiation` from one draft, so the two
     * must be equal — if anything here read a clock or a random source, they would not be.
     */
    @Test
    fun buildsAnIdenticalInitiationEveryTimeFromTheSameDraft() {
        val draft = draft()

        assertEquals(draft.toInitiation(), draft.toInitiation())
        assertEquals(
            draft.toConsentRequest().data?.initiation,
            draft.toPaymentRequest("812774903").data?.initiation,
        )
    }

    @Test
    fun bindsTheSubmissionToTheAuthorisedConsent() {
        val request = draft().toPaymentRequest("812774903")

        assertEquals("812774903", request.data?.consentId)
        assertNull(draft().toConsentRequest().data?.consentId)
    }

    /**
     * TC-SEND-005. The merchant fields describe a merchant collecting from a shopper, which this app
     * never is; leaving them null is what keeps them off the wire.
     */
    @Test
    fun risksAConsumerContextAndNoMerchantFields() {
        val risk = draft().toRisk()

        assertEquals("TransferToThirdParty", risk.paymentContextCode)
        assertNull(risk.merchantCategoryCode)
        assertNull(risk.merchantCustomerIdentification)
        assertNull(risk.deliveryAddress)
    }

    /** TC-SEND-006. */
    @Test
    fun risksATransferToSelfWhenPayingAnOwnAccount() {
        assertEquals("TransferToSelf", draft(isOwnAccount = true).toRisk().paymentContextCode)
    }

    @Test
    fun rendersMinorUnitsAsAnObieMajorUnitString() {
        assertEquals("850.00", draft(amountMinorUnits = 85_000L).toInitiation().instructedAmount?.amount)
        assertEquals("4.82", draft(amountMinorUnits = 482L).toInitiation().instructedAmount?.amount)
        assertEquals("0.01", draft(amountMinorUnits = 1L).toInitiation().instructedAmount?.amount)
        assertEquals("1000.00", draft(amountMinorUnits = 100_000L).toInitiation().instructedAmount?.amount)
    }

    @Test
    fun sendsTheSortCodeSchemeWithUnseparatedIdentification() {
        val initiation = draft().toInitiation()

        assertEquals("UK.OBIE.SortCodeAccountNumber", initiation.debtorAccount?.schemeName)
        assertEquals("80200110203349", initiation.debtorAccount?.identification)
        assertEquals("UK.OBIE.SortCodeAccountNumber", initiation.creditorAccount?.schemeName)
        assertEquals("40120965872310", initiation.creditorAccount?.identification)
    }

    /**
     * HSBC refuses a domestic consent without `Structured[0].CreditorReferenceInformation.Reference`
     * (`U004`), with or without a reference, so it is always sent: the PSU's reference, or the
     * end-to-end id when they gave none. `Unstructured` carries only a real reference.
     */
    @Test
    fun alwaysSendsAStructuredCreditorReference() {
        val given = draft().toInitiation().remittanceInformation
        assertEquals(listOf("RENT-FLAT12"), given?.unstructured)
        assertEquals("RENT-FLAT12", given?.structured?.single()?.creditorReferenceInformation?.reference)

        for (blank in listOf(null, "   ", "!!")) {
            val none = draft(reference = blank).toInitiation().remittanceInformation
            // The end-to-end id, cut to the 18-char FPS limit.
            assertEquals("E2E-RENT-FLAT12-20", none?.structured?.single()?.creditorReferenceInformation?.reference)
        }
        assertNull(draft(reference = null).toInitiation().remittanceInformation?.unstructured)
    }

    /** HSBC allows 18 chars of A-Z a-z 0-9 & - . / (`U002`); `Unstructured` keeps the reference as typed. */
    @Test
    fun theStructuredReferenceIsCutToTheFpsCharacterSet() {
        val remittance = draft(reference = "Rent for flat 12, Oct/Nov & more").toInitiation().remittanceInformation
        assertEquals("Rentforflat12Oct/N", remittance?.structured?.single()?.creditorReferenceInformation?.reference)
        assertEquals(listOf("Rent for flat 12, Oct/Nov & more"), remittance?.unstructured)
    }

    @Test
    fun readsASubmittedPaymentBackOffTheBanksEcho() {
        val receipt = DomesticPaymentResponse(
            data = Data(
                domesticPaymentId = "PMT-812774903-01",
                consentId = "812774903",
                status = "AcceptedSettlementInProcess",
                statusUpdateDateTime = "2026-08-05T10:44:05+00:00",
                initiation = Initiation(
                    instructedAmount = InstructedAmount(amount = "850.00", currency = "GBP"),
                    creditorAccount = org.mifosx.openbanking.core.network.model.pisp.domesticPayment
                        .response.CreditorAccount(name = "Jameson Lettings"),
                ),
            ),
        ).toPaymentReceipt()

        assertEquals("PMT-812774903-01", receipt.domesticPaymentId)
        assertEquals("812774903", receipt.consentId)
        assertEquals(PaymentStatus.AcceptedSettlementInProcess, receipt.status)
        assertEquals("£850.00", receipt.amountLabel)
        assertEquals("Jameson Lettings", receipt.creditorName)
    }

    /** Both parties, as sandbox payment 20123 returns them. */
    @Test
    fun carriesBothPartiesNameIdentificationAndSchemeOffTheEcho() {
        val receipt = DomesticPaymentResponse(
            data = Data(
                domesticPaymentId = "20123",
                consentId = "45689",
                status = "ACCC",
                initiation = Initiation(
                    instructedAmount = InstructedAmount(amount = "6.00", currency = "GBP"),
                    debtorAccount = org.mifosx.openbanking.core.network.model.pisp.domesticPayment
                        .response.DebtorAccount(
                            schemeName = "UK.OBIE.SortCodeAccountNumber",
                            identification = "80200110203349",
                            name = "Mr Nico",
                        ),
                    creditorAccount = org.mifosx.openbanking.core.network.model.pisp.domesticPayment
                        .response.CreditorAccount(
                            schemeName = "UK.OBIE.SortCodeAccountNumber",
                            identification = "80200110203350",
                            name = "Mr Dharani C",
                        ),
                ),
            ),
        ).toPaymentReceipt()

        assertEquals("Mr Nico", receipt.debtorName)
        assertEquals("80200110203349", receipt.debtorIdentification)
        assertEquals("UK.OBIE.SortCodeAccountNumber", receipt.debtorScheme)
        assertEquals("Mr Dharani C", receipt.creditorName)
        assertEquals("80200110203350", receipt.creditorIdentification)
        assertEquals("UK.OBIE.SortCodeAccountNumber", receipt.creditorScheme)
    }

    /** A payer chosen at the bank leaves `DebtorAccount` off the response entirely. */
    @Test
    fun leavesTheDebtorBlankWhenTheResponseCarriesNoDebtorAccount() {
        val receipt = DomesticPaymentResponse(
            data = Data(
                domesticPaymentId = "20123",
                consentId = "45689",
                status = "ACCC",
                initiation = Initiation(
                    instructedAmount = InstructedAmount(amount = "6.00", currency = "GBP"),
                    creditorAccount = org.mifosx.openbanking.core.network.model.pisp.domesticPayment
                        .response.CreditorAccount(name = "Mr Dharani C"),
                ),
            ),
        ).toPaymentReceipt()

        assertEquals("", receipt.debtorName)
        assertEquals("", receipt.debtorIdentification)
        assertEquals("", receipt.debtorScheme)
    }

    /** HSBC returns the ISO codes on some responses and the long OBIE names on others. */
    @Test
    fun resolvesBothTheIsoCodeAndTheLongStatusName() {
        assertEquals(PaymentStatus.AcceptedSettlementInProcess, PaymentStatus.fromWire("ACSP"))
        assertEquals(
            PaymentStatus.AcceptedSettlementInProcess,
            PaymentStatus.fromWire("AcceptedSettlementInProcess"),
        )
        assertEquals(PaymentStatus.Rejected, PaymentStatus.fromWire("RJCT"))
        assertEquals(PaymentStatus.Received, PaymentStatus.fromWire("RCVD"))
    }

    /**
     * A settled payment must resolve to a terminal success whichever spelling arrives, or it reads
     * as still in progress for ever.
     */
    @Test
    fun resolvesEverySettledSpellingOntoATerminalSuccess() {
        val settled = listOf(
            "ACCC",
            "AcceptedCreditSettlementCompleted",
            "AcceptedSettlementCompletedCreditorAccount",
        )
        settled.forEach { raw ->
            assertEquals(PaymentStatus.AcceptedCreditSettlementCompleted, PaymentStatus.fromWire(raw), raw)
            assertEquals(PaymentDisposition.TerminalSuccess, PaymentStatus.fromWire(raw).disposition, raw)
        }

        assertEquals(
            PaymentStatus.AcceptedSettlementCompleted,
            PaymentStatus.fromWire("AcceptedSettlementCompletedDebitorAccount"),
        )
    }

    /**
     * An unrecognised status reads as still in progress. Reporting a settlement that may not have
     * happened is the worse of the two ways to be wrong.
     */
    @Test
    fun treatsAnUnknownStatusAsStillInProgress() {
        assertEquals(PaymentStatus.Unknown, PaymentStatus.fromWire("SOMETHING_NEW"))
        assertEquals(PaymentDisposition.InProgress, PaymentStatus.fromWire(null).disposition)
    }

    @Test
    fun mapsEachStatusOntoItsDisposition() {
        assertEquals(PaymentDisposition.InProgress, PaymentStatus.Pending.disposition)
        assertEquals(PaymentDisposition.TerminalSuccess, PaymentStatus.AcceptedSettlementCompleted.disposition)
        assertEquals(PaymentDisposition.TerminalFailure, PaymentStatus.Rejected.disposition)
    }
}
