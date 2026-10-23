/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.data.banking.store

import org.mifosx.openbanking.core.data.banking.mapper.toConsentSummary
import org.mifosx.openbanking.core.data.util.toThrowable
import org.mifosx.openbanking.core.model.banking.ConsentSummary
import org.mifosx.openbanking.core.network.api.Aisp
import org.mobilenativefoundation.store.store5.Fetcher
import org.mobilenativefoundation.store.store5.Store
import template.core.base.network.NetworkResult
import template.core.base.store.infra.StoreFactory

/**
 * Builds the Store5 store backing the consent screens.
 *
 * Kept apart from [BankingStores] because consent management runs on a different credential at the
 * Worker: account reads use the PSU token, while consent reads use the Worker's client-credentials
 * token (and only for this session's own consent).
 *
 * The store is in-memory. Consent status is precisely the thing that must not be served stale — a
 * consent revoked from the bank's own app has to read as revoked on the next load.
 */
object ConsentStores {

    /** One consent, keyed by `ConsentId`. Backs both the list (current consent) and detail screens. */
    fun consentDetailStore(aisp: Aisp): Store<String, ConsentSummary> =
        StoreFactory.createMemoryStore(
            fetcher = Fetcher.of { consentId -> fetchConsent(aisp, consentId) },
        )

    private suspend fun fetchConsent(aisp: Aisp, consentId: String): ConsentSummary =
        when (val result = aisp.getConsent(consentId)) {
            is NetworkResult.Success -> result.data.toConsentSummary()
            is NetworkResult.Error -> throw result.error.toThrowable()
        }
}
