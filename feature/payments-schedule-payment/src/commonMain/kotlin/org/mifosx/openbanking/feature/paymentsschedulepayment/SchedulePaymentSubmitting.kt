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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.mifosx.openbanking.core.ui.components.MifosTonalPillButton
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.Res
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_abandon_authorisation
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_submitting_amount
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_submitting_awaiting
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_submitting_lock
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_submitting_staging
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.SchedulePaymentStage

private val ContentPadding = 24.dp
private val LineGap = 16.dp
private val IndicatorSize = 56.dp
private val IndicatorBottomGap = 8.dp
private val LockIconSize = 16.dp
private val LockGap = 8.dp
private val BodyMaxWidth = 300.dp

/**
 * Submission in flight.
 *
 * There is no button here at all — not a disabled one. Confirming moves the screen to this state,
 * which unmounts the control entirely, and a control that does not exist cannot be tapped twice.
 * That is a stronger guarantee than a disabled attribute for the one screen in this app where a
 * double tap would move money twice.
 */
@Composable
internal fun SchedulePaymentSubmitting(
    stage: SchedulePaymentStage,
    amountLabel: String,
    creditorName: String,
    onAbandon: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val caption = when (stage) {
        SchedulePaymentStage.StagingConsent ->
            stringResource(Res.string.feature_payments_schedule_payment_submitting_staging)
        SchedulePaymentStage.AwaitingAuthorisation ->
            stringResource(Res.string.feature_payments_schedule_payment_submitting_awaiting)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(ContentPadding)
            .testTag(SchedulePaymentTestTags.SUBMITTING_INDICATOR),
        verticalArrangement = Arrangement.spacedBy(LineGap, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .size(IndicatorSize)
                .padding(bottom = IndicatorBottomGap),
            color = MaterialTheme.colorScheme.primary,
        )

        Text(
            text = stringResource(
                Res.string.feature_payments_schedule_payment_submitting_amount,
                amountLabel,
                creditorName,
            ),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(SchedulePaymentTestTags.SUBMITTING_AMOUNT),
        )

        Text(
            text = caption,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = BodyMaxWidth),
        )

        // The way out of the waiting state, and only of the waiting state.
        //
        // Staging is brief and cannot be interrupted — a consent that has reached the bank cannot be
        // withdrawn — so there is nothing to offer there. Waiting is different: the customer may have
        // come back through the task switcher rather than the redirect, in which case no callback is
        // ever delivered and this screen would spin forever. The immediate rail has exactly that
        // defect. This does not cancel anything; it releases the screen.
        if (stage == SchedulePaymentStage.AwaitingAuthorisation) {
            MifosTonalPillButton(
                label = stringResource(Res.string.feature_payments_schedule_payment_abandon_authorisation),
                onClick = onAbandon,
                testTag = SchedulePaymentTestTags.ABANDON_AUTHORISATION_BUTTON,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LockGap),
            modifier = Modifier.testTag(SchedulePaymentTestTags.SUBMITTING_LOCK_NOTE),
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(LockIconSize),
            )
            Text(
                text = stringResource(Res.string.feature_payments_schedule_payment_submitting_lock),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
