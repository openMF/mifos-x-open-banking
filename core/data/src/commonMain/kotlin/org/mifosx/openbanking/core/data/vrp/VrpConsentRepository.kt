/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.data.vrp

import kotlinx.coroutines.flow.Flow
import org.mifosx.openbanking.core.model.vrp.VrpConsent
import org.mifosx.openbanking.core.model.vrp.VrpConsentDraft
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult

/** Creates, reads and ends standing authorities. */
interface VrpConsentRepository {

    /** Consents the app has not revoked, newest first. Read from storage. */
    fun observeActive(): Flow<List<VrpConsent>>

    /** One consent, revoked or not. Read from storage. */
    fun observeById(consentId: String): Flow<VrpConsent?>

    /**
     * Asks the bank to create the consent in [draft].
     *
     * Nothing is stored: the customer has not approved it yet, so it cannot be paid under.
     */
    suspend fun stageConsent(draft: VrpConsentDraft): NetworkResult<VrpConsent, NetworkError>

    /**
     * Reads [consentId] back from the bank.
     *
     * Stores it once it is authorised, and keeps an already-stored consent current after that.
     */
    suspend fun refreshStatus(consentId: String): NetworkResult<VrpConsent, NetworkError>

    /**
     * Ends [consentId] at the bank and discards its credential.
     *
     * The credential is discarded and the consent marked revoked whether or not the bank call
     * succeeds, because the bank does not invalidate the credential either way.
     */
    suspend fun revoke(consentId: String): NetworkResult<Unit, NetworkError>
}
