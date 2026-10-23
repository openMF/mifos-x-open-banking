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

import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.mifosx.openbanking.core.data.WORKER_SESSION
import org.mifosx.openbanking.core.data.banking.impl.PaymentStatusRepositoryImpl
import org.mifosx.openbanking.core.data.gatewayClient
import org.mifosx.openbanking.core.data.jsonHeaders
import org.mifosx.openbanking.core.model.banking.payment.ConsentType
import org.mifosx.openbanking.core.model.banking.payment.PaymentDraft
import org.mifosx.openbanking.core.model.banking.payment.PaymentHistoryRow
import org.mifosx.openbanking.core.model.banking.payment.PaymentParties
import org.mifosx.openbanking.core.model.banking.payment.PaymentReceipt
import org.mifosx.openbanking.core.model.banking.payment.PaymentStageTimestamps
import org.mifosx.openbanking.core.model.banking.payment.ScheduledPaymentDraft
import org.mifosx.openbanking.core.model.banking.payment.StandingOrderDraft
import template.core.base.network.NetworkResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val CONSENT_ID = "812774903"
private const val PAYMENT_ID = "PMT-1"

private const val PAYMENT_JSON =
    """{"Data":{"DomesticPaymentId":"$PAYMENT_ID","ConsentId":"$CONSENT_ID","Status":"AcceptedSettlementInProcess"}}"""
private const val INTL_PAYMENT_JSON =
    """{"Data":{"InternationalPaymentId":"$PAYMENT_ID","ConsentId":"$CONSENT_ID",""" +
        """"Status":"AcceptedSettlementInProcess"}}"""

/**
 * Covers [PaymentStatusRepositoryImpl], whose whole job is choosing an endpoint from a stored type.
 *
 * These cases moved here when the read was split off the single-payment repository: the id arrives
 * with no memory of which product issued it, so this class answers for every product and belongs to
 * none of them. Two properties are pinned — it reads through the Worker on the session, and it refuses
 * to guess.
 */
class PaymentStatusRepositoryImplTest {

    private data class Recorded(val path: String, val authorization: String?)

    private val captured = mutableListOf<Recorded>()

    private class FakePaymentHistoryRepo(
        private val type: ConsentType? = null,
    ) : PaymentHistoryRepository {
        override suspend fun saveSubmitted(receipt: PaymentReceipt, draft: PaymentDraft) {}
        override suspend fun saveFailed(draft: PaymentDraft, errorKind: String, errorDescription: String) {}
        override suspend fun saveSubmitted(receipt: PaymentReceipt, draft: ScheduledPaymentDraft) {}
        override suspend fun saveFailed(
            draft: ScheduledPaymentDraft,
            errorKind: String,
            errorDescription: String,
        ) = Unit
        override suspend fun saveSubmitted(receipt: PaymentReceipt, draft: StandingOrderDraft) {}
        override suspend fun saveFailed(
            draft: StandingOrderDraft,
            errorKind: String,
            errorDescription: String,
        ) = Unit
        override suspend fun consentTypeOf(paymentId: String): ConsentType? = type
        override suspend fun stageTimestampsOf(paymentId: String): PaymentStageTimestamps? = null
        override suspend fun partiesOf(paymentId: String): PaymentParties? = null
        override fun observeHistory(
            types: Set<ConsentType>,
            limit: Int,
        ): Flow<List<PaymentHistoryRow>> = flowOf(emptyList())
        override suspend fun recordStatus(paymentId: String, receipt: PaymentReceipt) = Unit
    }

    private fun repository(storedType: ConsentType?): PaymentStatusRepositoryImpl {
        val client = gatewayClient { request: HttpRequestData ->
            captured += Recorded(
                path = request.url.encodedPath,
                authorization = request.headers[HttpHeaders.Authorization],
            )
            val isIntl = request.url.encodedPath.startsWith("/v1/payments/international")
            val body = if (isIntl) INTL_PAYMENT_JSON else PAYMENT_JSON
            respond(body, HttpStatusCode.OK, jsonHeaders)
        }
        return PaymentStatusRepositoryImpl(
            pisp = org.mifosx.openbanking.core.network.api.Pisp(client),
            paymentHistoryRepository = FakePaymentHistoryRepo(storedType),
        )
    }

    private fun requestTo(fragment: String): Recorded =
        assertNotNull(captured.lastOrNull { it.path.contains(fragment) }, "no request to $fragment")

    /**
     * The Worker reads a payment back on its client-credentials token (a payment resource is
     * TPP-authenticated), which keeps the status readable days later; the app presents only its
     * session, never a bearer token.
     */
    @Test
    fun readingAPaymentPresentsTheWorkerSessionNotABankToken() = runTest {
        repository(ConsentType.DomesticSinglePayment).paymentStatus(PAYMENT_ID)

        val read = requestTo("/v1/payments/domestic/$PAYMENT_ID")
        assertEquals("Session $WORKER_SESSION", read.authorization)
        assertTrue(!read.authorization.orEmpty().startsWith("Bearer"))
    }

    /**
     * The rail cannot be told from the id and the endpoints do not accept each other's, so this once
     * called the domestic one for everything — meaning every international payment answered 404 on
     * its own status screen. The type comes from the row written at submission.
     */
    @Test
    fun readingAnInternationalPaymentUsesTheInternationalEndpoint() = runTest {
        repository(ConsentType.InternationalSinglePayment).paymentStatus(PAYMENT_ID)

        assertNotNull(
            captured.lastOrNull { it.path.contains("/v1/payments/international/$PAYMENT_ID") },
            "expected the international endpoint, saw ${captured.map { it.path }}",
        )
        assertNull(captured.firstOrNull { it.path.contains("/v1/payments/domestic/$PAYMENT_ID") })
    }

    @Test
    fun readingADomesticPaymentUsesTheDomesticEndpoint() = runTest {
        repository(ConsentType.DomesticSinglePayment).paymentStatus(PAYMENT_ID)

        assertNotNull(captured.lastOrNull { it.path.contains("/v1/payments/domestic/$PAYMENT_ID") })
        assertNull(captured.firstOrNull { it.path.contains("/v1/payments/international/$PAYMENT_ID") })
    }

    /**
     * With no stored row the type is unknown, and the read fails rather than guessing.
     *
     * Defaulting to domestic was defensible while single payments were the only product — every id
     * predating the column really was domestic — but the moment a second product can be stored, the
     * same default sends another product's id to `/v1/payments/domestic/{id}` and reports whatever comes
     * back as the truth. A guess that is right for legacy rows and silently wrong for new ones is
     * worse than an error.
     */
    @Test
    fun readingAPaymentWithNoStoredRowFailsRatherThanGuessingTheEndpoint() = runTest {
        val result = repository(storedType = null).paymentStatus(PAYMENT_ID)

        assertIs<NetworkResult.Error<*>>(result)
        assertNull(captured.firstOrNull { it.path.contains("/v1/payments/domestic/$PAYMENT_ID") })
        assertNull(captured.firstOrNull { it.path.contains("/v1/payments/international/$PAYMENT_ID") })
    }
}
