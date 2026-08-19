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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
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
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.Res
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_continue
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_review_back
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_review_confirm
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_review_protected
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_review_title
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_title
import template.core.base.designsystem.component.KptTopAppBar
import template.core.base.designsystem.theme.KptTheme
import template.core.base.ui.effects.EventsEffect

@Composable
internal fun VrpSetupScreen(
    onBack: () -> Unit,
    onLaunchAuthorisation: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VrpSetupViewModel = koinViewModel(),
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val onAction = remember(viewModel) {
        { action: VrpSetupAction -> viewModel.trySendAction(action) }
    }

    EventsEffect(viewModel.eventFlow) { event ->
        when (event) {
            is VrpSetupEvent.LaunchAuthorisation -> onLaunchAuthorisation(event.url)
            is VrpSetupEvent.StagingFailed -> Unit
        }
    }

    VrpSetupScreenContent(
        state = state,
        onAction = onAction,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun VrpSetupScreenContent(
    state: VrpSetupState,
    onAction: (VrpSetupAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState = state.uiState
    val reviewing = (uiState as? VrpSetupUiState.Content)?.phase == SetupPhase.Review

    KptScaffold(
        modifier = modifier,
        topBar = {
            KptTopAppBar(
                title = stringResource(
                    if (reviewing) {
                        Res.string.feature_vrp_setup_review_title
                    } else {
                        Res.string.feature_vrp_setup_title
                    },
                ),
                onNavigationIconClick = onBack,
            )
        },
        bottomBar = {
            if (uiState is VrpSetupUiState.Content) {
                PrimaryAction(content = uiState, onAction = onAction)
            }
        },
    ) {
        when (uiState) {
            VrpSetupUiState.Loading -> VrpSetupSkeleton()

            is VrpSetupUiState.Content -> when (uiState.phase) {
                SetupPhase.Form -> VrpSetupFormPage(uiState.form, onAction)
                SetupPhase.Review -> VrpSetupReviewPage(uiState.form)
            }

            VrpSetupUiState.NoEligiblePayers -> VrpSetupNoAccounts()

            is VrpSetupUiState.Error -> VrpSetupError(
                onRetry = { onAction(VrpSetupAction.RetryLoad) },
            )
        }
    }
}

/** The pinned action, which is Continue on the form and the browser hop on the review. */
@Composable
private fun PrimaryAction(
    content: VrpSetupUiState.Content,
    onAction: (VrpSetupAction) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(KptTheme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (content.phase) {
            SetupPhase.Form -> MifosFilledPillButton(
                label = stringResource(Res.string.feature_vrp_setup_continue),
                onClick = { onAction(VrpSetupAction.Continue) },
                enabled = content.canContinue,
                testTag = VrpSetupTestTags.CONTINUE_BUTTON,
            )

            SetupPhase.Review -> {
                MifosFilledPillButton(
                    label = stringResource(Res.string.feature_vrp_setup_review_confirm),
                    onClick = { onAction(VrpSetupAction.StageConsent) },
                    enabled = !content.isStaging,
                    testTag = VrpSetupTestTags.REVIEW_CONFIRM,
                )
                ProtectedNotice()
                TextButton(
                    onClick = { onAction(VrpSetupAction.BackToForm) },
                    enabled = !content.isStaging,
                    modifier = Modifier.testTag(VrpSetupTestTags.REVIEW_BACK),
                ) {
                    Text(stringResource(Res.string.feature_vrp_setup_review_back))
                }
            }
        }
    }
}

/** The lock line under an action that leaves the app. */
@Composable
private fun ProtectedNotice() {
    Row(
        modifier = Modifier.padding(top = KptTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = KptTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(Res.string.feature_vrp_setup_review_protected),
            style = KptTheme.typography.bodySmall,
            color = KptTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = KptTheme.spacing.xs),
        )
    }
}
