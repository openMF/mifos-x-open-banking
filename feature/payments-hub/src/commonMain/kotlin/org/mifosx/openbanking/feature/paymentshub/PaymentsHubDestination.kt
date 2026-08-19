/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
@file:Suppress("MatchingDeclarationName")

package org.mifosx.openbanking.feature.paymentshub

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.navigation
import kotlinx.serialization.Serializable
import template.core.base.ui.nav.composableWithStayTransitions

/** The Pay tab's graph route. */
@Serializable
data object PaymentsHubDestination

/** The hub screen inside it — the tab's start destination. */
@Serializable
data object PaymentsHubRoute

/**
 * Registers the Pay tab's hub graph.
 *
 * The hub itself is the tab landing; payment flows (send-money, payment-consent, payment-status)
 * are sibling destinations the hub navigates to, not children of this graph. That separation lets
 * each payment type own its own OAuth/browser lifecycle independently.
 */
fun NavGraphBuilder.paymentsHubGraph(
    onNavigateToSendMoney: () -> Unit,
    onNavigateToSchedulePayment: () -> Unit = {},
    onNavigateToStandingOrder: () -> Unit = {},
    onNavigateToVrp: () -> Unit = {},
) {
    navigation<PaymentsHubDestination>(startDestination = PaymentsHubRoute) {
        composableWithStayTransitions<PaymentsHubRoute> {
            PaymentsHubScreen(
                onNavigateToSendMoney = onNavigateToSendMoney,
                onNavigateToSchedulePayment = onNavigateToSchedulePayment,
                onNavigateToStandingOrder = onNavigateToStandingOrder,
                onNavigateToVrp = onNavigateToVrp,
            )
        }
    }
}
