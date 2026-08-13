/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.data.banking.impl

import org.mifosx.openbanking.core.data.banking.AccountCapabilityRegistry
import org.mifosx.openbanking.core.data.banking.PaymentHistoryRepository
import org.mifosx.openbanking.core.data.banking.SinglePaymentInitiationRepository
import org.mifosx.openbanking.core.data.banking.mapper.consentIdOrNull
import org.mifosx.openbanking.core.data.banking.mapper.intlConsentIdOrNull
import org.mifosx.openbanking.core.data.banking.mapper.intlStatusOrEmpty
import org.mifosx.openbanking.core.data.banking.mapper.statusOrEmpty
import org.mifosx.openbanking.core.data.banking.mapper.toConsentRequest
import org.mifosx.openbanking.core.data.banking.mapper.toIntlConsentRequest
import org.mifosx.openbanking.core.data.banking.mapper.toIntlPaymentReceipt
import org.mifosx.openbanking.core.data.banking.mapper.toIntlPaymentRequest
import org.mifosx.openbanking.core.data.banking.mapper.toPaymentReceipt
import org.mifosx.openbanking.core.data.banking.mapper.toPaymentRequest
import org.mifosx.openbanking.core.data.callback.PaymentAuthSession
import org.mifosx.openbanking.core.data.util.isDebtorAccountRefusal
import org.mifosx.openbanking.core.data.util.toThrowable
import org.mifosx.openbanking.core.model.banking.payment.ConsentType
import org.mifosx.openbanking.core.model.banking.payment.PaymentDraft
import org.mifosx.openbanking.core.model.banking.payment.PaymentReceipt
import org.mifosx.openbanking.core.model.banking.payment.StagedConsent
import org.mifosx.openbanking.core.model.hsbcProduct.AccountEndpoint
import org.mifosx.openbanking.core.network.api.ConsentCreationScope
import org.mifosx.openbanking.core.network.api.OAuth
import org.mifosx.openbanking.core.network.api.Pisp
import org.mifosx.openbanking.core.network.authorize.generateConsentAuthorizationUrl
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult
import kotlin.time.Clock

private const val AUTHORIZE_PATH = "/obie/open-banking/v1.1/oauth2/authorize"
private const val RESPONSE_TYPE = "code id_token"

internal class SinglePaymentInitiationRepositoryImpl(
    private val pisp: Pisp,
    private val oauth: OAuth,
    private val paymentAuthSession: PaymentAuthSession,
    private val capabilityRegistry: AccountCapabilityRegistry,
    private val signingKeyPem: String,
    private val clientId: String,
    private val kid: String,
    private val bankHost: String,
    private val authorizeHost: String,
    private val redirectUri: String,
    private val paymentHistoryRepository: PaymentHistoryRepository,
) : SinglePaymentInitiationRepository {

    @Suppress("ReturnCount")
    override suspend fun stagePayment(draft: PaymentDraft): NetworkResult<StagedConsent, NetworkError> {
        paymentAuthSession.clear()

        val tokenResult = oauth.clientCredentialsToken(ConsentCreationScope.PAYMENTS)
        val paymentsToken = when (tokenResult) {
            is NetworkResult.Success -> tokenResult.data.accessToken
            is NetworkResult.Error -> return tokenResult
        }

        val pair = if (draft.isInternational()) {
            stageIntlConsent(paymentsToken, draft)
        } else {
            stageDomesticConsent(paymentsToken, draft)
        }

        val (consentId, status) = when (pair) {
            is NetworkResult.Success -> pair.data
            is NetworkResult.Error -> return pair
        }

        val auth = generateConsentAuthorizationUrl(
            audience = "https://$bankHost",
            authorizeUrl = "https://$authorizeHost$AUTHORIZE_PATH",
            clientId = clientId,
            kid = kid,
            scope = ConsentCreationScope.PAYMENTS,
            responseType = RESPONSE_TYPE,
            redirectUri = redirectUri,
            consentId = consentId,
            signingKeyPem = signingKeyPem,
            nowEpochSeconds = Clock.System.now().epochSeconds,
        )

        paymentAuthSession.savePending(
            consentId = consentId,
            state = auth.state,
            nonce = auth.nonce,
            type = draft.consentType(),
        )
        paymentAuthSession.saveDraft(draft)

        return NetworkResult.Success(
            StagedConsent(
                consentId = consentId,
                status = status,
                authorizationUrl = auth.authorizationUrl,
                state = auth.state,
                nonce = auth.nonce,
            ),
        )
    }

    private suspend fun stageDomesticConsent(
        token: String,
        draft: PaymentDraft,
    ): NetworkResult<Pair<String, String>, NetworkError> {
        val result = pisp.createDomesticPaymentConsent(
            paymentsScopeToken = token,
            request = draft.toConsentRequest(),
            idempotencyKey = draft.consentIdempotencyKey,
        )
        return when (result) {
            is NetworkResult.Success -> {
                val id = result.data.consentIdOrNull()
                    ?: return NetworkResult.Error(NetworkError.Client.BadRequest("no ConsentId"))
                NetworkResult.Success(id to result.data.statusOrEmpty())
            }
            is NetworkResult.Error -> result.alsoRecordRefusedPayer(draft)
        }
    }

    private suspend fun stageIntlConsent(
        token: String,
        draft: PaymentDraft,
    ): NetworkResult<Pair<String, String>, NetworkError> {
        val result = pisp.createInternationalPaymentConsent(
            paymentsScopeToken = token,
            request = draft.toIntlConsentRequest(),
            idempotencyKey = draft.consentIdempotencyKey,
        )
        return when (result) {
            is NetworkResult.Success -> {
                val id = result.data.intlConsentIdOrNull()
                    ?: return NetworkResult.Error(NetworkError.Client.BadRequest("no ConsentId"))
                NetworkResult.Success(id to result.data.intlStatusOrEmpty())
            }
            is NetworkResult.Error -> result.alsoRecordRefusedPayer(draft)
        }
    }

    /**
     * Two guards before the call, and each says something different: no PSU token means the consent
     * was never authorised, while no recorded type means this build cannot tell which endpoint owns
     * the consent. Collapsing them would report one as the other.
     */
    @Suppress("ReturnCount")
    override suspend fun confirmFunds(consentId: String): NetworkResult<Boolean, NetworkError> {
        val token = paymentAuthSession.paymentToken()?.accesstoken
            ?: return NetworkResult.Error(
                NetworkError.Client.Unauthorized("No payments token — the consent is not authorised"),
            )
        val type = paymentAuthSession.pendingConsentType()
            ?: return NetworkResult.Error(
                NetworkError.Client.BadRequest("Unknown consent type — cannot choose an endpoint"),
            )
        val result = when (type) {
            ConsentType.DomesticSinglePayment -> pisp.getFundsConfirmation(token, consentId)
            ConsentType.InternationalSinglePayment ->
                pisp.getInternationalFundsConfirmation(token, consentId)

            // Unreachable in practice — a deferred consent is staged by its own repository and its
            // return leg never asks. The compiler demands an answer, and refusing is the honest one:
            // OBIE defines no funds-confirmation endpoint for domestic-scheduled at all, HSBC marks
            // the international-scheduled one unsupported for every brand, and neither standing-order
            // consent defines the sub-resource at all. Answering `true` here would invent a
            // confirmation the bank never gave.
            ConsentType.DomesticScheduledPayment,
            ConsentType.InternationalScheduledPayment,
            ConsentType.DomesticStandingOrder,
            ConsentType.InternationalStandingOrder,
            -> return NetworkResult.Error(
                NetworkError.Client.BadRequest("Deferred payments have no funds confirmation"),
            )
        }
        return when (result) {
            is NetworkResult.Success ->
                NetworkResult.Success(result.data.data?.fundsAvailableResult?.fundsAvailable == true)
            is NetworkResult.Error -> result
        }
    }

    private fun NetworkResult.Error<NetworkError>.alsoRecordRefusedPayer(
        draft: PaymentDraft,
    ): NetworkResult.Error<NetworkError> = also {
        // A refusal with no debtor cannot be attributed to an account: the bank was rejecting the
        // payer it selected itself, not one this app offered, so there is nothing to remember.
        val refusedAccountId = draft.debtorAccount?.accountId ?: return@also
        if (error.toThrowable().isDebtorAccountRefusal()) {
            capabilityRegistry.markUnsupported(
                accountId = refusedAccountId,
                endpoint = AccountEndpoint.PaymentDebtor,
            )
        }
    }

    override fun stagedDraft(): PaymentDraft? = paymentAuthSession.draft()

    override suspend fun submitPayment(
        draft: PaymentDraft,
        consentId: String,
    ): NetworkResult<PaymentReceipt, NetworkError> {
        val token = paymentAuthSession.paymentToken()?.accesstoken
            ?: return NetworkResult.Error(
                NetworkError.Client.Unauthorized("No payments token — the consent is not authorised"),
            )
        val submitted: NetworkResult<PaymentReceipt, NetworkError> = if (draft.isInternational()) {
            when (
                val result = pisp.createInternationalPayment(
                    psuAccessToken = token,
                    request = draft.toIntlPaymentRequest(consentId),
                    idempotencyKey = draft.paymentIdempotencyKey,
                )
            ) {
                is NetworkResult.Success -> NetworkResult.Success(result.data.toIntlPaymentReceipt())
                is NetworkResult.Error -> result
            }
        } else {
            when (
                val result = pisp.createDomesticPayment(
                    psuAccessToken = token,
                    request = draft.toPaymentRequest(consentId),
                    idempotencyKey = draft.paymentIdempotencyKey,
                )
            ) {
                is NetworkResult.Success -> NetworkResult.Success(result.data.toPaymentReceipt())
                is NetworkResult.Error -> result
            }
        }
        // Recorded once, after either rail resolves, so the two paths cannot drift on what is saved.
        if (submitted is NetworkResult.Success) {
            paymentHistoryRepository.saveSubmitted(submitted.data, draft)
        }
        return submitted
    }

    private fun PaymentDraft.isInternational(): Boolean = currencyOfTransfer != null

    /**
     * The consent family this draft stages. `CurrencyOfTransfer` is the rail; the product is single
     * payment, because a [PaymentDraft] cannot express any other.
     */
    private fun PaymentDraft.consentType(): ConsentType =
        if (isInternational()) {
            ConsentType.InternationalSinglePayment
        } else {
            ConsentType.DomesticSinglePayment
        }
}
