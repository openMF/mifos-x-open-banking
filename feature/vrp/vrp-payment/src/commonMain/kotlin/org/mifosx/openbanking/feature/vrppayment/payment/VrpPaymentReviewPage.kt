/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.vrppayment.payment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.mifosx.openbanking.feature.vrppayment.generated.resources.Res
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_review_amount
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_review_error_limit
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_review_error_network
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_review_error_reauthorise
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_review_error_rejected
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_review_error_unconfirmed
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_review_error_unusable
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_review_failed_title
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_review_in_progress
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_review_sent_title
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_review_settled
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_review_support_reference
import org.mifosx.openbanking.feature.vrppayment.generated.resources.feature_vrp_payment_to
import org.mifosx.openbanking.feature.vrppayment.periodLabel
import template.core.base.designsystem.theme.KptTheme

private val BadgeSize = 96.dp
private val BadgeIconSize = 48.dp

/** The check phase, and what became of the payment once it was sent. */
@Composable
internal fun VrpPaymentReviewPage(
    form: PaymentFormUi,
    outcome: SubmissionUi,
) {
    when (outcome) {
        SubmissionUi.NotStarted -> ReviewDetails(form)
        SubmissionUi.Sending -> Sending()
        is SubmissionUi.Sent -> Sent(form, outcome)
        is SubmissionUi.Failed -> Failed(form, outcome)
    }
}

@Composable
private fun ReviewDetails(form: PaymentFormUi) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(KptTheme.spacing.md)
            .testTag(VrpPaymentTestTags.REVIEW_PAGE),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(Res.string.feature_vrp_payment_review_amount).uppercase(),
            style = KptTheme.typography.labelMedium,
            color = KptTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "£${form.amount}",
            style = KptTheme.typography.displaySmall,
            fontWeight = FontWeight.Light,
            color = KptTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(top = KptTheme.spacing.sm)
                .testTag(VrpPaymentTestTags.REVIEW_AMOUNT),
        )
        Text(
            text = stringResource(Res.string.feature_vrp_payment_to, form.payeeName),
            style = KptTheme.typography.titleMedium,
            color = KptTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = KptTheme.spacing.md),
        )

    }
}

@Composable
private fun Sending() {
    Box(
        modifier = Modifier.fillMaxSize().testTag(VrpPaymentTestTags.REVIEW_PAGE),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun Sent(form: PaymentFormUi, outcome: SubmissionUi.Sent) {
    Outcome(
        badgeColour = KptTheme.colorScheme.primaryContainer,
        iconTint = KptTheme.colorScheme.onPrimaryContainer,
        icon = Icons.Filled.Check,
        title = stringResource(Res.string.feature_vrp_payment_review_sent_title),
        body = if (outcome.settled) {
            stringResource(Res.string.feature_vrp_payment_review_settled)
        } else {
            stringResource(Res.string.feature_vrp_payment_review_in_progress)
        },
        amount = form.amount,
        stateTag = VrpPaymentTestTags.SENT_STATE,
    )
}

@Composable
private fun Failed(form: PaymentFormUi, outcome: SubmissionUi.Failed) {
    Outcome(
        badgeColour = KptTheme.colorScheme.errorContainer,
        iconTint = KptTheme.colorScheme.onErrorContainer,
        icon = Icons.Filled.PriorityHigh,
        title = stringResource(Res.string.feature_vrp_payment_review_failed_title),
        body = outcome.kind.message(form),
        amount = form.amount,
        stateTag = VrpPaymentTestTags.FAILED_STATE,
        bodyTag = VrpPaymentTestTags.FAILED_BODY,
        supportReference = outcome.supportReference,
    )
}

@Composable
private fun PaymentFailureKind.message(form: PaymentFormUi): String = when (this) {
    PaymentFailureKind.OverLimit -> stringResource(
        Res.string.feature_vrp_payment_review_error_limit,
        periodLabel(form.periodType),
    )

    PaymentFailureKind.ConsentUnusable ->
        stringResource(Res.string.feature_vrp_payment_review_error_unusable)

    PaymentFailureKind.NeedsReauthorisation ->
        stringResource(Res.string.feature_vrp_payment_review_error_reauthorise)

    PaymentFailureKind.Rejected -> stringResource(Res.string.feature_vrp_payment_review_error_rejected)

    PaymentFailureKind.Unconfirmed ->
        stringResource(Res.string.feature_vrp_payment_review_error_unconfirmed)

    PaymentFailureKind.NetworkUnavailable ->
        stringResource(Res.string.feature_vrp_payment_review_error_network)
}

@Composable
private fun Outcome(
    badgeColour: androidx.compose.ui.graphics.Color,
    iconTint: androidx.compose.ui.graphics.Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    amount: String,
    stateTag: String,
    bodyTag: String? = null,
    supportReference: String = "",
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(KptTheme.spacing.md)
            .testTag(stateTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(BadgeSize).clip(CircleShape).background(badgeColour),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(BadgeIconSize),
            )
        }

        Text(
            text = "£$amount",
            style = KptTheme.typography.headlineLarge,
            fontWeight = FontWeight.Light,
            color = KptTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = KptTheme.spacing.lg),
        )
        Text(
            text = title,
            style = KptTheme.typography.titleLarge,
            color = KptTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = KptTheme.spacing.sm),
        )
        Text(
            text = body,
            style = KptTheme.typography.bodyMedium,
            color = KptTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = KptTheme.spacing.md)
                .then(bodyTag?.let { Modifier.testTag(it) } ?: Modifier),
        )

        if (supportReference.isNotBlank()) {
            Text(
                text = stringResource(
                    Res.string.feature_vrp_payment_review_support_reference,
                    supportReference,
                ),
                style = KptTheme.typography.bodySmall,
                color = KptTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = KptTheme.spacing.md)
                    .testTag(VrpPaymentTestTags.SUPPORT_REFERENCE),
            )
        }
    }
}
