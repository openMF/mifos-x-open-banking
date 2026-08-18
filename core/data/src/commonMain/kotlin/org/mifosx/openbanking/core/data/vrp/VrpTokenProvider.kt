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

import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult

/** Supplies the access token a consent's calls are made with. */
interface VrpTokenProvider {

    /** A usable access token for [consentId], redeeming the refresh token first if needed. */
    suspend fun accessToken(consentId: String): NetworkResult<String, NetworkError>

    /** Redeems [consentId]'s refresh token and returns the new access token. */
    suspend fun refreshAccessToken(consentId: String): NetworkResult<String, NetworkError>

    /** Discards the held access token for [consentId], leaving the refresh token in place. */
    fun invalidateAccessToken(consentId: String)
}
