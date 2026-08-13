/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.paymentshub

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mifosx.openbanking.core.common.formatMinorUnits
import org.mifosx.openbanking.core.model.banking.payment.PaymentHistoryItem
import org.mifosx.openbanking.feature.paymentshub.ui.PaymentsHubAction
import org.mifosx.openbanking.feature.paymentshub.ui.PaymentsHubState
import org.mifosx.openbanking.feature.paymentshub.ui.PaymentsHubUiState
import template.core.base.designsystem.theme.KptTheme

private val QuickActionCardMinHeight = 120.dp
private val QuickActionIconSize = 24.dp
private val ActivityAvatarSize = 48.dp
private val StatusDotSize = 8.dp
private val CardBorderThickness = 1.dp
private val SuccessCheckSize = 24.dp

private fun quickActionIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) =
    @Composable {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = KptTheme.colorScheme.primary,
            modifier = Modifier.size(QuickActionIconSize),
        )
    }

@Composable
internal fun PaymentsHubContent(
    state: PaymentsHubState,
    onAction: (PaymentsHubAction) -> Unit,
    onNavigateToSendMoney: () -> Unit,
    onNavigateToPaymentStatus: (String) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToSchedulePayment: () -> Unit = {},
    onNavigateToStandingOrder: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    when (val current = state.uiState) {
        PaymentsHubUiState.Loading -> PaymentsHubSkeleton(
            modifier = modifier.padding(contentPadding),
        )
        is PaymentsHubUiState.Content -> PaymentsHubContentLoaded(
            current = current,
            onNavigateToSendMoney = onNavigateToSendMoney,
            onNavigateToSchedulePayment = onNavigateToSchedulePayment,
            onNavigateToStandingOrder = onNavigateToStandingOrder,
            onNavigateToPaymentStatus = onNavigateToPaymentStatus,
            modifier = modifier.padding(contentPadding),
        )
        is PaymentsHubUiState.Error -> PaymentsHubError(
            message = current.message,
            onRetry = { onAction(PaymentsHubAction.RetryLoad) },
            modifier = modifier.padding(contentPadding),
        )
    }
}

@Composable
private fun PaymentsHubContentLoaded(
    current: PaymentsHubUiState.Content,
    onNavigateToSendMoney: () -> Unit,
    onNavigateToPaymentStatus: (String) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToSchedulePayment: () -> Unit = {},
    onNavigateToStandingOrder: () -> Unit = {},
) {
    LazyColumn(
        modifier = modifier.fillMaxSize()
            .padding(horizontal = KptTheme.spacing.lg),
        contentPadding = PaddingValues(
            top = KptTheme.spacing.sm,
            bottom = KptTheme.spacing.lg,
        ),
        verticalArrangement = Arrangement.spacedBy(KptTheme.spacing.lg),
    ) {
        item {
            Text(
                text = "Quick Actions",
                style = KptTheme.typography.titleLarge,
                color = KptTheme.colorScheme.primary,
            )
        }

        item {
            QuickActionsSection(
                onNavigateToSendMoney = onNavigateToSendMoney,
                onNavigateToSchedulePayment = onNavigateToSchedulePayment,
                onNavigateToStandingOrder = onNavigateToStandingOrder,
            )
        }

        item {
            Text(
                text = "Recent",
                style = KptTheme.typography.titleMedium,
                color = KptTheme.colorScheme.onSurface,
            )
        }

        if (current.activityItems.isEmpty()) {
            item { EmptyActivity() }
        } else {
            items(current.activityItems, key = { it.id }) { item ->
                ActivityCard(
                    item = item,
                    onClick = {
                        item.domesticPaymentId?.let { onNavigateToPaymentStatus(it) }
                    },
                )
            }
        }
    }
}

@Composable
private fun QuickActionsSection(
    onNavigateToSendMoney: () -> Unit,
    onNavigateToSchedulePayment: () -> Unit,
    onNavigateToStandingOrder: () -> Unit,
) {
    val gap = KptTheme.spacing.md
    val rows = quickActionRows(
        onSendMoney = onNavigateToSendMoney,
        onSchedulePayment = onNavigateToSchedulePayment,
        onStandingOrder = onNavigateToStandingOrder,
    )
    Column(modifier = Modifier.testTag(PaymentsHubTestTags.QUICK_ACTIONS_GRID)) {
        for (row in rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                for (card in row) {
                    QuickActionCard(
                        modifier = Modifier.weight(1f).testTag(card.testTag),
                        icon = card.icon,
                        label = card.label,
                        subtext = card.subtext,
                        onClick = card.onClick,
                    )
                }
            }
            if (row != rows.last()) {
                Spacer(Modifier.height(gap))
            }
        }
    }
}

private data class QuickActionItem(
    val icon: @Composable () -> Unit,
    val label: String,
    val testTag: String,
    val subtext: String = "",
    val onClick: (() -> Unit)? = null,
)

@Composable
private fun quickActionRows(
    onSendMoney: () -> Unit,
    onSchedulePayment: () -> Unit,
    onStandingOrder: () -> Unit,
): List<List<QuickActionItem>> = listOf(
    listOf(
        QuickActionItem(
            icon = quickActionIcon(Icons.Filled.Send),
            label = "Send money",
            testTag = PaymentsHubTestTags.QUICK_ACTION_SEND_MONEY,
            subtext = "Pay instantly",
            onClick = onSendMoney,
        ),
        QuickActionItem(
            icon = quickActionIcon(Icons.Filled.CalendarMonth),
            label = "Schedule",
            testTag = PaymentsHubTestTags.QUICK_ACTION_SCHEDULE,
            subtext = "Pay on a date",
            onClick = onSchedulePayment,
        ),
    ),
    listOf(
        QuickActionItem(
            icon = quickActionIcon(Icons.Filled.Sync),
            label = "Standing order",
            testTag = PaymentsHubTestTags.QUICK_ACTION_STANDING_ORDER,
            subtext = "Repeat on schedule",
            onClick = onStandingOrder,
        ),
        QuickActionItem(
            icon = quickActionIcon(Icons.Filled.Speed),
            label = "VRP / Sweeping",
            testTag = PaymentsHubTestTags.QUICK_ACTION_VRP,
            subtext = "Automatic sweep",
        ),
    ),
)

private val QuickActionIconCircleSize = 48.dp

@Composable
private fun QuickActionCard(
    icon: @Composable () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    subtext: String = "",
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier
            .height(QuickActionCardMinHeight)
            .border(
                CardBorderThickness,
                KptTheme.colorScheme.outlineVariant,
                KptTheme.shapes.medium,
            )
            .then(onClick?.let { Modifier.clickable { it() } } ?: Modifier),
        colors = CardDefaults.cardColors(containerColor = KptTheme.colorScheme.surface),
        shape = KptTheme.shapes.medium,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(QuickActionIconCircleSize)
                        .clip(CircleShape)
                        .background(KptTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    icon()
                }
                Spacer(Modifier.height(KptTheme.spacing.sm))
                Text(
                    text = label,
                    style = KptTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = KptTheme.colorScheme.onSurface,
                )
                if (subtext.isNotBlank()) {
                    Text(
                        text = subtext,
                        style = KptTheme.typography.bodySmall,
                        color = KptTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityCard(item: PaymentHistoryItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PaymentsHubTestTags.activityCard(item.id))
            .then(if (item.isFailure) Modifier else activityCardBorder())
            .clickable(enabled = item.domesticPaymentId != null) { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (item.isFailure) {
                KptTheme.colorScheme.errorContainer
            } else {
                KptTheme.colorScheme.surface
            },
        ),
        shape = KptTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(KptTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActivityAvatar(item)
            Spacer(Modifier.width(KptTheme.spacing.md))
            ActivityInfo(item, modifier = Modifier.weight(1f))
            ActivityAmount(item)
        }
    }
}

@Composable
private fun activityCardBorder() =
    Modifier.border(CardBorderThickness, KptTheme.colorScheme.outlineVariant, KptTheme.shapes.medium)

@Composable
private fun ActivityAvatar(item: PaymentHistoryItem) {
    val bg = when {
        item.isFailure -> KptTheme.colorScheme.error.copy(alpha = 0.1f)
        item.isInFlight -> KptTheme.colorScheme.surfaceContainerHighest
        else -> KptTheme.colorScheme.secondaryContainer
    }
    val iconColor = when {
        item.isFailure -> KptTheme.colorScheme.error
        item.isInFlight -> KptTheme.colorScheme.onSurfaceVariant
        else -> KptTheme.colorScheme.onSecondaryContainer
    }
    Box(
        modifier = Modifier.size(ActivityAvatarSize).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        if (item.isFailure || item.isInFlight) {
            Text(
                text = item.creditorName.take(1).uppercase(),
                style = KptTheme.typography.labelMedium,
                color = iconColor,
            )
        } else {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = KptTheme.colorScheme.primary,
                modifier = Modifier.size(SuccessCheckSize),
            )
        }
    }
}

@Composable
private fun ActivityInfo(item: PaymentHistoryItem, modifier: Modifier = Modifier) {
    val textColor = if (item.isFailure) {
        KptTheme.colorScheme.onErrorContainer
    } else {
        KptTheme.colorScheme.onSurface
    }

    val statusColor = when {
        item.isFailure -> KptTheme.colorScheme.error
        item.isInFlight -> KptTheme.colorScheme.outline
        else -> KptTheme.colorScheme.primary
    }

    Column(modifier = modifier) {
        Text(
            text = item.creditorName,
            style = KptTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = textColor,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (item.isInFlight) {
                Box(
                    modifier = Modifier.size(StatusDotSize)
                        .clip(CircleShape)
                        .background(KptTheme.colorScheme.outline),
                )
                Spacer(Modifier.width(KptTheme.spacing.xs))
            }
            Text(
                text = item.statusLabel,
                style = KptTheme.typography.bodySmall,
                color = statusColor,
            )
        }
    }
}

@Composable
private fun ActivityAmount(item: PaymentHistoryItem) {
    val prefix = if (item.isFailure) "" else "-"
    val color = if (item.isFailure) {
        KptTheme.colorScheme.onErrorContainer
    } else {
        KptTheme.colorScheme.onSurface
    }
    Text(
        text = "$prefix${formatMinorUnits(item.amountMinorUnits, item.currency)}",
        style = KptTheme.typography.titleMedium,
        color = color,
    )
}

@Composable
private fun EmptyActivity() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = KptTheme.spacing.xl)
            .testTag(PaymentsHubTestTags.EMPTY_ACTIVITY),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No payments yet",
                style = KptTheme.typography.bodyLarge,
                color = KptTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
