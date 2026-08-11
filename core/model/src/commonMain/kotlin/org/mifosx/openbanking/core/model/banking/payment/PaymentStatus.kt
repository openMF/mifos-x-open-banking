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
 * What a payment status means to someone waiting on their money.
 *
 * The three-way split is the point. A success/failure pair would force the most common answer —
 * accepted but not yet settled — into one bucket or the other, and both readings are wrong: the
 * bank has taken the instruction, and the money has not arrived.
 */
@Serializable
enum class PaymentDisposition {
    InProgress,
    TerminalSuccess,
    TerminalFailure,
}

/**
 * OBIE payment status, resolved from the wire value.
 *
 * HSBC returns the four-letter ISO 20022 codes on some responses and the long OBIE names on others,
 * so [fromWire] accepts both. An unrecognised value resolves to [Unknown], which is treated as still
 * in progress: refusing to guess is safer than reporting a settlement that may not have happened.
 */
@Serializable
enum class PaymentStatus(val disposition: PaymentDisposition) {
    Received(PaymentDisposition.InProgress),
    Pending(PaymentDisposition.InProgress),
    AcceptedSettlementInProcess(PaymentDisposition.InProgress),
    AcceptedTechnicalValidation(PaymentDisposition.InProgress),

    /**
     * The instruction is set up and waiting for its execution date.
     *
     * Both scheduled rails return this immediately after the payment resource is created, and it is
     * the status a scheduled payment then holds for up to 365 days. Without it here the wire value
     * `INCO` fell to [Unknown] — which is also `InProgress`, so nothing looked broken, but the hub's
     * refresh re-read every scheduled payment on every visit for a status that cannot move until the
     * date arrives.
     */
    InitiationCompleted(PaymentDisposition.InProgress),
    AcceptedSettlementCompleted(PaymentDisposition.TerminalSuccess),
    AcceptedCreditSettlementCompleted(PaymentDisposition.TerminalSuccess),
    AcceptedWithoutPosting(PaymentDisposition.TerminalSuccess),
    Rejected(PaymentDisposition.TerminalFailure),
    Unknown(PaymentDisposition.InProgress),
    ;

    companion object {
        fun fromWire(raw: String?): PaymentStatus = when (raw?.trim()?.uppercase()) {
            "RCVD", "RECEIVED" -> Received
            "PDNG", "PENDING" -> Pending
            "ACSP", "ACCEPTEDSETTLEMENTINPROCESS" -> AcceptedSettlementInProcess
            "ACTC", "ACCEPTEDTECHNICALVALIDATION" -> AcceptedTechnicalValidation
            "INCO", "INITIATIONCOMPLETED" -> InitiationCompleted
            "ACSC", "ACCEPTEDSETTLEMENTCOMPLETED" -> AcceptedSettlementCompleted
            "ACCC", "ACCEPTEDCREDITSETTLEMENTCOMPLETED" -> AcceptedCreditSettlementCompleted
            "ACWP", "ACCEPTEDWITHOUTPOSTING" -> AcceptedWithoutPosting
            "RJCT", "REJECTED" -> Rejected
            else -> Unknown
        }
    }
}
