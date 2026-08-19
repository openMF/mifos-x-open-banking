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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.mifosx.openbanking.core.ui.components.MifosFilledPillButton
import org.mifosx.openbanking.core.ui.scaffold.KptScaffold
import org.mifosx.openbanking.feature.vrppayment.generated.resources.Res
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_continue
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_error_body
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_error_title
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_retry
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_review_back
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_review_confirm
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_review_done
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_review_refresh
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_review_retry
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_review_title
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_title
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_unusable
import template.core.base.designsystem.component.KptTopAppBar
import template.core.base.designsystem.theme.KptTheme
import template.core.base.ui.effects.EventsEffect

@Composable
internal fun VrpPaymentScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VrpPaymentViewModel = koinViewModel(),
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val onAction = remember(viewModel) {
        { action: VrpPaymentAction -> viewModel.trySendAction(action) }
    }

    EventsEffect(viewModel.eventFlow) { event ->
        when (event) {
            is VrpPaymentEvent.Finished -> onBack()
        }
    }

    VrpPaymentScreenContent(
        state = state,
        onAction = onAction,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun VrpPaymentScreenContent(
    state: VrpPaymentState,
    onAction: (VrpPaymentAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState = state.uiState
    val reviewing = (uiState as? VrpPaymentUiState.Content)?.phase == PaymentPhase.Review

    KptScaffold(
        modifier = modifier,
        topBar = {
            KptTopAppBar(
                title = stringResource(
                    if (reviewing) {
                        Res.string.feature_vrp_payment_review_title
                    } else {
                        Res.string.feature_vrp_payment_title
                    },
                ),
                onNavigationIconClick = onBack,
            )
        },
        bottomBar = {
            if (uiState is VrpPaymentUiState.Content) {
                PrimaryAction(content = uiState, onAction = onAction)
            }
        },
    ) {
        when (uiState) {
            VrpPaymentUiState.Loading -> CentredProgress()

            is VrpPaymentUiState.Content -> when (uiState.phase) {
                PaymentPhase.Amount -> VrpPaymentAmountPage(uiState.form, onAction)
                PaymentPhase.Review -> VrpPaymentReviewPage(uiState.form, uiState.outcome)
            }

            VrpPaymentUiState.Unusable -> MessageState(
                body = stringResource(Res.string.feature_vrp_payment_unusable),
                stateTag = VrpPaymentTestTags.UNUSABLE_STATE,
            )

            is VrpPaymentUiState.Error -> MessageState(
                title = stringResource(Res.string.feature_vrp_payment_error_title),
                body = stringResource(Res.string.feature_vrp_payment_error_body),
                stateTag = VrpPaymentTestTags.ERROR_STATE,
                action = {
                    MifosFilledPillButton(
                        label = stringResource(Res.string.feature_vrp_payment_retry),
                        onClick = { onAction(VrpPaymentAction.RetryLoad) },
                        testTag = VrpPaymentTestTags.RETRY_BUTTON,
                    )
                },
            )
        }
    }
}

/**
 * The pinned action, which changes with the phase and with what became of the submission.
 *
 * A retry replays the same idempotency key, which is why it is offered at all — and why it is
 * offered only for a failure that cannot have moved money.
 */
@Composable
private fun PrimaryAction(
    content: VrpPaymentUiState.Content,
    onAction: (VrpPaymentAction) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(KptTheme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            content.phase == PaymentPhase.Amount -> MifosFilledPillButton(
                label = stringResource(Res.string.feature_vrp_payment_continue),
                onClick = { onAction(VrpPaymentAction.Continue) },
                enabled = content.canContinue,
                testTag = VrpPaymentTestTags.CONTINUE_BUTTON,
            )

            content.outcome is SubmissionUi.Sent -> {
                if (!content.outcome.settled) {
                    TextButton(
                        onClick = { onAction(VrpPaymentAction.RefreshOutcome) },
                        modifier = Modifier.testTag(VrpPaymentTestTags.REFRESH_BUTTON),
                    ) {
                        Text(stringResource(Res.string.feature_vrp_payment_review_refresh))
                    }
                }
                MifosFilledPillButton(
                    label = stringResource(Res.string.feature_vrp_payment_review_done),
                    onClick = { onAction(VrpPaymentAction.Done) },
                    testTag = VrpPaymentTestTags.DONE_BUTTON,
                )
            }

            content.outcome is SubmissionUi.Failed -> {
                if (content.outcome.kind.isRetryable) {
                    MifosFilledPillButton(
                        label = stringResource(Res.string.feature_vrp_payment_review_retry),
                        onClick = { onAction(VrpPaymentAction.RetrySubmission) },
                        testTag = VrpPaymentTestTags.RETRY_SUBMISSION_BUTTON,
                    )
                }
                TextButton(
                    onClick = { onAction(VrpPaymentAction.Done) },
                    modifier = Modifier.testTag(VrpPaymentTestTags.DONE_BUTTON),
                ) {
                    Text(stringResource(Res.string.feature_vrp_payment_review_done))
                }
            }

            else -> {
                MifosFilledPillButton(
                    label = stringResource(Res.string.feature_vrp_payment_review_confirm),
                    onClick = { onAction(VrpPaymentAction.Confirm) },
                    enabled = content.outcome !is SubmissionUi.Sending,
                    testTag = VrpPaymentTestTags.CONFIRM_BUTTON,
                )
                TextButton(
                    onClick = { onAction(VrpPaymentAction.BackToAmount) },
                    enabled = content.outcome !is SubmissionUi.Sending,
                    modifier = Modifier.testTag(VrpPaymentTestTags.BACK_BUTTON),
                ) {
                    Text(stringResource(Res.string.feature_vrp_payment_review_back))
                }
            }
        }
    }
}

@Composable
private fun CentredProgress() {
    Box(
        modifier = Modifier.fillMaxSize().testTag(VrpPaymentTestTags.LOADING_SKELETON),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MessageState(
    body: String,
    stateTag: String,
    title: String? = null,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(KptTheme.spacing.md).testTag(stateTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        title?.let {
            Text(
                text = it,
                style = KptTheme.typography.headlineSmall,
                color = KptTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = body,
            style = KptTheme.typography.bodyMedium,
            color = KptTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = KptTheme.spacing.sm),
        )
        action?.let {
            Box(modifier = Modifier.padding(top = KptTheme.spacing.lg)) { it() }
        }
    }
}
