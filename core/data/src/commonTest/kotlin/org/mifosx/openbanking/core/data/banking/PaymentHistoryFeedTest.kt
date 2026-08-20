/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.data.banking

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.mifosx.openbanking.core.model.banking.payment.ConsentType
import org.mifosx.openbanking.core.model.banking.payment.PaymentDraft
import org.mifosx.openbanking.core.model.banking.payment.PaymentHistoryRow
import org.mifosx.openbanking.core.model.banking.payment.PaymentReceipt
import org.mifosx.openbanking.core.model.banking.payment.PaymentStageTimestamps
import org.mifosx.openbanking.core.model.banking.payment.PaymentStatus
import org.mifosx.openbanking.core.model.banking.payment.ScheduledPaymentDraft
import org.mifosx.openbanking.core.model.banking.payment.StandingOrderDraft
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult
import kotlin.test.Test
import kotlin.test.assertEquals

class PaymentHistoryFeedTest {

    private fun row(
        paymentId: String,
        status: PaymentStatus,
        consentType: ConsentType = ConsentType.DomesticSinglePayment,
    ) = PaymentHistoryRow(
        paymentId = paymentId,
        consentType = consentType,
        status = status,
        amountMinorUnits = 2_50,
        currency = "GBP",
        creditorName = "Mr Dharani C",
        submittedAt = "2026-08-15T18:00:00Z",
    )

    private fun receipt(status: PaymentStatus) = PaymentReceipt(
        domesticPaymentId = "19901",
        consentId = "45171",
        status = status,
        creationDateTime = "2026-08-15T18:00:00Z",
        statusUpdateDateTime = "2026-08-15T18:00:00Z",
        amountLabel = "£2.50",
        creditorName = "Mr Dharani C",
    )

    private class FakeHistory(
        val rows: MutableStateFlow<List<PaymentHistoryRow>>,
    ) : PaymentHistoryRepository {
        val recorded = mutableListOf<String>()

        override suspend fun saveSubmitted(receipt: PaymentReceipt, draft: PaymentDraft) = Unit
        override suspend fun saveSubmitted(receipt: PaymentReceipt, draft: ScheduledPaymentDraft) = Unit
        override suspend fun saveSubmitted(receipt: PaymentReceipt, draft: StandingOrderDraft) = Unit
        override suspend fun saveFailed(draft: PaymentDraft, errorKind: String, errorDescription: String) = Unit
        override suspend fun saveFailed(
            draft: ScheduledPaymentDraft,
            errorKind: String,
            errorDescription: String,
        ) = Unit

        override suspend fun saveFailed(
            draft: StandingOrderDraft,
            errorKind: String,
            errorDescription: String,
        ) = Unit

        override suspend fun consentTypeOf(paymentId: String): ConsentType? = null
        override suspend fun stageTimestampsOf(paymentId: String): PaymentStageTimestamps? = null
        override fun observeHistory(
            types: Set<ConsentType>,
            limit: Int,
        ): Flow<List<PaymentHistoryRow>> = rows

        override suspend fun recordStatus(paymentId: String, receipt: PaymentReceipt) {
            recorded += paymentId
        }
    }

    private class FakeStatus(
        private val answer: NetworkResult<PaymentReceipt, NetworkError>,
    ) : PaymentStatusRepository {
        val asked = mutableListOf<String>()

        override suspend fun paymentStatus(
            paymentId: String,
        ): NetworkResult<PaymentReceipt, NetworkError> {
            asked += paymentId
            return answer
        }
    }

    @Test
    fun readsBackAPaymentThatHasNotSettled() = runTest {
        val history = FakeHistory(MutableStateFlow(listOf(row("1", PaymentStatus.AcceptedSettlementInProcess))))
        val status = FakeStatus(NetworkResult.Success(receipt(PaymentStatus.AcceptedCreditSettlementCompleted)))

        PaymentHistoryFeed(history, status)
            .rows(setOf(ConsentType.DomesticSinglePayment), limit = 5, scope = this@runTest)
            .first()
        advanceUntilIdle()

        assertEquals(listOf("1"), status.asked)
        assertEquals(listOf("1"), history.recorded)
    }

    @Test
    fun leavesASettledPaymentAlone() = runTest {
        val history = FakeHistory(
            MutableStateFlow(listOf(row("1", PaymentStatus.AcceptedCreditSettlementCompleted))),
        )
        val status = FakeStatus(NetworkResult.Success(receipt(PaymentStatus.AcceptedCreditSettlementCompleted)))

        PaymentHistoryFeed(history, status)
            .rows(setOf(ConsentType.DomesticSinglePayment), limit = 5, scope = this@runTest)
            .first()
        advanceUntilIdle()

        assertEquals(emptyList(), status.asked)
    }

    /** The rail-aware case: a set-up mandate is final, so re-reading it would never stop. */
    @Test
    fun leavesASetUpStandingOrderAlone() = runTest {
        val history = FakeHistory(
            MutableStateFlow(
                listOf(
                    row("1", PaymentStatus.InitiationCompleted, ConsentType.DomesticStandingOrder),
                ),
            ),
        )
        val status = FakeStatus(NetworkResult.Success(receipt(PaymentStatus.InitiationCompleted)))

        PaymentHistoryFeed(history, status)
            .rows(setOf(ConsentType.DomesticStandingOrder), limit = 5, scope = this@runTest)
            .first()
        advanceUntilIdle()

        assertEquals(emptyList(), status.asked)
    }

    /** A single payment at the same status is still in flight — the rail is what separates them. */
    @Test
    fun readsBackAnInitiationCompletedSinglePayment() = runTest {
        val history = FakeHistory(
            MutableStateFlow(
                listOf(row("1", PaymentStatus.InitiationCompleted, ConsentType.DomesticSinglePayment)),
            ),
        )
        val status = FakeStatus(NetworkResult.Success(receipt(PaymentStatus.AcceptedCreditSettlementCompleted)))

        PaymentHistoryFeed(history, status)
            .rows(setOf(ConsentType.DomesticSinglePayment), limit = 5, scope = this@runTest)
            .first()
        advanceUntilIdle()

        assertEquals(listOf("1"), status.asked)
    }

    @Test
    fun readsEachPaymentBackOnlyOnceHoweverOftenStorageEmits() = runTest {
        val rows = MutableStateFlow(listOf(row("1", PaymentStatus.AcceptedSettlementInProcess)))
        val history = FakeHistory(rows)
        val status = FakeStatus(NetworkResult.Success(receipt(PaymentStatus.AcceptedSettlementInProcess)))

        val feed = PaymentHistoryFeed(history, status)
        val types = setOf(ConsentType.DomesticSinglePayment)

        feed.rows(types, limit = 5, scope = this@runTest).first()
        advanceUntilIdle()

        rows.value = listOf(row("1", PaymentStatus.AcceptedSettlementInProcess), row("2", PaymentStatus.Pending))
        feed.rows(types, limit = 5, scope = this@runTest).first()
        advanceUntilIdle()

        assertEquals(listOf("1", "2"), status.asked)
    }

    @Test
    fun aFailedReadLeavesTheStoredStatusAlone() = runTest {
        val history = FakeHistory(MutableStateFlow(listOf(row("1", PaymentStatus.AcceptedSettlementInProcess))))
        val status = FakeStatus(NetworkResult.Error(NetworkError.Network(IllegalStateException("offline"))))

        PaymentHistoryFeed(history, status)
            .rows(setOf(ConsentType.DomesticSinglePayment), limit = 5, scope = this@runTest)
            .first()
        advanceUntilIdle()

        assertEquals(listOf("1"), status.asked)
        assertEquals(emptyList(), history.recorded)
    }
}
