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
import org.mifosx.openbanking.core.model.banking.payment.ChargeBearer
import org.mifosx.openbanking.core.model.banking.payment.CreditorSelection
import org.mifosx.openbanking.core.model.banking.payment.PaymentStatus
import org.mifosx.openbanking.core.model.banking.payment.ScheduledPaymentDraft
import org.mifosx.openbanking.core.network.model.pisp.internationalScheduledPayment.response.Charge
import org.mifosx.openbanking.core.network.model.pisp.internationalScheduledPayment.response.ChargeAmount
import org.mifosx.openbanking.core.network.model.pisp.internationalScheduledPayment.response.InternationalScheduledPaymentResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.mifosx.openbanking.core.network.model.pisp.internationalScheduledPayment.response.Data as IntlSchedRespData
import org.mifosx.openbanking.core.network.model.pisp.internationalScheduledPayment.response.Initiation as IntlSchedRespInitiation
import org.mifosx.openbanking.core.network.model.pisp.internationalScheduledPayment.response.InstructedAmount as IntlSchedRespAmount

/**
 * The international scheduled mapper, which inverts three of the domestic rail's rules.
 *
 * Each inversion is a proven refusal rather than a preference: `RemittanceInformation` is `U005`
 * here, `ChargeBearer` is `U004` when missing, and a sort-code creditor is `U027`.
 */
class ScheduledPaymentInitiationIntlMapperTest {

    private fun draft(
        debtor: BankAccount? = BankAccount(
            accountId = "acc-1",
            nickname = "",
            accountSubType = "CurrentAccount",
            currency = "GBP",
            sortCode = "802001",
            accountNumber = "10203349",
            rawIdentification = "80200110203349",
        ),
    ) = ScheduledPaymentDraft(
        debtorAccount = debtor,
        creditor = CreditorSelection(
            name = "Klara Weiss",
            scheme = BeneficiaryScheme.Iban,
            identification = "DE89370400440532013000",
        ),
        amountMinorUnits = 25_000L,
        currency = "GBP",
        // Set on the immediate rail's drafts too, and refused on this one — the mapper must drop it.
        reference = "RENT-AUG",
        instructionIdentification = "MFX20260811T1000000002",
        endToEndIdentification = "E2E-INTLSCHED-202608",
        consentIdempotencyKey = "consent-key-2",
        paymentIdempotencyKey = "payment-key-2",
        requestedExecutionDate = "2026-08-14",
        currencyOfTransfer = "EUR",
        chargeBearer = ChargeBearer.BorneByCreditor,
    )

    @Test
    fun theConsentBodyCarriesTheCreatePermission() {
        assertEquals("Create", draft().toIntlScheduledConsentRequest().data?.permission)
    }

    @Test
    fun thePaymentBodyDoesNotRepeatThePermission() {
        val payment = draft().toIntlScheduledPaymentRequest("45157")

        assertNull(payment.data?.permission)
        assertEquals("45157", payment.data?.consentId)
    }

    @Test
    fun theConsentAndPaymentInitiationsAreIdentical() {
        val d = draft()

        assertEquals(
            d.toIntlScheduledConsentRequest().data?.initiation,
            d.toIntlScheduledPaymentRequest("45157").data?.initiation,
        )
    }

    @Test
    fun theExecutionDateIsSentAtMidnightUtc() {
        assertEquals(
            "2026-08-14T00:00:00+00:00",
            draft().toIntlScheduledInitiation().requestedExecutionDateTime,
        )
    }

    @Test
    fun noLocalInstrumentIsSent() {
        assertNull(draft().toIntlScheduledInitiation().localInstrument)
    }

    @Test
    fun theTransferCurrencyAndChargeBearerAreSent() {
        val initiation = draft().toIntlScheduledInitiation()

        assertEquals("EUR", initiation.currencyOfTransfer)
        assertEquals("BorneByCreditor", initiation.chargeBearer)
    }

    /** `RemittanceInformation` is refused `U005` on this rail even when the draft carries one. */
    @Test
    fun theReferenceIsNeverSentOnThisRail() {
        val json = draft().toIntlScheduledConsentRequest().toString()

        assertTrue("RENT-AUG" !in json, "the reference must not reach the international rail")
    }

    /** Always an IBAN — a sort-code creditor is refused `U027` here. */
    @Test
    fun theCreditorIsAlwaysAnIban() {
        assertEquals("UK.OBIE.IBAN", draft().toIntlScheduledInitiation().creditorAccount?.schemeName)
    }

    @Test
    fun lettingTheBankChooseSendsNoDebtorAccount() {
        assertNull(draft(debtor = null).toIntlScheduledInitiation().debtorAccount)
    }

    /** `Risk` carries the category purpose only — no payment context code, which is the domestic shape. */
    @Test
    fun theRiskBlockCarriesOnlyTheCategoryPurpose() {
        assertEquals("EPAY", draft().toIntlScheduledRisk().categoryPurposeCode)
    }

    private fun response(charges: List<Charge>? = null) = InternationalScheduledPaymentResponse(
        data = IntlSchedRespData(
            internationalScheduledPaymentId = "19921",
            consentId = "45176",
            status = "INCO",
            creationDateTime = "2026-08-06T10:44:12+00:00",
            statusUpdateDateTime = "2026-08-06T10:44:12+00:00",
            initiation = IntlSchedRespInitiation(
                requestedExecutionDateTime = "2026-08-13T00:00:00+00:00",
                instructedAmount = IntlSchedRespAmount(amount = "5.00", currency = "GBP"),
            ),
            charges = charges,
        ),
    )

    @Test
    fun theCreatedPaymentResolvesTheInitiationCompletedStatus() {
        assertEquals(PaymentStatus.InitiationCompleted, response().toIntlScheduledPaymentReceipt().status)
    }

    @Test
    fun theReceiptCarriesTheRequestedDateAndNoSettlementDate() {
        val receipt = response().toIntlScheduledPaymentReceipt()

        assertEquals("2026-08-13T00:00:00+00:00", receipt.requestedExecutionDateTime)
        assertTrue(receipt.settlementDateTime.isEmpty())
    }

    /**
     * This rail declares its charge only at creation, which is after the customer authorised.
     *
     * Capturing it is the whole reason the response DTO was extended: the bank does state a figure,
     * and without this field the app discarded it silently.
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

        val mapped = response(charges).toIntlScheduledPaymentReceipt().charges.single()

        assertEquals("BorneByDebtor", mapped.bearer)
        assertEquals("UK.OBIE.CHAPSOut", mapped.typeLabel)
    }

    @Test
    fun theReceiptCarriesNoReferenceOnThisRail() {
        assertTrue(response().toIntlScheduledPaymentReceipt().reference.isEmpty())
    }
}
