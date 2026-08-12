/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.paymentshub.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.mifosx.openbanking.core.data.banking.PaymentHistoryRepository
import org.mifosx.openbanking.core.model.banking.payment.ConsentType
import org.mifosx.openbanking.core.model.banking.payment.PaymentDraft
import org.mifosx.openbanking.core.model.banking.payment.PaymentHistoryItem
import org.mifosx.openbanking.core.model.banking.payment.PaymentReceipt
import org.mifosx.openbanking.core.model.banking.payment.PaymentStageTimestamps
import org.mifosx.openbanking.core.model.banking.payment.ScheduledPaymentDraft
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FakePaymentHistoryRepository : PaymentHistoryRepository {
    private val items = MutableStateFlow(emptyList<PaymentHistoryItem>())
    private var delayedEmit: Boolean = true
    val savedSubmitted = mutableListOf<PaymentReceipt>()
    val savedFailed = mutableListOf<Pair<PaymentDraft, String>>()
    var refreshCallCount = 0

    fun emit(items: List<PaymentHistoryItem>) {
        delayedEmit = false
        this.items.value = items
    }

    override fun observeRecent(): Flow<List<PaymentHistoryItem>> = items

    override suspend fun saveSubmitted(receipt: PaymentReceipt, draft: PaymentDraft) {
        savedSubmitted += receipt
    }

    override suspend fun saveFailed(draft: PaymentDraft, errorKind: String, errorDescription: String) {
        savedFailed += draft to errorKind
    }

    // The scheduled overloads exist for the interface only. The hub reads history and never writes
    // it — a call landing here would mean a write path had drifted into the tab landing.
    override suspend fun saveSubmitted(receipt: PaymentReceipt, draft: ScheduledPaymentDraft) {
        savedSubmitted += receipt
    }

    override suspend fun saveFailed(
        draft: ScheduledPaymentDraft,
        errorKind: String,
        errorDescription: String,
    ) = Unit

    override suspend fun refreshStatuses() {
        refreshCallCount++
    }

    /** The hub reads rows straight from history, so it never has to ask which rail one came from. */
    override suspend fun consentTypeOf(paymentId: String): ConsentType? = null

    /** The hub lists payments; it never renders a timeline, so it records no stage times. */
    override suspend fun stageTimestampsOf(paymentId: String): PaymentStageTimestamps? = null
}

class PaymentsHubViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        history: FakePaymentHistoryRepository = FakePaymentHistoryRepository(),
    ) = PaymentsHubViewModel(history)

    @Test
    fun startsWithEmptyContentWhenHistoryIsEmpty() = runTest {
        val vm = viewModel()
        val content = assertIs<PaymentsHubUiState.Content>(vm.stateFlow.value.uiState)
        assertTrue(content.activityItems.isEmpty())
    }

    @Test
    fun transitionsToContentOnceHistoryEmits() = runTest {
        val history = FakePaymentHistoryRepository()
        val vm = viewModel(history = history)
        history.emit(emptyList())

        val content = assertIs<PaymentsHubUiState.Content>(vm.stateFlow.value.uiState)
        assertTrue(content.activityItems.isEmpty())
    }

    @Test
    fun showsActivityItemsFromHistory() = runTest {
        val history = FakePaymentHistoryRepository()
        val vm = viewModel(history = history)
        history.emit(
            listOf(
                PaymentHistoryItem(
                    id = "19901", domesticPaymentId = "19901",
                    debtorName = "CurrentAccount", creditorName = "Mr Dharani C",
                    creditorIdentification = "80200110203350",
                    amountMinorUnits = 10_000L, currency = "GBP",
                    creationDateTime = "2026-08-07T15:30:00Z",
                    isFailure = false, isInFlight = false,
                    statusLabel = "Sent", errorDescription = null,
                ),
            ),
        )

        val content = assertIs<PaymentsHubUiState.Content>(vm.stateFlow.value.uiState)
        assertEquals(1, content.activityItems.size)
        assertEquals("Mr Dharani C", content.activityItems.first().creditorName)
    }

    @Test
    fun refreshActivityCallsRepository() = runTest {
        val history = FakePaymentHistoryRepository()
        val vm = viewModel(history = history)
        history.emit(emptyList())

        advanceUntilIdle()
        vm.trySendAction(PaymentsHubAction.RefreshActivity)
        advanceUntilIdle()

        assertTrue(history.refreshCallCount > 0)
    }

    @Test
    fun retryLoadTriggersRefresh() = runTest {
        val history = FakePaymentHistoryRepository()
        val vm = viewModel(history = history)

        vm.trySendAction(PaymentsHubAction.RetryLoad)
        advanceUntilIdle()

        assertTrue(history.refreshCallCount > 0)
    }
}
