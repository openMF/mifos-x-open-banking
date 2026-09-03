/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.datastore

import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import template.core.base.common.manager.DispatcherManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val LICENCE = "Mozilla Public License Version 2.0"

/**
 * Covers the licence text on [UserPreferencesRepositoryImpl]: it is persisted, read back, and
 * re-emitted to collectors so the Store5 source of truth sees its own write.
 *
 * Test names are camelCase: this source set also compiles for Kotlin/Native, whose frontend
 * rejects punctuation inside backticked names.
 */
class UserPreferencesLicenceTest {

    private fun repository(
        plainSettings: Settings = MapSettings(),
    ): UserPreferencesRepositoryImpl = UserPreferencesRepositoryImpl(
        plainSettings = plainSettings,
        secureSettings = MapSettings(),
        dispatcher = TestDispatcherManager(),
    )

    @Test
    fun theLicenceIsNullBeforeOneHasBeenStored() = runTest {
        assertNull(repository().openSourceLicenceText.first())
    }

    @Test
    fun aStoredLicenceIsEmittedToCollectors() = runTest {
        val repository = repository()

        repository.setOpenSourceLicenceText(LICENCE)

        assertEquals(LICENCE, repository.openSourceLicenceText.first())
    }

    @Test
    fun theLastStoredLicenceWins() = runTest {
        val repository = repository()

        repository.setOpenSourceLicenceText("first")
        repository.setOpenSourceLicenceText(LICENCE)

        assertEquals(LICENCE, repository.openSourceLicenceText.first())
    }

    @Test
    fun aStoredLicenceSurvivesANewInstance() = runTest {
        val plainSettings = MapSettings()
        repository(plainSettings).setOpenSourceLicenceText(LICENCE)

        assertEquals(LICENCE, repository(plainSettings).openSourceLicenceText.first())
    }
}

/** [DispatcherManager] running everything on the test dispatcher. */
private class TestDispatcherManager : DispatcherManager {
    private val dispatcher = UnconfinedTestDispatcher()

    override val default: CoroutineDispatcher = dispatcher
    override val main: MainCoroutineDispatcher = Dispatchers.Main
    override val io: CoroutineDispatcher = dispatcher
    override val unconfined: CoroutineDispatcher = dispatcher
    override val appScope: CoroutineScope = CoroutineScope(dispatcher)
}
