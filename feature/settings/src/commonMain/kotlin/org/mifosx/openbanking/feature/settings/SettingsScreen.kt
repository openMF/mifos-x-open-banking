/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.mifosx.openbanking.core.ui.scaffold.KptScaffold
import org.mifosx.openbanking.feature.settings.generated.resources.Res
import org.mifosx.openbanking.feature.settings.generated.resources.feature_settings_screen_title
import org.mifosx.openbanking.feature.settings.ui.SettingsViewModel

/**
 * The settings hub: appearance, account and about rows.
 *
 * @param onBack Returns to the screen that opened settings.
 * @param onNavigateToConsents Opens the consent list.
 * @param onNavigateToLicences Opens the open-source licences screen.
 * @param onOpenUrl Hands a URL to the platform browser.
 */
@Composable
internal fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToConsents: () -> Unit,
    onNavigateToLicences: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    KptScaffold(
        showNavigationIcon = true,
        onNavigationIconClick = onBack,
        title = stringResource(Res.string.feature_settings_screen_title),
        modifier = modifier,
    ) {
        SettingsScreenContent(
            state = state,
            onAction = viewModel::trySendAction,
            onNavigateToConsents = onNavigateToConsents,
            onNavigateToLicences = onNavigateToLicences,
            onOpenUrl = onOpenUrl,
        )
    }
}
