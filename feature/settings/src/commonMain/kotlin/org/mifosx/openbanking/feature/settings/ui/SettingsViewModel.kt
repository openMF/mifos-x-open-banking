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
import kotlinx.coroutines.launch
import org.mifosx.openbanking.core.data.user.UserDataRepository
import org.mifosx.openbanking.core.model.user.DarkThemeConfig
import template.core.base.ui.viewmodel.BaseViewModel

/** Holds the theme preference and the theme picker's open state for the settings hub. */
class SettingsViewModel(
    private val userDataRepository: UserDataRepository,
) : BaseViewModel<SettingsState, Nothing, SettingsAction>(
    initialState = SettingsState(themeConfig = DarkThemeConfig.FOLLOW_SYSTEM),
) {
    init {
        observeUserPreferences()
    }

    override fun handleAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.SelectTheme -> selectTheme(action.config)
        }
    }

    private fun selectTheme(config: DarkThemeConfig) {
        viewModelScope.launch { userDataRepository.setDarkThemeConfig(config) }
    }

    private fun observeUserPreferences() {
        viewModelScope.launch {
            userDataRepository.observeDarkThemeConfig.collect { theme ->
                updateState { copy(themeConfig = theme) }
            }
        }
    }
}
