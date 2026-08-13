/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.network.model.pisp.domesticStandingOrder.response

import kotlinx.serialization.Serializable

/**
 * The mandatory-but-empty risk block.
 *
 * `Risk` is `1..1` on the consent, yet every property of `OBRisk1` is optional, so `{}` is
 * schema-valid and is exactly what HSBC's own collection sends for this product. The bank echoes it
 * back empty whatever is supplied.
 *
 * An `object` rather than an empty class so it has structural equality: the submitted `Risk` must
 * match the staged one, and a round-trip test that cannot compare them proves nothing.
 */
@Serializable
data object Risk
