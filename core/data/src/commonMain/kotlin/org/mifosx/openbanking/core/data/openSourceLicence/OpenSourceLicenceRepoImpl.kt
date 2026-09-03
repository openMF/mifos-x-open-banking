/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.data.openSourceLicence

import kotlinx.coroutines.CoroutineScope
import org.mifosx.openbanking.core.data.infra.NetworkMonitor
import org.mobilenativefoundation.store.store5.Store
import template.core.base.store.infra.FetchedAtRepository
import template.core.base.store.screen.ScreenDataStream
import template.core.base.store.screen.asScreenStream

/** [OpenSourceLicenceRepo] over the licence [Store]. */
class OpenSourceLicenceRepoImpl(
    private val store: Store<String, String>,
    private val networkMonitor: NetworkMonitor,
    private val fetchedAtRepository: FetchedAtRepository,
) : OpenSourceLicenceRepo {

    override fun getLicence(scope: CoroutineScope): ScreenDataStream<String> =
        store.asScreenStream(
            key = OpenSourceLicenceStore.OSS_LICENCE_KEY,
            networkMonitor = networkMonitor,
            fetchedAtRepository = fetchedAtRepository,
            cacheKey = CACHE_KEY,
            scope = scope,
        )

    private companion object {
        /** Key the last-fetched timestamp is persisted under. */
        const val CACHE_KEY = "oss_licence"
    }
}
