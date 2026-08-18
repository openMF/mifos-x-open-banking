/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.consentdetail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.mifosx.openbanking.core.model.callback.ConsentStatus
import org.mifosx.openbanking.core.ui.components.MifosTonalPillButton
import org.mifosx.openbanking.feature.consentdetail.generated.resources.Res
import org.mifosx.openbanking.feature.consentdetail.generated.resources.feature_consent_detail_consent_id
import org.mifosx.openbanking.feature.consentdetail.generated.resources.feature_consent_detail_expiry_warning
import org.mifosx.openbanking.feature.consentdetail.generated.resources.feature_consent_detail_label_connected_on
import org.mifosx.openbanking.feature.consentdetail.generated.resources.feature_consent_detail_label_expires_on
import org.mifosx.openbanking.feature.consentdetail.generated.resources.feature_consent_detail_label_transaction_from
import org.mifosx.openbanking.feature.consentdetail.generated.resources.feature_consent_detail_label_transaction_to
import org.mifosx.openbanking.feature.consentdetail.generated.resources.feature_consent_detail_permissions_a11y
import org.mifosx.openbanking.feature.consentdetail.generated.resources.feature_consent_detail_reconfirm_button
import org.mifosx.openbanking.feature.consentdetail.generated.resources.feature_consent_detail_revoke_a11y
import org.mifosx.openbanking.feature.consentdetail.generated.resources.feature_consent_detail_revoke_button
import org.mifosx.openbanking.feature.consentdetail.generated.resources.feature_consent_detail_section_access_period
import org.mifosx.openbanking.feature.consentdetail.generated.resources.feature_consent_detail_section_data_shared
import org.mifosx.openbanking.feature.consentdetail.generated.resources.feature_consent_detail_status_a11y
import org.mifosx.openbanking.feature.consentdetail.generated.resources.feature_consent_detail_status_authorised
import org.mifosx.openbanking.feature.consentdetail.generated.resources.feature_consent_detail_status_awaiting
import org.mifosx.openbanking.feature.consentdetail.generated.resources.feature_consent_detail_status_cancelled
import org.mifosx.openbanking.feature.consentdetail.generated.resources.feature_consent_detail_status_consumed
import org.mifosx.openbanking.feature.consentdetail.generated.resources.feature_consent_detail_status_expired
import org.mifosx.openbanking.feature.consentdetail.generated.resources.feature_consent_detail_status_rejected
import org.mifosx.openbanking.feature.consentdetail.generated.resources.feature_consent_detail_status_revoked
import org.mifosx.openbanking.feature.consentdetail.ui.ConsentDetailUi

private val ScreenPadding = 16.dp
private val CardPadding = 16.dp
private val CardGap = 8.dp
private val SectionTopPadding = 16.dp
private val SectionBottomPadding = 8.dp
private val RowVerticalPadding = 12.dp
private val RowGap = 16.dp
private val ChipVerticalPadding = 4.dp
private val ChipHorizontalPadding = 10.dp
private val ChipGap = 4.dp
private val ChipIconSize = 16.dp
private val CardCorner = 12.dp
private val ChipCorner = 999.dp
private val BannerCorner = 12.dp
private val ButtonTopGap = 8.dp
private val RevokeBorderWidth = 1.5.dp

/** The four date slots, in the order the design lists them. Used as stable test-tag keys too. */
private enum class DateSlot(val slug: String) {
    ConnectedOn("connected"),
    ExpiresOn("expires"),
    TransactionFrom("txnFrom"),
    TransactionTo("txnTo"),
}

/**
 * Content state: status, access period, what was shared, and the two actions.
 *
 * The revoke action is an outlined error button rather than a filled one — it is destructive, so it
 * reads as a deliberate choice rather than the screen's primary call to action. It only opens the
 * confirmation gate; nothing is issued until the dialog is confirmed.
 */
@Composable
internal fun ConsentDetailContent(
    consent: ConsentDetailUi,
    onReconfirm: () -> Unit,
    onRevokeClick: () -> Unit,
    modifier: Modifier = Modifier,
    actionsEnabled: Boolean = true,
) {
    val permissionsDescription = stringResource(Res.string.feature_consent_detail_permissions_a11y)
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(ConsentDetailTestTags.CONTENT_LIST),
        contentPadding = PaddingValues(ScreenPadding),
    ) {
        item(key = "status-card") { StatusCard(consent = consent) }

        consent.expiryWarningDays?.let { days ->
            item(key = "expiry-banner") { ExpiryBanner(days = days) }
        }

        item(key = "dates-header") {
            SectionHeader(
                text = stringResource(Res.string.feature_consent_detail_section_access_period),
                tag = ConsentDetailTestTags.DATES_HEADER,
            )
        }
        item(key = "dates-list") { DatesList(consent = consent) }

        item(key = "reconfirm") {
            MifosTonalPillButton(
                label = stringResource(Res.string.feature_consent_detail_reconfirm_button),
                onClick = onReconfirm,
                icon = Icons.Filled.Refresh,
                testTag = ConsentDetailTestTags.RECONFIRM_BUTTON,
                modifier = Modifier.padding(top = ButtonTopGap),
            )
        }

        item(key = "permissions-header") {
            SectionHeader(
                text = stringResource(Res.string.feature_consent_detail_section_data_shared),
                tag = ConsentDetailTestTags.PERMISSIONS_HEADER,
            )
        }
        items(items = consent.permissions, key = { it }) { code ->
            PermissionRow(
                code = code,
                modifier = Modifier.semantics { contentDescription = permissionsDescription },
            )
        }

        item(key = "revoke") { RevokeButton(onClick = onRevokeClick, enabled = actionsEnabled) }
    }
}

/**
 * The destructive action.
 *
 * Outlined in the error colour rather than filled with it: this is a deliberate, rare choice, and a
 * filled error button would read as the screen's primary call to action.
 */
@Composable
private fun RevokeButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(Res.string.feature_consent_detail_revoke_a11y)
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(ChipCorner),
        border = BorderStroke(width = RevokeBorderWidth, color = MaterialTheme.colorScheme.error),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = ButtonTopGap)
            .testTag(ConsentDetailTestTags.REVOKE_BUTTON)
            .semantics { contentDescription = description },
    ) {
        Icon(Icons.Filled.LinkOff, contentDescription = null, modifier = Modifier.size(ChipIconSize))
        Text(
            text = stringResource(Res.string.feature_consent_detail_revoke_button),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = ChipGap),
        )
    }
}

@Composable
private fun StatusCard(
    consent: ConsentDetailUi,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(CardCorner),
        modifier = modifier
            .fillMaxWidth()
            .testTag(ConsentDetailTestTags.STATUS_CARD),
    ) {
        Column(
            modifier = Modifier.padding(CardPadding),
            verticalArrangement = Arrangement.spacedBy(CardGap),
        ) {
            StatusChip(status = consent.status)
            Text(
                text = stringResource(Res.string.feature_consent_detail_consent_id, consent.consentId),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(ConsentDetailTestTags.CONSENT_ID),
            )
        }
    }
}

@Composable
private fun StatusChip(
    status: ConsentStatus,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(status.labelResource())
    val description = stringResource(Res.string.feature_consent_detail_status_a11y, label)
    val authorised = status == ConsentStatus.Authorised

    Surface(
        color = if (authorised) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        shape = RoundedCornerShape(ChipCorner),
        modifier = modifier
            .testTag(ConsentDetailTestTags.STATUS_CHIP)
            .semantics { contentDescription = description },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = ChipHorizontalPadding, vertical = ChipVerticalPadding),
            horizontalArrangement = Arrangement.spacedBy(ChipGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (authorised) Icons.Filled.CheckCircle else Icons.Filled.Schedule,
                contentDescription = null,
                tint = if (authorised) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(ChipIconSize),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (authorised) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/**
 * The expiry warning, rendered above the dates section.
 *
 * Uses the tertiary container rather than the error container: an approaching expiry is a prompt,
 * not a fault, and the design reserves error red for things that have actually gone wrong.
 */
@Composable
private fun ExpiryBanner(
    days: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(BannerCorner),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = CardGap)
            .testTag(ConsentDetailTestTags.EXPIRY_BANNER),
    ) {
        Row(
            modifier = Modifier.padding(CardPadding),
            horizontalArrangement = Arrangement.spacedBy(RowGap / 2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.AccessTime,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(ChipIconSize),
            )
            Text(
                text = stringResource(Res.string.feature_consent_detail_expiry_warning, days),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
private fun SectionHeader(
    text: String,
    tag: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .padding(top = SectionTopPadding, bottom = SectionBottomPadding)
            .testTag(tag),
    )
}

@Composable
private fun DatesList(
    consent: ConsentDetailUi,
    modifier: Modifier = Modifier,
) {
    val rows = listOf(
        Triple(DateSlot.ConnectedOn, Icons.Filled.Event, consent.connectedDate),
        Triple(DateSlot.ExpiresOn, Icons.Filled.EventBusy, consent.expiresDate),
        Triple(DateSlot.TransactionFrom, Icons.Filled.History, consent.transactionFromDate),
        Triple(DateSlot.TransactionTo, Icons.Filled.EventAvailable, consent.transactionToDate),
    )

    Column(modifier = modifier.testTag(ConsentDetailTestTags.DATES_LIST)) {
        rows.forEachIndexed { index, (slot, icon, value) ->
            DateRow(slot = slot, icon = icon, value = value)
            if (index != rows.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun DateRow(
    slot: DateSlot,
    icon: ImageVector,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = RowVerticalPadding)
            .testTag(ConsentDetailTestTags.dateRow(slot.slug)),
        horizontalArrangement = Arrangement.spacedBy(RowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(slot.labelResource()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PermissionRow(
    code: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = RowVerticalPadding)
            .testTag(ConsentDetailTestTags.permissionRow(code)),
        horizontalArrangement = Arrangement.spacedBy(RowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.CheckCircleOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = code.humanisePermissionCode(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Turns an OBIE permission code into readable words.
 *
 * `ReadBeneficiariesDetail` becomes `Read beneficiaries detail`. The bank sends dozens of these and
 * they change between OBIE versions, so they are split mechanically rather than held in a lookup
 * table that would silently render a new code as a blank row.
 */
internal fun String.humanisePermissionCode(): String {
    if (isEmpty()) return this
    val spaced = buildString {
        this@humanisePermissionCode.forEachIndexed { index, char ->
            if (index > 0 && char.isUpperCase()) append(' ')
            append(char)
        }
    }
    return spaced.replaceFirstChar { it.uppercase() }.let { text ->
        text.first() + text.drop(1).lowercase()
    }
}

private fun DateSlot.labelResource(): StringResource = when (this) {
    DateSlot.ConnectedOn -> Res.string.feature_consent_detail_label_connected_on
    DateSlot.ExpiresOn -> Res.string.feature_consent_detail_label_expires_on
    DateSlot.TransactionFrom -> Res.string.feature_consent_detail_label_transaction_from
    DateSlot.TransactionTo -> Res.string.feature_consent_detail_label_transaction_to
}

private fun ConsentStatus.labelResource(): StringResource = when (this) {
    ConsentStatus.Authorised -> Res.string.feature_consent_detail_status_authorised
    ConsentStatus.Expired -> Res.string.feature_consent_detail_status_expired
    ConsentStatus.Revoked -> Res.string.feature_consent_detail_status_revoked
    ConsentStatus.Cancelled -> Res.string.feature_consent_detail_status_cancelled
    ConsentStatus.Rejected -> Res.string.feature_consent_detail_status_rejected
    ConsentStatus.AwaitingAuthorisation -> Res.string.feature_consent_detail_status_awaiting
    ConsentStatus.Consumed -> Res.string.feature_consent_detail_status_consumed
}
