/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.vrpcallback

import org.mifosx.openbanking.feature.vrpcallback.callback.CallbackErrorKind
import org.mifosx.openbanking.feature.vrpcallback.callback.CallbackStage
import org.mifosx.openbanking.feature.vrpcallback.callback.VrpCallbackState
import org.mifosx.openbanking.feature.vrpcallback.callback.VrpCallbackUiState

/** Fixtures shared by the callback unit, UI and screenshot suites. */
object VrpCallbackFixtures {

    const val CONSENT_ID = "45411"
    const val REDIRECT_URL = "org.mifosx.openbanking://callback/#code=auth-code-1&state=s&id_token=t"
    const val SUPPORT_REFERENCE = "9b7e4d20-1a6c-4f88-9d3a-2c5b7e10f4a6"

    fun state(uiState: VrpCallbackUiState) =
        VrpCallbackState(redirectUrl = REDIRECT_URL, uiState = uiState)

    fun workingState(stage: CallbackStage = CallbackStage.Exchanging) =
        state(VrpCallbackUiState.Working(stage))

    fun successState() = state(VrpCallbackUiState.Success(CONSENT_ID, "Sarah Chen"))

    fun unusableState() = state(VrpCallbackUiState.Unusable(CONSENT_ID))

    fun failedState(
        kind: CallbackErrorKind,
        supportReference: String = "",
    ) = state(VrpCallbackUiState.Failed(kind, supportReference))
}
