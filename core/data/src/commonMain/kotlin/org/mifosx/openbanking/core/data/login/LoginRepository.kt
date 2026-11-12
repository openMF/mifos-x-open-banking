/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.data.login

import org.mifosx.openbanking.core.model.hsbcPermission.OBPermission
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult

/**
 * A started sign-in: the Worker-signed [authorizationUrl] to open, its [state], and the Worker's
 * pending [flowId] that `/v1/auth/complete` redeems.
 */
data class ConsentResult(
    val authorizationUrl: String,
    val state: String,
    val flowId: String,
    val consentId: String,
)

interface LoginRepository {
    fun getPermissions(): List<OBPermission>

    suspend fun createConsentAndBuildAuthorizationUrl(): NetworkResult<ConsentResult, NetworkError>
}
