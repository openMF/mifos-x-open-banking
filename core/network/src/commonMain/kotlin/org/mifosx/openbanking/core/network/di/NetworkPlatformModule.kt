/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.network.di

import org.koin.core.module.Module

/**
 * Binds the platform's [org.mifosx.openbanking.core.network.attest.AppAttestation]: Play Integrity on
 * Android, App Attest on iOS, none on desktop and web. Nothing else is platform-specific — the app
 * holds no certificate, key or bank credential on any platform.
 */
expect val networkPlatformModule: Module
