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
): NetworkResult<StagedConsent, NetworkError> = when (started) {
    is NetworkResult.Error -> started
    is NetworkResult.Success -> {
        val state = started.data.authorizeState()
            ?: return NetworkResult.Error(NetworkError.Client.BadRequest("authorizeUrl carries no state"))
        savePending(consentId = started.data.consentId, state = state, flowId = started.data.flowId, type = type)
        NetworkResult.Success(
            StagedConsent(
                consentId = started.data.consentId,
                status = AWAITING_AUTHORISATION,
                authorizationUrl = started.data.authorizeUrl,
                state = state,
                flowId = started.data.flowId,
            ),
        )
    }
}
