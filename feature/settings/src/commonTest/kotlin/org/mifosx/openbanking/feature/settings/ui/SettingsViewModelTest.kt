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
import org.mifosx.openbanking.core.model.user.DarkThemeConfig
import org.mifosx.openbanking.feature.settings.FakeUserDataRepository
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers [SettingsViewModel]'s preference reads and theme writes.
 *
 * Test names are camelCase: this source set also compiles for Kotlin/Native, whose frontend
 * rejects punctuation inside backticked names.
 */
class SettingsViewModelTest {

    private fun viewModel(
        repository: FakeUserDataRepository = FakeUserDataRepository(),
    ): SettingsViewModel {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        return SettingsViewModel(userDataRepository = repository)
    }

    private fun state(viewModel: SettingsViewModel): SettingsState = viewModel.stateFlow.value

    @Test
    fun contentReflectsTheStoredTheme() {
        val repository = FakeUserDataRepository(initialTheme = DarkThemeConfig.DARK)
        assertEquals(DarkThemeConfig.DARK, state(viewModel(repository)).themeConfig)
    }

    @Test
    fun selectingFollowSystemWritesIt() = runTest {
        val repository = FakeUserDataRepository(initialTheme = DarkThemeConfig.DARK)
        val vm = viewModel(repository)

        vm.trySendAction(SettingsAction.SelectTheme(DarkThemeConfig.FOLLOW_SYSTEM))

        assertEquals(listOf(DarkThemeConfig.FOLLOW_SYSTEM), repository.writtenThemes)
    }

    @Test
    fun selectingLightWritesIt() = runTest {
        val repository = FakeUserDataRepository()
        val vm = viewModel(repository)

        vm.trySendAction(SettingsAction.SelectTheme(DarkThemeConfig.LIGHT))

        assertEquals(listOf(DarkThemeConfig.LIGHT), repository.writtenThemes)
    }

    @Test
    fun selectingDarkWritesIt() = runTest {
        val repository = FakeUserDataRepository()
        val vm = viewModel(repository)

        vm.trySendAction(SettingsAction.SelectTheme(DarkThemeConfig.DARK))

        assertEquals(listOf(DarkThemeConfig.DARK), repository.writtenThemes)
    }

    @Test
    fun selectingTheAlreadySelectedThemeStillWritesExactlyOnce() = runTest {
        val repository = FakeUserDataRepository(initialTheme = DarkThemeConfig.LIGHT)
        val vm = viewModel(repository)

        vm.trySendAction(SettingsAction.SelectTheme(DarkThemeConfig.LIGHT))

        assertEquals(listOf(DarkThemeConfig.LIGHT), repository.writtenThemes)
    }

    @Test
    fun aWriteIsReflectedBackThroughTheObservedFlow() = runTest {
        val repository = FakeUserDataRepository(initialTheme = DarkThemeConfig.FOLLOW_SYSTEM)
        val vm = viewModel(repository)

        vm.trySendAction(SettingsAction.SelectTheme(DarkThemeConfig.DARK))

        assertEquals(DarkThemeConfig.DARK, state(vm).themeConfig)
    }

    @Test
    fun anExternalWriteIsReflectedBackThroughTheObservedFlow() = runTest {
        val repository = FakeUserDataRepository()
        val vm = viewModel(repository)

        repository.emitTheme(DarkThemeConfig.LIGHT)

        assertEquals(DarkThemeConfig.LIGHT, state(vm).themeConfig)
    }

    @Test
    fun threeSuccessiveWritesArriveInOrder() = runTest {
        val repository = FakeUserDataRepository()
        val vm = viewModel(repository)

        vm.trySendAction(SettingsAction.SelectTheme(DarkThemeConfig.DARK))
        vm.trySendAction(SettingsAction.SelectTheme(DarkThemeConfig.LIGHT))
        vm.trySendAction(SettingsAction.SelectTheme(DarkThemeConfig.FOLLOW_SYSTEM))

        assertEquals(
            listOf(DarkThemeConfig.DARK, DarkThemeConfig.LIGHT, DarkThemeConfig.FOLLOW_SYSTEM),
            repository.writtenThemes,
        )
    }

    @Test
    fun nothingIsWrittenBeforeAnyActionIsDispatched() = runTest {
        val repository = FakeUserDataRepository(initialTheme = DarkThemeConfig.DARK)
        viewModel(repository)

        assertEquals(emptyList(), repository.writtenThemes)
    }

    @Test
    fun everyDarkThemeConfigEntryIsOfferedByThePicker() {
        assertEquals(3, DarkThemeConfig.entries.size)
    }
}
