/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.vrpconsents.consentDetail

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.mifosx.openbanking.core.model.banking.payment.PaymentStatus
import org.mifosx.openbanking.core.model.callback.ConsentStatus
import org.mifosx.openbanking.core.model.vrp.AccountIdentity
import org.mifosx.openbanking.core.model.vrp.Money
import org.mifosx.openbanking.core.model.vrp.PeriodType
import org.mifosx.openbanking.core.model.vrp.PeriodUsage
import org.mifosx.openbanking.core.model.vrp.PeriodicLimit
import org.mifosx.openbanking.core.model.vrp.ValidityWindow
import org.mifosx.openbanking.core.model.vrp.VrpConsent
import org.mifosx.openbanking.core.model.vrp.VrpControlParameters
import org.mifosx.openbanking.core.model.vrp.VrpPayment
import org.mifosx.openbanking.feature.vrpconsents.FakeVrpConsentRepository
import org.mifosx.openbanking.feature.vrpconsents.FakeVrpPaymentRepository
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val CONSENT_ID = "45411"
private const val SORT_CODE_ACCOUNT_NUMBER = "UK.OBIE.SortCodeAccountNumber"

class VrpConsentDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var consents: FakeVrpConsentRepository
    private lateinit var payments: FakeVrpPaymentRepository

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        consents = FakeVrpConsentRepository(byId = consent())
        payments = FakeVrpPaymentRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = VrpConsentDetailViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(VrpConsentDetailViewModel.CONSENT_ID_ARG to CONSENT_ID),
        ),
        consents = consents,
        payments = payments,
    )

    private fun content(vm: VrpConsentDetailViewModel) =
        assertIs<VrpConsentDetailUiState.Content>(vm.stateFlow.value.uiState)

    // loading

    /** The stored status alone can be stale, so the bank is read on arrival. */
    @Test
    fun theBankIsReadOnceOnArrival() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        assertContentEquals(listOf(CONSENT_ID), consents.refreshedIds)
        assertEquals(CONSENT_ID, vm.stateFlow.value.consentId)
    }

    @Test
    fun aConsentWithNoStoredRecordIsNotFound() = runTest {
        consents.emit(null)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(VrpConsentDetailUiState.NotFound, vm.stateFlow.value.uiState)
    }

    @Test
    fun theLimitsAndUsageAreRenderedAgainstTheHeadlinePeriod() = runTest {
        payments.emitUsage(
            listOf(
                PeriodUsage(
                    limit = PeriodicLimit(PeriodType.Month, Money(500_00L, "GBP")),
                    consumed = Money(120_00L, "GBP"),
                    remaining = Money(380_00L, "GBP"),
                    periodStart = Instant.parse("2026-08-01T00:00:00Z"),
                ),
            ),
        )
        val vm = viewModel()
        advanceUntilIdle()

        val rendered = content(vm)
        assertEquals("£200.00", rendered.perPaymentCeilingAmount)
        assertEquals(listOf(PeriodType.Month to "£500.00"), rendered.limits.map { it.periodType to it.ceilingAmount })

        val usage = assertIs<PeriodicLimitUsageUi>(rendered.periodicLimitUsage)
        assertEquals("£120.00", usage.sentAmount)
        assertEquals("£380.00", usage.remainingAmount)
        assertEquals(0.24f, usage.sentAmountFraction)
    }

    /** Usage is measured against the headline ceiling; one for another period is not that. */
    @Test
    fun usageForAnotherPeriodIsNotRendered() = runTest {
        payments.emitUsage(
            listOf(
                PeriodUsage(
                    limit = PeriodicLimit(PeriodType.Week, Money(150_00L, "GBP")),
                    consumed = Money(10_00L, "GBP"),
                    remaining = Money(140_00L, "GBP"),
                    periodStart = Instant.parse("2026-08-17T00:00:00Z"),
                ),
            ),
        )
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(null, content(vm).periodicLimitUsage)
    }

    @Test
    fun everyRecordedPaymentBecomesARow() = runTest {
        payments.emitPayments(listOf(payment(), payment(localId = "pay-2", minorUnits = 30_00L)))
        val vm = viewModel()
        advanceUntilIdle()

        assertContentEquals(
            listOf("pay-1" to "£45.00", "pay-2" to "£30.00"),
            content(vm).payments.map { it.localId to it.sentAmount },
        )
    }

    /**
     * A submission reports an interim status and nothing else revisits it, so without this the row
     * reads as still sending for good and its amount never counts against the ceiling.
     */
    @Test
    fun aPaymentTheBankHasNotFinishedWithIsReadBack() = runTest {
        payments.emitPayments(listOf(payment(status = PaymentStatus.AcceptedSettlementInProcess)))
        viewModel()
        advanceUntilIdle()

        assertContentEquals(listOf("pay-1"), payments.refreshedPayments.map { it.localId })
    }

    @Test
    fun aSettledPaymentIsNotReadBackAgain() = runTest {
        payments.emitPayments(listOf(payment()))
        viewModel()
        advanceUntilIdle()

        assertContentEquals(emptyList(), payments.refreshedPayments)
    }

    /** The read writes to storage, which emits back in, so repeating on every emission never stops. */
    @Test
    fun anUnsettledPaymentIsReadBackOnlyOnce() = runTest {
        val inProgress = payment(status = PaymentStatus.AcceptedSettlementInProcess)
        payments.emitPayments(listOf(inProgress))
        viewModel()
        advanceUntilIdle()

        payments.emitPayments(listOf(inProgress))
        advanceUntilIdle()

        assertEquals(1, payments.refreshedPayments.size)
    }

    // what the screen allows

    @Test
    fun anAuthorisedConsentMayBePaidAndRemoved() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(content(vm).canPay)
        assertTrue(content(vm).canRevoke)
    }

    /** Nothing can be paid before the customer has approved it, but it may still be abandoned. */
    @Test
    fun anUnapprovedConsentMayBeRemovedButNotPaid() = runTest {
        consents.emit(consent(status = ConsentStatus.AwaitingAuthorisation))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(false, content(vm).canPay)
        assertTrue(content(vm).canRevoke)
    }

    /**
     * A payer chosen at the bank that is not a sort code and account number is a card, and every
     * payment under it fails permanently. The screen says so rather than offering to pay.
     */
    @Test
    fun aCardPayerMakesTheConsentUnusable() = runTest {
        consents.emit(
            consent(
                payer = AccountIdentity(
                    schemeName = "UK.OBIE.PAN",
                    identification = "4444333322221111",
                    name = "Credit Card",
                ),
            ),
        )
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(VrpConsentDetailUiState.Unusable, vm.stateFlow.value.uiState)
    }

    @Test
    fun aSortCodeAndAccountNumberPayerLeavesTheConsentUsable() = runTest {
        consents.emit(
            consent(
                payer = AccountIdentity(
                    schemeName = SORT_CODE_ACCOUNT_NUMBER,
                    identification = "80200110204021",
                    name = "Everyday Current Account",
                ),
            ),
        )
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals("Everyday Current Account", content(vm).payerName)
    }

    @Test
    fun aConsentTheBankHasEndedRendersItsHistoryOnly() = runTest {
        consents.emit(consent(status = ConsentStatus.Expired))
        payments.emitPayments(listOf(payment()))
        val vm = viewModel()
        advanceUntilIdle()

        val ended = assertIs<VrpConsentDetailUiState.Ended>(vm.stateFlow.value.uiState)
        assertEquals("Sarah Chen", ended.payeeName)
        assertEquals(listOf("pay-1"), ended.payments.map { it.localId })
        assertEquals(false, ended.bankRefusedRemoval)
    }

    // removal

    /** Two steps: the confirmation opens the gate, and only the second one calls the bank. */
    @Test
    fun askingToRemoveCallsNothing() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.trySendAction(VrpConsentDetailAction.RevokeRequested)
        advanceUntilIdle()

        assertEquals(RevokePhase.Confirming, content(vm).revoke)
        assertContentEquals(emptyList(), consents.revokedIds)
    }

    @Test
    fun dismissingTheConfirmationCallsNothing() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.trySendAction(VrpConsentDetailAction.RevokeRequested)
        advanceUntilIdle()

        vm.trySendAction(VrpConsentDetailAction.RevokeDismissed)
        advanceUntilIdle()

        assertEquals(RevokePhase.Idle, content(vm).revoke)
        assertContentEquals(emptyList(), consents.revokedIds)
    }

    @Test
    fun confirmingRemovalCallsTheBankAndLeavesTheScreen() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.trySendAction(VrpConsentDetailAction.RevokeRequested)
        advanceUntilIdle()

        vm.trySendAction(VrpConsentDetailAction.RevokeConfirmed)
        advanceUntilIdle()

        assertContentEquals(listOf(CONSENT_ID), consents.revokedIds)
        assertEquals(VrpConsentDetailEvent.Revoked, vm.eventFlow.first())
    }

    /** The authority is dropped locally either way, so a refusal must be said out loud. */
    @Test
    fun aRefusedRemovalSaysTheAuthorityMayStillBeLive() = runTest {
        consents.revokeReturns(NetworkResult.Error(NetworkError.Network(IllegalStateException("offline"))))
        val vm = viewModel()
        advanceUntilIdle()
        vm.trySendAction(VrpConsentDetailAction.RevokeConfirmed)
        advanceUntilIdle()

        consents.emit(consent(revokedAt = Instant.parse("2026-08-19T12:00:00Z")))
        advanceUntilIdle()

        val ended = assertIs<VrpConsentDetailUiState.Ended>(vm.stateFlow.value.uiState)
        assertTrue(ended.bankRefusedRemoval)
    }

    /** The refusal can arrive after the local removal has already rendered, so it stamps that state. */
    @Test
    fun aRefusalArrivingAfterTheEndedStateStillStampsIt() = runTest {
        consents.revokeReturns(NetworkResult.Error(NetworkError.Network(IllegalStateException("offline"))))
        val vm = viewModel()
        advanceUntilIdle()
        consents.emit(consent(revokedAt = Instant.parse("2026-08-19T12:00:00Z")))
        advanceUntilIdle()

        vm.trySendAction(VrpConsentDetailAction.RevokeConfirmed)
        advanceUntilIdle()

        val ended = assertIs<VrpConsentDetailUiState.Ended>(vm.stateFlow.value.uiState)
        assertTrue(ended.bankRefusedRemoval)
    }

    /** A storage emission mid-removal must not reopen the actions the removal locked. */
    @Test
    fun aStorageEmissionDuringRemovalKeepsTheActionsLocked() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.trySendAction(VrpConsentDetailAction.RevokeRequested)
        advanceUntilIdle()

        payments.emitPayments(listOf(payment()))
        advanceUntilIdle()

        assertEquals(RevokePhase.Confirming, content(vm).revoke)
    }

    private fun consent(
        status: ConsentStatus = ConsentStatus.Authorised,
        payer: AccountIdentity? = null,
        revokedAt: Instant? = null,
    ) = VrpConsent(
        consentId = CONSENT_ID,
        status = status,
        createdAt = Instant.parse("2026-08-01T12:00:00Z"),
        controlParameters = VrpControlParameters(
            maximumIndividualAmount = Money(200_00L, "GBP"),
            periodicLimits = listOf(PeriodicLimit(PeriodType.Month, Money(500_00L, "GBP"))),
            interactionType = "UK.OBIE.VRPType.Sweeping",
        ),
        payee = AccountIdentity(
            schemeName = SORT_CODE_ACCOUNT_NUMBER,
            identification = "40478412345678",
            name = "Sarah Chen",
        ),
        payer = payer,
        validity = ValidityWindow(validFrom = null, validTo = LocalDate(2027, 3, 18)),
        revokedAt = revokedAt,
        syncedAt = Instant.parse("2026-08-19T12:00:00Z"),
    )

    private fun payment(
        localId: String = "pay-1",
        minorUnits: Long = 45_00L,
        status: PaymentStatus = PaymentStatus.AcceptedCreditSettlementCompleted,
    ) = VrpPayment(
        localId = localId,
        consentId = CONSENT_ID,
        amount = Money(minorUnits, "GBP"),
        status = status,
        createdAt = Instant.parse("2026-08-14T09:00:00Z"),
    )
}
