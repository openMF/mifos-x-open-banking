/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.vrpsetup

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.mifosx.openbanking.core.common.parseMinorUnits
import kotlin.time.Clock
import kotlin.time.Instant

/** The soonest end date the bank accepts, counted in days from today. */
private const val MIN_DAYS_AHEAD = 2

/**
 * How many years ahead the picker offers.
 *
 * A presentation bound, not a bank rule: no ceiling on `ValidToDateTime` has been observed, so this
 * only keeps the calendar navigable.
 */
private const val MAX_YEARS_AHEAD = 10

/** The identification the bank requires: a six-digit sort code and an eight-digit account number. */
private const val SORT_CODE_LENGTH = 6
private const val ACCOUNT_NUMBER_LENGTH = 8

/** The smallest amount the bank accepts, in pence. */
private const val MIN_AMOUNT_MINOR = 1L

/** Why an entered amount cannot be used. */
enum class AmountProblem {
    Missing,
    NotANumber,
    BelowMinimum,

    /** The per-payment ceiling is not below the periodic one, which the bank requires. */
    NotBelowPeriodic,
}

/** Why an entered payee cannot be used. */
enum class PayeeProblem {
    NameMissing,
    SortCodeIncomplete,
    AccountNumberIncomplete,

    /** The payee is the account paying, which the bank refuses. */
    SameAsPayer,
}

/**
 * Today in UTC.
 *
 * The bank drops the offset from a validity date without converting it, so the calendar date written
 * here is the one it uses. Reading the device zone would offer a date the bank treats as past.
 */
fun todayUtc(now: Instant = Clock.System.now()): LocalDate =
    now.toLocalDateTime(TimeZone.UTC).date

/** The soonest end date selectable on [today]. */
fun earliestEndDate(today: LocalDate): LocalDate = today.plus(MIN_DAYS_AHEAD, DateTimeUnit.DAY)

/** Whether [date] is far enough ahead to be accepted on [today]. */
fun isSelectableEndDate(date: LocalDate, today: LocalDate): Boolean = date >= earliestEndDate(today)

/**
 * The date [epochMillis] falls on, read in UTC.
 *
 * The picker hands out UTC millis by contract. Reading them in the device zone resolves east of UTC
 * to the following calendar day, which would offer a date the bank refuses.
 */
fun utcDateOf(epochMillis: Long): LocalDate =
    Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.UTC).date

/** The inverse, for seeding a picker with a date already chosen. */
fun epochMillisOf(date: LocalDate): Long = date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

/**
 * The years an end date may fall in, counted from [today].
 *
 * Left unbounded, the picker pages through decades in which every day is disabled, which reads as a
 * broken calendar rather than a bounded one.
 */
fun selectableEndYears(today: LocalDate): IntRange = today.year..(today.year + MAX_YEARS_AHEAD)

/**
 * Checks one ceiling on its own.
 *
 * @return null when the amount is usable.
 */
fun checkAmount(raw: String): AmountProblem? {
    val minorUnits = parseMinorUnits(raw)
    return when {
        raw.isBlank() -> AmountProblem.Missing
        minorUnits == null -> AmountProblem.NotANumber
        minorUnits < MIN_AMOUNT_MINOR -> AmountProblem.BelowMinimum
        else -> null
    }
}

/**
 * Checks the per-payment ceiling against the periodic one.
 *
 * The bank requires the per-payment ceiling to be strictly below the periodic ceiling. Checked as a
 * pair, so editing either half cannot leave the two in a state the bank will refuse.
 *
 * @return null when the pair is usable, or when either half is unreadable and has its own problem.
 */
fun checkOrdering(perPayment: String, periodic: String): AmountProblem? {
    val perPaymentMinor = parseMinorUnits(perPayment)
    val periodicMinor = parseMinorUnits(periodic)
    return when {
        perPaymentMinor == null || periodicMinor == null -> null
        perPaymentMinor >= periodicMinor -> AmountProblem.NotBelowPeriodic
        else -> null
    }
}

/** Checks a name typed for a new payee. */
fun checkPayeeName(name: String): PayeeProblem? =
    if (name.isBlank()) PayeeProblem.NameMissing else null

/** Checks a sort code typed for a new payee. */
fun checkSortCode(sortCode: String): PayeeProblem? =
    if (sortCode.digitsOnly().length != SORT_CODE_LENGTH) PayeeProblem.SortCodeIncomplete else null

/** Checks an account number typed for a new payee. */
fun checkAccountNumber(accountNumber: String): PayeeProblem? =
    if (accountNumber.digitsOnly().length != ACCOUNT_NUMBER_LENGTH) {
        PayeeProblem.AccountNumberIncomplete
    } else {
        null
    }

/**
 * Checks that the payee is not the account paying.
 *
 * Only answerable when the payer is named here. A payer the customer will choose at the bank is
 * unknown until the callback reads the consent back, so this cannot fire for it.
 */
fun checkPayeeDiffersFromPayer(
    payeeIdentification: String,
    payerIdentification: String?,
): PayeeProblem? {
    if (payerIdentification.isNullOrBlank()) return null
    return if (payeeIdentification.digitsOnly() == payerIdentification.digitsOnly()) {
        PayeeProblem.SameAsPayer
    } else {
        null
    }
}

/** The identification the bank expects: the sort code and account number, digits only. */
fun combinedIdentification(sortCode: String, accountNumber: String): String =
    sortCode.digitsOnly() + accountNumber.digitsOnly()

private fun String.digitsOnly(): String = filter { it.isDigit() }
