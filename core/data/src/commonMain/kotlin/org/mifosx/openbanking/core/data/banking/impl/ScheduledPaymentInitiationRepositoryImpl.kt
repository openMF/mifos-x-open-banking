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

import org.mifosx.openbanking.core.data.banking.PaymentHistoryRepository
import org.mifosx.openbanking.core.data.banking.ScheduledPaymentInitiationRepository
import org.mifosx.openbanking.core.data.banking.mapper.intlScheduledConsentIdOrNull
import org.mifosx.openbanking.core.data.banking.mapper.intlScheduledStatusOrEmpty
import org.mifosx.openbanking.core.data.banking.mapper.scheduledConsentIdOrNull
import org.mifosx.openbanking.core.data.banking.mapper.scheduledStatusOrEmpty
import org.mifosx.openbanking.core.data.banking.mapper.toIntlScheduledConsentRequest
import org.mifosx.openbanking.core.data.banking.mapper.toIntlScheduledPaymentReceipt
import org.mifosx.openbanking.core.data.banking.mapper.toIntlScheduledPaymentRequest
import org.mifosx.openbanking.core.data.banking.mapper.toScheduledConsentRequest
import org.mifosx.openbanking.core.data.banking.mapper.toScheduledPaymentReceipt
import org.mifosx.openbanking.core.data.banking.mapper.toScheduledPaymentRequest
import org.mifosx.openbanking.core.data.callback.PaymentAuthSession
import org.mifosx.openbanking.core.model.banking.payment.ConsentType
import org.mifosx.openbanking.core.model.banking.payment.PaymentReceipt
import org.mifosx.openbanking.core.model.banking.payment.ScheduledPaymentDraft
import org.mifosx.openbanking.core.model.banking.payment.StagedConsent
import org.mifosx.openbanking.core.network.api.ConsentCreationScope
import org.mifosx.openbanking.core.network.api.OAuth
import org.mifosx.openbanking.core.network.api.Pisp
import org.mifosx.openbanking.core.network.authorize.generateConsentAuthorizationUrl
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult
import kotlin.time.Clock

private const val AUTHORIZE_PATH = "/obie/open-banking/v1.1/oauth2/authorize"
private const val RESPONSE_TYPE = "code id_token"

/**
 * The scheduled write path.
 *
 * Structurally the immediate repository minus funds confirmation, which does not exist on either
 * scheduled rail. The rail is chosen by `currencyOfTransfer` being non-null, the same discriminator
 * the immediate path uses.
 */
internal class ScheduledPaymentInitiationRepositoryImpl(
    private val pisp: Pisp,
    private val oauth: OAuth,
    private val paymentAuthSession: PaymentAuthSession,
    private val signingKeyPem: String,
    private val clientId: String,
    private val kid: String,
    private val bankHost: String,
    private val authorizeHost: String,
    private val redirectUri: String,
    private val paymentHistoryRepository: PaymentHistoryRepository,
) : ScheduledPaymentInitiationRepository {

    @Suppress("ReturnCount")
    override suspend fun stagePayment(
        draft: ScheduledPaymentDraft,
    ): NetworkResult<StagedConsent, NetworkError> {
        // Clears any single-payment draft too, so the two keys can never both be populated.
        paymentAuthSession.clear()

        val tokenResult = oauth.clientCredentialsToken(ConsentCreationScope.PAYMENTS)
        val paymentsToken = when (tokenResult) {
            is NetworkResult.Success -> tokenResult.data.accessToken
            is NetworkResult.Error -> return tokenResult
        }

        val pair = if (draft.isInternational()) {
            stageIntlScheduledConsent(paymentsToken, draft)
        } else {
            stageDomesticScheduledConsent(paymentsToken, draft)
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
        paymentAuthSession.saveScheduledDraft(draft)

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

    override fun stagedDraft(): ScheduledPaymentDraft? = paymentAuthSession.scheduledDraft()

    override suspend fun submitPayment(
        draft: ScheduledPaymentDraft,
        consentId: String,
    ): NetworkResult<PaymentReceipt, NetworkError> {
        val token = paymentAuthSession.paymentToken()?.accesstoken
            ?: return NetworkResult.Error(
                NetworkError.Client.Unauthorized("No payments token — the consent is not authorised"),
            )

        val submitted: NetworkResult<PaymentReceipt, NetworkError> = if (draft.isInternational()) {
            val request = draft.toIntlScheduledPaymentRequest(consentId)
            when (val r = pisp.createInternationalScheduledPayment(token, request, draft.paymentIdempotencyKey)) {
                is NetworkResult.Success -> NetworkResult.Success(r.data.toIntlScheduledPaymentReceipt())
                is NetworkResult.Error -> r
            }
        } else {
            val request = draft.toScheduledPaymentRequest(consentId)
            when (val r = pisp.createDomesticScheduledPayment(token, request, draft.paymentIdempotencyKey)) {
                is NetworkResult.Success -> NetworkResult.Success(r.data.toScheduledPaymentReceipt())
                is NetworkResult.Error -> r
            }
        }

        // Recorded once, after either rail resolves, so the two paths cannot drift on what is saved.
        if (submitted is NetworkResult.Success) {
            paymentHistoryRepository.saveSubmitted(submitted.data, draft)
        }
        return submitted
    }

    private suspend fun stageDomesticScheduledConsent(
        token: String,
        draft: ScheduledPaymentDraft,
    ): NetworkResult<Pair<String, String>, NetworkError> {
        val result = pisp.createDomesticScheduledPaymentConsent(
            paymentsScopeToken = token,
            request = draft.toScheduledConsentRequest(),
            idempotencyKey = draft.consentIdempotencyKey,
        )
        return when (result) {
            is NetworkResult.Success -> {
                val id = result.data.scheduledConsentIdOrNull()
                    ?: return NetworkResult.Error(NetworkError.Client.BadRequest("no ConsentId"))
                NetworkResult.Success(id to result.data.scheduledStatusOrEmpty())
            }
            is NetworkResult.Error -> result
        }
    }

    private suspend fun stageIntlScheduledConsent(
        token: String,
        draft: ScheduledPaymentDraft,
    ): NetworkResult<Pair<String, String>, NetworkError> {
        val result = pisp.createInternationalScheduledPaymentConsent(
            paymentsScopeToken = token,
            request = draft.toIntlScheduledConsentRequest(),
            idempotencyKey = draft.consentIdempotencyKey,
        )
        return when (result) {
            is NetworkResult.Success -> {
                val id = result.data.intlScheduledConsentIdOrNull()
                    ?: return NetworkResult.Error(NetworkError.Client.BadRequest("no ConsentId"))
                NetworkResult.Success(id to result.data.intlScheduledStatusOrEmpty())
            }
            is NetworkResult.Error -> result
        }
    }

    private fun ScheduledPaymentDraft.isInternational(): Boolean = currencyOfTransfer != null

    private fun ScheduledPaymentDraft.consentType(): ConsentType =
        if (isInternational()) {
            ConsentType.InternationalScheduledPayment
        } else {
            ConsentType.DomesticScheduledPayment
        }
}
