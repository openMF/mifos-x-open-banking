/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.settings

import org.mifosx.openbanking.core.model.user.DarkThemeConfig
import org.mifosx.openbanking.feature.settings.ui.LicencesState
import org.mifosx.openbanking.feature.settings.ui.SettingsState

/** The settings states the suites render, shared by the view-model and Compose suites. */
object SettingsFixtures {

    /** Rows the screen offers: consents, privacy, licences. The theme picker is cards, not rows. */
    const val EXPECTED_ROW_COUNT: Int = 3

    /** Appearance, Account, About & Legal. */
    const val EXPECTED_SECTION_COUNT: Int = 3

    fun contentState(
        themeConfig: DarkThemeConfig = DarkThemeConfig.FOLLOW_SYSTEM,
    ): SettingsState = SettingsState(themeConfig = themeConfig)
}

/** The licences states the suites render. */
object LicencesFixtures {

    /** Stand-in licence text; the real one is fetched from the project repository. */
    const val LICENCE_TEXT: String = "Mozilla Public License Version 2.0"

    fun contentState(licence: String = LICENCE_TEXT): LicencesState =
        LicencesState(licence = licence)

    fun loadingState(): LicencesState =
        LicencesState(dialogState = LicencesState.DialogState.Loading)

    fun errorState(
        message: String? = "Something went wrong.",
        isNetworkError: Boolean = false,
    ): LicencesState = LicencesState(
        dialogState = LicencesState.DialogState.Error(
            message = message,
            isNetworkError = isNetworkError,
        ),
    )
}
