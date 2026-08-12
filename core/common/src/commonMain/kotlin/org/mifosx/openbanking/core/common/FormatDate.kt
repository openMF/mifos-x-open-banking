/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.common

import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

private const val ISO_DATE_LENGTH = 10
private val MONTH_ABBREVIATIONS =
    listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/**
 * Renders the leading `yyyy-MM-dd` of an ISO-8601 timestamp as a short day/month label, e.g.
 * `"27 Jun"`. Falls back to the raw input when it is not a parseable ISO date.
 */
fun formatShortMonthDay(isoDateTime: String): String {
    val parts = isoDateTime.take(ISO_DATE_LENGTH).split('-')
    val day = parts.getOrNull(2)?.toIntOrNull()
    val month = parts.getOrNull(1)?.toIntOrNull()?.minus(1)?.let { MONTH_ABBREVIATIONS.getOrNull(it) }
    return if (isoDateTime.length >= ISO_DATE_LENGTH && day != null && month != null) {
        "$day $month"
    } else {
        isoDateTime
    }
}

/**
 * Renders the leading `yyyy-MM-dd` of an ISO-8601 value as a full date, e.g. `"14 Aug 2026"`.
 *
 * Date only, deliberately. It exists for values that ARE dates rather than instants — a scheduled
 * payment's execution date is carried on the wire as midnight UTC, so rendering it through
 * [formatDateTime] appends a `00:00` that means nothing to the customer and, worse, reads as a
 * precise time the bank never committed to. It also does not convert zones, because shifting a
 * date-only value into a local zone is what moves it to the day before.
 *
 * Falls back to the raw input when it will not parse, the same contract as the others here.
 */
fun formatIsoDate(isoDateTime: String): String {
    val parts = isoDateTime.take(ISO_DATE_LENGTH).split('-')
    val day = parts.getOrNull(2)?.toIntOrNull()
    val month = parts.getOrNull(1)?.toIntOrNull()?.minus(1)?.let { MONTH_ABBREVIATIONS.getOrNull(it) }
    val year = parts.getOrNull(0)?.toIntOrNull()
    return if (day != null && month != null && year != null) "$day $month $year" else isoDateTime
}

/**
 * Renders a full ISO-8601 timestamp as a readable local date and time, e.g. `"5 Aug 2026, 18:46"`.
 *
 * Converts to [timeZone] — the device's by default — so a bank offset such as `+00:00` reads as the
 * wall-clock time the customer was looking at. Falls back to the raw input when it will not parse:
 * the same contract as [formatShortMonthDay], and the right one for a value that comes from a bank,
 * where an unexpected format should still be legible on screen rather than blank or fatal.
 *
 * @param timeZone Overridable so a test can assert a fixed rendering instead of one that moves with
 *   the machine it runs on.
 */
fun formatDateTime(
    isoDateTime: String,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val instant = runCatching { Instant.parse(isoDateTime) }.getOrNull() ?: return isoDateTime
    val dateTime = instant.toLocalDateTime(timeZone)
    // Indexed rather than getOrNull: month numbers are 1..12 against twelve abbreviations, so a
    // miss would be a bug in this file, not a value a bank could send.
    val month = MONTH_ABBREVIATIONS[dateTime.month.number - 1]
    val hour = dateTime.hour.toString().padStart(2, '0')
    val minute = dateTime.minute.toString().padStart(2, '0')
    return "${dateTime.day} $month ${dateTime.year}, $hour:$minute"
}

/**
 * Renders an instant as a 24-hour local wall-clock time, e.g. `"18:46"`.
 *
 * For "last checked at" style stamps, where the date is implicitly today and only the time carries
 * information.
 */
fun formatTimeOfDay(
    instant: Instant,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val dateTime = instant.toLocalDateTime(timeZone)
    val hour = dateTime.hour.toString().padStart(2, '0')
    val minute = dateTime.minute.toString().padStart(2, '0')
    return "$hour:$minute"
}

fun formatDate(millis: Long): String {
    val dateTime = Instant
        .fromEpochMilliseconds(millis)
        .toLocalDateTime(TimeZone.currentSystemDefault())

    val day = dateTime.day.toString().padStart(2, '0')
    val month = dateTime.month.number.toString().padStart(2, '0')
    val year = dateTime.year
    return "$day/$month/$year"
}
