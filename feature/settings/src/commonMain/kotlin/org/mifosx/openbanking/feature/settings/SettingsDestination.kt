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

package org.mifosx.openbanking.feature.settings

import androidx.navigation.NavGraphBuilder
import kotlinx.serialization.Serializable
import template.core.base.ui.nav.composableWithStayTransitions

/** The settings route. A tab root reached from the bottom bar; it carries no argument. */
@Serializable
data object SettingsRoute

/**
 * Registers the settings screen in the host graph.
 *
 * @param onBack Returns to the screen that opened settings.
 * @param onNavigateToConsents Opens the consent list.
 * @param onNavigateToLicences Opens the open-source licences screen.
 * @param onOpenUrl Hands the privacy policy to a browser.
 */
fun NavGraphBuilder.settingsScreen(
    onBack: () -> Unit,
    onNavigateToConsents: () -> Unit,
    onNavigateToLicences: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    composableWithStayTransitions<SettingsRoute> {
        SettingsScreen(
            onBack = onBack,
            onNavigateToConsents = onNavigateToConsents,
            onNavigateToLicences = onNavigateToLicences,
            onOpenUrl = onOpenUrl,
        )
    }
}

/**
 * The open-source licences route. A single pushed screen from the About & Legal section; it
 * carries no argument.
 */
@Serializable
data object LicencesRoute

/** Registers the open-source licences screen in the host graph. */
fun NavGraphBuilder.licencesScreen(onBack: () -> Unit) {
    composableWithStayTransitions<LicencesRoute> {
        LicencesScreen(onBack = onBack)
    }
}
