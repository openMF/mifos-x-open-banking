/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.vrpconsents.consentDetail

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import org.mifosx.openbanking.core.ui.payment.MifosPaymentHistoryList
import org.mifosx.openbanking.core.ui.payment.MifosPaymentRowUi
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.Res
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_detail_history_empty
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_detail_history_title
import org.mifosx.openbanking.feature.vrpconsents.paymentStatusLabel

/**
 * Every payment attempted under the consent, newest first.
 *
 * The rows do not open anything: VRP keeps no per-payment screen.
 */
@Composable
internal fun PaymentHistory(payments: List<PaymentRowUi>) {
    MifosPaymentHistoryList(
        payments = payments.map { it.toRow() },
        title = stringResource(Res.string.feature_vrp_consents_detail_history_title),
        emptyLabel = stringResource(Res.string.feature_vrp_consents_detail_history_empty),
        testTag = VrpConsentDetailTestTags.HISTORY,
        emptyTestTag = VrpConsentDetailTestTags.HISTORY_EMPTY,
        rowTestTag = VrpConsentDetailTestTags::paymentRow,
    )
}

@Composable
private fun PaymentRowUi.toRow(): MifosPaymentRowUi = MifosPaymentRowUi(
    id = localId,
    amountLabel = sentAmount,
    statusLabel = paymentStatusLabel(status),
    dateLabel = sentOn,
    failed = hasFailed,
)
