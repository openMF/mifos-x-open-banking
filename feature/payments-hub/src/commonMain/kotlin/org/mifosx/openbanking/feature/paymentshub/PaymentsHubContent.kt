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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
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
import template.core.base.designsystem.theme.KptTheme

private val QuickActionCardMinHeight = 120.dp
private val QuickActionIconSize = 24.dp
private val CardBorderThickness = 1.dp

private fun quickActionIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) =
    @Composable {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = KptTheme.colorScheme.primary,
            modifier = Modifier.size(QuickActionIconSize),
        )
    }

/**
 * The hub, which is now four cards and nothing else.
 *
 * Stateless by construction. It used to render a Recent list from the local `payment_history` table
 * behind a Loading/Content/Error state machine; that list is gone, and with it the only asynchronous
 * work this screen ever did. Keeping the state machine would have been worse than useless — `Loading`
 * was left *only* by an emission from `observeRecent()`, so a hub that still waited for one would
 * have shimmered for ever.
 *
 * `payment_history` itself is untouched. It is still written on every submission and failure, and
 * still read for the payment-status screen's rail routing and stage timeline — none of which this
 * screen ever displayed.
 *
 * No heading above the cards: with one section left there is nothing to distinguish it from.
 */
@Composable
internal fun PaymentsHubContent(
    onNavigateToSendMoney: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToSchedulePayment: () -> Unit = {},
    onNavigateToStandingOrder: () -> Unit = {},
    onNavigateToVrp: () -> Unit = {},
) {
    // Scrollable even though it is one section: two rows of 120dp cards plus the scaffold's chrome
    // overflow a short screen, and the LazyColumn this replaced scrolled by construction.
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = KptTheme.spacing.lg)
            .padding(top = KptTheme.spacing.sm, bottom = KptTheme.spacing.lg),
    ) {
        QuickActionsSection(
            onNavigateToSendMoney = onNavigateToSendMoney,
            onNavigateToSchedulePayment = onNavigateToSchedulePayment,
            onNavigateToStandingOrder = onNavigateToStandingOrder,
            onNavigateToVrp = onNavigateToVrp,
        )
    }
}

@Composable
private fun QuickActionsSection(
    onNavigateToSendMoney: () -> Unit,
    onNavigateToSchedulePayment: () -> Unit,
    onNavigateToStandingOrder: () -> Unit,
    onNavigateToVrp: () -> Unit,
) {
    val gap = KptTheme.spacing.md
    val rows = quickActionRows(
        onSendMoney = onNavigateToSendMoney,
        onSchedulePayment = onNavigateToSchedulePayment,
        onStandingOrder = onNavigateToStandingOrder,
        onVrp = onNavigateToVrp,
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
    onVrp: () -> Unit,
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
            label = "Variable Recurring Payments",
            testTag = PaymentsHubTestTags.QUICK_ACTION_VRP,
            subtext = "Pay without signing in",
            onClick = onVrp,
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
