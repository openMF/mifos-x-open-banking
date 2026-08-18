/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.consentlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.mifosx.openbanking.core.model.callback.ConsentStatus
import org.mifosx.openbanking.feature.consentlist.generated.resources.Res
import org.mifosx.openbanking.feature.consentlist.generated.resources.feature_consent_list_card_a11y
import org.mifosx.openbanking.feature.consentlist.generated.resources.feature_consent_list_connected_on
import org.mifosx.openbanking.feature.consentlist.generated.resources.feature_consent_list_expired_on
import org.mifosx.openbanking.feature.consentlist.generated.resources.feature_consent_list_expires_in
import org.mifosx.openbanking.feature.consentlist.generated.resources.feature_consent_list_list_a11y
import org.mifosx.openbanking.feature.consentlist.generated.resources.feature_consent_list_permissions
import org.mifosx.openbanking.feature.consentlist.generated.resources.feature_consent_list_reconfirm_banner_body
import org.mifosx.openbanking.feature.consentlist.generated.resources.feature_consent_list_reconfirm_banner_title
import org.mifosx.openbanking.feature.consentlist.generated.resources.feature_consent_list_reconfirm_chip
import org.mifosx.openbanking.feature.consentlist.generated.resources.feature_consent_list_section_active
import org.mifosx.openbanking.feature.consentlist.generated.resources.feature_consent_list_status_a11y
import org.mifosx.openbanking.feature.consentlist.generated.resources.feature_consent_list_status_authorised
import org.mifosx.openbanking.feature.consentlist.generated.resources.feature_consent_list_status_awaiting
import org.mifosx.openbanking.feature.consentlist.generated.resources.feature_consent_list_status_cancelled
import org.mifosx.openbanking.feature.consentlist.generated.resources.feature_consent_list_status_consumed
import org.mifosx.openbanking.feature.consentlist.generated.resources.feature_consent_list_status_expired
import org.mifosx.openbanking.feature.consentlist.generated.resources.feature_consent_list_status_rejected
import org.mifosx.openbanking.feature.consentlist.generated.resources.feature_consent_list_status_revoked
import org.mifosx.openbanking.feature.consentlist.ui.ConsentCardUi
import org.mifosx.openbanking.feature.consentlist.ui.ConsentListUiState

private val ScreenHorizontalPadding = 16.dp
private val BannerPadding = 16.dp
private val BannerGap = 12.dp
private val CardPadding = 16.dp
private val CardGap = 8.dp
private val ChipVerticalPadding = 4.dp
private val ChipHorizontalPadding = 10.dp
private val ChipGap = 4.dp
private val ChipIconSize = 16.dp
private val SectionLabelTopPadding = 16.dp
private val SectionLabelBottomPadding = 8.dp
private val CardCorner = 12.dp
private val BannerCorner = 12.dp
private val ChipCorner = 999.dp
private val ActiveCardElevation = 1.dp
private val HistoryCardElevation = 0.dp

/**
 * Content state: an optional reconfirmation banner, then the current connection.
 *
 * There is one consent — the bank the PSU is connected under — so this is a single labelled card;
 * withdrawing it signs the PSU out rather than moving it to a history list.
 */
@Composable
internal fun ConsentListContent(
    content: ConsentListUiState.Content,
    onCardClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(Res.string.feature_consent_list_list_a11y)
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(ConsentListTestTags.CONTENT_LIST)
            .semantics { contentDescription = description },
    ) {
        if (content.showReconfirmBanner) {
            item(key = "reconfirm-banner") { ReconfirmBanner() }
        }

        if (content.active.isNotEmpty()) {
            item(key = "active-label") {
                SectionLabel(
                    text = stringResource(Res.string.feature_consent_list_section_active),
                    tag = ConsentListTestTags.ACTIVE_SECTION_LABEL,
                )
            }
            items(items = content.active, key = { it.consentId }) { card ->
                ConsentCard(card = card, onClick = onCardClick, isHistory = false)
            }
        }
    }
}

/**
 * The reconfirmation warning.
 *
 * Rendered once at the top rather than per card: the OBIE 90-day rule is a property of the
 * connection as a whole, and repeating it above every card would bury the list it is warning about.
 */
@Composable
private fun ReconfirmBanner(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(BannerCorner),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenHorizontalPadding)
            .padding(top = BannerGap)
            .testTag(ConsentListTestTags.RECONFIRM_BANNER),
    ) {
        Row(
            modifier = Modifier.padding(BannerPadding),
            horizontalArrangement = Arrangement.spacedBy(BannerGap),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Column(verticalArrangement = Arrangement.spacedBy(ChipGap)) {
                Text(
                    text = stringResource(Res.string.feature_consent_list_reconfirm_banner_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = stringResource(Res.string.feature_consent_list_reconfirm_banner_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(
    text: String,
    tag: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .padding(horizontal = ScreenHorizontalPadding)
            .padding(top = SectionLabelTopPadding, bottom = SectionLabelBottomPadding)
            .testTag(tag),
    )
}

/**
 * One consent card.
 *
 * A history card sits at elevation zero on the plain container so it reads as subordinate to the
 * live ones without being greyed out — it is still tappable, because the detail screen is where the
 * user sees what a revoked consent used to cover.
 */
@Composable
private fun ConsentCard(
    card: ConsentCardUi,
    onClick: (String) -> Unit,
    isHistory: Boolean,
    modifier: Modifier = Modifier,
) {
    val cardDescription = stringResource(Res.string.feature_consent_list_card_a11y)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isHistory) {
                MaterialTheme.colorScheme.surfaceContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isHistory) HistoryCardElevation else ActiveCardElevation,
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(CardCorner),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenHorizontalPadding, vertical = CardGap / 2)
            .clickable { onClick(card.consentId) }
            .testTag(ConsentListTestTags.card(card.consentId))
            .semantics(mergeDescendants = true) { contentDescription = cardDescription },
    ) {
        Column(
            modifier = Modifier.padding(CardPadding),
            verticalArrangement = Arrangement.spacedBy(CardGap),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusChip(card = card)
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (card.isNearExpiry) {
                UrgencyChip(consentId = card.consentId)
            }

            card.permissionCount?.let { count ->
                Text(
                    text = stringResource(Res.string.feature_consent_list_permissions, count),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Text(
                text = card.expiredOnDate
                    ?.let { stringResource(Res.string.feature_consent_list_expired_on, it) }
                    ?: stringResource(Res.string.feature_consent_list_expires_in, card.daysUntilExpiry),
                style = MaterialTheme.typography.labelMedium,
                color = if (card.isNearExpiry) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            Text(
                text = stringResource(Res.string.feature_consent_list_connected_on, card.connectedDate),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusChip(
    card: ConsentCardUi,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(card.status.labelResource())
    val description = stringResource(Res.string.feature_consent_list_status_a11y, label)
    val muted = card.status != ConsentStatus.Authorised

    Surface(
        color = if (muted) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(ChipCorner),
        modifier = modifier
            .testTag(ConsentListTestTags.statusChip(card.consentId))
            .semantics { contentDescription = description },
    ) {
        ChipRow(
            icon = if (muted) Icons.Filled.Schedule else Icons.Filled.CheckCircle,
            label = label,
            tint = if (muted) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            },
        )
    }
}

@Composable
private fun UrgencyChip(
    consentId: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(ChipCorner),
        modifier = modifier.testTag(ConsentListTestTags.urgencyChip(consentId)),
    ) {
        ChipRow(
            icon = Icons.Filled.WarningAmber,
            label = stringResource(Res.string.feature_consent_list_reconfirm_chip),
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun ChipRow(
    icon: ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = ChipHorizontalPadding, vertical = ChipVerticalPadding),
        horizontalArrangement = Arrangement.spacedBy(ChipGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(ChipIconSize),
        )
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

private fun ConsentStatus.labelResource(): StringResource = when (this) {
    ConsentStatus.Authorised -> Res.string.feature_consent_list_status_authorised
    ConsentStatus.Expired -> Res.string.feature_consent_list_status_expired
    ConsentStatus.Revoked -> Res.string.feature_consent_list_status_revoked
    ConsentStatus.Cancelled -> Res.string.feature_consent_list_status_cancelled
    ConsentStatus.Rejected -> Res.string.feature_consent_list_status_rejected
    ConsentStatus.AwaitingAuthorisation -> Res.string.feature_consent_list_status_awaiting
    ConsentStatus.Consumed -> Res.string.feature_consent_list_status_consumed
}
