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

/**
 * Screen state for the open-source licences screen.
 *
 * @property licence The licence text, empty until it has been read.
 * @property dialogState The load state overlaying the licence; `null` while the text is showing.
 */
data class LicencesState(
    val licence: String = "",
    val dialogState: DialogState? = null,
) {
    sealed interface DialogState {
        /** The licence is being read. */
        data object Loading : DialogState

        /**
         * The licence could not be read.
         *
         * @property message The failure's own message; `null` when the screen should show its
         *   default wording.
         * @property isNetworkError Whether the device was offline.
         */
        data class Error(val message: String?, val isNetworkError: Boolean) : DialogState
    }
}

/** Actions the view model owns. Navigation is the screen's lambda, not routed here. */
sealed interface LicencesAction {
    /** Read the licence again after a failure. */
    data object RetryLoad : LicencesAction
}
