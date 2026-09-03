/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.settings.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.mifosx.openbanking.core.data.openSourceLicence.OpenSourceLicenceRepo
import template.core.base.common.screen.ScreenState
import template.core.base.common.screen.emptyIfContent
import template.core.base.ui.viewmodel.BaseViewModel

/** Holds the app's open-source licence text and the state of reading it. */
class LicencesViewModel(
    repository: OpenSourceLicenceRepo,
) : BaseViewModel<LicencesState, Nothing, LicencesAction>(
    initialState = LicencesState(dialogState = LicencesState.DialogState.Loading),
) {

    /** The stream this screen renders, scoped to this view model. */
    private val stream = repository.getLicence(viewModelScope)

    init {
        stream.state
            .emptyIfContent { licence -> licence.isBlank() }
            .onEach { screenState -> updateState { screenState.toState() } }
            .launchIn(viewModelScope)
    }

    override fun handleAction(action: LicencesAction) {
        when (action) {
            LicencesAction.RetryLoad -> stream.retry()
        }
    }

    private fun ScreenState<String>.toState(): LicencesState = when (this) {
        is ScreenState.Content -> LicencesState(licence = data)
        ScreenState.Loading -> LicencesState(dialogState = LicencesState.DialogState.Loading)
        is ScreenState.NoNetwork -> errorState(message = null, isNetworkError = true)
        is ScreenState.Error -> errorState(message = error.message, isNetworkError = false)
        ScreenState.Unauthenticated -> errorState(message = null, isNetworkError = false)
        ScreenState.Empty -> errorState(message = null, isNetworkError = false)
    }

    private fun errorState(message: String?, isNetworkError: Boolean): LicencesState =
        LicencesState(
            dialogState = LicencesState.DialogState.Error(
                message = message,
                isNetworkError = isNetworkError,
            ),
        )
}
