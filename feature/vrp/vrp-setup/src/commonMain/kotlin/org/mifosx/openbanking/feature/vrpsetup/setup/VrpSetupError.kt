/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.vrpsetup.setup

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import org.mifosx.openbanking.core.ui.components.MifosFilledPillButton
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.Res
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_error_body
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_error_title
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_retry

/** The accounts could not be read. Retriable. */
@Composable
internal fun VrpSetupError(onRetry: () -> Unit) {
    VrpSetupMessagePage(
        title = stringResource(Res.string.feature_vrp_setup_error_title),
        body = stringResource(Res.string.feature_vrp_setup_error_body),
        stateTag = VrpSetupTestTags.ERROR_STATE,
        action = {
            MifosFilledPillButton(
                label = stringResource(Res.string.feature_vrp_setup_retry),
                onClick = onRetry,
                testTag = VrpSetupTestTags.RETRY_BUTTON,
            )
        },
    )
}
