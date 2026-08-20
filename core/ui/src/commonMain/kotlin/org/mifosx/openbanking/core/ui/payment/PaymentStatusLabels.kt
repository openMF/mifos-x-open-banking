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
import org.jetbrains.compose.resources.stringResource
import org.mifosx.openbanking.core.model.banking.payment.ConsentType
import org.mifosx.openbanking.core.model.banking.payment.PaymentDisposition
import org.mifosx.openbanking.core.model.banking.payment.PaymentStatus
import org.mifosx.openbanking.core.model.banking.payment.dispositionFor
import org.mifosx.openbanking.core.ui.generated.resources.Res
import org.mifosx.openbanking.core.ui.generated.resources.core_ui_payment_status_not_sent
import org.mifosx.openbanking.core.ui.generated.resources.core_ui_payment_status_scheduled
import org.mifosx.openbanking.core.ui.generated.resources.core_ui_payment_status_scheduling
import org.mifosx.openbanking.core.ui.generated.resources.core_ui_payment_status_sending
import org.mifosx.openbanking.core.ui.generated.resources.core_ui_payment_status_sent
import org.mifosx.openbanking.core.ui.generated.resources.core_ui_payment_status_set_up
import org.mifosx.openbanking.core.ui.generated.resources.core_ui_payment_status_setting_up

/**
 * The status word for one payment, e.g. `Sent` or `Set up`.
 *
 * Read from the disposition rather than the status code, so the several spellings a bank uses for
 * one outcome all render alike, and from [consentType] as well, so a settled mandate reads as set up
 * rather than as money that has moved.
 */
@Composable
fun paymentStatusLabel(status: PaymentStatus, consentType: ConsentType): String =
    stringResource(
        when (status.dispositionFor(consentType)) {
            PaymentDisposition.TerminalFailure -> Res.string.core_ui_payment_status_not_sent
            PaymentDisposition.TerminalSuccess -> consentType.settledWord()
            PaymentDisposition.InProgress -> consentType.inFlightWord()
        },
    )

private fun ConsentType.settledWord() = when (this) {
    ConsentType.DomesticSinglePayment,
    ConsentType.InternationalSinglePayment,
    -> Res.string.core_ui_payment_status_sent

    ConsentType.DomesticScheduledPayment,
    ConsentType.InternationalScheduledPayment,
    -> Res.string.core_ui_payment_status_scheduled

    ConsentType.DomesticStandingOrder,
    ConsentType.InternationalStandingOrder,
    -> Res.string.core_ui_payment_status_set_up
}

private fun ConsentType.inFlightWord() = when (this) {
    ConsentType.DomesticSinglePayment,
    ConsentType.InternationalSinglePayment,
    -> Res.string.core_ui_payment_status_sending

    ConsentType.DomesticScheduledPayment,
    ConsentType.InternationalScheduledPayment,
    -> Res.string.core_ui_payment_status_scheduling

    ConsentType.DomesticStandingOrder,
    ConsentType.InternationalStandingOrder,
    -> Res.string.core_ui_payment_status_setting_up
}
