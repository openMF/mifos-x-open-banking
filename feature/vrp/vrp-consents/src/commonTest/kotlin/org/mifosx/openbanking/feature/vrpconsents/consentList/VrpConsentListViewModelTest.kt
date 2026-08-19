/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.vrpconsents.consentList

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.mifosx.openbanking.core.model.callback.ConsentStatus
import org.mifosx.openbanking.core.model.vrp.AccountIdentity
import org.mifosx.openbanking.core.model.vrp.Money
import org.mifosx.openbanking.core.model.vrp.PeriodType
import org.mifosx.openbanking.core.model.vrp.PeriodicLimit
import org.mifosx.openbanking.core.model.vrp.ValidityWindow
import org.mifosx.openbanking.core.model.vrp.VrpConsent
import org.mifosx.openbanking.core.model.vrp.VrpControlParameters
import org.mifosx.openbanking.feature.vrpconsents.FakeVrpConsentRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

class VrpConsentListViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var consents: FakeVrpConsentRepository

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        consents = FakeVrpConsentRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = VrpConsentListViewModel(consents)

    private fun content(vm: VrpConsentListViewModel) =
        assertIs<VrpConsentListUiState.Content>(vm.stateFlow.value.uiState)

    @Test
    fun theListStartsLoading() {
        val vm = viewModel()

        assertEquals(VrpConsentListUiState.Loading, vm.stateFlow.value.uiState)
    }

    @Test
    fun noStoredConsentsRendersEmptyRatherThanAnError() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(VrpConsentListUiState.Empty, vm.stateFlow.value.uiState)
    }

    @Test
    fun eachStoredConsentBecomesARow() = runTest {
        consents.emitActive(listOf(consent(), consent(consentId = "45412", payeeName = "Oakwood")))
        val vm = viewModel()
        advanceUntilIdle()

        assertContentEquals(
            listOf("45411" to "Sarah Chen", "45412" to "Oakwood"),
            content(vm).consents.map { it.consentId to it.payeeName },
        )
    }

    /**
     * The headline is the largest periodic ceiling, which is the figure that describes the authority.
     * The per-payment ceiling is the larger number here and must not be the one shown.
     */
    @Test
    fun theHeadlineIsTheLargestPeriodicCeiling() = runTest {
        consents.emitActive(
            listOf(
                consent(
                    perPaymentMinor = 900_00L,
                    periodicLimits = listOf(
                        PeriodicLimit(PeriodType.Week, Money(150_00L, "GBP")),
                        PeriodicLimit(PeriodType.Month, Money(500_00L, "GBP")),
                    ),
                ),
            ),
        )
        val vm = viewModel()
        advanceUntilIdle()

        val row = content(vm).consents.single()
        assertEquals("£500", row.ceilingAmount)
        assertEquals(PeriodType.Month, row.periodType)
    }

    /** The list drops the pence a whole ceiling would otherwise carry; the detail screen keeps them. */
    @Test
    fun aCeilingWithPenceKeepsThem() = runTest {
        consents.emitActive(
            listOf(
                consent(periodicLimits = listOf(PeriodicLimit(PeriodType.Month, Money(499_50L, "GBP")))),
            ),
        )
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals("£499.50", content(vm).consents.single().ceilingAmount)
    }

    @Test
    fun aConsentWithNoEndDateCarriesNoValidUntil() = runTest {
        consents.emitActive(listOf(consent(validTo = null)))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(null, content(vm).consents.single().validUntil)
    }

    @Test
    fun anEndDateIsRenderedForReading() = runTest {
        consents.emitActive(listOf(consent()))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals("18 Mar 2027", content(vm).consents.single().validUntil)
    }

    @Test
    fun aRevokedConsentIsMarkedAsSuch() = runTest {
        consents.emitActive(listOf(consent(revokedAt = Instant.parse("2026-08-19T12:00:00Z"))))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(true, content(vm).consents.single().isRevoked)
    }

    @Test
    fun aStorageFailureRendersTheErrorState() = runTest {
        consents.storageFails = true
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(
            VrpConsentListUiState.Error(VrpConsentListErrorKind.StorageUnavailable),
            vm.stateFlow.value.uiState,
        )
    }

    @Test
    fun retryReadsStorageAgainAndRecovers() = runTest {
        consents.storageFails = true
        val vm = viewModel()
        advanceUntilIdle()

        consents.storageFails = false
        consents.emitActive(listOf(consent()))
        vm.trySendAction(VrpConsentListAction.RetryLoad)
        advanceUntilIdle()

        assertEquals(listOf("45411"), content(vm).consents.map { it.consentId })
    }

    /** Storage is the only source, so a consent removed there leaves the list without a reload. */
    @Test
    fun aConsentLeavingStorageLeavesTheList() = runTest {
        consents.emitActive(listOf(consent()))
        val vm = viewModel()
        advanceUntilIdle()

        consents.emitActive(emptyList())
        advanceUntilIdle()

        assertEquals(VrpConsentListUiState.Empty, vm.stateFlow.value.uiState)
    }

    private fun consent(
        consentId: String = "45411",
        payeeName: String = "Sarah Chen",
        perPaymentMinor: Long = 200_00L,
        periodicLimits: List<PeriodicLimit> =
            listOf(PeriodicLimit(PeriodType.Month, Money(500_00L, "GBP"))),
        validTo: LocalDate? = LocalDate(2027, 3, 18),
        revokedAt: Instant? = null,
    ) = VrpConsent(
        consentId = consentId,
        status = ConsentStatus.Authorised,
        createdAt = Instant.parse("2026-08-19T12:00:00Z"),
        controlParameters = VrpControlParameters(
            maximumIndividualAmount = Money(perPaymentMinor, "GBP"),
            periodicLimits = periodicLimits,
            interactionType = "UK.OBIE.VRPType.Sweeping",
        ),
        payee = AccountIdentity(
            schemeName = "UK.OBIE.SortCodeAccountNumber",
            identification = "40478412345678",
            name = payeeName,
        ),
        validity = validTo?.let { ValidityWindow(validFrom = null, validTo = it) },
        revokedAt = revokedAt,
    )
}
