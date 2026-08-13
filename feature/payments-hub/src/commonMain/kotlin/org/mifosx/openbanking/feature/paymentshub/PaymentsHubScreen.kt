/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.paymentshub

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.mifosx.openbanking.core.ui.scaffold.KptScaffold
import org.mifosx.openbanking.core.ui.scaffold.rememberKptPullToRefreshState
import org.mifosx.openbanking.feature.paymentshub.ui.PaymentsHubAction
import org.mifosx.openbanking.feature.paymentshub.ui.PaymentsHubUiState
import org.mifosx.openbanking.feature.paymentshub.ui.PaymentsHubViewModel

@Composable
internal fun PaymentsHubScreen(
    onNavigateToSendMoney: () -> Unit,
    onNavigateToPaymentStatus: (String) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToSchedulePayment: () -> Unit = {},
    onNavigateToStandingOrder: () -> Unit = {},
    viewModel: PaymentsHubViewModel = koinViewModel(),
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    /**
     * Reconcile in-progress payments every time this screen is shown.
     *
     * The list renders from Room, and a row is written at submission with whatever status the bank
     * returned then — `ACSP` for a domestic payment, because settlement is asynchronous. Nothing else
     * ever updates it: `refreshStatuses()` existed and worked, but no caller reached it, so a payment
     * that had long since settled to `ACCC` sat on "In progress" forever while the detail screen —
     * which reads the bank live — showed it completed.
     *
     * Keyed on [Unit] rather than done in the ViewModel's `init` because the ViewModel outlives this
     * screen: it is scoped to the back stack entry, so returning from a payment's detail would not
     * re-run `init`. Navigation does dispose the composable, so this effect runs again on return —
     * which is exactly when a status is most likely to have moved.
     */
    LaunchedEffect(Unit) {
        viewModel.trySendAction(PaymentsHubAction.RefreshActivity)
    }

    KptScaffold(
        showNavigationIcon = false,
        title = "Payments",
        pullToRefreshState = rememberKptPullToRefreshState(
            isEnabled = true,
            isRefreshing = (state.uiState as? PaymentsHubUiState.Content)?.isRefreshing == true,
            onRefresh = { viewModel.trySendAction(PaymentsHubAction.RefreshActivity) },
        ),
        modifier = modifier,
    ) {
        PaymentsHubContent(
            state = state,
            onAction = viewModel::trySendAction,
            onNavigateToSendMoney = onNavigateToSendMoney,
            onNavigateToSchedulePayment = onNavigateToSchedulePayment,
            onNavigateToStandingOrder = onNavigateToStandingOrder,
            onNavigateToPaymentStatus = onNavigateToPaymentStatus,
        )
    }
}
