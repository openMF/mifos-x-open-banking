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

package org.mifosx.openbanking.feature.sendmoney

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.navigation
import kotlinx.serialization.Serializable
import template.core.base.ui.nav.composableWithStayTransitions

/** The Pay tab's graph route. */
@Serializable
data object SendMoneyDestination

/** The screen inside it. */
@Serializable
data object SendMoneyRoute

/** The full list of payments sent, reached from the form's "See all". */
@Serializable
data object SendMoneyHistoryRoute

/**
 * Registers the Pay tab.
 *
 * A graph rather than a flat route so the tab matches Home and Accounts, and so any screen pushed
 * on top of it correctly hides the bottom bar — the bar shows only while the tab's start
 * destination is current.
 */
fun NavGraphBuilder.sendMoneyGraph(
    onLaunchAuthorisation: (String) -> Unit,
    onNavigateToConsents: () -> Unit,
    onNavigateToPayment: (String) -> Unit,
    onNavigateToHistory: () -> Unit,
    onBack: () -> Unit,
) {
    navigation<SendMoneyDestination>(startDestination = SendMoneyRoute) {
        composableWithStayTransitions<SendMoneyRoute> {
            SendMoneyScreen(
                onLaunchAuthorisation = onLaunchAuthorisation,
                onNavigateToConsents = onNavigateToConsents,
                onNavigateToPayment = onNavigateToPayment,
                onNavigateToHistory = onNavigateToHistory,
            )
        }
        composableWithStayTransitions<SendMoneyHistoryRoute> {
            SendMoneyHistoryScreen(
                onBack = onBack,
                onNavigateToPayment = onNavigateToPayment,
            )
        }
    }
}
