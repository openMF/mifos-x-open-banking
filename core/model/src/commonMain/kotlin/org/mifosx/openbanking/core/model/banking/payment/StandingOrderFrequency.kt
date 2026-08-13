/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.model.banking.payment

import kotlinx.serialization.Serializable

/**
 * How often a standing order repeats.
 *
 * In v4.0 the frequency is an **object** — `MandateRelatedInformation.Frequency` with a `Type` inside
 * it — not the OBIE v3 interval string. There is no `IntrvlMnthDay:01:15` here, and code written
 * against the older grammar is refused outright.
 *
 * OBIE defines nine codes; HSBC accepts five. `DAIL`, `ADHO`, `INDA` and `MIAN` are each refused
 * `400 U002 "Invalid value"` at `Data.Initiation.MandateRelatedInformation.Frequency.Type`, so they
 * are absent from this enum rather than offered and rejected at the bank.
 *
 * Modelled as an enum rather than a raw string for one specific reason: `MONT` is not an OBIE code,
 * is refused, and is one character from [Monthly]'s. A closed set is what makes that typo
 * unrepresentable instead of merely unlikely.
 *
 * The two `Frequency` siblings the object can also carry — `CountPerPeriod` and `PointInTime` — are
 * deliberately never sent. `PointInTime` is refused `U005` on every shape tried, and `CountPerPeriod`
 * counts instructions *within* one period rather than the total number of payments, so offering it
 * as "how many payments" would ship a misreading of the field.
 */
@Serializable
enum class StandingOrderFrequency(val wireValue: String) {
    Weekly("WEEK"),
    Fortnightly("FRTN"),
    Monthly("MNTH"),
    Quarterly("QURT"),
    Yearly("YEAR"),
    ;

    companion object {
        /** Resolves a persisted or echoed wire value, or null when this build does not know it. */
        fun fromWire(raw: String?): StandingOrderFrequency? =
            entries.firstOrNull { it.wireValue == raw }
    }
}
