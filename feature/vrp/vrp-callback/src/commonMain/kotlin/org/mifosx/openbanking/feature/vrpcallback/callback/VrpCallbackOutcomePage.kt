/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.vrpcallback.callback

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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.mifosx.openbanking.feature.vrpcallback.generated.resources.Res
import org.mifosx.openbanking.feature.vrpcallback.generated.resources.feature_vrp_callback_failed_confirmation
import org.mifosx.openbanking.feature.vrpcallback.generated.resources.feature_vrp_callback_failed_expired
import org.mifosx.openbanking.feature.vrpcallback.generated.resources.feature_vrp_callback_failed_invalid
import org.mifosx.openbanking.feature.vrpcallback.generated.resources.feature_vrp_callback_failed_network
import org.mifosx.openbanking.feature.vrpcallback.generated.resources.feature_vrp_callback_failed_not_saved
import org.mifosx.openbanking.feature.vrpcallback.generated.resources.feature_vrp_callback_failed_title
import org.mifosx.openbanking.feature.vrpcallback.generated.resources.feature_vrp_callback_success_body
import org.mifosx.openbanking.feature.vrpcallback.generated.resources.feature_vrp_callback_success_title
import org.mifosx.openbanking.feature.vrpcallback.generated.resources.feature_vrp_callback_support_reference
import org.mifosx.openbanking.feature.vrpcallback.generated.resources.feature_vrp_callback_unusable_body
import org.mifosx.openbanking.feature.vrpcallback.generated.resources.feature_vrp_callback_unusable_title
import template.core.base.designsystem.theme.KptTheme

private val BadgeSize = 96.dp
private val BadgeIconSize = 48.dp

/** The VRP is set up. */
@Composable
internal fun VrpCallbackSuccessPage(payeeName: String) {
    OutcomePage(
        icon = Icons.Filled.Check,
        badgeColour = KptTheme.colorScheme.primaryContainer,
        iconTint = KptTheme.colorScheme.onPrimaryContainer,
        title = stringResource(Res.string.feature_vrp_callback_success_title),
        body = stringResource(Res.string.feature_vrp_callback_success_body, payeeName),
        stateTag = VrpCallbackTestTags.SUCCESS,
        bodyTag = VrpCallbackTestTags.SUCCESS_BODY,
    )
}

/** Authorised and stored, but nothing can ever be paid under it. */
@Composable
internal fun VrpCallbackUnusablePage() {
    OutcomePage(
        icon = Icons.Filled.Info,
        badgeColour = KptTheme.colorScheme.surfaceVariant,
        iconTint = KptTheme.colorScheme.onSurfaceVariant,
        title = stringResource(Res.string.feature_vrp_callback_unusable_title),
        body = stringResource(Res.string.feature_vrp_callback_unusable_body),
        stateTag = VrpCallbackTestTags.UNUSABLE,
    )
}

/**
 * The return did not finish, named by the step that failed.
 *
 * A read-back failure keeps the success title and the calm tone: the VRP is set up, and only its
 * details could not be loaded.
 */
@Composable
internal fun VrpCallbackFailedPage(
    kind: CallbackErrorKind,
    supportReference: String,
) {
    val usable = kind.leavesAUsableConsent

    OutcomePage(
        icon = if (usable) Icons.Filled.Check else Icons.Filled.PriorityHigh,
        badgeColour = if (usable) {
            KptTheme.colorScheme.primaryContainer
        } else {
            KptTheme.colorScheme.errorContainer
        },
        iconTint = if (usable) {
            KptTheme.colorScheme.onPrimaryContainer
        } else {
            KptTheme.colorScheme.onErrorContainer
        },
        title = if (usable) {
            stringResource(Res.string.feature_vrp_callback_success_title)
        } else {
            stringResource(Res.string.feature_vrp_callback_failed_title)
        },
        body = kind.message(),
        stateTag = VrpCallbackTestTags.FAILED,
        bodyTag = VrpCallbackTestTags.FAILED_BODY,
        supportReference = supportReference,
    )
}

@Composable
private fun CallbackErrorKind.message(): String = when (this) {
    CallbackErrorKind.CallbackInvalid -> stringResource(Res.string.feature_vrp_callback_failed_invalid)
    CallbackErrorKind.CodeExpiredOrUsed -> stringResource(Res.string.feature_vrp_callback_failed_expired)
    CallbackErrorKind.AuthorityNotSaved -> stringResource(Res.string.feature_vrp_callback_failed_not_saved)
    CallbackErrorKind.ConfirmationFailed ->
        stringResource(Res.string.feature_vrp_callback_failed_confirmation)

    CallbackErrorKind.NetworkUnavailable -> stringResource(Res.string.feature_vrp_callback_failed_network)
}

@Composable
private fun OutcomePage(
    icon: ImageVector,
    badgeColour: Color,
    iconTint: Color,
    title: String,
    body: String,
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
            text = title,
            style = KptTheme.typography.headlineLarge,
            color = KptTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = KptTheme.spacing.lg),
        )
        Text(
            text = body,
            style = KptTheme.typography.bodyLarge,
            color = KptTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = KptTheme.spacing.md)
                .then(bodyTag?.let { Modifier.testTag(it) } ?: Modifier),
        )

        if (supportReference.isNotBlank()) {
            Text(
                text = stringResource(Res.string.feature_vrp_callback_support_reference, supportReference),
                style = KptTheme.typography.bodySmall,
                color = KptTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = KptTheme.spacing.md)
                    .testTag(VrpCallbackTestTags.SUPPORT_REFERENCE),
            )
        }
    }
}
