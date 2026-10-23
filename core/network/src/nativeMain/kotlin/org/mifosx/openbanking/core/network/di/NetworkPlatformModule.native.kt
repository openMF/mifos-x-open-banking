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
import org.koin.dsl.module
import org.mifosx.openbanking.core.network.attest.AppAttestAttestation
import org.mifosx.openbanking.core.network.attest.AppAttestation

actual val networkPlatformModule: Module = module {
    single<AppAttestation> { AppAttestAttestation() }
}
