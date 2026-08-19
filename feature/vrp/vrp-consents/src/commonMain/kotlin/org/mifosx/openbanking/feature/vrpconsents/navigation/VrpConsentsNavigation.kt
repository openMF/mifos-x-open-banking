/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.vrpconsents.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import org.mifosx.openbanking.feature.vrpconsents.consentDetail.navigateToVrpConsentDetail
import org.mifosx.openbanking.feature.vrpconsents.consentDetail.vrpConsentDetailScreen
import org.mifosx.openbanking.feature.vrpconsents.consentList.vrpConsentListScreen

/**
 * Registers both of the module's screens, flat in the host's graph.
 *
 * @param onNavigateToSetup Opens the setup flow, which lives in another module.
 * @param onNavigateToPayment Opens the payment flow for one consent, which lives in another module.
 */
fun NavGraphBuilder.vrpConsentsDestination(
    navController: NavController,
    onBack: () -> Unit,
    onNavigateToSetup: () -> Unit,
    onNavigateToPayment: (String) -> Unit,
) {
    vrpConsentListScreen(
        onBack = onBack,
        onOpenConsent = { consentId -> navController.navigateToVrpConsentDetail(consentId) },
        onNavigateToSetup = onNavigateToSetup,
    )

    vrpConsentDetailScreen(
        onBack = { navController.popBackStack() },
        onNavigateToPayment = onNavigateToPayment,
    )
}
