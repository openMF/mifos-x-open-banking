/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.paymentsstandingorder.ui

import org.mifosx.openbanking.core.data.util.RemoteException
import template.core.base.network.NetworkError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Turning a refusal into a recovery.
 *
 * The whole suite exists because of one property of this product: HSBC raises a single `U003` whose
 * message recites three date rules at once — not today or tomorrow, within twelve months, and after
 * the first payment date — whichever was actually broken. The code cannot say which of the customer's
 * two dates to change. The OBIE **path** can, and these cases pin that the path is read first.
 */
class StandingOrderErrorClassificationTest {

    private fun refusal(code: String, path: String, message: String = "Bad Request"): Throwable =
        RemoteException(
            NetworkError.Client.BadRequest(
                """{"Code":"400","Id":"ref-1","Message":"Bad Request","Errors":[""" +
                    """{"ErrorCode":"UK.OBIE.$code","Message":"$message","Path":"$path"}]}""",
            ),
        )

    private val firstDatePath = "Data.Initiation.MandateRelatedInformation.FirstPaymentDateTime"
    private val finalDatePath = "Data.Initiation.MandateRelatedInformation.FinalPaymentDateTime"
    private val frequencyPath = "Data.Initiation.MandateRelatedInformation.Frequency.Type"

    // region — the two dates, which only the path tells apart

    @Test
    fun aRefusedStartDateIsAttributedToTheStartDate() {
        assertEquals(
            StandingOrderErrorKind.FirstDateRefused,
            classifyStandingOrderError(refusal("U003", firstDatePath)),
        )
    }

    /**
     * The message here is the real one, and it names all three rules.
     *
     * Reading it — or the code — would give the customer no way to know which of their two dates to
     * change. Only the path does.
     */
    @Test
    fun aRefusedEndDateIsAttributedToTheEndDate() {
        val kind = classifyStandingOrderError(
            refusal(
                code = "U003",
                path = finalDatePath,
                message = "Sorry, We cannot process the Standing Order Instruction as the final " +
                    "transfer date cannot be today or tomorrow, and must be within 12 months.",
            ),
        )

        assertEquals(StandingOrderErrorKind.FinalDateRefused, kind)
        assertNotEquals(StandingOrderErrorKind.FirstDateRefused, kind, "the wrong field would be blamed")
    }

    /** A date beyond the window arrives as `U002`, which alone says only "Invalid Field". */
    @Test
    fun aDateBeyondTheWindowIsADateRefusalRatherThanAGenericInvalidField() {
        val kind = classifyStandingOrderError(refusal("U002", finalDatePath, "Invalid value"))

        assertEquals(StandingOrderErrorKind.FinalDateRefused, kind)
        assertNotEquals(StandingOrderErrorKind.InvalidField, kind)
    }

    @Test
    fun aMissingStartDateIsStillADateRefusal() {
        assertEquals(
            StandingOrderErrorKind.FirstDateRefused,
            classifyStandingOrderError(refusal("U004", firstDatePath, "Field is missing")),
        )
    }

    /** Each date refusal sends the customer back to its own field, and neither offers a bare retry. */
    @Test
    fun eachRefusedDateNamesTheFieldToChange() {
        assertEquals(
            StandingOrderDateRole.First,
            StandingOrderErrorKind.FirstDateRefused.refusedDateRole,
        )
        assertEquals(
            StandingOrderDateRole.Final,
            StandingOrderErrorKind.FinalDateRefused.refusedDateRole,
        )
        assertFalse(StandingOrderErrorKind.FirstDateRefused.isRetryable, "the same date would be refused again")
        assertFalse(StandingOrderErrorKind.FinalDateRefused.isRetryable)
    }

    @Test
    fun akindThatIsNotADateRefusalNamesNoDate() {
        assertNull(StandingOrderErrorKind.NetworkError.refusedDateRole)
        assertNull(StandingOrderErrorKind.PayerNotSupported.refusedDateRole)
    }

    // endregion

    // region — the payer, which shares `U002` with everything else

    /**
     * A refused payer is classified by its path, not its code.
     *
     * The two rails answer differently for the same refused card — `U027` domestically at
     * `DebtorAccount.SchemeName`, `U002` internationally at `DebtorAccount.Identification` — so
     * matching on either code would catch one rail and miss the other.
     */
    @Test
    fun aRefusedPayerIsClassifiedAsTheProductRatherThanTheScheme() {
        val domestic = refusal("U027", "Data.Initiation.DebtorAccount.SchemeName", "Unsupported scheme")
        val international = refusal("U002", "Data.Initiation.DebtorAccount.Identification", "Invalid Field")

        assertEquals(StandingOrderErrorKind.PayerNotSupported, classifyStandingOrderError(domestic))
        assertEquals(StandingOrderErrorKind.PayerNotSupported, classifyStandingOrderError(international))
    }

    // endregion

    // region — the app's own defects

    /**
     * A refused frequency means the closed enum was bypassed.
     *
     * Reachable only through a defect, so it gets a kind — shipping a crash would be worse — but its
     * copy must not read as something the customer chose wrongly.
     */
    @Test
    fun aRefusedFrequencyIsItsOwnKind() {
        assertEquals(
            StandingOrderErrorKind.FrequencyRefused,
            classifyStandingOrderError(refusal("U002", frequencyPath, "Invalid value")),
        )
    }

    @Test
    fun anUnexpectedFieldIsClassifiedAsAMalformedRequest() {
        assertEquals(
            StandingOrderErrorKind.RequestMalformed,
            classifyStandingOrderError(
                refusal("U005", "Data.Initiation.RemittanceInformation", "Field is not expected"),
            ),
        )
    }

    @Test
    fun aMissingPermissionIsClassifiedAsAMalformedRequest() {
        assertEquals(
            StandingOrderErrorKind.RequestMalformed,
            classifyStandingOrderError(refusal("U004", "Data.Permission", "Data.Permission is missing")),
        )
    }

    @Test
    fun aMalformedRequestIsNotRetryable() {
        assertFalse(StandingOrderErrorKind.RequestMalformed.isRetryable, "the same body would be refused again")
    }

    @Test
    fun anUnsupportedCreditorSchemeIsClassifiedAsSuch() {
        assertEquals(
            StandingOrderErrorKind.SchemeNotSupported,
            classifyStandingOrderError(
                refusal("U027", "Data.Initiation.CreditorAccount.SchemeName", "Unsupported scheme"),
            ),
        )
    }

    // endregion

    // region — nothing falls through

    /** Every code this product can raise must reach a kind rather than a generic network failure. */
    @Test
    fun noneOfTheStandingOrderCodesFallThroughToANetworkError() {
        listOf(
            refusal("U002", frequencyPath),
            refusal("U003", finalDatePath),
            refusal("U004", firstDatePath),
            refusal("U005", "Data.Initiation.RemittanceInformation"),
            refusal("U027", "Data.Initiation.CreditorAccount.SchemeName"),
        ).forEach { throwable ->
            assertNotEquals(
                StandingOrderErrorKind.NetworkError,
                classifyStandingOrderError(throwable),
                "a refusal the bank explained was reported as a connection problem",
            )
        }
    }

    @Test
    fun aTransportFailureIsStillANetworkError() {
        assertEquals(
            StandingOrderErrorKind.NetworkError,
            classifyStandingOrderError(RemoteException(NetworkError.Client.BadRequest(null))),
        )
    }

    @Test
    fun anExpiredTokenIsClassifiedByItsStatus() {
        val kind = classifyStandingOrderError(RemoteException(NetworkError.Client.Unauthorized("expired")))

        assertEquals(StandingOrderErrorKind.TokenExpired, kind)
        assertTrue(kind.needsReauthorisation)
    }

    @Test
    fun aRevokedConsentIsClassifiedByItsStatus() {
        assertEquals(
            StandingOrderErrorKind.ConsentRevoked,
            classifyStandingOrderError(RemoteException(NetworkError.Client.Forbidden("revoked"))),
        )
    }

    /** The support reference is surfaced so a customer who cannot act has something to quote. */
    @Test
    fun theBanksOwnReferenceIsCarriedThrough() {
        assertEquals("ref-1", supportReferenceOf(refusal("U003", finalDatePath)))
    }

    // endregion
}
