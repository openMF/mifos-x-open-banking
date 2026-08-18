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

import org.mifosx.openbanking.core.model.banking.payment.PaymentStatus
import org.mifosx.openbanking.core.model.callback.ConsentStatus
import org.mifosx.openbanking.core.model.vrp.AccountIdentity
import org.mifosx.openbanking.core.model.vrp.Money
import org.mifosx.openbanking.core.model.vrp.PeriodType
import org.mifosx.openbanking.core.model.vrp.PeriodicLimit
import org.mifosx.openbanking.core.model.vrp.VrpConsent
import org.mifosx.openbanking.core.model.vrp.VrpControlParameters
import org.mifosx.openbanking.core.model.vrp.VrpPayment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Fixtures are positioned relative to the real clock rather than pinning it, so a payment can be
 * placed inside or outside the current window without restating the date arithmetic under test.
 */
class VrpUsageTest {

    private val now = Clock.System.now()

    private val payee = AccountIdentity(
        schemeName = "UK.OBIE.SortCodeAccountNumber",
        identification = "80200110203350",
        name = "Mr Dharani C",
    )

    private fun gbp(minorUnits: Long) = Money(minorUnits = minorUnits, currency = "GBP")

    private fun consent(
        createdAt: Instant,
        limits: List<PeriodicLimit>,
        consentId: String = "45365",
    ) = VrpConsent(
        consentId = consentId,
        status = ConsentStatus.Authorised,
        createdAt = createdAt,
        controlParameters = VrpControlParameters(
            maximumIndividualAmount = gbp(1_00),
            periodicLimits = limits,
        ),
        payee = payee,
    )

    private fun payment(
        createdAt: Instant,
        minorUnits: Long,
        status: PaymentStatus = PaymentStatus.AcceptedCreditSettlementCompleted,
        consentId: String = "45365",
    ) = VrpPayment(
        localId = "local-$createdAt-$minorUnits",
        consentId = consentId,
        amount = gbp(minorUnits),
        status = status,
        createdAt = createdAt,
    )

    @Test
    fun countsOnlySettledPayments() {
        val consent = consent(
            createdAt = now - 1.days,
            limits = listOf(PeriodicLimit(PeriodType.Month, gbp(100_00))),
        )
        val payments = listOf(
            payment(now - 2.hours, 10_00, PaymentStatus.AcceptedCreditSettlementCompleted),
            payment(now - 2.hours, 25_00, PaymentStatus.Rejected),
            payment(now - 2.hours, 30_00, PaymentStatus.AcceptedSettlementInProcess),
        )

        val usage = currentUsage(consent, payments).single()

        assertEquals(10_00, usage.consumed.minorUnits)
        assertEquals(90_00, usage.remaining.minorUnits)
    }

    @Test
    fun recognisesBothSettledSpellings() {
        val consent = consent(
            createdAt = now - 1.days,
            limits = listOf(PeriodicLimit(PeriodType.Month, gbp(100_00))),
        )
        val payments = listOf(
            payment(now - 2.hours, 10_00, PaymentStatus.fromWire("ACCC")),
            payment(now - 2.hours, 5_00, PaymentStatus.fromWire("AcceptedCreditSettlementCompleted")),
        )

        val usage = currentUsage(consent, payments).single()

        assertEquals(15_00, usage.consumed.minorUnits)
    }

    @Test
    fun ignoresPaymentsMadeBeforeTheCurrentPeriod() {
        val consent = consent(
            createdAt = now - 40.days,
            limits = listOf(PeriodicLimit(PeriodType.Month, gbp(100_00))),
        )
        val payments = listOf(
            payment(now - 39.days, 40_00),
            payment(now - 1.days, 7_00),
        )

        val usage = currentUsage(consent, payments).single()

        assertEquals(7_00, usage.consumed.minorUnits)
        assertTrue(usage.periodStart > now - 40.days, "the window should have rolled forward")
        assertTrue(usage.periodStart <= now, "the window cannot start in the future")
    }

    @Test
    fun startsTheWindowAtCreationWhileTheFirstPeriodIsStillRunning() {
        val createdAt = now - 3.days
        val consent = consent(
            createdAt = createdAt,
            limits = listOf(PeriodicLimit(PeriodType.Month, gbp(100_00))),
        )

        val usage = currentUsage(consent, emptyList()).single()

        assertEquals(createdAt, usage.periodStart)
    }

    @Test
    fun returnsOneEntryPerLimitInOrder() {
        val consent = consent(
            createdAt = now - 1.days,
            limits = listOf(
                PeriodicLimit(PeriodType.Day, gbp(50_00)),
                PeriodicLimit(PeriodType.Month, gbp(500_00)),
                PeriodicLimit(PeriodType.Year, gbp(5000_00)),
            ),
        )

        val usage = currentUsage(consent, emptyList())

        assertEquals(3, usage.size)
        assertEquals(listOf(PeriodType.Day, PeriodType.Month, PeriodType.Year), usage.map { it.limit.periodType })
    }

    @Test
    fun scopesUsageToOneConsent() {
        val consent = consent(
            createdAt = now - 1.days,
            limits = listOf(PeriodicLimit(PeriodType.Month, gbp(100_00))),
        )
        val payments = listOf(
            payment(now - 2.hours, 10_00, consentId = "45365"),
            payment(now - 2.hours, 60_00, consentId = "45366"),
        )

        val usage = currentUsage(consent, payments).single()

        assertEquals(10_00, usage.consumed.minorUnits)
    }

    @Test
    fun neverReportsNegativeRemaining() {
        val consent = consent(
            createdAt = now - 1.days,
            limits = listOf(PeriodicLimit(PeriodType.Month, gbp(10_00))),
        )
        val payments = listOf(payment(now - 2.hours, 25_00))

        val usage = currentUsage(consent, payments).single()

        assertEquals(25_00, usage.consumed.minorUnits)
        assertEquals(0, usage.remaining.minorUnits)
    }

    @Test
    fun carriesTheLimitsCurrencyOntoTheFigures() {
        val consent = consent(
            createdAt = now - 1.days,
            limits = listOf(PeriodicLimit(PeriodType.Month, gbp(100_00))),
        )

        val usage = currentUsage(consent, emptyList()).single()

        assertEquals("GBP", usage.consumed.currency)
        assertEquals("GBP", usage.remaining.currency)
    }
}
