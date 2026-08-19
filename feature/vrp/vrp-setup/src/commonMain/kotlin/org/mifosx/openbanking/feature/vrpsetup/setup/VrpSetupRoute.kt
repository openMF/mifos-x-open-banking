/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.vrpsetup.setup

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import kotlinx.serialization.Serializable
import template.core.base.ui.nav.composableWithStayTransitions

/** Setting up a VRP: one destination carrying both the form and the review. */
@Serializable
data object VrpSetupRoute

/** Registers VRP setup, flat in the host's graph. */
fun NavGraphBuilder.vrpSetupScreen(
    onBack: () -> Unit,
    onLaunchAuthorisation: (String) -> Unit,
) {
    composableWithStayTransitions<VrpSetupRoute> {
        VrpSetupScreen(
            onBack = onBack,
            onLaunchAuthorisation = onLaunchAuthorisation,
        )
    }
}

/** Navigates to VRP setup. */
fun NavController.navigateToVrpSetup(navOptions: NavOptions? = null) {
    navigate(VrpSetupRoute, navOptions)
}
