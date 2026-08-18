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
import org.mifosx.openbanking.core.model.banking.payment.PaymentStatus
import kotlin.time.Instant

/**
 * A fee the bank applied to a payment. Rendered as returned, never recalculated.
 *
 * @property bearer Which party pays the charge, e.g. `BorneByDebtor`.
 * @property type The charge's category, e.g. `UK.OBIE.CHAPSOut`.
 * @property amount What was charged.
 */
@Serializable
data class VrpCharge(
    val bearer: String,
    val type: String,
    val amount: Money,
)

/**
 * One payment attempted under a consent.
 *
 * A row exists for every attempt, including one that failed before the bank saw it — in which case
 * [paymentId] is null and the failure fields are set.
 *
 * @property localId The app's own identifier, present from the moment the attempt is recorded.
 * @property consentId The authority the payment was made under.
 * @property amount What the customer asked to pay.
 * @property status The bank's state for the payment, as of [syncedAt].
 * @property createdAt When the app recorded the attempt.
 * @property paymentId The bank's identifier. Null when the attempt never reached the bank.
 * @property submittedAt When the bank accepted the instruction. Null if it never did.
 * @property settledAt When the payment reached a terminal state. Null while still in progress.
 * @property charges Fees the bank applied. Empty when it applied none.
 * @property reference The payment reference sent with the instruction.
 * @property errorKind Classification of a failure, for choosing what to tell the customer.
 * @property errorDescription The bank's own message for a failure.
 * @property supportReference The bank's error identifier, quotable to support.
 * @property syncedAt When [status] was last read from the bank.
 */
@Serializable
data class VrpPayment(
    val localId: String,
    val consentId: String,
    val amount: Money,
    val status: PaymentStatus,
    val createdAt: Instant,
    val paymentId: String? = null,
    val submittedAt: Instant? = null,
    val settledAt: Instant? = null,
    val charges: List<VrpCharge> = emptyList(),
    val reference: String? = null,
    val errorKind: String? = null,
    val errorDescription: String? = null,
    val supportReference: String? = null,
    val syncedAt: Instant? = null,
)
