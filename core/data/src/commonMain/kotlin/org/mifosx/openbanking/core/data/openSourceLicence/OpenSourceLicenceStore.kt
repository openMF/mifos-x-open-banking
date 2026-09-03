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

import org.mifosx.openbanking.core.data.util.toThrowable
import org.mifosx.openbanking.core.datastore.UserPreferencesRepository
import org.mifosx.openbanking.core.network.api.OpenSourceLicenceAPI
import org.mobilenativefoundation.store.store5.Fetcher
import org.mobilenativefoundation.store.store5.SourceOfTruth
import org.mobilenativefoundation.store.store5.Store
import template.core.base.network.NetworkResult
import template.core.base.store.infra.DefaultValidator
import template.core.base.store.infra.StoreFactory
import kotlin.time.Duration.Companion.hours

/** Store for the app's open-source licence text. */
object OpenSourceLicenceStore {

    /** The store's single key — the licence is not per-account or per-anything. */
    const val OSS_LICENCE_KEY = "OpenSourceLicenceStore"

    /** Time a cached licence is served before the network is consulted again. */
    private val LICENCE_TTL = 24.hours

    /** Licence text fetched from the project repository, persisted in user preferences. */
    fun licenceStore(
        openSourceLicenceAPI: OpenSourceLicenceAPI,
        userPreferencesRepository: UserPreferencesRepository,
    ): Store<String, String> {
        val validator = DefaultValidator.withTtl<String>(LICENCE_TTL)
        return StoreFactory.createStore(
            fetcher = Fetcher.of { _ ->
                when (val networkResult = openSourceLicenceAPI.fetchLicence()) {
                    is NetworkResult.Error -> throw networkResult.error.toThrowable()
                    is NetworkResult.Success -> {
                        validator.markFresh()
                        networkResult.data
                    }
                }
            },
            sourceOfTruth = SourceOfTruth.of(
                reader = { _ -> userPreferencesRepository.openSourceLicenceText },
                writer = { _, licence -> userPreferencesRepository.setOpenSourceLicenceText(licence) },
            ),
            validator = validator,
        )
    }
}
