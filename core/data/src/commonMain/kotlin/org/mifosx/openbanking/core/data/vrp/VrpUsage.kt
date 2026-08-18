/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.data.vrp

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.mifosx.openbanking.core.model.banking.payment.PaymentDisposition
import org.mifosx.openbanking.core.model.vrp.Money
import org.mifosx.openbanking.core.model.vrp.PeriodType
import org.mifosx.openbanking.core.model.vrp.PeriodUsage
import org.mifosx.openbanking.core.model.vrp.VrpConsent
import org.mifosx.openbanking.core.model.vrp.VrpPayment
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * How much of each of [consent]'s limits the current period has used.
 *
 * Only settled payments count. Periods run from the consent's creation date and repeat, so the
 * current window is found by advancing whole periods from that date, in UTC.
 *
 * @param payments Payments recorded against this consent.
 * @return One entry per limit, in the order the consent holds them.
 */
fun currentUsage(
    consent: VrpConsent,
    payments: List<VrpPayment>,
): List<PeriodUsage> {
    val now = Clock.System.now()
    val settled = payments.filter {
        it.consentId == consent.consentId &&
            it.status.disposition == PaymentDisposition.TerminalSuccess
    }

    return consent.controlParameters.periodicLimits.map { limit ->
        val periodStart = currentPeriodStart(consent.createdAt, limit.periodType, now)
        val consumed = settled
            .filter { it.createdAt >= periodStart }
            .sumOf { it.amount.minorUnits }

        PeriodUsage(
            limit = limit,
            consumed = Money(minorUnits = consumed, currency = limit.amount.currency),
            remaining = Money(
                minorUnits = (limit.amount.minorUnits - consumed).coerceAtLeast(0),
                currency = limit.amount.currency,
            ),
            periodStart = periodStart,
        )
    }
}

/** The start of the [periodType] window containing [now], counting from [createdAt]. */
private fun currentPeriodStart(
    createdAt: Instant,
    periodType: PeriodType,
    now: Instant,
): Instant {
    if (createdAt >= now) return createdAt

    val zone = TimeZone.UTC
    val created = createdAt.toLocalDateTime(zone)
    val timeOfDay = created.time
    val length = periodType.length()

    var date = created.date
    var start = createdAt
    while (true) {
        val nextDate = date.plus(length)
        val next = nextDate.atTime(timeOfDay).toInstant(zone)
        if (next > now) break
        date = nextDate
        start = next
    }
    return start
}

private fun PeriodType.length(): DatePeriod = when (this) {
    PeriodType.Day -> DatePeriod(days = 1)
    PeriodType.Week -> DatePeriod(days = 7)
    PeriodType.Fortnight -> DatePeriod(days = 14)
    PeriodType.Month -> DatePeriod(months = 1)
    PeriodType.HalfYear -> DatePeriod(months = 6)
    PeriodType.Year -> DatePeriod(years = 1)
}
