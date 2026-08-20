/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.ui.payment

/**
 * One payment as a history row shows it, with every value already formatted.
 *
 * @property id Identifies the row to its caller and keys its test tag.
 * @property amountLabel The amount with its currency mark, e.g. `£45.00`.
 * @property statusLabel The status word, e.g. `Sent`. Not shown on a failed row.
 * @property dateLabel When the payment was made, e.g. `14 Aug 2026`.
 * @property failed Whether the bank refused the payment.
 */
data class MifosPaymentRowUi(
    val id: String,
    val amountLabel: String,
    val statusLabel: String,
    val dateLabel: String,
    val failed: Boolean = false,
)
