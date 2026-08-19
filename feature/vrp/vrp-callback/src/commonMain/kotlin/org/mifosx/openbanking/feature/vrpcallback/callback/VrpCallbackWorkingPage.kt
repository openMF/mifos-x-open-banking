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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import org.jetbrains.compose.resources.stringResource
import org.mifosx.openbanking.feature.vrpcallback.generated.resources.Res
import org.mifosx.openbanking.feature.vrpcallback.generated.resources.feature_vrp_callback_confirming
import org.mifosx.openbanking.feature.vrpcallback.generated.resources.feature_vrp_callback_exchanging
import org.mifosx.openbanking.feature.vrpcallback.generated.resources.feature_vrp_callback_validating
import template.core.base.designsystem.theme.KptTheme

/**
 * Indeterminate progress, naming the step that is running.
 *
 * The step is named because the customer has already approved at their bank: a bare "please wait"
 * followed by a bare failure would leave them unable to tell whether anything was set up.
 */
@Composable
internal fun VrpCallbackWorkingPage(stage: CallbackStage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(KptTheme.spacing.md)
            .testTag(VrpCallbackTestTags.WORKING),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text(
            text = stage.message(),
            style = KptTheme.typography.titleMedium,
            color = KptTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = KptTheme.spacing.lg)
                .testTag(VrpCallbackTestTags.WORKING_MESSAGE),
        )
    }
}

@Composable
private fun CallbackStage.message(): String = when (this) {
    CallbackStage.Validating -> stringResource(Res.string.feature_vrp_callback_validating)
    CallbackStage.Exchanging -> stringResource(Res.string.feature_vrp_callback_exchanging)
    CallbackStage.Confirming -> stringResource(Res.string.feature_vrp_callback_confirming)
}
