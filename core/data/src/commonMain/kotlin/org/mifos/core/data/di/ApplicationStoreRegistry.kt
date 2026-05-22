/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.mifos.core.data.di

import template.core.base.store.infra.StoreRegistry

/**
 * Application-wide Store5 registry.
 *
 * Currently empty after removal of the template's Frankfurter + CoinGecko
 * stores (template residue, 2026-05-22). Re-populate with `val MyStore = store("myStore")`
 * declarations + a `Ttl` companion as real Store5-backed features land.
 */
object ApplicationStoreRegistry : StoreRegistry()
