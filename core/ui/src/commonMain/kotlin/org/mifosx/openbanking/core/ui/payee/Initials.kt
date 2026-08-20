/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.ui.payee

/** Where an account's readable name stops and its masked identifier begins. */
private val IDENTIFIER_MARKERS = charArrayOf('·', '•')

/**
 * Up to two letters from the name, for an avatar.
 *
 * The trailing identifier is dropped first — an account reads "Current account ·· 3349", and
 * including the digits would render "C3" where the design asks for "CA". A single-word name falls
 * back to its first two letters so "Savings" gives "SA" rather than one lonely letter.
 */
fun initialsOf(name: String): String {
    val words = name.nameHalf()
        .split(' ')
        .filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> ""
        words.size == 1 -> words.first().take(2).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}

/**
 * The readable half of an account label — "Current account" out of "Current account ·· 3349".
 *
 * The two halves are split apart rather than derived separately because `accountDisplayName` is the
 * one place the label is resolved, and re-deriving either half here would be a second answer to a
 * question this package has already settled.
 */
fun String.nameHalf(): String = substringBefore('·').substringBefore('•').trim()

/** The masked-identifier half, "·· 3349", or blank when the name carries no identifier at all. */
fun String.identifierHalf(): String {
    val start = indexOfFirst { it in IDENTIFIER_MARKERS }
    return if (start < 0) "" else substring(start).trim()
}
