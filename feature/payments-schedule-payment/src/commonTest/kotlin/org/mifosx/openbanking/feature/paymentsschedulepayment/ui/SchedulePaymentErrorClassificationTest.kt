/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.paymentsschedulepayment.ui

import org.mifosx.openbanking.core.data.util.RemoteException
import template.core.base.network.NetworkError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Four OBIE codes reach this feature and reach nothing else, and none of them may fall through.
 *
 * Unclassified they land on [SchedulePaymentErrorKind.NetworkError], which tells the customer to
 * check their internet connection and offers a Retry that can only fail again. That is not
 * hypothetical: it shipped once already, when a credit card sent as the debtor account produced a
 * `U021` nobody had mapped.
 *
 * The bodies here are the shapes the sandbox actually returns — a `Code`, a `Message` and a `Path` —
 * because the path is what several of these are matched on.
 */
class SchedulePaymentErrorClassificationTest {

    private fun refusal(code: String, path: String, message: String = "Bad Request"): Throwable =
        RemoteException(
            NetworkError.Client.BadRequest(
                """{"Code":"400","Message":"Bad Request","Errors":[""" +
                    """{"ErrorCode":"UK.OBIE.$code","Message":"$message","Path":"$path"}]}""",
            ),
        )

    private val datePath = "Data.Initiation.RequestedExecutionDateTime"

    // region — The date, which is the only refusal with a recovery on this screen

    /** `U003`: today or earlier. The bank counts today as past. */
    @Test
    fun aPastDateIsClassifiedAsARefusedDate() {
        assertEquals(
            SchedulePaymentErrorKind.DateRefused,
            classifySchedulePaymentError(refusal("U003", datePath)),
        )
    }

    /**
     * `U002` on the date field, which is the case the code alone gets wrong.
     *
     * `U002` is only "Invalid Field" and is raised for refused payers and refused schemes too. Read
     * without the path it classifies as [SchedulePaymentErrorKind.InvalidField] — "check your
     * details" — which points the customer at everything except the one control that would fix it.
     */
    @Test
    fun aDateBeyondTheWindowIsARefusedDateRatherThanAGenericInvalidField() {
        val kind = classifySchedulePaymentError(refusal("U002", datePath, "Invalid Field"))

        assertEquals(SchedulePaymentErrorKind.DateRefused, kind)
        assertNotEquals(SchedulePaymentErrorKind.InvalidField, kind)
    }

    /** `U004` on the date field: omitted altogether. Same path, so the same classification. */
    @Test
    fun aMissingDateIsClassifiedAsARefusedDate() {
        assertEquals(
            SchedulePaymentErrorKind.DateRefused,
            classifySchedulePaymentError(refusal("U004", datePath, "is missing")),
        )
    }

    /** And a refused date offers changing the date, which is the whole point of separating it. */
    @Test
    fun aRefusedDateOffersADateChangeAndNotARetry() {
        val kind = SchedulePaymentErrorKind.DateRefused

        assertTrue(kind.needsDateChange)
        assertFalse(kind.isRetryable, "the same date would be refused again")
    }

    // endregion

    // region — The app's own defects

    /** `U005` — a field sent where the rail does not accept it. Not the customer's doing. */
    @Test
    fun anUnexpectedFieldIsClassifiedAsAMalformedRequest() {
        assertEquals(
            SchedulePaymentErrorKind.RequestMalformed,
            classifySchedulePaymentError(
                refusal("U005", "Data.Initiation.RemittanceInformation", "Field is not expected"),
            ),
        )
    }

    @Test
    fun aMissingPermissionIsClassifiedAsAMalformedRequest() {
        assertEquals(
            SchedulePaymentErrorKind.RequestMalformed,
            classifySchedulePaymentError(refusal("U004", "Data.Permission", "is missing")),
        )
    }

    /** No retry: the same body would be rebuilt and refused identically. */
    @Test
    fun aMalformedRequestIsNotRetryable() {
        assertFalse(SchedulePaymentErrorKind.RequestMalformed.isRetryable)
    }

    // endregion

    // region — Products that cannot be used on this rail

    /** `U027` — an IBAN creditor on the domestic rail, or a sort-code one on the international. */
    @Test
    fun anUnsupportedSchemeIsClassifiedAsSuch() {
        assertEquals(
            SchedulePaymentErrorKind.SchemeNotSupported,
            classifySchedulePaymentError(
                refusal("U027", "Data.Initiation.CreditorAccount.SchemeName", "Unsupported scheme"),
            ),
        )
    }

    /**
     * A debtor refusal still wins over the scheme reading.
     *
     * `U027` on the debtor's scheme name means the payer product cannot send payments at all, which
     * is a different recovery — change the account — from a payee whose details are in the wrong
     * shape for this rail.
     */
    @Test
    fun aRefusedDebtorIsClassifiedAsTheProductRatherThanTheScheme() {
        assertEquals(
            SchedulePaymentErrorKind.PayerNotSupported,
            classifySchedulePaymentError(
                refusal("U027", "Data.Initiation.DebtorAccount.SchemeName", "Unsupported scheme"),
            ),
        )
    }

    // endregion

    /** The regression this suite exists for: none of the four may read as a connection failure. */
    @Test
    fun noneOfTheNewCodesFallThroughToANetworkError() {
        val refusals = listOf(
            refusal("U003", datePath),
            refusal("U004", "Data.Permission"),
            refusal("U005", "Data.Initiation.RemittanceInformation"),
            refusal("U027", "Data.Initiation.CreditorAccount.SchemeName"),
        )

        refusals.forEach { throwable ->
            assertNotEquals(
                SchedulePaymentErrorKind.NetworkError,
                classifySchedulePaymentError(throwable),
                "a refusal the bank explained must never be reported as a connection problem",
            )
        }
    }

    /** A genuine transport failure still reads as one. */
    @Test
    fun aTransportFailureIsStillANetworkError() {
        assertEquals(
            SchedulePaymentErrorKind.NetworkError,
            classifySchedulePaymentError(RemoteException(NetworkError.Network(IllegalStateException()))),
        )
    }

    @Test
    fun anExpiredTokenIsStillClassifiedByItsStatus() {
        assertEquals(
            SchedulePaymentErrorKind.TokenExpired,
            classifySchedulePaymentError(RemoteException(NetworkError.Client.Unauthorized())),
        )
    }
}
