/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.paymentsstandingorder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.mifosx.openbanking.core.ui.scaffold.KptScaffold
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.Res
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_screen_title
import org.mifosx.openbanking.feature.paymentsstandingorder.ui.StandingOrderAction
import org.mifosx.openbanking.feature.paymentsstandingorder.ui.StandingOrderEvent
import org.mifosx.openbanking.feature.paymentsstandingorder.ui.StandingOrderState
import org.mifosx.openbanking.feature.paymentsstandingorder.ui.StandingOrderUiState
import org.mifosx.openbanking.feature.paymentsstandingorder.ui.StandingOrderViewModel
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
internal fun StandingOrderScreen(
    onLaunchAuthorisation: (String) -> Unit,
    onNavigateToConsents: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StandingOrderViewModel = koinViewModel(),
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    EventsEffect(viewModel.eventFlow) { event ->
        when (event) {
            is StandingOrderEvent.LaunchAuthorisation -> onLaunchAuthorisation(event.url)
        }
    }

    KptScaffold(
        showNavigationIcon = false,
        title = stringResource(Res.string.feature_payments_standing_order_screen_title),
        modifier = modifier,
    ) {
        StandingOrderScreenContent(
            state = state,
            onAction = viewModel::trySendAction,
            onNavigateToConsents = onNavigateToConsents,
        )
    }
}

/** The stateless half every UI suite drives directly. */
@Composable
internal fun StandingOrderScreenContent(
    state: StandingOrderState,
    onAction: (StandingOrderAction) -> Unit,
    onNavigateToConsents: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val current = state.uiState) {
        StandingOrderUiState.Loading -> StandingOrderSkeleton(modifier = modifier)

        is StandingOrderUiState.Content -> StandingOrderContent(
            state = current,
            onAction = onAction,
            modifier = modifier,
        )

        is StandingOrderUiState.Submitting -> StandingOrderSubmitting(
            stage = current.stage,
            amountLabel = current.amountLabel,
            creditorName = current.creditorName,
            onAbandon = { onAction(StandingOrderAction.AbandonAuthorisation) },
            modifier = modifier,
        )

        is StandingOrderUiState.Error -> StandingOrderError(
            kind = current.kind,
            supportReference = current.supportReference,
            onRetry = { onAction(StandingOrderAction.RetryStaging) },
            onReauthorise = { onAction(StandingOrderAction.ConfirmAndStageConsent) },
            onViewConsents = onNavigateToConsents,
            onEditAmount = { onAction(StandingOrderAction.BackStep) },
            onChangePayer = { onAction(StandingOrderAction.ChangePayer) },
            // Back to the form, not straight into the picker: the window has moved since the date
            // was chosen, and the field's helper text is where the new one is stated.
            onChangeDate = { onAction(StandingOrderAction.BackStep) },
            modifier = modifier,
        )
    }
}
