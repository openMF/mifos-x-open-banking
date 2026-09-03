/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.settings.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.mifosx.openbanking.feature.settings.FakeOpenSourceLicenceRepo
import org.mifosx.openbanking.feature.settings.LicencesFixtures
import template.core.base.common.screen.DataFreshness
import template.core.base.common.screen.ScreenState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers [LicencesViewModel]'s mapping from the licence stream to screen state.
 *
 * Test names are camelCase: this source set also compiles for Kotlin/Native, whose frontend
 * rejects punctuation inside backticked names.
 */
class LicencesViewModelTest {

    private fun viewModel(repository: FakeOpenSourceLicenceRepo): LicencesViewModel {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        return LicencesViewModel(repository = repository)
    }

    private fun content(licence: String): ScreenState<String> = ScreenState.Content(
        data = licence,
        freshness = DataFreshness.FRESH,
    )

    @Test
    fun theInitialStateIsLoading() = runTest {
        val vm = viewModel(FakeOpenSourceLicenceRepo())

        assertEquals(LicencesState.DialogState.Loading, vm.stateFlow.value.dialogState)
    }

    @Test
    fun contentCarriesTheLicenceAndClearsTheDialog() = runTest {
        val repository = FakeOpenSourceLicenceRepo()
        val vm = viewModel(repository)

        repository.emit(content(LicencesFixtures.LICENCE_TEXT))

        assertEquals(LicencesFixtures.LICENCE_TEXT, vm.stateFlow.value.licence)
        assertNull(vm.stateFlow.value.dialogState)
    }

    @Test
    fun aFailureCarriesItsMessage() = runTest {
        val repository = FakeOpenSourceLicenceRepo()
        val vm = viewModel(repository)

        repository.emit(ScreenState.Error(error = RuntimeException("licence unavailable")))

        val error = assertIs<LicencesState.DialogState.Error>(vm.stateFlow.value.dialogState)
        assertEquals("licence unavailable", error.message)
        assertEquals(false, error.isNetworkError)
    }

    @Test
    fun beingOfflineIsReportedAsANetworkError() = runTest {
        val repository = FakeOpenSourceLicenceRepo()
        val vm = viewModel(repository)

        repository.emit(ScreenState.NoNetwork())

        val error = assertIs<LicencesState.DialogState.Error>(vm.stateFlow.value.dialogState)
        assertTrue(error.isNetworkError)
        assertNull(error.message)
    }

    @Test
    fun aBlankLicenceIsReportedAsAnError() = runTest {
        val repository = FakeOpenSourceLicenceRepo()
        val vm = viewModel(repository)

        repository.emit(content(""))

        assertIs<LicencesState.DialogState.Error>(vm.stateFlow.value.dialogState)
    }

    @Test
    fun retryLoadAsksTheStreamAgain() = runTest {
        val repository = FakeOpenSourceLicenceRepo()
        val vm = viewModel(repository)

        vm.trySendAction(LicencesAction.RetryLoad)

        assertEquals(1, repository.retryCount)
    }

    @Test
    fun aRetryAfterAFailureRecoversToContent() = runTest {
        val repository = FakeOpenSourceLicenceRepo()
        val vm = viewModel(repository)
        repository.emit(ScreenState.Error(error = RuntimeException("licence unavailable")))

        vm.trySendAction(LicencesAction.RetryLoad)
        repository.emit(content(LicencesFixtures.LICENCE_TEXT))

        assertEquals(LicencesFixtures.LICENCE_TEXT, vm.stateFlow.value.licence)
        assertNull(vm.stateFlow.value.dialogState)
    }
}
