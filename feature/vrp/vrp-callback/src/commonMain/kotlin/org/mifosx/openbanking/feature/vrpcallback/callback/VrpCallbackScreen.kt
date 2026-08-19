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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.mifosx.openbanking.core.ui.components.MifosFilledPillButton
import org.mifosx.openbanking.core.ui.scaffold.KptScaffold
import org.mifosx.openbanking.feature.vrpcallback.generated.resources.Res
import org.mifosx.openbanking.feature.vrpcallback.generated.resources.feature_vrp_callback_dismiss
import org.mifosx.openbanking.feature.vrpcallback.generated.resources.feature_vrp_callback_done
import org.mifosx.openbanking.feature.vrpcallback.generated.resources.feature_vrp_callback_start_again
import org.mifosx.openbanking.feature.vrpcallback.generated.resources.feature_vrp_callback_title
import template.core.base.designsystem.component.KptTopAppBar
import template.core.base.designsystem.core.TopAppBarVariant
import template.core.base.designsystem.theme.KptTheme
import template.core.base.ui.effects.EventsEffect

@Composable
internal fun VrpCallbackScreen(
    onCompleted: (String) -> Unit,
    onAbandoned: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VrpCallbackViewModel = koinViewModel(),
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val onAction = remember(viewModel) {
        { action: VrpCallbackAction -> viewModel.trySendAction(action) }
    }

    EventsEffect(viewModel.eventFlow) { event ->
        when (event) {
            is VrpCallbackEvent.Completed -> onCompleted(event.consentId)
            VrpCallbackEvent.Abandoned -> onAbandoned()
        }
    }

    VrpCallbackScreenContent(
        state = state,
        onAction = onAction,
        modifier = modifier,
    )
}

@Composable
internal fun VrpCallbackScreenContent(
    state: VrpCallbackState,
    onAction: (VrpCallbackAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    KptScaffold(
        modifier = modifier,
        topBar = {
            KptTopAppBar(
                title = stringResource(Res.string.feature_vrp_callback_title),
                variant = TopAppBarVariant.CenterAligned,
            )
        },
        bottomBar = {
            val uiState = state.uiState
            if (uiState !is VrpCallbackUiState.Working) {
                VrpCallbackActions(
                    uiState = uiState,
                    onAction = onAction,
                )
            }
        },
    ) {
        when (val uiState = state.uiState) {
            is VrpCallbackUiState.Working -> VrpCallbackWorkingPage(uiState.stage)
            is VrpCallbackUiState.Success -> VrpCallbackSuccessPage(uiState.payeeName)
            is VrpCallbackUiState.Unusable -> VrpCallbackUnusablePage()

            is VrpCallbackUiState.Failed -> VrpCallbackFailedPage(
                kind = uiState.kind,
                supportReference = uiState.supportReference,
            )
        }
    }
}

/**
 * The pinned actions for a terminal state.
 *
 * A read-back failure offers only Close: the VRP is set up, and offering to start again would invite
 * a second one for the same intent.
 */
@Composable
private fun VrpCallbackActions(
    uiState: VrpCallbackUiState,
    onAction: (VrpCallbackAction) -> Unit,
) {
    val canStartAgain = when (uiState) {
        is VrpCallbackUiState.Unusable -> true
        is VrpCallbackUiState.Failed -> !uiState.kind.leavesAUsableConsent
        else -> false
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(KptTheme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (canStartAgain) {
            MifosFilledPillButton(
                label = stringResource(Res.string.feature_vrp_callback_start_again),
                onClick = { onAction(VrpCallbackAction.StartAgain) },
                testTag = VrpCallbackTestTags.START_AGAIN_BUTTON,
            )
            TextButton(
                onClick = { onAction(VrpCallbackAction.Dismiss) },
                modifier = Modifier.testTag(VrpCallbackTestTags.DISMISS_BUTTON),
            ) {
                Text(stringResource(Res.string.feature_vrp_callback_dismiss))
            }
        } else {
            MifosFilledPillButton(
                label = stringResource(Res.string.feature_vrp_callback_done),
                onClick = { onAction(VrpCallbackAction.Dismiss) },
                testTag = VrpCallbackTestTags.DISMISS_BUTTON,
            )
        }
    }
}
