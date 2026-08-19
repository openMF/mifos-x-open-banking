/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.vrpconsents.consentList

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import kotlinx.serialization.Serializable
import template.core.base.ui.nav.composableWithStayTransitions

/** The standing-payment list. The feature's landing surface, entered from the payments hub. */
@Serializable
data object VrpConsentListRoute

/** Registers the standing-payment list, flat in the host's graph. */
fun NavGraphBuilder.vrpConsentListScreen(
    onBack: () -> Unit,
    onOpenConsent: (String) -> Unit,
    onNavigateToSetup: () -> Unit,
) {
    composableWithStayTransitions<VrpConsentListRoute> {
        VrpConsentListScreen(
            onBack = onBack,
            onOpenConsent = onOpenConsent,
            onNavigateToSetup = onNavigateToSetup,
        )
    }
}

/** Navigates to the standing-payment list. */
fun NavController.navigateToVrpConsentList(navOptions: NavOptions? = null) {
    navigate(VrpConsentListRoute, navOptions)
}
