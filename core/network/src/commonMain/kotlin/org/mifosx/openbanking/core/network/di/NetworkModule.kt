/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.network.di

import com.russhwolf.settings.Settings
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.mifosx.openbanking.core.network.api.Aisp
import org.mifosx.openbanking.core.network.api.OpenSourceLicenceAPI
import org.mifosx.openbanking.core.network.api.Pisp
import org.mifosx.openbanking.core.network.api.Session
import org.mifosx.openbanking.core.network.api.Vrp
import org.mifosx.openbanking.core.network.gatewayHttpClient

/**
 * The networking graph: one plain HTTPS client to the bank gateway (Cloudflare Worker) and the API facades over
 * it. No certificate, signing key or bank identifier is bound anywhere — the Worker holds them.
 * [networkPlatformModule] contributes only the platform's attestation provider.
 */
val NetworkModule = module {
    includes(networkPlatformModule)

    single { gatewayHttpClient(settings = get<Settings>(named("secure")), attestation = get()) }

    single { Session(get()) }

    single { Aisp(get()) }

    single { Pisp(get()) }

    single { Vrp(get()) }

    single { OpenSourceLicenceAPI(get()) }
}
