/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.paymentshub

/**
 * Stable tags the UI suites drive this screen by.
 *
 * Every tag here is applied to something. The screen has no account-chip row and no activity list
 * separate from the column that also holds the quick actions, so tags for those were removed rather
 * than left declared against nothing — a tag no composable emits reads as coverage that does not
 * exist, which is how the two quick-action assertions came to fail from the day they were written.
 */
internal object PaymentsHubTestTags {
    const val QUICK_ACTIONS_GRID = "paymentsHub:quickActionsGrid"
    const val QUICK_ACTION_SEND_MONEY = "paymentsHub:quickActionSendMoney"
    const val QUICK_ACTION_SCHEDULE = "paymentsHub:quickActionSchedule"
    const val QUICK_ACTION_STANDING_ORDER = "paymentsHub:quickActionStandingOrder"

    /** The fourth card is "Variable Recurring Payments"; it was named for a rail it never showed. */
    const val QUICK_ACTION_VRP = "paymentsHub:quickActionVrp"
}
