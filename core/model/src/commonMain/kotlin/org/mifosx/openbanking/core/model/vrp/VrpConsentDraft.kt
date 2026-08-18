/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.model.vrp

import kotlinx.serialization.Serializable

/**
 * A consent the customer has configured but the bank has not yet been asked to create.
 *
 * @property payee The account to be paid.
 * @property controlParameters The limits to request.
 * @property idempotencyKey Identifies this staging attempt. Replayed on retry so a second attempt
 *   returns the first one's consent instead of creating another.
 * @property payer The account to be debited. Null when the customer chose to pick one at the bank.
 * @property payerAccountId The app's own account identifier for [payer]. Not sent to the bank; used
 *   to record which account a refusal referred to.
 * @property validity When the authority should start and stop. Null for no dates.
 * @property reference Payment reference to carry on every payment made under the consent.
 */
@Serializable
data class VrpConsentDraft(
    val payee: AccountIdentity,
    val controlParameters: VrpControlParameters,
    val idempotencyKey: String,
    val payer: AccountIdentity? = null,
    val payerAccountId: String? = null,
    val validity: ValidityWindow? = null,
    val reference: String? = null,
)
