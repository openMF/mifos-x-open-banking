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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.mifosx.openbanking.core.model.banking.payment.PaymentDisposition
import org.mifosx.openbanking.core.model.banking.payment.PaymentStatus
import org.mifosx.openbanking.core.ui.components.MifosTonalPillButton
import org.mifosx.openbanking.feature.paymentstatus.components.PaymentTimeline
import org.mifosx.openbanking.feature.paymentstatus.generated.resources.Res
import org.mifosx.openbanking.feature.paymentstatus.generated.resources.feature_payment_status_completed
import org.mifosx.openbanking.feature.paymentstatus.generated.resources.feature_payment_status_detail_accc
import org.mifosx.openbanking.feature.paymentstatus.generated.resources.feature_payment_status_detail_acsc
import org.mifosx.openbanking.feature.paymentstatus.generated.resources.feature_payment_status_detail_acsp
import org.mifosx.openbanking.feature.paymentstatus.generated.resources.feature_payment_status_detail_actc
import org.mifosx.openbanking.feature.paymentstatus.generated.resources.feature_payment_status_detail_acwp
import org.mifosx.openbanking.feature.paymentstatus.generated.resources.feature_payment_status_detail_inco
import org.mifosx.openbanking.feature.paymentstatus.generated.resources.feature_payment_status_detail_pending
import org.mifosx.openbanking.feature.paymentstatus.generated.resources.feature_payment_status_detail_received
import org.mifosx.openbanking.feature.paymentstatus.generated.resources.feature_payment_status_detail_rjct
import org.mifosx.openbanking.feature.paymentstatus.generated.resources.feature_payment_status_detail_unknown
import org.mifosx.openbanking.feature.paymentstatus.generated.resources.feature_payment_status_details
import org.mifosx.openbanking.feature.paymentstatus.generated.resources.feature_payment_status_failed
import org.mifosx.openbanking.feature.paymentstatus.generated.resources.feature_payment_status_fee
import org.mifosx.openbanking.feature.paymentstatus.generated.resources.feature_payment_status_from
import org.mifosx.openbanking.feature.paymentstatus.generated.resources.feature_payment_status_in_progress
import org.mifosx.openbanking.feature.paymentstatus.generated.resources.feature_payment_status_in_progress_note
import org.mifosx.openbanking.feature.paymentstatus.generated.resources.feature_payment_status_last_checked
import org.mifosx.openbanking.feature.paymentstatus.generated.resources.feature_payment_status_new_payment
import org.mifosx.openbanking.feature.paymentstatus.generated.resources.feature_payment_status_payment_id
import org.mifosx.openbanking.feature.paymentstatus.generated.resources.feature_payment_status_reference
import org.mifosx.openbanking.feature.paymentstatus.generated.resources.feature_payment_status_reference_empty
import org.mifosx.openbanking.feature.paymentstatus.generated.resources.feature_payment_status_refresh
import org.mifosx.openbanking.feature.paymentstatus.generated.resources.feature_payment_status_refresh_failed
import org.mifosx.openbanking.feature.paymentstatus.generated.resources.feature_payment_status_scheduled_for
import org.mifosx.openbanking.feature.paymentstatus.generated.resources.feature_payment_status_settled
import org.mifosx.openbanking.feature.paymentstatus.generated.resources.feature_payment_status_status_changed
import org.mifosx.openbanking.feature.paymentstatus.generated.resources.feature_payment_status_submitted
import org.mifosx.openbanking.feature.paymentstatus.generated.resources.feature_payment_status_to
import org.mifosx.openbanking.feature.paymentstatus.ui.PaymentStatusAction
import org.mifosx.openbanking.feature.paymentstatus.ui.PaymentStatusUiState

private val ScreenPadding = 16.dp
private val SectionGap = 16.dp
private val CardShape = RoundedCornerShape(16.dp)
private val CardPadding = 24.dp
private val NoteShape = RoundedCornerShape(12.dp)
private val NotePadding = 16.dp
private val NoteGap = 12.dp
private val NoteIconSize = 24.dp
private val ChipShape = RoundedCornerShape(percent = 50)
private val ChipPaddingHorizontal = 12.dp
private val ChipPaddingVertical = 6.dp
private val ChipGap = 6.dp
private val ChipIconSize = 16.dp
private val RowPadding = 16.dp
private val TitleGap = 8.dp

/**
 * The payment as it currently stands: what was paid, its disposition, and the details behind it.
 *
 * The disposition drives the colour throughout. In-flight is deliberately not error-coloured —
 * "accepted, not yet settled" is the normal answer for a fresh payment, and rendering it as a fault
 * would send people chasing a problem that does not exist.
 */
@Composable
internal fun PaymentStatusContent(
    state: PaymentStatusUiState.Content,
    onAction: (PaymentStatusAction) -> Unit,
    onStartNewPayment: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(SectionGap),
    ) {
        SummaryCard(state)

        // A refresh that failed has not invalidated anything below it — the answer on screen is
        // still the bank's, only older than asked for. Said once, above the content it qualifies.
        if (state.refreshFailure != null) {
            RefreshFailureNote()
        }

        if (state.inProgress) {
            InProgressNote()
        }

        if (state.timeline.isNotEmpty()) {
            PaymentTimeline(entries = state.timeline)
        }

        DetailsSection(state)

        MifosTonalPillButton(
            label = stringResource(Res.string.feature_payment_status_refresh),
            onClick = { onAction(PaymentStatusAction.RefreshStatus) },
            icon = Icons.Filled.Refresh,
            testTag = PaymentStatusTestTags.REFRESH_BUTTON,
            enabled = !state.refreshing,
        )

        MifosTonalPillButton(
            label = stringResource(Res.string.feature_payment_status_new_payment),
            onClick = onStartNewPayment,
            testTag = PaymentStatusTestTags.NEW_PAYMENT_BUTTON,
        )

        // Without this, a refresh that returns the same status is indistinguishable from a button
        // that does nothing — which is exactly how it read while the sandbox sat at ACSP.
        if (state.lastCheckedAt.isNotBlank()) {
            Text(
                text = stringResource(
                    Res.string.feature_payment_status_last_checked,
                    state.lastCheckedAt,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(PaymentStatusTestTags.LAST_CHECKED),
            )
        }
    }
}

@Composable
private fun SummaryCard(state: PaymentStatusUiState.Content) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(CardPadding)
            .testTag(PaymentStatusTestTags.SUMMARY_CARD),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TitleGap),
    ) {
        Text(
            text = state.amountLabel,
            style = MaterialTheme.typography.displaySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(PaymentStatusTestTags.AMOUNT),
        )
        Text(
            text = stringResource(Res.string.feature_payment_status_to, state.creditorName),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        StatusChip(state.disposition)

        // The bank's own word, under the plain-English chip. "In progress" is our summary of ACSP;
        // this line is the thing the bank actually said, which is what someone checking whether a
        // refresh did anything needs to see.
        Text(
            text = stringResource(state.status.detailResource()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(PaymentStatusTestTags.STATUS_DETAIL),
        )
    }
}

@Composable
private fun StatusChip(disposition: PaymentDisposition) {
    val container: Color
    val onContainer: Color
    val icon: ImageVector
    when (disposition) {
        PaymentDisposition.InProgress -> {
            container = MaterialTheme.colorScheme.secondaryContainer
            onContainer = MaterialTheme.colorScheme.onSecondaryContainer
            icon = Icons.Filled.Schedule
        }

        PaymentDisposition.TerminalSuccess -> {
            container = MaterialTheme.colorScheme.tertiaryContainer
            onContainer = MaterialTheme.colorScheme.onTertiaryContainer
            icon = Icons.Filled.CheckCircle
        }

        PaymentDisposition.TerminalFailure -> {
            container = MaterialTheme.colorScheme.errorContainer
            onContainer = MaterialTheme.colorScheme.onErrorContainer
            icon = Icons.Filled.ErrorOutline
        }
    }

    Row(
        modifier = Modifier
            .clip(ChipShape)
            .background(container)
            .padding(horizontal = ChipPaddingHorizontal, vertical = ChipPaddingVertical)
            .testTag(PaymentStatusTestTags.STATUS_CHIP),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ChipGap),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = onContainer,
            modifier = Modifier.size(ChipIconSize),
        )
        Text(
            text = stringResource(disposition.labelResource()),
            style = MaterialTheme.typography.labelLarge,
            color = onContainer,
        )
    }
}

/**
 * The read failed; what is already on screen stays.
 *
 * Error-container coloured so it is not mistaken for the payment having gone wrong — the payment is
 * fine, the refresh is what did not land, which is why this sits above the status rather than
 * replacing it.
 */
@Composable
private fun RefreshFailureNote() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(NoteShape)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(NotePadding)
            .testTag(PaymentStatusTestTags.REFRESH_FAILURE),
        horizontalArrangement = Arrangement.spacedBy(NoteGap),
    ) {
        Icon(
            imageVector = Icons.Filled.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(NoteIconSize),
        )
        Text(
            text = stringResource(Res.string.feature_payment_status_refresh_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun InProgressNote() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(NoteShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(NotePadding)
            .testTag(PaymentStatusTestTags.IN_PROGRESS_NOTE),
        horizontalArrangement = Arrangement.spacedBy(NoteGap),
    ) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(NoteIconSize),
        )
        Text(
            text = stringResource(Res.string.feature_payment_status_in_progress_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun DetailsSection(state: PaymentStatusUiState.Content) {
    Column(verticalArrangement = Arrangement.spacedBy(TitleGap)) {
        Text(
            text = stringResource(Res.string.feature_payment_status_details).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(NoteShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .testTag(PaymentStatusTestTags.DETAILS_LIST),
        ) {
            DetailRow(
                label = stringResource(Res.string.feature_payment_status_reference),
                value = state.reference.ifBlank {
                    stringResource(Res.string.feature_payment_status_reference_empty)
                },
                tag = PaymentStatusTestTags.DETAIL_REFERENCE,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DetailRow(
                label = stringResource(Res.string.feature_payment_status_from),
                value = state.debtorLabel,
                tag = PaymentStatusTestTags.DETAIL_FROM,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DetailRow(
                label = stringResource(Res.string.feature_payment_status_submitted),
                value = state.submittedAt,
                tag = PaymentStatusTestTags.DETAIL_SUBMITTED,
            )

            // A scheduled payment states when it is due. The wording is deliberately future tense
            // and never says paid or sent: nothing has moved, and on this rail nothing will until
            // the date. The app also cannot confirm that it did — no per-execution status exists.
            if (state.scheduledForAt.isNotBlank()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                DetailRow(
                    label = stringResource(Res.string.feature_payment_status_scheduled_for),
                    value = state.scheduledForAt,
                    tag = PaymentStatusTestTags.DETAIL_SCHEDULED_FOR,
                )
            }

            // Omitted rather than drawn blank when the bank did not say — an empty value beside a
            // label reads as data we lost, not data we were never given.
            if (state.settledAt.isNotBlank()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                DetailRow(
                    label = stringResource(Res.string.feature_payment_status_settled),
                    value = state.settledAt,
                    tag = PaymentStatusTestTags.DETAIL_SETTLED,
                )
            }

            if (state.statusChangedAt.isNotBlank()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                DetailRow(
                    label = stringResource(Res.string.feature_payment_status_status_changed),
                    value = state.statusChangedAt,
                    tag = PaymentStatusTestTags.DETAIL_STATUS_CHANGED,
                )
            }

            // Only what the bank charged. No row at all when it charged nothing — "£0.00" would be
            // a claim, and this screen is the first place in the journey entitled to make one.
            // Indexed: one tag repeated across every charge row made `onNodeWithTag` ambiguous the
            // moment a payment carried two, which the single-charge fixture never showed.
            state.charges.forEachIndexed { index, charge ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                DetailRow(
                    label = stringResource(Res.string.feature_payment_status_fee, charge.typeLabel),
                    value = charge.amountLabel,
                    tag = PaymentStatusTestTags.detailFee(index),
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DetailRow(
                label = stringResource(Res.string.feature_payment_status_payment_id),
                value = state.paymentId,
                tag = PaymentStatusTestTags.DETAIL_PAYMENT_ID,
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, tag: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(RowPadding)
            .testTag(tag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
        )
    }
}

private fun PaymentDisposition.labelResource() = when (this) {
    PaymentDisposition.InProgress -> Res.string.feature_payment_status_in_progress
    PaymentDisposition.TerminalSuccess -> Res.string.feature_payment_status_completed
    PaymentDisposition.TerminalFailure -> Res.string.feature_payment_status_failed
}

/**
 * The bank's own status in words, rather than the enum name.
 *
 * Several OBIE statuses collapse into one disposition — `RCVD`, `PDNG` and `ACSP` all read as "In
 * progress" — so the chip alone cannot tell you which one you are looking at, or whether a refresh
 * moved between them. This line can.
 */
private fun PaymentStatus.detailResource() = when (this) {
    PaymentStatus.Received -> Res.string.feature_payment_status_detail_received
    PaymentStatus.Pending -> Res.string.feature_payment_status_detail_pending
    PaymentStatus.AcceptedSettlementInProcess -> Res.string.feature_payment_status_detail_acsp
    PaymentStatus.AcceptedTechnicalValidation -> Res.string.feature_payment_status_detail_actc
    PaymentStatus.InitiationCompleted -> Res.string.feature_payment_status_detail_inco
    PaymentStatus.AcceptedSettlementCompleted -> Res.string.feature_payment_status_detail_acsc
    PaymentStatus.AcceptedCreditSettlementCompleted -> Res.string.feature_payment_status_detail_accc
    PaymentStatus.AcceptedWithoutPosting -> Res.string.feature_payment_status_detail_acwp
    PaymentStatus.Rejected -> Res.string.feature_payment_status_detail_rjct
    PaymentStatus.Unknown -> Res.string.feature_payment_status_detail_unknown
}
