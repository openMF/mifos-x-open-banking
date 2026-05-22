/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.mifosx.openbanking.core.network.di

import org.koin.dsl.module

/**
 * Network DI module. Currently empty after removal of the template's
 * Frankfurter + CoinGecko API clients (template residue, 2026-05-22).
 * Re-populate with `single<MyApi> { ... }` registrations as real APIs land.
 */
val NetworkModule = module {
}
