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

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import kotlinx.serialization.Serializable
import template.core.base.ui.nav.composableWithStayTransitions

/**
 * One standing payment, entered by tapping a row on the list.
 *
 * @property consentId The property name is the `SavedStateHandle` key, so it must equal the constant
 *   the view model reads or the argument arrives empty.
 */
@Serializable
data class VrpConsentDetailRoute(val consentId: String)

/** Registers one standing payment, flat in the host's graph. */
fun NavGraphBuilder.vrpConsentDetailScreen(
    onBack: () -> Unit,
    onNavigateToPayment: (String) -> Unit,
) {
    composableWithStayTransitions<VrpConsentDetailRoute> {
        VrpConsentDetailScreen(
            onBack = onBack,
            onNavigateToPayment = onNavigateToPayment,
        )
    }
}

/** Navigates to one standing payment. */
fun NavController.navigateToVrpConsentDetail(
    consentId: String,
    navOptions: NavOptions? = null,
) {
    navigate(VrpConsentDetailRoute(consentId), navOptions)
}
