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

/**
 * How many characters of a payee's name the avatar caption holds before eliding the rest.
 *
 * The caption wraps to two lines, so this is the budget across both of them.
 */
private const val MAX_PAYEE_NAME = 20

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
