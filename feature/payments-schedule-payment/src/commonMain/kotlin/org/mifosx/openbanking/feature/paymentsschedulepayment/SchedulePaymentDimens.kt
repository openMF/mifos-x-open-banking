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

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import template.core.base.designsystem.theme.KptTheme

/** Spacing shared by the form page and the review page, so the two cannot drift apart. */
internal val ScreenPadding = 16.dp

/**
 * How wide the form is allowed to get.
 *
 * The same composition runs on a desktop window several times a phone's width, where a form stretched
 * edge to edge puts a 40-character reference field beside a 56dp avatar and reads as a spreadsheet.
 * `core/ui` carries no shared constraint to reuse — the only `widthIn` in it is the navigation rail's
 * minimum — so this is declared here rather than invented as a design-system component for one caller.
 */
internal val FormMaxWidth = 480.dp
internal val SectionGap = 16.dp
internal val HeadingGap = 8.dp
internal val RowGap = 12.dp
internal val HeroGap = 4.dp
internal val ChipCorner = 20.dp
internal val ChipPaddingHorizontal = 10.dp
internal val ChipPaddingVertical = 6.dp
internal val ChipGap = 8.dp
internal val ChipAvatarSize = 24.dp
internal val NoticeCorner = 12.dp
internal val NoticePadding = 12.dp
internal val NoticeGap = 10.dp
internal val NoticeIconSize = 16.dp

/** The payer picker and the amount card share a card shape, border and inner padding. */
internal val CardCorner = 12.dp
internal val CardBorder = 1.dp
internal val CardPadding = 16.dp
internal val GlyphSize = 40.dp

/** The payee scroller. 56dp avatars, with the selection ring drawn in reserved outer space. */
internal val AvatarSize = 56.dp
internal val AvatarRingGap = 4.dp
internal val AvatarRing = 2.dp
internal val AvatarGap = 16.dp
internal val AvatarBadgeSize = 18.dp

/** An uppercase section label. Shared for the same reason as the spacing above. */
@Composable
internal fun SectionHeading(text: String) {
    Text(
        text = text.uppercase(),
        style = KptTheme.typography.bodySmall,
        color = KptTheme.colorScheme.outline,
        letterSpacing = 0.8.sp,
    )
}
