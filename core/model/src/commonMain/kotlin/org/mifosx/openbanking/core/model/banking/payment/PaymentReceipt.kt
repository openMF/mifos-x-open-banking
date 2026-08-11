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
 * One charge the bank applied to a payment.
 *
 * @property typeLabel The OBIE charge type as the bank named it, e.g. `UK.OBIE.CHAPSOut`. It does
 *   not necessarily match the rail the Initiation declared.
 * @property amountLabel The charge formatted for display, e.g. `£0.05`.
 */
@Serializable
data class PaymentCharge(
    val bearer: String,
    val typeLabel: String,
    val amountLabel: String,
)

/**
 * A submitted payment as the bank now reports it.
 *
 * @property domesticPaymentId The bank's id for the payment, and the key its status is read back
 *   under. Empty string when the payload omitted it.
 * @property consentId The consent this payment was executed against.
 * @property creationDateTime When the payment was made. This — not [statusUpdateDateTime] — is what
 *   "submitted" means; the two are separate facts even where a bank returns them equal.
 * @property statusUpdateDateTime When [status] last moved. HSBC returns this unchanged from
 *   [creationDateTime] even on a settled payment, so it is reported, not relied upon.
 * @property settlementDateTime When the funds are expected to settle, or empty when the bank did not
 *   say. **Always empty on the scheduled rails**: they do return an `ExpectedSettlementDateTime`, but
 *   the sandbox sets it equal to `CreationDateTime` — today — rather than to the requested date, so
 *   mapping it would report a payment due next week as settling now. Read
 *   [requestedExecutionDateTime] instead for those.
 * @property requestedExecutionDateTime The date a scheduled payment is due, echoed from the
 *   `Initiation`. Empty on an immediate payment, which has no such date. This is the only date on a
 *   scheduled payment the bank states truthfully.
 * @property amountLabel The instructed amount echoed by the bank, formatted for display.
 * @property creditorName Who was paid, echoed from the submitted `Initiation`.
 * @property reference The remittance reference, or empty when the payment carried none.
 * @property debtorIdentification The paying account as the bank echoes it, unformatted.
 * @property charges What the bank charged. Empty when it charged nothing — which is different from
 *   charging zero, and different again from not having told us yet, so this is never defaulted to a
 *   displayed "£0.00".
 */
@Serializable
data class PaymentReceipt(
    val domesticPaymentId: String,
    val consentId: String,
    val status: PaymentStatus,
    val creationDateTime: String,
    val statusUpdateDateTime: String,
    val amountLabel: String,
    val creditorName: String,
    val settlementDateTime: String = "",
    val requestedExecutionDateTime: String = "",
    val reference: String = "",
    val debtorIdentification: String = "",
    val charges: List<PaymentCharge> = emptyList(),
)
