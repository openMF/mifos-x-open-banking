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

import androidx.compose.runtime.Composable
import org.mifosx.openbanking.core.common.formatIsoDate
import org.mifosx.openbanking.core.common.formatMinorUnits
import org.mifosx.openbanking.core.model.banking.payment.ConsentType
import org.mifosx.openbanking.core.model.banking.payment.PaymentDisposition
import org.mifosx.openbanking.core.model.banking.payment.PaymentHistoryRow
import org.mifosx.openbanking.core.model.banking.payment.PaymentStatus
import org.mifosx.openbanking.core.model.banking.payment.dispositionFor

/**
 * A history row as a payment feature holds it in state, before its status has a word.
 *
 * The amount and date are formatted by the ViewModel; the status word is a string resource and so
 * can only be resolved while composing — [toRowUi] does that.
 *
 * @property paymentId The bank's id for the payment.
 * @property amountLabel The amount with its currency mark, e.g. `£45.00`.
 * @property dateLabel When the payment was made, e.g. `14 Aug 2026`.
 * @property status The last status the app knows of.
 * @property consentType The product and rail the payment was made on.
 */
data class PaymentHistoryEntry(
    val paymentId: String,
    val amountLabel: String,
    val dateLabel: String,
    val status: PaymentStatus,
    val consentType: ConsentType,
) {

    /** Whether the status will not move again, so no refresh is worth making. */
    val settled: Boolean
        get() = status.dispositionFor(consentType) != PaymentDisposition.InProgress
}

/** A stored payment as the history list holds it, with its amount and date formatted. */
fun PaymentHistoryRow.toHistoryEntry(): PaymentHistoryEntry = PaymentHistoryEntry(
    paymentId = paymentId,
    amountLabel = formatMinorUnits(amountMinorUnits, currency),
    dateLabel = formatIsoDate(submittedAt),
    status = status,
    consentType = consentType,
)

/** Resolves the status word, giving the row everything it needs to render. */
@Composable
fun PaymentHistoryEntry.toRowUi(): MifosPaymentRowUi = MifosPaymentRowUi(
    id = paymentId,
    amountLabel = amountLabel,
    statusLabel = paymentStatusLabel(status, consentType),
    dateLabel = dateLabel,
    failed = status.dispositionFor(consentType) == PaymentDisposition.TerminalFailure,
)
