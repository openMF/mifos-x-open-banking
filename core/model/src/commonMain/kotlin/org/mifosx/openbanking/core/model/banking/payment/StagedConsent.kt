/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.model.banking.payment

import kotlinx.serialization.Serializable

/**
 * A payment consent the bank has accepted but the PSU has not yet authorised.
 *
 * Nothing has moved at this point. [authorizationUrl] is where the PSU goes to approve it (signed by
 * the Worker); [state] is what the returning redirect must echo, and [flowId] is the Worker's pending
 * flow the redirect is redeemed against.
 *
 * @property status `AWAU` — awaiting authorisation; the bank reports `AUTH` once granted.
 */
@Serializable
data class StagedConsent(
    val consentId: String,
    val status: String,
    val authorizationUrl: String,
    val state: String,
    val flowId: String,
)
