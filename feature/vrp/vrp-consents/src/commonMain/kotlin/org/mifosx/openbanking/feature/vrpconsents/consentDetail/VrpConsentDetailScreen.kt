/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.vrpconsents.consentDetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.mifosx.openbanking.core.model.callback.ConsentStatus
import org.mifosx.openbanking.core.ui.components.MifosFilledPillButton
import org.mifosx.openbanking.core.ui.scaffold.KptScaffold
import org.mifosx.openbanking.feature.vrpconsents.consentStatusLabel
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.Res
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_detail_consumed
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_detail_ended
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_detail_error_title
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_detail_history_empty
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_detail_history_title
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_detail_limits
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_detail_no_end_date
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_detail_not_found
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_detail_pay
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_detail_payer
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_detail_payer_chosen_at_bank
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_detail_payment_failed
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_detail_per_payment_limit
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_detail_periodic_limit
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_detail_remaining
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_detail_remaining_note
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_detail_revoke
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_detail_revoke_body
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_detail_revoke_cancel
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_detail_revoke_confirm
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_detail_revoke_failed
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_detail_revoke_title
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_detail_synced_at
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_detail_this_period
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_detail_title
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_detail_unusable_body
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_detail_unusable_title
import org.mifosx.openbanking.feature.vrpconsents.generated.resources.feature_vrp_consents_detail_valid_until
import org.mifosx.openbanking.feature.vrpconsents.paymentStatusLabel
import org.mifosx.openbanking.feature.vrpconsents.periodLabel
import org.mifosx.openbanking.feature.vrpconsents.timeElapsedSince
import template.core.base.designsystem.theme.KptTheme
import template.core.base.ui.effects.EventsEffect

@Composable
internal fun VrpConsentDetailScreen(
    onBack: () -> Unit,
    onNavigateToPayment: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VrpConsentDetailViewModel = koinViewModel(),
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val onAction = remember(viewModel) {
        { action: VrpConsentDetailAction -> viewModel.trySendAction(action) }
    }

    EventsEffect(viewModel.eventFlow) { event ->
        when (event) {
            VrpConsentDetailEvent.Revoked -> onBack()
        }
    }

    VrpConsentDetailScreenContent(
        state = state,
        onAction = onAction,
        onBack = onBack,
        onNavigateToPayment = { onNavigateToPayment(state.consentId) },
        modifier = modifier,
    )
}

@Composable
internal fun VrpConsentDetailScreenContent(
    state: VrpConsentDetailState,
    onAction: (VrpConsentDetailAction) -> Unit,
    onBack: () -> Unit,
    onNavigateToPayment: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState = state.uiState

    KptScaffold(
        modifier = modifier,
        topBar = {
            DetailTopBar(
                title = uiState.headerTitle(),
                onBack = onBack,
            )
        },
    ) {
        when (uiState) {
            VrpConsentDetailUiState.Loading -> CentredProgress()

            is VrpConsentDetailUiState.Content -> ConsentDetail(
                content = uiState,
                onAction = onAction,
                onNavigateToPayment = onNavigateToPayment,
            )

            VrpConsentDetailUiState.Unusable -> MessageState(
                title = stringResource(Res.string.feature_vrp_consents_detail_unusable_title),
                body = stringResource(Res.string.feature_vrp_consents_detail_unusable_body),
                stateTag = VrpConsentDetailTestTags.UNUSABLE_STATE,
                bodyTag = VrpConsentDetailTestTags.UNUSABLE_BODY,
            )

            is VrpConsentDetailUiState.Ended -> MessageState(
                title = uiState.payeeName,
                body = stringResource(
                    if (uiState.bankRefusedRemoval) {
                        Res.string.feature_vrp_consents_detail_revoke_failed
                    } else {
                        Res.string.feature_vrp_consents_detail_ended
                    },
                ),
                stateTag = VrpConsentDetailTestTags.ENDED_STATE,
                bodyTag = if (uiState.bankRefusedRemoval) VrpConsentDetailTestTags.REVOKE_ERROR else null,
            )

            VrpConsentDetailUiState.NotFound -> MessageState(
                title = stringResource(Res.string.feature_vrp_consents_detail_error_title),
                body = stringResource(Res.string.feature_vrp_consents_detail_not_found),
                stateTag = VrpConsentDetailTestTags.NOT_FOUND_STATE,
            )
        }
    }
}

@Composable
private fun VrpConsentDetailUiState.headerTitle(): String = when (this) {
    is VrpConsentDetailUiState.Content -> payeeName
    is VrpConsentDetailUiState.Ended -> payeeName
    else -> stringResource(Res.string.feature_vrp_consents_detail_title)
}

/** The screen's top bar: the payee, and the way back. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailTopBar(
    title: String,
    onBack: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = KptTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = KptTheme.colorScheme.primary,
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = KptTheme.colorScheme.onSurface,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = KptTheme.colorScheme.background,
        ),
    )
}

@Composable
private fun ConsentDetail(
    content: VrpConsentDetailUiState.Content,
    onAction: (VrpConsentDetailAction) -> Unit,
    onNavigateToPayment: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(KptTheme.spacing.md)
            .testTag(VrpConsentDetailTestTags.CONTENT),
        verticalArrangement = Arrangement.spacedBy(KptTheme.spacing.md),
    ) {
        StatusHeader(content)
        content.periodicLimitUsage?.let { UsageCard(it) }
        LimitsCard(content)
        content.PayerRow()

        if (content.status == ConsentStatus.Authorised) {
            MifosFilledPillButton(
                label = stringResource(Res.string.feature_vrp_consents_detail_pay),
                onClick = onNavigateToPayment,
                icon = Icons.AutoMirrored.Filled.Send,
                testTag = VrpConsentDetailTestTags.PAY_BUTTON,
                enabled = content.canPay,
            )
        }

        PaymentHistory(content.payments)

        RevokeButton(
            onClick = { onAction(VrpConsentDetailAction.RevokeRequested) },
            enabled = content.canRevoke,
        )
    }

    if (content.revoke == RevokePhase.Confirming) {
        RevokeConfirmation(
            onConfirm = { onAction(VrpConsentDetailAction.RevokeConfirmed) },
            onDismiss = { onAction(VrpConsentDetailAction.RevokeDismissed) },
        )
    }
}

@Composable
private fun StatusHeader(content: VrpConsentDetailUiState.Content) {
    Column(modifier = Modifier.fillMaxWidth().testTag(VrpConsentDetailTestTags.HEADER)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KptTheme.spacing.sm),
        ) {
            StatusPill(content)
            Text(
                text = content.validUntil
                    ?.let { stringResource(Res.string.feature_vrp_consents_detail_valid_until, it) }
                    ?: stringResource(Res.string.feature_vrp_consents_detail_no_end_date),
                style = KptTheme.typography.titleMedium,
                color = KptTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).testTag(VrpConsentDetailTestTags.VALIDITY),
            )
        }

        content.syncedAt?.let {
            Text(
                text = stringResource(
                    Res.string.feature_vrp_consents_detail_synced_at,
                    timeElapsedSince(it),
                ),
                style = KptTheme.typography.bodySmall,
                color = KptTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = KptTheme.spacing.xs)
                    .testTag(VrpConsentDetailTestTags.SYNCED_AT),
            )
        }
    }
}

@Composable
private fun StatusPill(content: VrpConsentDetailUiState.Content) {
    Surface(
        shape = KptTheme.shapes.extraLarge,
        color = KptTheme.colorScheme.primary,
        modifier = Modifier.testTag(VrpConsentDetailTestTags.STATUS),
    ) {
        Text(
            text = consentStatusLabel(content.status).uppercase(),
            style = KptTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = KptTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(
                horizontal = KptTheme.spacing.sm,
                vertical = KptTheme.spacing.xs,
            ),
        )
    }
}

@Composable
private fun UsageCard(usage: PeriodicLimitUsageUi) {
    DetailCard {
        SectionHeading(
            stringResource(
                Res.string.feature_vrp_consents_detail_this_period,
                periodLabel(usage.periodType),
            ),
        )

        Row(
            modifier = Modifier.padding(top = KptTheme.spacing.sm),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = usage.remainingAmount,
                style = KptTheme.typography.headlineLarge,
                fontWeight = FontWeight.Light,
                color = KptTheme.colorScheme.onSurface,
                modifier = Modifier.testTag(VrpConsentDetailTestTags.REMAINING),
            )
            Text(
                text = stringResource(Res.string.feature_vrp_consents_detail_remaining, ""),
                style = KptTheme.typography.bodyMedium,
                color = KptTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = KptTheme.spacing.xs, bottom = KptTheme.spacing.xs),
            )
        }

        LinearProgressIndicator(
            progress = { usage.sentAmountFraction },
            modifier = Modifier.fillMaxWidth().padding(top = KptTheme.spacing.sm),
        )

        Text(
            text = stringResource(
                Res.string.feature_vrp_consents_detail_consumed,
                usage.sentAmount,
                usage.ceilingAmount,
            ),
            style = KptTheme.typography.bodyMedium,
            color = KptTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = KptTheme.spacing.sm)
                .testTag(VrpConsentDetailTestTags.CONSUMED),
        )

        AdvisoryNote()
    }
}

@Composable
private fun AdvisoryNote() {
    Surface(
        shape = KptTheme.shapes.medium,
        color = KptTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(top = KptTheme.spacing.md),
    ) {
        Row(
            modifier = Modifier.padding(KptTheme.spacing.md),
            horizontalArrangement = Arrangement.spacedBy(KptTheme.spacing.sm),
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = KptTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = stringResource(Res.string.feature_vrp_consents_detail_remaining_note),
                style = KptTheme.typography.bodyMedium,
                color = KptTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun LimitsCard(content: VrpConsentDetailUiState.Content) {
    DetailCard {
        SectionHeading(stringResource(Res.string.feature_vrp_consents_detail_limits))

        Text(
            text = stringResource(
                Res.string.feature_vrp_consents_detail_per_payment_limit,
                content.perPaymentCeilingAmount,
            ),
            style = KptTheme.typography.titleMedium,
            color = KptTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(top = KptTheme.spacing.sm)
                .testTag(VrpConsentDetailTestTags.PER_PAYMENT_LIMIT),
        )

        content.limits.forEach { limit ->
            HorizontalDivider(modifier = Modifier.padding(vertical = KptTheme.spacing.sm))
            Text(
                text = stringResource(
                    Res.string.feature_vrp_consents_detail_periodic_limit,
                    limit.ceilingAmount,
                    periodLabel(limit.periodType),
                ),
                style = KptTheme.typography.titleMedium,
                color = KptTheme.colorScheme.onSurface,
                modifier = Modifier.testTag(VrpConsentDetailTestTags.PERIODIC_LIMIT),
            )
        }
    }
}

@Composable
private fun VrpConsentDetailUiState.Content.PayerRow() {
    Row(
        modifier = Modifier.fillMaxWidth().testTag(VrpConsentDetailTestTags.PAYER),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KptTheme.spacing.sm),
    ) {
        Icon(
            imageVector = Icons.Filled.AccountBalance,
            contentDescription = null,
            tint = KptTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (payerName.isBlank()) {
                stringResource(Res.string.feature_vrp_consents_detail_payer_chosen_at_bank)
            } else {
                stringResource(Res.string.feature_vrp_consents_detail_payer, payerName)
            },
            style = KptTheme.typography.bodyLarge,
            color = KptTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun PaymentHistory(payments: List<PaymentRowUi>) {
    Column(modifier = Modifier.fillMaxWidth().testTag(VrpConsentDetailTestTags.HISTORY)) {
        SectionHeading(stringResource(Res.string.feature_vrp_consents_detail_history_title))

        if (payments.isEmpty()) {
            Text(
                text = stringResource(Res.string.feature_vrp_consents_detail_history_empty),
                style = KptTheme.typography.bodyMedium,
                color = KptTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = KptTheme.spacing.sm)
                    .testTag(VrpConsentDetailTestTags.HISTORY_EMPTY),
            )
            return@Column
        }

        // Flat: the history is a list to read, and a raised surface reads as something to tap.
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = KptTheme.spacing.sm),
            shape = KptTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = KptTheme.colorScheme.surfaceContainerLowest),
            elevation = CardDefaults.cardElevation(defaultElevation = KptTheme.elevation.level0),
        ) {
            payments.forEachIndexed { index, payment ->
                if (index > 0) HorizontalDivider()
                PaymentRow(payment)
            }
        }
    }
}

@Composable
private fun PaymentRow(payment: PaymentRowUi) {
    val failed = payment.hasFailed
    val tone = if (failed) KptTheme.colorScheme.error else KptTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(KptTheme.spacing.md)
            .testTag(VrpConsentDetailTestTags.paymentRow(payment.localId)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = payment.sentAmount,
                style = KptTheme.typography.titleMedium,
                color = tone,
                modifier = Modifier.testTag(VrpConsentDetailTestTags.paymentAmount(payment.localId)),
            )
            if (failed) {
                Text(
                    text = stringResource(Res.string.feature_vrp_consents_detail_payment_failed),
                    style = KptTheme.typography.bodySmall,
                    color = KptTheme.colorScheme.error,
                )
            }
            Text(
                text = payment.sentOn,
                style = KptTheme.typography.bodySmall,
                color = KptTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KptTheme.spacing.xs),
            modifier = Modifier.testTag(VrpConsentDetailTestTags.paymentStatus(payment.localId)),
        ) {
            Icon(
                imageVector = if (failed) Icons.Filled.ErrorOutline else Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = if (failed) KptTheme.colorScheme.error else KptTheme.colorScheme.outline,
            )
            if (!failed) {
                Text(
                    text = paymentStatusLabel(payment.status),
                    style = KptTheme.typography.bodyMedium,
                    color = KptTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RevokeButton(
    onClick: () -> Unit,
    enabled: Boolean,
) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.testTag(VrpConsentDetailTestTags.REVOKE_BUTTON),
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = null,
                tint = KptTheme.colorScheme.error,
            )
            Text(
                text = stringResource(Res.string.feature_vrp_consents_detail_revoke),
                color = KptTheme.colorScheme.error,
                modifier = Modifier.padding(start = KptTheme.spacing.sm),
            )
        }
    }
}

@Composable
private fun RevokeConfirmation(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(VrpConsentDetailTestTags.REVOKE_DIALOG),
        title = { Text(stringResource(Res.string.feature_vrp_consents_detail_revoke_title)) },
        text = {
            Text(
                text = stringResource(Res.string.feature_vrp_consents_detail_revoke_body),
                modifier = Modifier.testTag(VrpConsentDetailTestTags.REVOKE_IRREVERSIBLE_NOTICE),
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag(VrpConsentDetailTestTags.REVOKE_CONFIRM),
            ) {
                Text(
                    text = stringResource(Res.string.feature_vrp_consents_detail_revoke_confirm),
                    color = KptTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(VrpConsentDetailTestTags.REVOKE_CANCEL),
            ) {
                Text(stringResource(Res.string.feature_vrp_consents_detail_revoke_cancel))
            }
        },
    )
}

@Composable
private fun DetailCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = KptTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = KptTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = KptTheme.elevation.level1),
    ) {
        Column(modifier = Modifier.padding(KptTheme.spacing.md)) { content() }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text.uppercase(),
        style = KptTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = KptTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CentredProgress() {
    Box(
        modifier = Modifier.fillMaxSize().testTag(VrpConsentDetailTestTags.LOADING_SKELETON),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MessageState(
    title: String,
    body: String,
    stateTag: String,
    bodyTag: String? = null,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(KptTheme.spacing.md).testTag(stateTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = KptTheme.typography.headlineSmall,
            color = KptTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = KptTheme.typography.bodyMedium,
            color = KptTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = KptTheme.spacing.sm)
                .then(bodyTag?.let { Modifier.testTag(it) } ?: Modifier),
        )
        action?.let {
            Box(modifier = Modifier.padding(top = KptTheme.spacing.lg)) { it() }
        }
    }
}
