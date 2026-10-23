/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.data.banking.impl

import org.mifosx.openbanking.core.data.callback.PaymentAuthSession
import org.mifosx.openbanking.core.data.callback.impl.authorizeState
import org.mifosx.openbanking.core.model.banking.payment.ConsentType
import org.mifosx.openbanking.core.model.banking.payment.StagedConsent
import org.mifosx.openbanking.core.network.api.ConsentStart
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult

/** OBIE v4 status of a freshly staged consent: awaiting the PSU's authorisation. */
private const val AWAITING_AUTHORISATION = "AWAU"

/**
 * Records a consent the Worker just staged so its redirect can be matched on return, and describes it
 * to the caller. Shared by the single, scheduled and standing-order write paths.
 */
internal fun PaymentAuthSession.recordStaged(
    started: NetworkResult<ConsentStart, NetworkError>,
    type: ConsentType,
): NetworkResult<StagedConsent, NetworkError> {
    val start = when (started) {
        is NetworkResult.Success -> started.data
        is NetworkResult.Error -> return started
    }
    val state = start.authorizeState()
        ?: return NetworkResult.Error(NetworkError.Client.BadRequest("authorizeUrl carries no state"))
    savePending(consentId = start.consentId, state = state, flowId = start.flowId, type = type)
    return NetworkResult.Success(
        StagedConsent(
            consentId = start.consentId,
            status = AWAITING_AUTHORISATION,
            authorizationUrl = start.authorizeUrl,
            state = state,
            flowId = start.flowId,
        ),
    )
}
