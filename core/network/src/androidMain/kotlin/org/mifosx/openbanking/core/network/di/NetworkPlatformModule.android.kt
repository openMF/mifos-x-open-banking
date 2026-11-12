/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.network.di

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import org.mifosx.openbanking.core.network.attest.AppAttestation
import org.mifosx.openbanking.core.network.attest.PlayIntegrityAttestation
import org.mifosx.openbanking.core.network.config.GatewayConfig

actual val networkPlatformModule: Module = module {
    single<AppAttestation> {
        PlayIntegrityAttestation(androidContext(), GatewayConfig.PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER)
    }
}
