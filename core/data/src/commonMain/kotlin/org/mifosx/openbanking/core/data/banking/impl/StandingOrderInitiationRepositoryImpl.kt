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
import org.mifosx.openbanking.core.data.banking.StandingOrderInitiationRepository
import org.mifosx.openbanking.core.data.banking.mapper.intlStandingOrderConsentIdOrNull
import org.mifosx.openbanking.core.data.banking.mapper.intlStandingOrderStatusOrEmpty
import org.mifosx.openbanking.core.data.banking.mapper.standingOrderConsentIdOrNull
import org.mifosx.openbanking.core.data.banking.mapper.standingOrderStatusOrEmpty
import org.mifosx.openbanking.core.data.banking.mapper.toIntlStandingOrderConsentRequest
import org.mifosx.openbanking.core.data.banking.mapper.toIntlStandingOrderReceipt
import org.mifosx.openbanking.core.data.banking.mapper.toIntlStandingOrderRequest
import org.mifosx.openbanking.core.data.banking.mapper.toStandingOrderConsentRequest
import org.mifosx.openbanking.core.data.banking.mapper.toStandingOrderReceipt
import org.mifosx.openbanking.core.data.banking.mapper.toStandingOrderRequest
import org.mifosx.openbanking.core.data.callback.PaymentAuthSession
import org.mifosx.openbanking.core.data.util.isDebtorAccountRefusal
import org.mifosx.openbanking.core.data.util.toThrowable
import org.mifosx.openbanking.core.model.banking.payment.ConsentType
import org.mifosx.openbanking.core.model.banking.payment.PaymentReceipt
import org.mifosx.openbanking.core.model.banking.payment.StagedConsent
import org.mifosx.openbanking.core.model.banking.payment.StandingOrderDraft
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

/**
 * The standing-order write path.
 *
 * Structurally the scheduled repository, with the funds-confirmation absence made total and the
 * single execution date replaced by a mandate. The rail is chosen by `currencyOfTransfer` being
 * non-null, the same discriminator both sibling paths use.
 */
internal class StandingOrderInitiationRepositoryImpl(
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
) : StandingOrderInitiationRepository {

    @Suppress("ReturnCount")
    override suspend fun stageStandingOrder(
        draft: StandingOrderDraft,
    ): NetworkResult<StagedConsent, NetworkError> {
        // Clears any single-payment or scheduled draft too, so no two draft keys can both be set.
        paymentAuthSession.clear()

        val tokenResult = oauth.clientCredentialsToken(ConsentCreationScope.PAYMENTS)
        val paymentsToken = when (tokenResult) {
            is NetworkResult.Success -> tokenResult.data.accessToken
            is NetworkResult.Error -> return tokenResult
        }

        val pair = if (draft.isInternational()) {
            stageIntlStandingOrderConsent(paymentsToken, draft)
        } else {
            stageDomesticStandingOrderConsent(paymentsToken, draft)
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
        paymentAuthSession.saveStandingOrderDraft(draft)

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

    override fun stagedDraft(): StandingOrderDraft? = paymentAuthSession.standingOrderDraft()

    override suspend fun submitStandingOrder(
        draft: StandingOrderDraft,
        consentId: String,
    ): NetworkResult<PaymentReceipt, NetworkError> {
        val token = paymentAuthSession.paymentToken()?.accesstoken
            ?: return NetworkResult.Error(
                NetworkError.Client.Unauthorized("No payments token — the consent is not authorised"),
            )

        val submitted: NetworkResult<PaymentReceipt, NetworkError> = if (draft.isInternational()) {
            val request = draft.toIntlStandingOrderRequest(consentId)
            when (val r = pisp.createInternationalStandingOrder(token, request, draft.paymentIdempotencyKey)) {
                is NetworkResult.Success -> NetworkResult.Success(r.data.toIntlStandingOrderReceipt())
                is NetworkResult.Error -> r
            }
        } else {
            val request = draft.toStandingOrderRequest(consentId)
            when (val r = pisp.createDomesticStandingOrder(token, request, draft.paymentIdempotencyKey)) {
                is NetworkResult.Success -> NetworkResult.Success(r.data.toStandingOrderReceipt())
                is NetworkResult.Error -> r
            }
        }

        // Recorded once, after either rail resolves, so the two paths cannot drift on what is saved.
        if (submitted is NetworkResult.Success) {
            paymentHistoryRepository.saveSubmitted(submitted.data, draft)
        }
        return submitted
    }

    private suspend fun stageDomesticStandingOrderConsent(
        token: String,
        draft: StandingOrderDraft,
    ): NetworkResult<Pair<String, String>, NetworkError> {
        val result = pisp.createDomesticStandingOrderConsent(
            paymentsScopeToken = token,
            request = draft.toStandingOrderConsentRequest(),
            idempotencyKey = draft.consentIdempotencyKey,
        )
        return when (result) {
            is NetworkResult.Success -> {
                val id = result.data.standingOrderConsentIdOrNull()
                    ?: return NetworkResult.Error(NetworkError.Client.BadRequest("no ConsentId"))
                NetworkResult.Success(id to result.data.standingOrderStatusOrEmpty())
            }

            is NetworkResult.Error -> result.alsoRecordRefusedPayer(draft)
        }
    }

    private suspend fun stageIntlStandingOrderConsent(
        token: String,
        draft: StandingOrderDraft,
    ): NetworkResult<Pair<String, String>, NetworkError> {
        val result = pisp.createInternationalStandingOrderConsent(
            paymentsScopeToken = token,
            request = draft.toIntlStandingOrderConsentRequest(),
            idempotencyKey = draft.consentIdempotencyKey,
        )
        return when (result) {
            is NetworkResult.Success -> {
                val id = result.data.intlStandingOrderConsentIdOrNull()
                    ?: return NetworkResult.Error(NetworkError.Client.BadRequest("no ConsentId"))
                NetworkResult.Success(id to result.data.intlStandingOrderStatusOrEmpty())
            }

            is NetworkResult.Error -> result.alsoRecordRefusedPayer(draft)
        }
    }

    /**
     * Remembers a payer the bank refused, so the picker stops offering it.
     *
     * Matched on the OBIE error **path** rather than the code, which is what makes it work unchanged
     * on a third product: this rail refuses a card with `U027` at `DebtorAccount.SchemeName` on the
     * domestic side and `U002` at `DebtorAccount.Identification` on the international one, and
     * neither code needs enumerating.
     *
     * The registry is keyed by account and endpoint rather than by product, so a refusal learned here
     * improves the payer picker on send-money and schedule-payment too.
     *
     * Nothing is recorded when the app named no debtor: the bank was rejecting the payer it chose
     * itself, not one this app offered.
     */
    private fun NetworkResult.Error<NetworkError>.alsoRecordRefusedPayer(
        draft: StandingOrderDraft,
    ): NetworkResult.Error<NetworkError> = also {
        val refusedAccountId = draft.debtorAccount?.accountId ?: return@also
        if (error.toThrowable().isDebtorAccountRefusal()) {
            capabilityRegistry.markUnsupported(
                accountId = refusedAccountId,
                endpoint = AccountEndpoint.PaymentDebtor,
            )
        }
    }

    private fun StandingOrderDraft.isInternational(): Boolean = currencyOfTransfer != null

    private fun StandingOrderDraft.consentType(): ConsentType =
        if (isInternational()) {
            ConsentType.InternationalStandingOrder
        } else {
            ConsentType.DomesticStandingOrder
        }
}
