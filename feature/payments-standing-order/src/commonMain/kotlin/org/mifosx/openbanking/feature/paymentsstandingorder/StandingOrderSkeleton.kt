/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.paymentsstandingorder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.Res
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_loading_a11y
import template.core.base.designsystem.component.KptShimmerLoadingBox

private val HeadingHeight = 20.dp
private val HeadingWidth = 220.dp
private val PickerListHeight = 144.dp
private val CreditorListHeight = 216.dp
private val DateRowHeight = 56.dp

/**
 * Loading state.
 *
 * Shaped like the form it replaces — heading, payer list, heading, payee list, heading, date row —
 * so nothing jumps when the accounts and payees land. The date row is included even though it needs
 * no data: leaving it out would let everything below it move up on the way in, and the amount field
 * is what ends up under the customer's finger.
 *
 * The mockups draw a centred spinner for this state. Every other screen in this app uses a shimmer
 * skeleton and the convention wins — a spinner here would be the one screen that loads differently
 * from the rest.
 */
@Composable
internal fun StandingOrderSkeleton(modifier: Modifier = Modifier) {
    val description = stringResource(Res.string.feature_payments_standing_order_loading_a11y)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(ScreenPadding)
            .testTag(StandingOrderTestTags.SKELETON)
            .semantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(SectionGap),
    ) {
        KptShimmerLoadingBox(
            modifier = Modifier.width(HeadingWidth).height(HeadingHeight),
        )
        KptShimmerLoadingBox(
            modifier = Modifier.fillMaxWidth().height(PickerListHeight),
        )
        KptShimmerLoadingBox(
            modifier = Modifier.width(HeadingWidth).height(HeadingHeight),
        )
        KptShimmerLoadingBox(
            modifier = Modifier.fillMaxWidth().height(CreditorListHeight),
        )
        KptShimmerLoadingBox(
            modifier = Modifier.width(HeadingWidth).height(HeadingHeight),
        )
        KptShimmerLoadingBox(
            modifier = Modifier.fillMaxWidth().height(DateRowHeight),
        )
    }
}
