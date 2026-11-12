/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.login.di

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import org.mifosx.openbanking.feature.login.browser.AndroidBrowserLauncher
import org.mifosx.openbanking.feature.login.browser.BrowserLauncher

actual val loginPlatformModule = module {
    // createdAtStart: the launcher must observe the first Activity resume to open Custom Tabs
    // inside the app task (see AndroidBrowserLauncher).
    single<BrowserLauncher>(createdAtStart = true) { AndroidBrowserLauncher(androidContext()) }
}
