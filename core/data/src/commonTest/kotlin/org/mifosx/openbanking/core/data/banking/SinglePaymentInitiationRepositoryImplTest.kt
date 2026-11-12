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

import com.russhwolf.settings.MapSettings
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.mifosx.openbanking.core.common.AccountScheme
import org.mifosx.openbanking.core.data.WORKER_SESSION
import org.mifosx.openbanking.core.data.banking.impl.SinglePaymentInitiationRepositoryImpl
import org.mifosx.openbanking.core.data.callback.PaymentAuthSession
import org.mifosx.openbanking.core.data.callback.SettingsPaymentAuthSession
import org.mifosx.openbanking.core.data.consentStartJson
import org.mifosx.openbanking.core.data.gatewayClient
import org.mifosx.openbanking.core.data.jsonHeaders
import org.mifosx.openbanking.core.model.banking.BankAccount
import org.mifosx.openbanking.core.model.banking.BeneficiaryScheme
import org.mifosx.openbanking.core.model.banking.payment.ConsentType
import org.mifosx.openbanking.core.model.banking.payment.CreditorSelection
import org.mifosx.openbanking.core.model.banking.payment.PaymentDraft
import org.mifosx.openbanking.core.model.banking.payment.PaymentHistoryRow
import org.mifosx.openbanking.core.model.banking.payment.PaymentParties
import org.mifosx.openbanking.core.model.banking.payment.PaymentReceipt
import org.mifosx.openbanking.core.model.banking.payment.PaymentStageTimestamps
import org.mifosx.openbanking.core.model.banking.payment.ScheduledPaymentDraft
import org.mifosx.openbanking.core.model.banking.payment.StagedConsent
import org.mifosx.openbanking.core.model.banking.payment.StandingOrderDraft
import org.mifosx.openbanking.core.network.api.Pisp
import template.core.base.network.NetworkResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val CONSENT_ID = "812774903"
private const val PAYMENT_ID = "PMT-1"
private const val CONSENT_KEY = "consent-key-1"
private const val PAYMENT_KEY = "payment-key-1"

private const val FUNDS_JSON = """{"Data":{"FundsAvailableResult":{"FundsAvailable":true}}}"""

private const val INTL_PAYMENT_JSON =
    """{"Data":{"InternationalPaymentId":"$PAYMENT_ID","ConsentId":"$CONSENT_ID",""" +
        """"Status":"AcceptedSettlementInProcess"}}"""
private const val PAYMENT_JSON =
    """{"Data":{"DomesticPaymentId":"$PAYMENT_ID","ConsentId":"$CONSENT_ID","Status":"AcceptedSettlementInProcess"}}"""

/**
 * Covers [SinglePaymentInitiationRepositoryImpl] at the Worker boundary: every call presents the
 * app's opaque Worker session (never a bank token), goes to the Worker route for its rail, and the
 * consent create's `{flowId, consentId, authorizeUrl}` is recorded for the return leg.
 */
class SinglePaymentInitiationRepositoryImplTest {

    private data class Recorded(
        val path: String,
        val authorization: String?,
        val idempotencyKey: String?,
        val body: String,
    )

    private val captured = mutableListOf<Recorded>()

    private fun draft() = PaymentDraft(
        debtorAccount = BankAccount(
            accountId = "acc-1",
            accountHolderName = "",
            accountTypeCode = "CACC",
            currency = "GBP",
            identification = "80200110203349",
            scheme = AccountScheme.SortCode,
        ),
        creditor = CreditorSelection(
            name = "Liam Walker",
            scheme = BeneficiaryScheme.SortCode,
            identification = "40120965872310",
        ),
        amountMinorUnits = 50_000L,
        currency = "GBP",
        reference = "Invoice 2026-05",
        instructionIdentification = "MFX20260805T1042330001",
        endToEndIdentification = "E2E-RENT-FLAT12-202608",
        consentIdempotencyKey = CONSENT_KEY,
        paymentIdempotencyKey = PAYMENT_KEY,
    )

    /**
     * The same draft on the other rail.
     *
     * `CurrencyOfTransfer` is the only difference that matters here: it is the discriminator the app
     * uses everywhere to tell the two rails apart, and setting it is what makes this draft
     * international.
     */
    private fun intlDraft() = draft().copy(currencyOfTransfer = "USD")

    private fun repository(
        session: PaymentAuthSession = SettingsPaymentAuthSession(MapSettings()),
        errorBody: String? = null,
        status: HttpStatusCode = HttpStatusCode.Created,
        storedType: ConsentType? = null,
    ): SinglePaymentInitiationRepositoryImpl {
        val client = gatewayClient { request: HttpRequestData ->
            val path = request.url.encodedPath
            captured += Recorded(
                path = path,
                authorization = request.headers[HttpHeaders.Authorization],
                idempotencyKey = request.headers["X-Idempotency-Key"],
                body = request.body.toByteArray().decodeToString(),
            )
            val body = when {
                errorBody != null -> errorBody
                path.endsWith("funds-confirmation") -> FUNDS_JSON
                path.startsWith("/v1/consents/payment/") -> consentStartJson(CONSENT_ID)
                path.startsWith("/v1/payments/international") -> INTL_PAYMENT_JSON
                path.startsWith("/v1/payments/domestic") -> PAYMENT_JSON
                else -> "{}"
            }
            respond(body, status, jsonHeaders)
        }
        return SinglePaymentInitiationRepositoryImpl(
            pisp = Pisp(client),
            paymentAuthSession = session,
            paymentHistoryRepository = FakePaymentHistoryRepo(storedType),
        )
    }

    /**
     * [rail] drives which status endpoint the repository is expected to call. Null is the
     * no-stored-row case, which must fall back to domestic rather than failing to read at all.
     */
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

    private fun authorisedSession(
        type: ConsentType = ConsentType.DomesticSinglePayment,
    ): PaymentAuthSession =
        SettingsPaymentAuthSession(MapSettings()).apply {
            savePending(consentId = CONSENT_ID, state = "s", flowId = "f", type = type)
        }

    private fun requestTo(fragment: String): Recorded =
        assertNotNull(captured.lastOrNull { it.path.contains(fragment) }, "no request to $fragment")

    // region — the Worker routes and the session each call presents

    /** Staging goes through the Worker, which mints the client-credentials token itself. */
    @Test
    fun stagingGoesToTheWorkerConsentRouteOnTheSession() = runTest {
        repository().stagePayment(draft())

        val request = requestTo("/v1/consents/payment/domestic")
        assertEquals("Session $WORKER_SESSION", request.authorization)
    }

    @Test
    fun stagingAnInternationalDraftUsesTheInternationalRoute() = runTest {
        repository().stagePayment(intlDraft())

        assertNotNull(captured.lastOrNull { it.path == "/v1/consents/payment/international" })
        assertNull(captured.lastOrNull { it.path == "/v1/consents/payment/domestic" })
    }

    /** Funds confirmation and submission run on the PSU token the Worker holds for this session. */
    @Test
    fun confirmingFundsPresentsTheSession() = runTest {
        repository(authorisedSession()).confirmFunds(CONSENT_ID)

        assertEquals("Session $WORKER_SESSION", requestTo("funds-confirmation").authorization)
    }

    /**
     * Both rails' paths end in `funds-confirmation`, so these assert on the segment that differs.
     *
     * The draft is hoisted into a local because inside `apply` on the session a bare `draft()`
     * resolves to the session's own accessor rather than this file's fixture.
     */
    @Test
    fun confirmingFundsForAnInternationalConsentUsesTheInternationalEndpoint() = runTest {
        val staged = intlDraft()
        val session = authorisedSession(ConsentType.InternationalSinglePayment).apply { saveDraft(staged) }

        repository(session).confirmFunds(CONSENT_ID)

        assertNotNull(
            captured.lastOrNull { it.path == "/v1/consents/payment/international/$CONSENT_ID/funds-confirmation" },
        )
        assertNull(
            captured.lastOrNull { "/payment/domestic/" in it.path },
            "an international funds check must never go to the domestic path",
        )
    }

    @Test
    fun confirmingFundsForADomesticConsentUsesTheDomesticEndpoint() = runTest {
        val staged = draft()
        val session = authorisedSession().apply { saveDraft(staged) }

        repository(session).confirmFunds(CONSENT_ID)

        assertNotNull(captured.lastOrNull { it.path == "/v1/consents/payment/domestic/$CONSENT_ID/funds-confirmation" })
        assertNull(
            captured.lastOrNull { "/payment/international/" in it.path },
            "a domestic funds check must never go to the international path",
        )
    }

    /**
     * A session that records no consent type cannot pick an endpoint, so the funds check fails rather
     * than guessing domestic (which would send a standing order's consent id to the wrong endpoint).
     */
    @Test
    fun confirmingFundsWithNoRecordedTypeFailsRatherThanGuessing() = runTest {
        val result = repository(SettingsPaymentAuthSession(MapSettings())).confirmFunds(CONSENT_ID)

        assertIs<NetworkResult.Error<*>>(result)
        assertNull(
            captured.lastOrNull { "funds-confirmation" in it.path },
            "an unknown type must not reach any funds-confirmation endpoint",
        )
    }

    @Test
    fun submittingGoesToTheWorkerPaymentRouteOnTheSession() = runTest {
        repository(authorisedSession()).submitPayment(draft(), CONSENT_ID)

        assertEquals("Session $WORKER_SESSION", requestTo("/v1/payments/domestic").authorization)
    }

    @Test
    fun submittingAnInternationalDraftUsesTheInternationalRoute() = runTest {
        val result = repository(authorisedSession(ConsentType.InternationalSinglePayment))
            .submitPayment(intlDraft(), CONSENT_ID)

        assertIs<NetworkResult.Success<PaymentReceipt>>(result)
        assertNotNull(captured.lastOrNull { it.path == "/v1/payments/international" })
    }

    /** No bank credential of any kind leaves the device. */
    @Test
    fun noCallPresentsABearerToken() = runTest {
        val repository = repository(authorisedSession())
        repository.stagePayment(draft())
        repository.confirmFunds(CONSENT_ID)
        repository.submitPayment(draft(), CONSENT_ID)

        assertTrue(captured.isNotEmpty())
        assertTrue(captured.none { it.authorization.orEmpty().startsWith("Bearer") })
    }

    // endregion

    // region — idempotency keys

    /**
     * The consent and submission bodies differ — only the latter carries `Data.ConsentId` — and the
     * Read/Write profile answers a repeated key over a changed body with `400 U029`. So each POST
     * carries its own key.
     */
    @Test
    fun theTwoWritesCarryTheirOwnIdempotencyKeys() = runTest {
        val session = SettingsPaymentAuthSession(MapSettings())
        val repository = repository(session)
        val draft = draft()

        repository.stagePayment(draft)
        repository.submitPayment(draft, CONSENT_ID)

        assertEquals(CONSENT_KEY, requestTo("/v1/consents/payment/domestic").idempotencyKey)
        assertEquals(PAYMENT_KEY, requestTo("/v1/payments/domestic").idempotencyKey)
    }

    /** Staging wipes whatever the last attempt left behind, so an abandoned flow cannot be redeemed. */
    @Test
    fun stagingReplacesTheFlowLeftBehindByAnAbandonedAttempt() = runTest {
        val session = SettingsPaymentAuthSession(MapSettings()).apply {
            savePending(
                consentId = "old",
                state = "old-state",
                flowId = "old-flow",
                type = ConsentType.DomesticSinglePayment,
            )
        }

        repository(session).stagePayment(draft())

        assertEquals(CONSENT_ID, session.pendingConsentId())
        assertEquals("flow-1", session.pendingFlowId())
        assertTrue(session.matchesPendingState("state-1"))
    }

    // endregion

    // region — the draft that crosses the browser hop

    /**
     * The screen that built the draft does not survive the hop to the bank, so staging is the last
     * moment the instruction can be written somewhere the returning leg will find it.
     */
    @Test
    fun stagingStoresTheDraftForTheReturningLeg() = runTest {
        val session = SettingsPaymentAuthSession(MapSettings())
        val draft = draft()

        repository(session).stagePayment(draft)

        assertEquals(draft, session.draft())
        assertEquals(draft, repository(session).stagedDraft())
    }

    @Test
    fun thereIsNoStagedDraftBeforeAPaymentIsStaged() = runTest {
        assertNull(repository().stagedDraft())
    }

    /** A staged consent hands back the id the authorisation and the submission both hang off. */
    @Test
    fun stagingReturnsTheConsentIdAndAnAuthorisationUrl() = runTest {
        val result = repository().stagePayment(draft())

        val staged = assertIs<NetworkResult.Success<StagedConsent>>(result).data
        assertEquals(CONSENT_ID, staged.consentId)
        assertEquals("flow-1", staged.flowId)
        assertEquals("state-1", staged.state)
        assertTrue(staged.authorizationUrl.startsWith("https://sandbox.ob.hsbc.co.uk/"))
    }

    // endregion
}
