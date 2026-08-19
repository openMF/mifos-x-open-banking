/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.vrpcallback.callback

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import kotlinx.serialization.Serializable
import template.core.base.ui.nav.composableWithStayTransitions

/**
 * The return from the bank.
 *
 * Registered in the root navigator, not the navbar host: the redirect arrives before any tab exists
 * and the navbar hosts its own graph the root cannot navigate into.
 *
 * @property redirectUrl The property name is the `SavedStateHandle` key, so it must equal the
 *   constant the view model reads or the callback arrives empty.
 */
@Serializable
data class VrpCallbackRoute(val redirectUrl: String)

/** Registers the return leg. */
fun NavGraphBuilder.vrpCallbackScreen(
    onCompleted: (String) -> Unit,
    onAbandoned: () -> Unit,
) {
    composableWithStayTransitions<VrpCallbackRoute> {
        VrpCallbackScreen(
            onCompleted = onCompleted,
            onAbandoned = onAbandoned,
        )
    }
}

/** Navigates to the return leg with the redirect the bank sent back. */
fun NavController.navigateToVrpCallback(
    redirectUrl: String,
    navOptions: NavOptions? = null,
) {
    navigate(VrpCallbackRoute(redirectUrl), navOptions)
}
