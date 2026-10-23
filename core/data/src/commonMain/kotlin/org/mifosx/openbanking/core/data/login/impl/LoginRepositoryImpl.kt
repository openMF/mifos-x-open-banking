/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.data.login.impl

import org.mifosx.openbanking.core.data.callback.impl.authorizeState
import org.mifosx.openbanking.core.data.login.ConsentResult
import org.mifosx.openbanking.core.data.login.LoginRepository
import org.mifosx.openbanking.core.model.hsbcPermission.OBPermission
import org.mifosx.openbanking.core.network.api.AisConsentRequest
import org.mifosx.openbanking.core.network.api.Session
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult

/**
 * Starts the sign-in through the Worker: `POST /v1/consents/ais` creates the consent and returns the
 * authorize URL the Worker signed. The consent window is the Worker's default (+90 days, 90 days of
 * history). Nothing is signed on the device.
 */
class LoginRepositoryImpl(
    private val session: Session,
) : LoginRepository {

    override fun getPermissions(): List<OBPermission> = OBPermission.ALL

    override suspend fun createConsentAndBuildAuthorizationUrl(): NetworkResult<ConsentResult, NetworkError> {
        val request = AisConsentRequest(permissions = OBPermission.ALL.map { it.id.obieScope })
        val start = when (val result = session.startAisConsent(request)) {
            is NetworkResult.Success -> result.data
            is NetworkResult.Error -> return result
        }
        val state = start.authorizeState()
            ?: return NetworkResult.Error(NetworkError.Client.BadRequest("authorizeUrl carries no state"))
        return NetworkResult.Success(
            ConsentResult(
                authorizationUrl = start.authorizeUrl,
                state = state,
                flowId = start.flowId,
                consentId = start.consentId,
            ),
        )
    }
}
