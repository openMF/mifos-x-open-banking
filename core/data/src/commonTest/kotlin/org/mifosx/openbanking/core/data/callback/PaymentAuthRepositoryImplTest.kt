/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.data.callback

import com.russhwolf.settings.MapSettings
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.mifosx.openbanking.core.data.WORKER_SESSION
import org.mifosx.openbanking.core.data.bffClient
import org.mifosx.openbanking.core.data.completedAuthJson
import org.mifosx.openbanking.core.data.jsonHeaders
import org.mifosx.openbanking.core.data.callback.impl.PaymentAuthRepositoryImpl
import org.mifosx.openbanking.core.model.banking.BeneficiaryScheme
import org.mifosx.openbanking.core.model.banking.payment.ConsentType
import org.mifosx.openbanking.core.model.banking.payment.CreditorSelection
import org.mifosx.openbanking.core.model.banking.payment.PaymentDraft
import org.mifosx.openbanking.core.network.api.Pisp
import org.mifosx.openbanking.core.network.api.Session
import template.core.base.network.NetworkResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val CONSENT_ID = "812774903"
private const val STATE = "payment-state-1"
private const val FLOW_ID = "payment-flow-1"
private const val CONSENT_JSON = """{"Data":{"ConsentId":"$CONSENT_ID","Status":"Authorised"}}"""

/**
 * Covers [PaymentAuthRepositoryImpl] — the return leg's data layer.
 *
 * The redirect is matched locally on `state`, then redeemed at the Worker's `/v1/auth/complete` with
 * the pending `flowId`, which attaches the payment leg to the app's existing session. The consent is
 * read back through the Worker on the session; no bank token is ever on the device.
 */
class PaymentAuthRepositoryImplTest {

    private data class Recorded(val path: String, val authorization: String?, val body: String = "")

    private val captured = mutableListOf<Recorded>()

    private fun repository(
        session: PaymentAuthSession = SettingsPaymentAuthSession(MapSettings()),
    ): PaymentAuthRepositoryImpl {
        val client = bffClient { request: HttpRequestData ->
            captured += Recorded(
                path = request.url.encodedPath,
                authorization = request.headers[HttpHeaders.Authorization],
                body = request.body.toByteArray().decodeToString(),
            )
            val body = when (request.url.encodedPath) {
                "/v1/auth/complete" -> completedAuthJson(CONSENT_ID, leg = "payment")
                else -> CONSENT_JSON
            }
            respond(body, HttpStatusCode.OK, jsonHeaders)
        }
        return PaymentAuthRepositoryImpl(
            session = Session(client),
            pisp = Pisp(client),
            paymentAuthSession = session,
        )
    }

    private fun pendingSession(): PaymentAuthSession =
        SettingsPaymentAuthSession(MapSettings()).apply {
            savePending(consentId = CONSENT_ID, state = STATE, flowId = FLOW_ID, type = ConsentType.DomesticSinglePayment)
        }

    /**
     * A staged draft, which is what tells the return leg which rail it is on.
     *
     * `CurrencyOfTransfer` is the discriminator the rest of the app already uses: set on
     * international drafts, absent on domestic ones.
     */
    private fun draft(currencyOfTransfer: String? = null): PaymentDraft = PaymentDraft(
        debtorAccount = null,
        creditor = CreditorSelection(
            name = "Klara Weiss",
            scheme = BeneficiaryScheme.Iban,
            identification = "DE89370400440532013000",
        ),
        amountMinorUnits = 25_000L,
        currency = "GBP",
        reference = null,
        instructionIdentification = "MFX-1",
        endToEndIdentification = "E2E-1",
        consentIdempotencyKey = "consent-key",
        paymentIdempotencyKey = "payment-key",
        currencyOfTransfer = currencyOfTransfer,
    )

    /**
     * A session staged for one consent family, recorded the way production records it.
     *
     * The type is written explicitly rather than left to be inferred: that is the whole change, and
     * a fixture that leaned on the draft would be testing the legacy fallback instead of the path
     * every new authorisation actually takes.
     */
    private fun sessionStaging(type: ConsentType): PaymentAuthSession =
        SettingsPaymentAuthSession(MapSettings()).apply {
            savePending(consentId = CONSENT_ID, state = STATE, flowId = FLOW_ID, type = type)
            saveDraft(draft(if (type == ConsentType.InternationalSinglePayment) "USD" else null))
        }

    // region — reading the consent and redeeming the redirect

    /**
     * The consent is read through the Worker on the session. The Worker uses its client-credentials
     * token (a consent resource is TPP-authenticated); the device presents no bank token at all.
     */
    @Test
    fun readingConsentStatusPresentsTheWorkerSession() = runTest {
        val result = repository(pendingSession()).consentStatus(CONSENT_ID)

        assertIs<NetworkResult.Success<String>>(result)
        val read = assertNotNull(captured.lastOrNull { it.path == "/v1/consents/payment/domestic/$CONSENT_ID" })
        assertEquals("Session $WORKER_SESSION", read.authorization)
    }

    /** A validated redirect is redeemed at the Worker with the pending flow, on the existing session. */
    @Test
    fun exchangingAValidatedRedirectCompletesTheFlowAtTheWorker() = runTest {
        val repository = repository(pendingSession())
        val validation = repository.validateCallback("https://cb/#code=abc&id_token=h.p.s&state=$STATE")
        assertIs<PaymentAuthValidation.Valid>(validation)

        val result = repository.exchangeCode("abc")

        assertIs<NetworkResult.Success<Unit>>(result)
        val complete = assertNotNull(captured.lastOrNull { it.path == "/v1/auth/complete" })
        assertEquals("Session $WORKER_SESSION", complete.authorization)
        val body = Json.parseToJsonElement(complete.body).jsonObject
        assertEquals(FLOW_ID, body["flowId"]?.jsonPrimitive?.content)
        assertEquals("abc", body["code"]?.jsonPrimitive?.content)
        assertEquals("h.p.s", body["idToken"]?.jsonPrimitive?.content)
        assertEquals(STATE, body["state"]?.jsonPrimitive?.content)
    }

    /** A code that was never validated is refused locally — nothing reaches the Worker. */
    @Test
    fun exchangingAnUnvalidatedCodeFailsWithoutCallingTheWorker() = runTest {
        val result = repository(pendingSession()).exchangeCode("abc")

        assertIs<NetworkResult.Error<*>>(result)
        assertTrue(captured.none { it.path == "/v1/auth/complete" })
    }

    /** A redirect without an `id_token` cannot be verified by the Worker, so it is hostile. */
    @Test
    fun aRedirectWithNoIdTokenIsASecurityError() = runTest {
        val result = repository(pendingSession()).validateCallback("https://cb/?code=abc&state=$STATE")

        assertEquals(PaymentAuthValidation.SecurityError, result)
    }

    @Test
    fun consentStatusReportsWhatTheBankSaid() = runTest {
        val result = repository(pendingSession()).consentStatus(CONSENT_ID)

        assertEquals("Authorised", assertIs<NetworkResult.Success<String>>(result).data)
    }

    // endregion

    // region — reading a consent from the endpoint that issued it

    /**
     * The regression that broke every international payment.
     *
     * The read was hardcoded to the domestic path, so an international consent was looked up where
     * it does not exist and answered `400 U011 Resource cannot be found`. The return leg could never
     * observe `AUTH`, and the payment could not complete. Captured live: consent `45282` was created
     * at `international-payment-consents` and read back at `domestic-payment-consents/45282`.
     *
     * Asserted negatively as well as positively — the old code would satisfy "a request was made"
     * and only fails on "and not to the other rail's path".
     */
    @Test
    fun anInternationalConsentIsReadFromTheInternationalEndpoint() = runTest {
        repository(sessionStaging(ConsentType.InternationalSinglePayment)).consentStatus(CONSENT_ID)

        assertNotNull(captured.lastOrNull { "/v1/consents/payment/international/" in it.path })
        assertNull(
            captured.lastOrNull { it.path.contains("/v1/consents/payment/domestic/") },
            "an international consent must never be read at the domestic path",
        )
    }

    @Test
    fun aDomesticConsentIsReadFromTheDomesticEndpoint() = runTest {
        repository(sessionStaging(ConsentType.DomesticSinglePayment)).consentStatus(CONSENT_ID)

        assertNotNull(captured.lastOrNull { "/v1/consents/payment/domestic/" in it.path })
        assertNull(
            captured.lastOrNull { "/v1/consents/payment/international/" in it.path },
            "a domestic consent must never be read at the international path",
        )
    }

    /**
     * A session staged before the draft was persisted has no rail to read.
     *
     * Domestic is the safe default: it reproduces the behaviour such a session already had rather
     * than failing, and it matches how an unknown rail is treated for payments.
     */
    @Test
    fun aSessionWithNoStagedDraftFallsBackToDomestic() = runTest {
        repository(pendingSession()).consentStatus(CONSENT_ID)

        assertNotNull(captured.lastOrNull { "/v1/consents/payment/domestic/" in it.path })
    }

    // endregion

    // region — telling a payment redirect from a sign-in one

    /**
     * Both legs return through the same bus on the same registered redirect URI. Getting this wrong
     * would redeem a payment code as a sign-in and replace the app's session, breaking every read.
     */
    @Test
    fun aRedirectMatchingTheAuthorisationInFlightIsAPaymentRedirect() = runTest {
        val repository = repository(pendingSession())

        assertTrue(repository.isPaymentRedirect("https://cb/?code=abc&state=$STATE"))
    }

    @Test
    fun aRedirectCarryingSomeoneElsesStateIsNotAPaymentRedirect() = runTest {
        val repository = repository(pendingSession())

        assertFalse(repository.isPaymentRedirect("https://cb/?code=abc&state=a-different-state"))
    }

    /** With no payment authorisation in flight, every redirect belongs to the sign-in leg. */
    @Test
    fun noPaymentIsInFlightSoNoRedirectIsAPaymentRedirect() = runTest {
        val repository = repository()

        assertFalse(repository.isPaymentRedirect("https://cb/?code=abc&state=$STATE"))
    }

    // endregion

    // region — a stale callback is not a security event

    /**
     * The split that used to be one branch.
     *
     * `discardAuthorisation()` clears the session the moment a payment finishes, so a callback for a
     * payment that already went through — a restored browser tab, a tapped history entry — arrives
     * with nothing pending. That is the ordinary way to reach this, and calling it a SecurityError
     * accused the customer of tampering for reopening their own link.
     */
    @Test
    fun aCallbackWithNothingInFlightIsReportedAsNoPending() = runTest {
        val result = repository().validateCallback("https://cb/?code=abc&state=$STATE")

        assertEquals(PaymentAuthValidation.NoPending, result)
    }

    /**
     * The other half of the split, and the one that really is a replay signal: a `state` was issued
     * for an authorisation in flight, and the one that came back is not it.
     */
    @Test
    fun aCallbackCarryingTheWrongStateIsStillASecurityError() = runTest {
        val repository = repository(pendingSession())

        val result = repository.validateCallback("https://cb/?code=abc&state=someone-elses-state")

        assertEquals(PaymentAuthValidation.SecurityError, result)
    }

    // endregion

    // region — discarding the authorisation

    /**
     * Called at each of the three points a payment stops being in flight. Leaving the flow behind
     * would let a stale redirect be redeemed against a payment that is no longer in progress.
     */
    @Test
    fun discardingForgetsTheFlowTheConsentAndTheDraft() = runTest {
        val session = pendingSession()

        repository(session).discardAuthorisation()

        assertNull(session.pendingConsentId())
        assertNull(session.pendingFlowId())
        assertNull(session.draft())
        assertFalse(session.matchesPendingState(STATE))
    }

    // endregion
}
