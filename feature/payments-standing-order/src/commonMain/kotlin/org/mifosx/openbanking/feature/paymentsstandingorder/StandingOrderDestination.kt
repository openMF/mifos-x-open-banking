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

package org.mifosx.openbanking.feature.paymentsstandingorder

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.navigation
import kotlinx.serialization.Serializable
import template.core.base.ui.nav.composableWithStayTransitions

/** The feature's graph route. */
@Serializable
data object StandingOrderDestination

/**
 * The one screen inside it.
 *
 * One route for the whole journey: the form and the review are steps on a single content state, and
 * the date picker is a dialog. Making either of them a destination of its own would rebuild it empty
 * on the way back, and both have to survive being returned to — the form because nothing typed may
 * be lost, the review because its staged draft must not be minted twice.
 */
@Serializable
data object StandingOrderRoute

/**
 * Registers the scheduled-payment flow.
 *
 * A sibling of the payments hub rather than a child of it, matching send-money: each payment type
 * owns its own browser hand-off and return, and nesting them would tie those lifecycles together.
 *
 * A nested graph rather than a flat route so that a screen pushed on top of it — the payment status
 * screen, on the way back — correctly hides the bottom bar, which is derived from the graph's start
 * destination.
 */
fun NavGraphBuilder.standingOrderGraph(
    onLaunchAuthorisation: (String) -> Unit,
    onNavigateToConsents: () -> Unit,
) {
    navigation<StandingOrderDestination>(startDestination = StandingOrderRoute) {
        composableWithStayTransitions<StandingOrderRoute> {
            StandingOrderScreen(
                onLaunchAuthorisation = onLaunchAuthorisation,
                onNavigateToConsents = onNavigateToConsents,
            )
        }
    }
}
