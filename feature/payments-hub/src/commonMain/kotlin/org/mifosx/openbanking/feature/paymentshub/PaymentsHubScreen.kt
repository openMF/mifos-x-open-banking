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
import androidx.compose.ui.Modifier
import org.mifosx.openbanking.core.ui.scaffold.KptScaffold

/**
 * A tab that renders four static cards, so it holds no state and needs no ViewModel.
 *
 * It had one, along with a pull-to-refresh that reconciled in-flight payment statuses on every
 * appearance. Both existed for the Recent list; with that gone there is nothing to load, nothing to
 * refresh, and nothing to fail — so there is no Loading state to leave and no Error state to retry.
 *
 * This is the exception to the feature template rather than a departure from it: every other screen
 * here reads a stream and therefore earns a ViewModel. Should this tab ever need one again, take the
 * shape from `feature/direct-debits` rather than restoring what was deleted.
 */
@Composable
internal fun PaymentsHubScreen(
    onNavigateToSendMoney: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToSchedulePayment: () -> Unit = {},
    onNavigateToStandingOrder: () -> Unit = {},
) {
    KptScaffold(
        showNavigationIcon = false,
        title = "Payments",
        modifier = modifier,
    ) {
        PaymentsHubContent(
            onNavigateToSendMoney = onNavigateToSendMoney,
            onNavigateToSchedulePayment = onNavigateToSchedulePayment,
            onNavigateToStandingOrder = onNavigateToStandingOrder,
        )
    }
}
