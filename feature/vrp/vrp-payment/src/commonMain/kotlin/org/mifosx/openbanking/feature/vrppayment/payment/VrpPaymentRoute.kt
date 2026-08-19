/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.vrppayment.payment

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import kotlinx.serialization.Serializable
import template.core.base.ui.nav.composableWithStayTransitions

/**
 * One payment under a VRP.
 *
 * @property consentId The property name is the `SavedStateHandle` key, so it must equal the constant
 *   the view model reads or the argument arrives empty.
 */
@Serializable
data class VrpPaymentRoute(val consentId: String)

/** Registers the payment flow, flat in the host's graph. */
fun NavGraphBuilder.vrpPaymentScreen(onBack: () -> Unit) {
    composableWithStayTransitions<VrpPaymentRoute> {
        VrpPaymentScreen(onBack = onBack)
    }
}

/** Navigates to paying under [consentId]. */
fun NavController.navigateToVrpPayment(
    consentId: String,
    navOptions: NavOptions? = null,
) {
    navigate(VrpPaymentRoute(consentId), navOptions)
}
