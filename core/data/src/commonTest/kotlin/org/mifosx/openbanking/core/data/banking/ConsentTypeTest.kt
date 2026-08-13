/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.data.banking

import org.mifosx.openbanking.core.data.banking.mapper.toConsentType
import org.mifosx.openbanking.core.model.banking.payment.ConsentType
import org.mifosx.openbanking.core.model.banking.payment.PaymentRail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The wire vocabulary a stored payment row is read back through.
 *
 * `ALL` is maintained by hand, so a member added to the sealed interface and forgotten here is
 * invisible to `fromWire` — the row resolves to null and its payment becomes unreadable, with no
 * compiler complaint. These cases are the only thing standing between that and a shipped defect.
 */
class ConsentTypeTest {

    @Test
    fun everyMemberIsRegisteredInAll() {
        assertEquals(6, ConsentType.ALL.size, "a member was added to the interface but not to ALL")
    }

    @Test
    fun everyMemberRoundTripsThroughItsWireValue() {
        ConsentType.ALL.forEach { type ->
            assertEquals(type, ConsentType.fromWire(type.wireValue), "${type.wireValue} did not resolve")
        }
    }

    /** Persisted values are append-only: two products sharing one string would misroute a payment. */
    @Test
    fun wireValuesAreDistinct() {
        val values = ConsentType.ALL.map { it.wireValue }

        assertEquals(values.size, values.toSet().size, "two consent types share a wire value")
    }

    @Test
    fun theScheduledTypesCarryTheirOwnWireValues() {
        assertEquals("domestic_scheduled_payment", ConsentType.DomesticScheduledPayment.wireValue)
        assertEquals("international_scheduled_payment", ConsentType.InternationalScheduledPayment.wireValue)
    }

    /**
     * Pinned because these strings are persisted and append-only.
     *
     * Changing one would not fail to compile; it would silently orphan every row already written
     * under the old spelling, and those rows are the only record the app keeps that a mandate was
     * ever set up — a standing order cannot be found again through the AIS read side.
     */
    @Test
    fun theStandingOrderTypesCarryTheirOwnWireValues() {
        assertEquals("domestic_standing_order", ConsentType.DomesticStandingOrder.wireValue)
        assertEquals("international_standing_order", ConsentType.InternationalStandingOrder.wireValue)
    }

    /** Product and rail together select an endpoint, so neither alone may be the discriminator. */
    @Test
    fun aStandingOrderIsDistinctFromEveryOtherProductOnItsRail() {
        val domestic = ConsentType.ALL.filter { it.rail == PaymentRail.Domestic }

        assertEquals(3, domestic.size, "three products share the domestic rail")
        assertEquals(domestic.size, domestic.map { it.wireValue }.toSet().size)
    }

    /**
     * An unrecognised non-blank value is null, never a guess.
     *
     * That string is a row written by a newer build, for a product this one cannot handle. Resolving
     * it to a domestic single payment would send another product's id to `domestic-payments/{id}`
     * and report whatever came back as this payment's truth.
     */
    @Test
    fun anUnrecognisedValueResolvesToNullRatherThanADefault() {
        assertNull(ConsentType.fromWire("domestic_vrp"))
        assertNull(ConsentType.fromWire("vrp"))
    }

    /**
     * Blank still means a domestic single payment, and that asymmetry is deliberate.
     *
     * A blank column is a row from before the column carried meaning, and every such row really was
     * a domestic single payment — nothing else could stage one then.
     */
    @Test
    fun aBlankStoredValueStillMeansDomesticSinglePayment() {
        assertEquals(ConsentType.DomesticSinglePayment, "".toConsentType())
        assertEquals(ConsentType.DomesticSinglePayment, null.toConsentType())
    }

    @Test
    fun anUnrecognisedStoredValueIsNullEvenThoughBlankIsNot() {
        assertNull("standing_order".toConsentType())
        assertNotNull("domestic_scheduled_payment".toConsentType())
    }
}
