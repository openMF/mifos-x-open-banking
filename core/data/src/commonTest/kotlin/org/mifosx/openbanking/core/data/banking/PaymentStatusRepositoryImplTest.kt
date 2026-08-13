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

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.mifosx.openbanking.core.data.TestSigningKey
import org.mifosx.openbanking.core.data.banking.impl.PaymentStatusRepositoryImpl
import org.mifosx.openbanking.core.model.banking.payment.ConsentType
import org.mifosx.openbanking.core.model.banking.payment.PaymentDraft
import org.mifosx.openbanking.core.model.banking.payment.PaymentHistoryItem
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

private const val CLIENT_CREDENTIALS_TOKEN = "tpp-client-credentials-token"
private const val PSU_TOKEN = "psu-payments-token"
private const val CONSENT_ID = "812774903"
private const val PAYMENT_ID = "PMT-1"

private const val TOKEN_JSON =
    """{"access_token":"$CLIENT_CREDENTIALS_TOKEN","expires_in":300,"scope":"payments","token_type":"Bearer"}"""
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
 * none of them. Two properties are pinned — the credential it presents, and that it refuses to guess.
 */
class PaymentStatusRepositoryImplTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private data class Recorded(val path: String, val authorization: String?)

    private val captured = mutableListOf<Recorded>()

    private class FakePaymentHistoryRepo(
        private val type: ConsentType? = null,
    ) : PaymentHistoryRepository {
        override fun observeRecent(): Flow<List<PaymentHistoryItem>> = MutableStateFlow(emptyList())
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
        override suspend fun refreshStatuses() {}
        override suspend fun consentTypeOf(paymentId: String): ConsentType? = type
        override suspend fun stageTimestampsOf(paymentId: String): PaymentStageTimestamps? = null
    }

    private suspend fun repository(storedType: ConsentType?): PaymentStatusRepositoryImpl {
        val client = HttpClient(
            MockEngine { request: HttpRequestData ->
                captured += Recorded(
                    path = request.url.encodedPath,
                    authorization = request.headers[HttpHeaders.Authorization],
                )
                val isToken = request.url.encodedPath.contains("oauth2/token")
                val body = when {
                    isToken -> TOKEN_JSON
                    // Longest discriminator first: "domestic-payments" also matches the
                    // international path's tail if the order is inverted.
                    request.url.encodedPath.contains("international-payments") -> INTL_PAYMENT_JSON
                    else -> PAYMENT_JSON
                }
                respond(body, HttpStatusCode.OK, jsonHeaders)
            },
        ) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val signingKey = TestSigningKey.pem()
        return PaymentStatusRepositoryImpl(
            pisp = org.mifosx.openbanking.core.network.api.Pisp(
                httpClient = client,
                kid = "test-kid",
                signingKeyPem = signingKey,
                financialId = "",
                signingIssuer = "mifos_init_00000/0000000000000000000000",
            ),
            oauth = org.mifosx.openbanking.core.network.api.OAuth(
                client,
                "https://sandbox.test/oauth2/token",
                "test-client",
                "test-kid",
                signingKey,
            ),
            paymentHistoryRepository = FakePaymentHistoryRepo(storedType),
        )
    }

    private fun requestTo(fragment: String): Recorded =
        assertNotNull(captured.lastOrNull { it.path.contains(fragment) }, "no request to $fragment")

    /**
     * Reading the payment back on the PSU token earned a `401` from the live sandbox: a payment
     * resource is TPP-authenticated, while the PSU token authorises only the payment itself. The
     * client credential is also what keeps the status readable days later, once the PSU token has
     * long expired.
     */
    @Test
    fun readingAPaymentPresentsTheClientCredentialsTokenNotThePsuOne() = runTest {
        repository(ConsentType.DomesticSinglePayment).paymentStatus(PAYMENT_ID)

        val read = requestTo("domestic-payments/$PAYMENT_ID")
        assertEquals("Bearer $CLIENT_CREDENTIALS_TOKEN", read.authorization)
        assertTrue(PSU_TOKEN !in read.authorization.orEmpty())
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
            captured.lastOrNull { it.path.contains("international-payments/$PAYMENT_ID") },
            "expected the international endpoint, saw ${captured.map { it.path }}",
        )
        assertNull(captured.firstOrNull { it.path.contains("domestic-payments/$PAYMENT_ID") })
    }

    @Test
    fun readingADomesticPaymentUsesTheDomesticEndpoint() = runTest {
        repository(ConsentType.DomesticSinglePayment).paymentStatus(PAYMENT_ID)

        assertNotNull(captured.lastOrNull { it.path.contains("domestic-payments/$PAYMENT_ID") })
        assertNull(captured.firstOrNull { it.path.contains("international-payments/$PAYMENT_ID") })
    }

    /**
     * With no stored row the type is unknown, and the read fails rather than guessing.
     *
     * Defaulting to domestic was defensible while single payments were the only product — every id
     * predating the column really was domestic — but the moment a second product can be stored, the
     * same default sends another product's id to `domestic-payments/{id}` and reports whatever comes
     * back as the truth. A guess that is right for legacy rows and silently wrong for new ones is
     * worse than an error.
     */
    @Test
    fun readingAPaymentWithNoStoredRowFailsRatherThanGuessingTheEndpoint() = runTest {
        val result = repository(storedType = null).paymentStatus(PAYMENT_ID)

        assertIs<NetworkResult.Error<*>>(result)
        assertNull(captured.firstOrNull { it.path.contains("domestic-payments/$PAYMENT_ID") })
        assertNull(captured.firstOrNull { it.path.contains("international-payments/$PAYMENT_ID") })
    }
}
