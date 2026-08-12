/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.paymentsschedulepayment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.mifosx.openbanking.core.ui.scaffold.KptScaffold
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.Res
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_screen_title
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.SchedulePaymentAction
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.SchedulePaymentEvent
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.SchedulePaymentState
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.SchedulePaymentUiState
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.SchedulePaymentViewModel
import template.core.base.ui.effects.EventsEffect

/**
 * The Pay tab.
 *
 * No navigation icon: this is a bottom-nav root, not a pushed screen, so there is nowhere to go
 * back to.
 *
 * Launching the bank's authorisation page leaves as an event rather than becoming state, because
 * only the composition can open a browser. There is no success event to handle: the payment is
 * completed by the leg that returns from the bank, which shows the receipt itself.
 */
@Composable
internal fun SchedulePaymentScreen(
    onLaunchAuthorisation: (String) -> Unit,
    onNavigateToConsents: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SchedulePaymentViewModel = koinViewModel(),
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    EventsEffect(viewModel.eventFlow) { event ->
        when (event) {
            is SchedulePaymentEvent.LaunchAuthorisation -> onLaunchAuthorisation(event.url)
        }
    }

    KptScaffold(
        showNavigationIcon = false,
        title = stringResource(Res.string.feature_payments_schedule_payment_screen_title),
        modifier = modifier,
    ) {
        SchedulePaymentScreenContent(
            state = state,
            onAction = viewModel::trySendAction,
            onNavigateToConsents = onNavigateToConsents,
        )
    }
}

/** The stateless half every UI suite drives directly. */
@Composable
internal fun SchedulePaymentScreenContent(
    state: SchedulePaymentState,
    onAction: (SchedulePaymentAction) -> Unit,
    onNavigateToConsents: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val current = state.uiState) {
        SchedulePaymentUiState.Loading -> SchedulePaymentSkeleton(modifier = modifier)

        is SchedulePaymentUiState.Content -> SchedulePaymentContent(
            state = current,
            onAction = onAction,
            modifier = modifier,
        )

        is SchedulePaymentUiState.Submitting -> SchedulePaymentSubmitting(
            stage = current.stage,
            amountLabel = current.amountLabel,
            creditorName = current.creditorName,
            onAbandon = { onAction(SchedulePaymentAction.AbandonAuthorisation) },
            modifier = modifier,
        )

        is SchedulePaymentUiState.Error -> SchedulePaymentError(
            kind = current.kind,
            supportReference = current.supportReference,
            onRetry = { onAction(SchedulePaymentAction.RetryStaging) },
            onReauthorise = { onAction(SchedulePaymentAction.ConfirmAndStageConsent) },
            onViewConsents = onNavigateToConsents,
            onEditAmount = { onAction(SchedulePaymentAction.BackStep) },
            onChangePayer = { onAction(SchedulePaymentAction.ChangePayer) },
            // Back to the form, not straight into the picker: the window has moved since the date
            // was chosen, and the field's helper text is where the new one is stated.
            onChangeDate = { onAction(SchedulePaymentAction.BackStep) },
            modifier = modifier,
        )
    }
}
