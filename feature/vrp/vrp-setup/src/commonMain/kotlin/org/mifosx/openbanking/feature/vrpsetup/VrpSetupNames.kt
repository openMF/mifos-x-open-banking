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

import org.mifosx.openbanking.core.common.formatIban
import org.mifosx.openbanking.core.common.formatSortCode

/**
 * How many characters of a payee's name the avatar caption holds before eliding the rest.
 *
 * The caption wraps to two lines, so this is the budget across both of them.
 */
private const val MAX_PAYEE_NAME = 20

/** The length of a UK sort code and account number together. */
private const val UK_IDENTIFICATION_LENGTH = 14

/** The leading digits of a UK identification that are the sort code. */
private const val SORT_CODE_LENGTH = 6

/**
 * Up to two letters from a payee's name, for an avatar.
 *
 * A single-word name falls back to its first two letters, so "Oakwood" gives "OA" rather than one
 * lonely letter.
 */
internal fun initialsOf(name: String): String {
    val words = name.split(' ').filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> ""
        words.size == 1 -> words.first().take(2).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}

/** A payee's name at the length the avatar caption holds, e.g. `Oakwood Pr…`. */
internal fun shortPayeeName(name: String): String =
    if (name.length <= MAX_PAYEE_NAME) name else name.take(MAX_PAYEE_NAME).trimEnd() + "…"

/**
 * A payee's account as the form shows it, e.g. `40-47-84 12345678`.
 *
 * Anything that is not a UK sort code and account number is grouped as an IBAN instead.
 */
internal fun formatPayeeIdentification(identification: String): String {
    val digits = identification.filter { it.isDigit() }
    return if (digits.length == UK_IDENTIFICATION_LENGTH) {
        formatSortCode(digits.take(SORT_CODE_LENGTH)) + " " + digits.drop(SORT_CODE_LENGTH)
    } else {
        formatIban(identification)
    }
}
