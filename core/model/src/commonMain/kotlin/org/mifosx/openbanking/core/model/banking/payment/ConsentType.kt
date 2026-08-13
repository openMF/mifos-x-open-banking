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

/**
 * Which OBIE consent an authorisation is for, and therefore which endpoints answer for it.
 *
 * Product **and** rail in one member, because that pair is what selects a path:
 * `domestic-payment-consents` and `domestic-standing-order-consents` share a rail and differ by
 * product, while `domestic-payment-consents` and `international-payment-consents` do the reverse.
 * [PaymentRail] alone answers only half of that question, which is why it must not be the thing
 * dispatch is written against — see its own KDoc for what it is still for.
 *
 * Single payments, scheduled payments and standing orders exist here. Domestic-only VRP is
 * deliberately absent rather than stubbed: adding a member later breaks every `when` over this type
 * at compile time, and that is the whole point. The rails diverged silently once already — an
 * international consent was read back at the domestic path and answered `400 U011`, which no test
 * caught because nothing forced the two apart.
 *
 * [wireValue] is what goes in `payment_history.paymentType`. The two strings predate this type and
 * are kept exactly, so a row written by an older build still reads. **Values are append-only**: a
 * string that has been persisted must never be re-used for a different meaning, because an older
 * build reading a newer row would resolve it to whatever it thought that string meant.
 */
sealed interface ConsentType {

    val wireValue: String

    val rail: PaymentRail

    data object DomesticSinglePayment : ConsentType {
        override val wireValue: String = "domestic_payment"
        override val rail: PaymentRail = PaymentRail.Domestic
    }

    data object InternationalSinglePayment : ConsentType {
        override val wireValue: String = "international_payment"
        override val rail: PaymentRail = PaymentRail.International
    }

    data object DomesticScheduledPayment : ConsentType {
        override val wireValue: String = "domestic_scheduled_payment"
        override val rail: PaymentRail = PaymentRail.Domestic
    }

    data object InternationalScheduledPayment : ConsentType {
        override val wireValue: String = "international_scheduled_payment"
        override val rail: PaymentRail = PaymentRail.International
    }

    /**
     * A recurring mandate rather than an instruction.
     *
     * Shares [PaymentRail.Domestic] with three other members and shares its product with the
     * international sibling, which is exactly the pair this type exists to keep apart: reading the
     * rail alone would send a mandate's id to `domestic-payments/{id}`.
     */
    data object DomesticStandingOrder : ConsentType {
        override val wireValue: String = "domestic_standing_order"
        override val rail: PaymentRail = PaymentRail.Domestic
    }

    data object InternationalStandingOrder : ConsentType {
        override val wireValue: String = "international_standing_order"
        override val rail: PaymentRail = PaymentRail.International
    }

    companion object {

        /**
         * Every type this build knows.
         *
         * Maintained by hand. An earlier comment claimed a new member landed here automatically; it
         * does not, and a member missing from this list is invisible to [fromWire] — the row reads
         * back as unrecognised and its payment becomes unreadable.
         */
        val ALL: List<ConsentType> = listOf(
            DomesticSinglePayment,
            InternationalSinglePayment,
            DomesticScheduledPayment,
            InternationalScheduledPayment,
            DomesticStandingOrder,
            InternationalStandingOrder,
        )

        /**
         * The type a stored string names, or null when this build does not recognise it.
         *
         * Null rather than a default, and the distinction is the point. A blank or absent value is a
         * row from before the column carried meaning, and its caller may reasonably read it as a
         * domestic single payment — that is what it was. A **non-blank string this build does not
         * know** is the opposite situation: a row written by a newer build, for a product this one
         * cannot handle. Resolving that to domestic single payment would send a standing order's id
         * to `domestic-payments/{id}` and report whatever came back as the truth.
         *
         * Callers must therefore treat null as "cannot answer", never as "domestic".
         */
        fun fromWire(raw: String?): ConsentType? = ALL.firstOrNull { it.wireValue == raw }
    }
}
