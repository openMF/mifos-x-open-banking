/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.sendmoney

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.mifosx.openbanking.core.ui.payment.MifosPaymentHistoryPage
import org.mifosx.openbanking.core.ui.payment.PaymentHistoryEntry
import org.mifosx.openbanking.core.ui.payment.toRowUi
import org.mifosx.openbanking.core.ui.scaffold.KptScaffold
import org.mifosx.openbanking.feature.sendmoney.generated.resources.Res
import org.mifosx.openbanking.feature.sendmoney.generated.resources.feature_send_money_history_empty
import org.mifosx.openbanking.feature.sendmoney.generated.resources.feature_send_money_history_screen_title
import org.mifosx.openbanking.feature.sendmoney.ui.SendMoneyHistoryViewModel

/** Every payment sent from this feature, with the newest first. */
@Composable
internal fun SendMoneyHistoryScreen(
    onBack: () -> Unit,
    onNavigateToPayment: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SendMoneyHistoryViewModel = koinViewModel(),
) {
    val payments by viewModel.payments.collectAsStateWithLifecycle()

    KptScaffold(
        onNavigationIconClick = onBack,
        title = stringResource(Res.string.feature_send_money_history_screen_title),
        modifier = modifier,
    ) {
        SendMoneyHistoryScreenContent(
            payments = payments,
            onNavigateToPayment = onNavigateToPayment,
        )
    }
}

/** The stateless half the UI suite drives directly. */
@Composable
internal fun SendMoneyHistoryScreenContent(
    payments: List<PaymentHistoryEntry>,
    onNavigateToPayment: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    MifosPaymentHistoryPage(
        payments = payments.map { it.toRowUi() },
        emptyLabel = stringResource(Res.string.feature_send_money_history_empty),
        modifier = modifier,
        onPaymentClick = onNavigateToPayment,
        testTag = SendMoneyTestTags.HISTORY_SCREEN,
        rowTestTag = SendMoneyTestTags::historyRow,
    )
}
