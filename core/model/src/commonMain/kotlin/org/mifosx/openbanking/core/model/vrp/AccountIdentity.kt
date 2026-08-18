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
 * How an account is identified on a VRP. Used for both the payer and the payee.
 *
 * @property schemeName `UK.OBIE.SortCodeAccountNumber` on every request. A read may also return
 *   `UK.OBIE.PAN`, which the declared enum does not contain.
 * @property identification 6-digit sort code followed by 8-digit account number, unseparated —
 *   14 digits, e.g. `80200110203348`. A `UK.OBIE.PAN` read returns 16 characters instead.
 * @property name Account holder name, 1–70 characters, e.g. `Mr Robert`. A payee's may carry no
 *   special characters.
 * @property secondaryIdentification Building-society roll number, 1–34 characters. Null when none.
 */
@Serializable
data class AccountIdentity(
    val schemeName: String,
    val identification: String,
    val name: String,
    val secondaryIdentification: String? = null,
)
