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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.mifosx.openbanking.core.designsystem.theme.DesignToken
import org.mifosx.openbanking.core.ui.components.MifosErrorComponent
import org.mifosx.openbanking.core.ui.components.MifosProgressIndicator
import org.mifosx.openbanking.core.ui.scaffold.KptScaffold
import org.mifosx.openbanking.feature.settings.generated.resources.Res
import org.mifosx.openbanking.feature.settings.generated.resources.feature_settings_licences_error
import org.mifosx.openbanking.feature.settings.generated.resources.feature_settings_licences_intro
import org.mifosx.openbanking.feature.settings.generated.resources.feature_settings_licences_screen_title
import org.mifosx.openbanking.feature.settings.ui.LicencesAction
import org.mifosx.openbanking.feature.settings.ui.LicencesState
import org.mifosx.openbanking.feature.settings.ui.LicencesViewModel
import template.core.base.designsystem.theme.KptTheme

/** The app's own open-source licence, read from the project repository. */
@Composable
internal fun LicencesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LicencesViewModel = koinViewModel(),
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    LicencesScreenContent(
        state = state,
        onAction = viewModel::trySendAction,
        onBack = onBack,
        modifier = modifier,
    )
}

/** The licences body, free of the view model so the Compose suites can drive every state. */
@Composable
internal fun LicencesScreenContent(
    state: LicencesState,
    onAction: (LicencesAction) -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    KptScaffold(
        showNavigationIcon = true,
        onNavigationIconClick = onBack,
        title = stringResource(Res.string.feature_settings_licences_screen_title),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(KptTheme.spacing.md)
                .testTag(SettingsTestTags.LICENCES_SCREEN),
            verticalArrangement = Arrangement.spacedBy(KptTheme.spacing.md),
        ) {
            Text(
                text = stringResource(Res.string.feature_settings_licences_intro),
                style = KptTheme.typography.titleMedium,
                color = KptTheme.colorScheme.onSurface,
            )
            LicenceBody(state = state, onAction = onAction)
        }
    }
}

/** The licence text, or the state of reading it, filling the space below the intro. */
@Composable
private fun ColumnScope.LicenceBody(
    state: LicencesState,
    onAction: (LicencesAction) -> Unit,
) {
    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        when (val dialogState = state.dialogState) {
            null -> LicenceText(licence = state.licence)

            LicencesState.DialogState.Loading -> MifosProgressIndicator()

            is LicencesState.DialogState.Error -> MifosErrorComponent(
                isNetworkConnected = !dialogState.isNetworkError,
                message = dialogState.message
                    ?: stringResource(Res.string.feature_settings_licences_error),
                isRetryEnabled = true,
                onRetry = { onAction(LicencesAction.RetryLoad) },
            )
        }
    }
}

/** The licence text in a bordered box that scrolls on its own, leaving the screen fixed. */
@Composable
private fun LicenceText(licence: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxSize(),
        shape = KptTheme.shapes.medium,
        color = KptTheme.colorScheme.surface,
        border = BorderStroke(DesignToken.strokes.hairline, KptTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = licence,
            style = KptTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = KptTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(KptTheme.spacing.md)
                .testTag(SettingsTestTags.LICENCES_LIST),
        )
    }
}
