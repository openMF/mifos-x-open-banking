/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import template.core.base.designsystem.theme.KptTheme

/** The uppercased label above a section of a screen. */
@Composable
fun MifosSectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = KptTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = KptTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
