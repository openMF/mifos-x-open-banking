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
import org.mifosx.openbanking.core.model.banking.payment.ScheduledPaymentDraft
import org.mifosx.openbanking.core.network.model.pisp.domesticScheduledPayment.response.Charge
import org.mifosx.openbanking.core.network.model.pisp.domesticScheduledPayment.response.ChargeAmount
import org.mifosx.openbanking.core.network.model.pisp.domesticScheduledPayment.response.DomesticScheduledPaymentResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.mifosx.openbanking.core.network.model.pisp.domesticScheduledPayment.response.Data as SchedRespData
import org.mifosx.openbanking.core.network.model.pisp.domesticScheduledPayment.response.Initiation as SchedRespInitiation
import org.mifosx.openbanking.core.network.model.pisp.domesticScheduledPayment.response.InstructedAmount as SchedRespAmount

/**
 * The domestic scheduled mapper, with one recurring question: **does the submission say exactly what
 * the consent said?**
 *
 * The bank enforces that with `U008`, and this rail adds a way to break it the immediate rail does
 * not have — the request `Data` class is shared between the consent and the payment, so `Permission`
 * is reachable on both and sending it twice would diverge the bodies.
 */
class ScheduledPaymentInitiationMapperTest {

    private fun draft(
        reference: String? = "RENT-AUG",
        debtor: BankAccount? = BankAccount(
            accountId = "acc-1",
            nickname = "",
            accountSubType = "CurrentAccount",
            currency = "GBP",
            sortCode = "802001",
            accountNumber = "10203349",
            rawIdentification = "80200110203349",
        ),
        ownAccount: Boolean = false,
    ) = ScheduledPaymentDraft(
        debtorAccount = debtor,
        creditor = CreditorSelection(
            name = "Mr Dharani C",
            scheme = BeneficiaryScheme.SortCode,
            identification = "80200110203350",
            isOwnAccount = ownAccount,
        ),
        amountMinorUnits = 25_000L,
        currency = "GBP",
        reference = reference,
        instructionIdentification = "MFX20260811T1000000001",
        endToEndIdentification = "E2E-SCHED-202608",
        consentIdempotencyKey = "consent-key-1",
        paymentIdempotencyKey = "payment-key-1",
        requestedExecutionDate = "2026-08-14",
    )

    @Test
    fun theConsentBodyCarriesTheCreatePermission() {
        assertEquals("Create", draft().toScheduledConsentRequest().data?.permission)
    }

    /**
     * The submission must not repeat `Permission`.
     *
     * The two bodies share one `Data` class on this rail, so the field is reachable here and setting
     * it would make the submitted body differ from the staged one — the single thing `U008` refuses.
     */
    @Test
    fun thePaymentBodyDoesNotRepeatThePermission() {
        val payment = draft().toScheduledPaymentRequest("45149")

        assertNull(payment.data?.permission)
        assertEquals("45149", payment.data?.consentId)
    }

    /** The property the whole staging-then-submitting design rests on. */
    @Test
    fun theConsentAndPaymentInitiationsAreIdentical() {
        val d = draft()

        assertEquals(
            d.toScheduledConsentRequest().data?.initiation,
            d.toScheduledPaymentRequest("45149").data?.initiation,
        )
    }

    /** Building the same draft twice must not drift — nothing here may read a clock. */
    @Test
    fun theSameDraftAlwaysYieldsAnEqualInitiation() {
        val d = draft()

        assertEquals(d.toScheduledInitiation(), d.toScheduledInitiation())
    }

    /**
     * The date goes out as midnight UTC with no local offset.
     *
     * Only `+00:00` has ever been sent to this bank, and the bank normalises whatever time it is
     * given to midnight anyway. Appending a fixed suffix means the two bodies agree by construction
     * and no timezone can shift the calendar day.
     */
    @Test
    fun theExecutionDateIsSentAtMidnightUtc() {
        assertEquals("2026-08-14T00:00:00+00:00", draft().toScheduledInitiation().requestedExecutionDateTime)
    }

    /** The scheduled rails send no `LocalInstrument`; the proven consent bodies carry no such key. */
    @Test
    fun noLocalInstrumentIsSent() {
        assertNull(draft().toScheduledInitiation().localInstrument)
    }

    @Test
    fun theDomesticRailSendsRemittanceInformation() {
        assertEquals(
            listOf("RENT-AUG"),
            draft().toScheduledInitiation().remittanceInformation?.unstructured,
        )
    }

    /** A blank reference is omitted rather than sent empty. */
    @Test
    fun aBlankReferenceIsOmittedEntirely() {
        assertNull(draft(reference = "  ").toScheduledInitiation().remittanceInformation)
        assertNull(draft(reference = null).toScheduledInitiation().remittanceInformation)
    }

    /**
     * No debtor at all when the customer asked the bank to choose.
     *
     * This is the only route to paying from a card or a Global Money wallet, both of which the bank
     * refuses when they are named but accepts when it picks them itself.
     */
    @Test
    fun lettingTheBankChooseSendsNoDebtorAccount() {
        assertNull(draft(debtor = null).toScheduledInitiation().debtorAccount)
    }

    @Test
    fun aNamedDebtorIsSentAsSortCodeAndAccountNumber() {
        val debtor = draft().toScheduledInitiation().debtorAccount

        assertEquals("UK.OBIE.SortCodeAccountNumber", debtor?.schemeName)
        assertEquals("80200110203349", debtor?.identification)
    }

    /**
     * The creditor scheme is stated, not derived from the beneficiary's own.
     *
     * This rail refuses an IBAN creditor with `U027`. Mapping the beneficiary's scheme would let a
     * Paym or card payee reach an endpoint that cannot accept one.
     */
    @Test
    fun theCreditorIsAlwaysSortCodeAndAccountNumber() {
        assertEquals(
            "UK.OBIE.SortCodeAccountNumber",
            draft().toScheduledInitiation().creditorAccount?.schemeName,
        )
    }

    @Test
    fun amountsConvertToMajorUnitsWithoutFloatingPoint() {
        assertEquals("250.00", draft().toScheduledInitiation().instructedAmount?.amount)
    }

    @Test
    fun theRiskBlockCarriesOnlyThePaymentContext() {
        assertEquals("TransferToThirdParty", draft().toScheduledRisk().paymentContextCode)
        assertEquals("TransferToSelf", draft(ownAccount = true).toScheduledRisk().paymentContextCode)
    }

    private fun response(charges: List<Charge>? = null) = DomesticScheduledPaymentResponse(
        data = SchedRespData(
            domesticScheduledPaymentId = "19919",
            consentId = "45175",
            status = "INCO",
            creationDateTime = "2026-08-06T10:42:08+00:00",
            statusUpdateDateTime = "2026-08-06T10:42:08+00:00",
            initiation = SchedRespInitiation(
                requestedExecutionDateTime = "2026-08-13T00:00:00+00:00",
                instructedAmount = SchedRespAmount(amount = "5.00", currency = "GBP"),
            ),
            charges = charges,
        ),
    )

    /** `INCO` is the status both scheduled rails return, and it must not fall through to Unknown. */
    @Test
    fun theCreatedPaymentResolvesTheInitiationCompletedStatus() {
        assertEquals(PaymentStatus.InitiationCompleted, response().toScheduledPaymentReceipt().status)
    }

    /**
     * The settlement date is left empty and the requested date carried instead.
     *
     * The bank returns an `ExpectedSettlementDateTime` equal to `CreationDateTime` — today — not the
     * date the customer chose. Mapping it would render a payment due next week as settling now.
     */
    @Test
    fun theReceiptCarriesTheRequestedDateAndNoSettlementDate() {
        val receipt = response().toScheduledPaymentReceipt()

        assertEquals("2026-08-13T00:00:00+00:00", receipt.requestedExecutionDateTime)
        assertTrue(receipt.settlementDateTime.isEmpty())
    }

    /** The charge the bank declares must reach the receipt rather than being silently dropped. */
    @Test
    fun theReceiptCarriesTheBanksCharge() {
        val charges = listOf(
            Charge(
                chargeBearer = "BorneByDebtor",
                type = "UK.OBIE.CHAPSOut",
                amount = ChargeAmount(amount = "0.05", currency = "GBP"),
            ),
        )

        val mapped = response(charges).toScheduledPaymentReceipt().charges.single()

        assertEquals("BorneByDebtor", mapped.bearer)
        assertEquals("UK.OBIE.CHAPSOut", mapped.typeLabel)
    }

    @Test
    fun aResponseWithNoChargesYieldsAnEmptyListNotAZeroCharge() {
        assertTrue(response().toScheduledPaymentReceipt().charges.isEmpty())
    }
}
