/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.network.api

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import org.mifosx.openbanking.core.network.result.toNetworkResult
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult

/** The project's `LICENSE` file on the `dev` branch, served as plain text. */
private const val OPEN_SOURCE_LICENCE_URL =
    "https://raw.githubusercontent.com/openMF/mifos-x-open-banking/refs/heads/dev/LICENSE"

/** Reads the app's own open-source licence text from the project repository. */
class OpenSourceLicenceAPI(
    private val httpClient: HttpClient,
) {
    /** The licence text, or the network error that prevented reading it. */
    suspend fun fetchLicence(): NetworkResult<String, NetworkError> =
        httpClient.get(OPEN_SOURCE_LICENCE_URL).toNetworkResult()
}
