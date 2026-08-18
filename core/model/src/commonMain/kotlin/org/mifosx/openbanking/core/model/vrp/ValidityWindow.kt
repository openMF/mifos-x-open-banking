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

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * How long a consent's authority lasts.
 *
 * Dates rather than instants: the bank discards the time component and replaces the offset with
 * `+00:00` without converting the date, so a time carried here would be a value the bank does not
 * hold.
 *
 * @property validFrom First day the authority is valid. Null when the bank was sent no start date,
 *   in which case it starts on the day the consent was created.
 * @property validTo Last day the authority is valid. Null means no end date — it runs until
 *   revoked. When set, the bank requires it to be at least two calendar days ahead.
 */
@Serializable
data class ValidityWindow(
    val validFrom: LocalDate? = null,
    val validTo: LocalDate? = null,
)
