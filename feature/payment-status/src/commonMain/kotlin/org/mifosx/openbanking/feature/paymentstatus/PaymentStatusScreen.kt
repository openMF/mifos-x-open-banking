/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.paymentstatus

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.mifosx.openbanking.core.ui.scaffold.KptScaffold
import org.mifosx.openbanking.core.ui.scaffold.rememberKptPullToRefreshState
import org.mifosx.openbanking.feature.paymentstatus.generated.resources.Res
import org.mifosx.openbanking.feature.paymentstatus.generated.resources.feature_payment_status_back_a11y
import org.mifosx.openbanking.feature.paymentstatus.ui.PaymentStatusAction
import org.mifosx.openbanking.feature.paymentstatus.ui.PaymentStatusState
import org.mifosx.openbanking.feature.paymentstatus.ui.PaymentStatusUiState
import org.mifosx.openbanking.feature.paymentstatus.ui.PaymentStatusViewModel
import org.mifosx.openbanking.feature.paymentstatus.ui.isReading

/**
 * One submitted payment as the bank currently reports it.
 *
 * Pull-to-refresh is wired at the scaffold so the gesture also works from the error page, which is
 * exactly where someone reaches for it. It dispatches the same `RefreshStatus` the Refresh button
 * does, so both share one path through the view model — and the button stays, because the gesture
 * is undiscoverable on desktop and web where this screen also runs.
 */
@Composable
internal fun PaymentStatusScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PaymentStatusViewModel = koinViewModel(),
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    // Null while the status is still being read or could not be read at all. Null is a real case
    // rather than a default: before the bank answers there is no status, and inventing one would
    // put a colour and a word on the screen that nothing supports.
    val content = state.uiState as? PaymentStatusUiState.Content

    KptScaffold(
        topBar = { PaymentStatusTopBar(content = content, onBack = onBack) },
        pullToRefreshState = rememberKptPullToRefreshState(
            isEnabled = true,
            isRefreshing = state.uiState.isReading,
            onRefresh = { viewModel.trySendAction(PaymentStatusAction.RefreshStatus) },
        ),
        modifier = modifier,
    ) {
        PaymentStatusScreenContent(
            state = state,
            onAction = viewModel::trySendAction,
        )
    }
}

/**
 * The app bar, carrying the payment's status as its title and its colour.
 *
 * The status is the only thing this screen is about, so it says it once, at the top, in the colour
 * of the outcome — rather than a fixed "Payment status" title that repeats the screen's own name.
 * The colours and the wording both come from [dispositionColours] and [labelResource], which the
 * status chip also uses, so the bar and the chip cannot disagree.
 *
 * **The title is the disposition, never the reason.** "Failed" appears here; *why* it failed —
 * "Rejected by your bank" and the rest — stays on the body, where there is room to explain and where
 * a customer is not reading it out of the corner of their eye.
 *
 * With no disposition — still loading, or the read failed outright — the bar is left plain and
 * untitled. A payment whose status could not be read has no status to colour the screen with.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PaymentStatusTopBar(
    content: PaymentStatusUiState.Content?,
    onBack: () -> Unit,
) {
    val disposition = content?.disposition
    val colours = disposition?.let { dispositionColours(it) }
    TopAppBar(
        title = {
            if (content != null && disposition != null) {
                Text(
                    text = statusWord(content.status, content.consentType, disposition),
                    modifier = Modifier.testTag(PaymentStatusTestTags.APP_BAR_STATUS),
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.feature_payment_status_back_a11y),
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colours?.container ?: TopAppBarDefaults.topAppBarColors().containerColor,
            titleContentColor = colours?.onContainer ?: TopAppBarDefaults.topAppBarColors().titleContentColor,
            navigationIconContentColor = colours?.onContainer
                ?: TopAppBarDefaults.topAppBarColors().navigationIconContentColor,
        ),
        modifier = Modifier.testTag(PaymentStatusTestTags.APP_BAR),
    )
}

/** The stateless half every UI suite drives directly. */
@Composable
internal fun PaymentStatusScreenContent(
    state: PaymentStatusState,
    onAction: (PaymentStatusAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val current = state.uiState) {
        PaymentStatusUiState.Loading -> PaymentStatusSkeleton(modifier = modifier)

        is PaymentStatusUiState.Content -> PaymentStatusContent(
            state = current,
            onAction = onAction,
            modifier = modifier,
        )

        is PaymentStatusUiState.Error -> PaymentStatusError(
            kind = current.kind,
            onRetry = { onAction(PaymentStatusAction.RefreshStatus) },
            modifier = modifier,
        )
    }
}
