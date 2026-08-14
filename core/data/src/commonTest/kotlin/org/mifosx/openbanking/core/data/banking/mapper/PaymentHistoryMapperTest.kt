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
import org.mifosx.openbanking.core.model.banking.payment.PaymentDraft
import org.mifosx.openbanking.core.model.banking.payment.PaymentReceipt
import org.mifosx.openbanking.core.model.banking.payment.PaymentStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaymentHistoryMapperTest {

    private fun payerAccount() = BankAccount(
        accountId = "123456791",
        nickname = "",
        accountSubType = "CurrentAccount",
        currency = "GBP",
        sortCode = "802001",
        accountNumber = "10203349",
        rawIdentification = "80200110203349",
    )

    private fun draft(
        amountMinorUnits: Long = 10_000L,
        reference: String? = "Dinner payment",
    ) = PaymentDraft(
        debtorAccount = payerAccount(),
        creditor = CreditorSelection(
            name = "Mr Dharani C",
            scheme = BeneficiaryScheme.SortCode,
            identification = "80200110203350",
            beneficiaryId = "BEN-001",
            isOwnAccount = false,
        ),
        amountMinorUnits = amountMinorUnits,
        currency = "GBP",
        reference = reference,
        instructionIdentification = "MFXa1b2c3d4",
        endToEndIdentification = "E2Ea1b2c3d4",
        consentIdempotencyKey = "key-consent-a1b2",
        paymentIdempotencyKey = "key-payment-a1b2",
    )

    private fun receipt(status: PaymentStatus = PaymentStatus.AcceptedSettlementCompleted) =
        PaymentReceipt(
            domesticPaymentId = "19901",
            consentId = "812774903",
            status = status,
            creationDateTime = "2026-08-07T15:30:00Z",
            statusUpdateDateTime = "2026-08-07T15:30:05Z",
            amountLabel = "£100.00",
            creditorName = "Mr Dharani C",
            settlementDateTime = "2026-08-07T16:30:00Z",
            reference = "Dinner payment",
            debtorIdentification = "80200110203349",
        )

    @Test
    fun mapsSubmittedReceiptToEntityWithAllFields() {
        val entity = receipt().toEntity(draft())

        assertEquals("19901", entity.id)
        assertEquals("19901", entity.paymentId)
        assertNull(entity.errorKind)
        assertNull(entity.errorDescription)
        assertEquals("AcceptedSettlementCompleted", entity.status)
        assertEquals("123456791", entity.debtorAccountId)
        assertEquals("CurrentAccount", entity.debtorName)
        assertEquals("80200110203349", entity.debtorIdentification)
        assertEquals("Mr Dharani C", entity.creditorName)
        assertEquals("80200110203350", entity.creditorIdentification)
        assertEquals(10_000L, entity.amountMinorUnits)
        assertEquals("GBP", entity.currency)
        assertEquals("Dinner payment", entity.reference)
        assertEquals("2026-08-07T15:30:00Z", entity.creationDateTime)
        assertEquals("2026-08-07T16:30:00Z", entity.settlementDateTime)
        assertEquals("domestic_payment", entity.paymentType)
    }

    @Test
    fun mapsPreSubmissionFailureToEntityWithNullFields() {
        val entity = draft().toFailureEntity("InsufficientFunds", "Not enough money")

        assertNull(entity.paymentId)
        assertEquals("InsufficientFunds", entity.errorKind)
        assertEquals("Not enough money", entity.errorDescription)
        assertNull(entity.status)
        assertNull(entity.settlementDateTime)
        assertNull(entity.syncedAt)
    }

    @Test
    fun usesNicknameWhenAvailable() {
        val d = draft().copy(
            debtorAccount = payerAccount().copy(nickname = "My Current"),
        )
        val entity = receipt().toEntity(d)

        assertEquals("My Current", entity.debtorName)
    }

    /** A draft the PSU never chose a payer for: the bank picks one during authorisation. */
    @Test
    fun writesBlankPayerColumnsWhenNoAccountWasChosen() {
        val entity = draft().copy(debtorAccount = null).toFailureEntity("NetworkError", "offline")

        assertEquals("", entity.debtorAccountId)
        assertEquals("", entity.debtorName)
        assertEquals("", entity.debtorIdentification)
    }

    /**
     * With no payer of our own, the identification recorded is the one the bank chose.
     *
     * The sandbox often answers a no-debtor consent with a Global Money wallet — the very product it
     * refuses when a TPP names it — so this cannot be assumed to be an account we offered.
     */
    @Test
    fun recordsTheBanksChosenPayerWhenNoneWasSent() {
        val bankChose = receipt().copy(debtorIdentification = "80119770009652")

        val entity = bankChose.toEntity(draft().copy(debtorAccount = null))

        assertEquals("80119770009652", entity.debtorIdentification)
        assertEquals("", entity.debtorAccountId)
    }

    /**
     * The rail is derived, not assumed.
     *
     * It was hardcoded to domestic on both paths, which mattered because the status read-back has to
     * hit the matching rail's endpoint — an international payment filed as domestic is looked up
     * against the wrong one.
     */
    @Test
    fun recordsAnInternationalDraftAsInternational() {
        val intl = draft().copy(
            currencyOfTransfer = "USD",
            chargeBearer = ChargeBearer.Shared,
        )

        val entity = receipt().toEntity(intl)

        assertEquals("international_payment", entity.paymentType)
        assertEquals("USD", entity.currencyOfTransfer)
        assertEquals("Shared", entity.chargeBearer)
    }

    @Test
    fun leavesTheInternationalColumnsNullOnADomesticPayment() {
        val entity = receipt().toEntity(draft())

        assertEquals("domestic_payment", entity.paymentType)
        assertNull(entity.currencyOfTransfer)
        assertNull(entity.chargeBearer)
    }

    /** The two stages OBIE never reports, so the timeline can only show what was observed. */
    @Test
    fun storesTheStageTimestampsItIsGiven() {
        val entity = receipt().toEntity(
            draft = draft(),
            approvedAt = "2026-08-07T09:20:00Z",
            submittedAt = "2026-08-07T09:45:00Z",
        )

        assertEquals("2026-08-07T09:20:00Z", entity.approvedAt)
        assertEquals("2026-08-07T09:45:00Z", entity.submittedAt)
    }

    @Test
    fun leavesTheStageTimestampsNullWhenNoneWereObserved() {
        val entity = receipt().toEntity(draft())

        assertNull(entity.approvedAt)
        assertNull(entity.submittedAt)
    }

    @Test
    fun fallsBackToAccountSubTypeWhenNicknameIsBlank() {
        val entity = receipt().toEntity(draft())

        assertEquals("CurrentAccount", entity.debtorName)
    }

    @Test
    fun omitsSettlementDateTimeWhenBlank() {
        val r = receipt().copy(settlementDateTime = "")
        val entity = r.toEntity(draft())

        assertNull(entity.settlementDateTime)
    }

    @Test
    fun omitsReferenceWhenNull() {
        val entity = receipt().copy(reference = "").toEntity(draft(reference = null))

        assertNull(entity.reference)
    }

    @Test
    fun generatesUniqueIdForEachFailureEntity() {
        val e1 = draft().toFailureEntity("NetworkError", "Connection failed")
        val e2 = draft().toFailureEntity("NetworkError", "Connection failed")

        assertTrue(e1.id != e2.id)
    }
}
