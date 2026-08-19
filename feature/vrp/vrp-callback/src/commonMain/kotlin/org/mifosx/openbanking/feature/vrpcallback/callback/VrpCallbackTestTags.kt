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

/**
 * Node tags for the return from the bank.
 *
 * Append-only: renaming or removing one a suite references makes that suite silently stop asserting
 * what it claims to.
 */
internal object VrpCallbackTestTags {
    const val WORKING = "vrpCallback:working"
    const val WORKING_MESSAGE = "vrpCallback:workingMessage"
    const val SUCCESS = "vrpCallback:success"
    const val SUCCESS_BODY = "vrpCallback:successBody"
    const val UNUSABLE = "vrpCallback:unusable"
    const val FAILED = "vrpCallback:failed"
    const val FAILED_BODY = "vrpCallback:failedBody"
    const val SUPPORT_REFERENCE = "vrpCallback:supportReference"
    const val DISMISS_BUTTON = "vrpCallback:dismissButton"
    const val START_AGAIN_BUTTON = "vrpCallback:startAgainButton"
}
