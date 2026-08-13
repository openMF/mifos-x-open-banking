/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.paymentsstandingorder.ui

import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number

/**
 * Written out in full, weekday included — "Friday, 14 August 2026".
 *
 * The weekday is not decoration. A customer choosing a payment date is deciding when money leaves
 * their account, and "14 August" alone does not say whether that is a working day or a weekend; on
 * the international rail, where weekends are refused, the weekday is the field's own explanation.
 *
 * English names are hardcoded here, matching `core/common`'s `formatDateTime` and `formatShortMonthDay`
 * rather than introducing a second, inconsistent approach in one feature. Localising dates is a
 * project-wide change to that file, not something to start here.
 */
internal fun formatStandingOrderDate(date: LocalDate): String =
    "${WEEKDAY_NAMES[date.dayOfWeek.isoDayNumber - 1]}, " +
        "${date.day} ${MONTH_NAMES[date.month.number - 1]} ${date.year}"

private val WEEKDAY_NAMES = listOf(
    "Monday",
    "Tuesday",
    "Wednesday",
    "Thursday",
    "Friday",
    "Saturday",
    "Sunday",
)

private val MONTH_NAMES = listOf(
    "January",
    "February",
    "March",
    "April",
    "May",
    "June",
    "July",
    "August",
    "September",
    "October",
    "November",
    "December",
)

/**
 * The wire form — a bare ISO date, no time and no offset.
 *
 * The mapper appends a fixed midnight-UTC suffix to this, which is what makes the consent body and
 * the payment body identical by construction. Building a timestamp here instead would put a clock in
 * the path of a value that has to be reproducible.
 */
internal fun LocalDate.toIsoDate(): String =
    "$year-${month.number.pad()}-${day.pad()}"

private fun Int.pad(): String = toString().padStart(2, '0')
