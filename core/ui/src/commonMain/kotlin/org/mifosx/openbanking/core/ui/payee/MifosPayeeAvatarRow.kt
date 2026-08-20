/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.ui.payee

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAddAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.mifosx.openbanking.core.ui.generated.resources.Res
import org.mifosx.openbanking.core.ui.generated.resources.core_ui_payee_pay_new
import template.core.base.designsystem.component.KptShimmerLoadingBox
import template.core.base.designsystem.theme.KptTheme

private val AvatarSize = 56.dp
private val AvatarRing = 2.dp
private val AvatarRingGap = 4.dp

/** The ring is drawn in reserved space, so selecting cannot resize an avatar and reflow the row. */
private val SlotSize = AvatarSize + (AvatarRingGap + AvatarRing) * 2

/** Wide enough for the caption's two lines without widening the row. */
private val CaptionWidth = SlotSize + 8.dp

private const val DASH_ON = 6f
private const val DASH_OFF = 5f

/** How many lines a payee's name wraps across before it is elided. */
private const val CAPTION_LINES = 2

/** How many placeholder avatars stand in while the payees load. */
private const val LOADING_AVATARS = 3

/**
 * One payee the row offers.
 *
 * @property payeeId The value the row reports on select.
 * @property shortName The caption, already shortened to fit two lines.
 * @property initials Up to two letters for the avatar; see [initialsOf].
 */
data class MifosPayeeOption(
    val payeeId: String,
    val shortName: String,
    val initials: String,
)

/**
 * The payees, as a horizontal row of avatars.
 *
 * "Pay new" leads rather than trails, so the escape from an empty list is the first thing under the
 * heading rather than the last thing after a scroll.
 *
 * @param payNewSelected Whether "Pay new" is itself the current choice, which it is while a new
 *   payee is being typed.
 * @param loading Replaces the payees with placeholders; "Pay new" stays usable throughout.
 * @param payeeTestTag Applied per payee, so a caller's own suite can address one avatar.
 */
@Composable
fun MifosPayeeAvatarRow(
    payees: List<MifosPayeeOption>,
    selectedId: String?,
    payNewSelected: Boolean,
    onSelect: (String) -> Unit,
    onPayNew: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    loadingContentDescription: String = "",
    payNewTestTag: String = PAY_NEW_TAG,
    loadingTestTag: String = LOADING_TAG,
    payeeTestTag: (String) -> String = ::payeeAvatarTag,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(KptTheme.spacing.md),
    ) {
        PayNewAvatar(selected = payNewSelected, onClick = onPayNew, testTag = payNewTestTag)

        if (loading) {
            LoadingAvatars(
                contentDescription = loadingContentDescription,
                testTag = loadingTestTag,
            )
            return@Row
        }

        payees.forEach { payee ->
            PayeeAvatar(
                payee = payee,
                selected = payee.payeeId == selectedId,
                onClick = { onSelect(payee.payeeId) },
                testTag = payeeTestTag(payee.payeeId),
            )
        }
    }
}

@Composable
private fun PayNewAvatar(
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    val outline = KptTheme.colorScheme.outline

    AvatarColumn(
        caption = stringResource(Res.string.core_ui_payee_pay_new),
        selected = selected,
        modifier = Modifier
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .testTag(testTag),
    ) {
        Box(
            modifier = Modifier.size(AvatarSize),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(AvatarSize)) {
                drawCircle(
                    color = outline,
                    style = Stroke(
                        width = AvatarRing.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH_ON, DASH_OFF)),
                    ),
                )
            }
            Icon(
                imageVector = Icons.Filled.PersonAddAlt,
                contentDescription = null,
                tint = KptTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PayeeAvatar(
    payee: MifosPayeeOption,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    AvatarColumn(
        caption = payee.shortName,
        selected = selected,
        modifier = Modifier
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .testTag(testTag),
    ) {
        Box(
            modifier = Modifier
                .size(AvatarSize)
                .clip(CircleShape)
                .background(
                    if (selected) {
                        KptTheme.colorScheme.primaryContainer
                    } else {
                        KptTheme.colorScheme.surfaceVariant
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = payee.initials,
                style = KptTheme.typography.titleMedium,
                color = if (selected) {
                    KptTheme.colorScheme.onPrimaryContainer
                } else {
                    KptTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/** Placeholders in the avatars' own footprint, so the row does not jump when the payees land. */
@Composable
private fun LoadingAvatars(contentDescription: String, testTag: String) {
    repeat(LOADING_AVATARS) { index ->
        Column(
            modifier = Modifier
                .width(CaptionWidth)
                .then(if (index == 0) Modifier.testTag(testTag) else Modifier)
                .semantics { this.contentDescription = contentDescription },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.size(SlotSize), contentAlignment = Alignment.Center) {
                KptShimmerLoadingBox(modifier = Modifier.size(AvatarSize).clip(CircleShape))
            }
        }
    }
}

/** One slot: the avatar in its reserved ring space, with the caption beneath. */
@Composable
private fun AvatarColumn(
    caption: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    avatar: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.width(CaptionWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(SlotSize)
                .then(
                    if (selected) {
                        Modifier.border(AvatarRing, KptTheme.colorScheme.primary, CircleShape)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            avatar()
        }
        Text(
            text = caption,
            style = KptTheme.typography.bodySmall,
            color = if (selected) {
                KptTheme.colorScheme.onSurface
            } else {
                KptTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
            maxLines = CAPTION_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = KptTheme.spacing.xs),
        )
    }
}

private const val PAY_NEW_TAG = "mifosPayeeRow:payNew"
private const val LOADING_TAG = "mifosPayeeRow:loading"

/** The default per-payee tag, matching [accountPickerRowTag]'s shape. */
fun payeeAvatarTag(payeeId: String): String = "mifosPayeeRow:avatar:$payeeId"
