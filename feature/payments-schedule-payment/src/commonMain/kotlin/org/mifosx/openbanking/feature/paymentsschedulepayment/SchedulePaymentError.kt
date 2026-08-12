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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.mifosx.openbanking.core.ui.components.MifosFilledPillButton
import org.mifosx.openbanking.core.ui.components.MifosTonalPillButton
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.Res
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_change_date
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_change_payer
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_edit_amount
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_error_consent_mismatch
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_error_consent_not_authorised
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_error_consent_revoked
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_error_date_refused
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_error_invalid_field
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_error_network
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_error_outside_limits
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_error_payer_not_supported
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_error_rate_limited
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_error_reference
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_error_request_malformed
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_error_scheme_not_supported
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_error_signature_missing
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_error_title
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_error_token_expired
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_reauthorise
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_retry
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_view_consents
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.SchedulePaymentErrorKind
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.isRetryable
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.needsAmountChange
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.needsDateChange
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.needsReauthorisation

private val ContentPadding = 24.dp
private val LineGap = 16.dp
private val IconWellSize = 96.dp
private val IconSize = 44.dp
private val IconBottomGap = 8.dp
private val BodyMaxWidth = 320.dp
private val ButtonTopGap = 8.dp

/**
 * The payment failed, and which recoveries appear depends on why.
 *
 * The four are not interchangeable and are never all shown at once: retrying a revoked consent will
 * never succeed, and re-authorising an insufficient balance does not add money to the account.
 * `SignatureMissing` deliberately offers nothing — it is a defect in this app, so every button
 * would be a false promise; the support reference is the only useful thing on screen.
 */
@Composable
internal fun SchedulePaymentError(
    kind: SchedulePaymentErrorKind,
    supportReference: String?,
    onRetry: () -> Unit,
    onReauthorise: () -> Unit,
    onViewConsents: () -> Unit,
    onEditAmount: () -> Unit,
    onChangePayer: () -> Unit,
    onChangeDate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(Res.string.feature_payments_schedule_payment_error_title)
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(ContentPadding)
            .testTag(SchedulePaymentTestTags.ERROR_STATE)
            .semantics { contentDescription = title },
        verticalArrangement = Arrangement.spacedBy(LineGap, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(IconWellSize)
                .padding(bottom = IconBottomGap)
                .background(color = MaterialTheme.colorScheme.errorContainer, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(IconSize),
            )
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Text(
            text = stringResource(kind.bodyResource()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = BodyMaxWidth),
        )

        if (supportReference != null) {
            Text(
                text = stringResource(Res.string.feature_payments_schedule_payment_error_reference, supportReference),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag(SchedulePaymentTestTags.ERROR_SUPPORT_REFERENCE),
            )
        }

        SchedulePaymentErrorActions(
            kind = kind,
            onRetry = onRetry,
            onReauthorise = onReauthorise,
            onViewConsents = onViewConsents,
            onEditAmount = onEditAmount,
            onChangePayer = onChangePayer,
            onChangeDate = onChangeDate,
        )
    }
}

@Composable
private fun SchedulePaymentErrorActions(
    kind: SchedulePaymentErrorKind,
    onRetry: () -> Unit,
    onReauthorise: () -> Unit,
    onViewConsents: () -> Unit,
    onEditAmount: () -> Unit,
    onChangePayer: () -> Unit,
    onChangeDate: () -> Unit,
) {
    if (kind.isRetryable) {
        MifosFilledPillButton(
            label = stringResource(Res.string.feature_payments_schedule_payment_retry),
            onClick = onRetry,
            modifier = Modifier.padding(top = ButtonTopGap),
            testTag = SchedulePaymentTestTags.RETRY_BUTTON,
        )
    }
    if (kind.needsReauthorisation) {
        MifosTonalPillButton(
            label = stringResource(Res.string.feature_payments_schedule_payment_reauthorise),
            onClick = onReauthorise,
            modifier = Modifier.padding(top = ButtonTopGap),
            testTag = SchedulePaymentTestTags.REAUTHORISE_BUTTON,
        )
    }
    if (kind == SchedulePaymentErrorKind.ConsentRevoked) {
        MifosTonalPillButton(
            label = stringResource(Res.string.feature_payments_schedule_payment_view_consents),
            onClick = onViewConsents,
            modifier = Modifier.padding(top = ButtonTopGap),
            testTag = SchedulePaymentTestTags.VIEW_CONSENTS_BUTTON,
        )
    }
    if (kind.needsAmountChange) {
        MifosTonalPillButton(
            label = stringResource(Res.string.feature_payments_schedule_payment_edit_amount),
            onClick = onEditAmount,
            modifier = Modifier.padding(top = ButtonTopGap),
            testTag = SchedulePaymentTestTags.EDIT_AMOUNT_BUTTON,
        )
    }
    // The one recovery the customer can carry out here without changing account or giving up. It
    // returns to the form rather than reopening the picker directly: the window has moved since the
    // date was chosen, and the field's helper text is where that is explained.
    if (kind.needsDateChange) {
        MifosFilledPillButton(
            label = stringResource(Res.string.feature_payments_schedule_payment_change_date),
            onClick = onChangeDate,
            modifier = Modifier.padding(top = ButtonTopGap),
            testTag = SchedulePaymentTestTags.CHANGE_DATE_BUTTON,
        )
    }
    // The refused account is already gone from the picker by the time this is tapped — the registry
    // removed it when the bank refused it — so the customer returns to a list they can succeed from.
    if (kind == SchedulePaymentErrorKind.PayerNotSupported) {
        MifosFilledPillButton(
            label = stringResource(Res.string.feature_payments_schedule_payment_change_payer),
            onClick = onChangePayer,
            modifier = Modifier.padding(top = ButtonTopGap),
            testTag = SchedulePaymentTestTags.CHANGE_PAYER_BUTTON,
        )
    }
}

private fun SchedulePaymentErrorKind.bodyResource(): StringResource = when (this) {
    SchedulePaymentErrorKind.SignatureMissing -> Res.string.feature_payments_schedule_payment_error_signature_missing
    SchedulePaymentErrorKind.ConsentNotAuthorised ->
        Res.string.feature_payments_schedule_payment_error_consent_not_authorised
    SchedulePaymentErrorKind.ConsentMismatch -> Res.string.feature_payments_schedule_payment_error_consent_mismatch
    SchedulePaymentErrorKind.OutsideControlParameters ->
        Res.string.feature_payments_schedule_payment_error_outside_limits
    SchedulePaymentErrorKind.InvalidField -> Res.string.feature_payments_schedule_payment_error_invalid_field
    SchedulePaymentErrorKind.ConsentRevoked -> Res.string.feature_payments_schedule_payment_error_consent_revoked
    SchedulePaymentErrorKind.TokenExpired -> Res.string.feature_payments_schedule_payment_error_token_expired
    SchedulePaymentErrorKind.RateLimited -> Res.string.feature_payments_schedule_payment_error_rate_limited
    SchedulePaymentErrorKind.DateRefused -> Res.string.feature_payments_schedule_payment_error_date_refused
    SchedulePaymentErrorKind.RequestMalformed -> Res.string.feature_payments_schedule_payment_error_request_malformed
    SchedulePaymentErrorKind.SchemeNotSupported ->
        Res.string.feature_payments_schedule_payment_error_scheme_not_supported
    SchedulePaymentErrorKind.PayerNotSupported -> Res.string.feature_payments_schedule_payment_error_payer_not_supported
    SchedulePaymentErrorKind.NetworkError -> Res.string.feature_payments_schedule_payment_error_network
}
