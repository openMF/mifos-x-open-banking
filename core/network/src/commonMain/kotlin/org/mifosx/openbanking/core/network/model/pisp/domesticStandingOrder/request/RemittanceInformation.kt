/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The reference the payee sees.
 *
 * Domestic only: the international rail refuses `RemittanceInformation` with `U005`. This is the one
 * place a customer-visible reference can go — `MandateRelatedInformation.Reference` is not a v4.0
 * field at all and is refused.
 */
@Serializable
data class RemittanceInformation(
    @SerialName("Unstructured")
    val unstructured: List<String>? = null,
)
