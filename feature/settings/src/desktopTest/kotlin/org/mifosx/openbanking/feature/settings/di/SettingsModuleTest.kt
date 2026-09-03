/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.settings.di

import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.mifosx.openbanking.core.data.openSourceLicence.OpenSourceLicenceRepo
import org.mifosx.openbanking.core.data.user.UserDataRepository
import org.mifosx.openbanking.feature.settings.FakeOpenSourceLicenceRepo
import org.mifosx.openbanking.feature.settings.FakeUserDataRepository
import org.mifosx.openbanking.feature.settings.ui.LicencesViewModel
import org.mifosx.openbanking.feature.settings.ui.SettingsViewModel
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * The view models this module declares can actually be built.
 *
 * It resolves each definition rather than calling `Module.verify()`, which walks constructor
 * parameters reflectively and treats a defaulted one as optional — the case that fails at runtime.
 */
class SettingsModuleTest {

    private val stubs = module {
        single<UserDataRepository> { FakeUserDataRepository() }
        single<OpenSourceLicenceRepo> { FakeOpenSourceLicenceRepo() }
    }

    @Test
    fun theSettingsViewModelResolvesFromTheGraph() {
        val koin = koinApplication { modules(stubs, SettingsModule) }.koin

        assertNotNull(koin.get<SettingsViewModel>())
    }

    @Test
    fun theLicencesViewModelResolvesFromTheGraph() {
        val koin = koinApplication { modules(stubs, SettingsModule) }.koin

        assertNotNull(koin.get<LicencesViewModel>())
    }
}
