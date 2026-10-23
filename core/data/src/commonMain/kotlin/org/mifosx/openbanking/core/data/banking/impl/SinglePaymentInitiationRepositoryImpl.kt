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
import org.mifosx.openbanking.core.data.banking.SinglePaymentInitiationRepository
import org.mifosx.openbanking.core.data.banking.mapper.toConsentRequest
import org.mifosx.openbanking.core.data.banking.mapper.toIntlConsentRequest
import org.mifosx.openbanking.core.data.banking.mapper.toIntlPaymentReceipt
import org.mifosx.openbanking.core.data.banking.mapper.toIntlPaymentRequest
import org.mifosx.openbanking.core.data.banking.mapper.toPaymentReceipt
import org.mifosx.openbanking.core.data.banking.mapper.toPaymentRequest
import org.mifosx.openbanking.core.data.callback.PaymentAuthSession
import org.mifosx.openbanking.core.model.banking.payment.ConsentType
import org.mifosx.openbanking.core.model.banking.payment.PaymentDraft
import org.mifosx.openbanking.core.model.banking.payment.PaymentReceipt
import org.mifosx.openbanking.core.model.banking.payment.StagedConsent
import org.mifosx.openbanking.core.network.api.Pisp
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult

internal class SinglePaymentInitiationRepositoryImpl(
    private val pisp: Pisp,
    private val paymentAuthSession: PaymentAuthSession,
    private val paymentHistoryRepository: PaymentHistoryRepository,
) : SinglePaymentInitiationRepository {

    override suspend fun stagePayment(draft: PaymentDraft): NetworkResult<StagedConsent, NetworkError> {
        paymentAuthSession.clear()

        val started = if (draft.isInternational()) {
            pisp.createInternationalPaymentConsent(draft.toIntlConsentRequest(), draft.consentIdempotencyKey)
        } else {
            pisp.createDomesticPaymentConsent(draft.toConsentRequest(), draft.consentIdempotencyKey)
        }
        val staged = paymentAuthSession.recordStaged(started, draft.consentType())
        if (staged is NetworkResult.Success) paymentAuthSession.saveDraft(draft)
        return staged
    }

    /**
     * No recorded type means this build cannot tell which endpoint owns the consent. Whether the
     * consent was authorised is the Worker's call: it holds the PSU token and answers 403/401 if not.
     */
    @Suppress("ReturnCount")
    override suspend fun confirmFunds(consentId: String): NetworkResult<Boolean, NetworkError> {
        val type = paymentAuthSession.pendingConsentType()
            ?: return NetworkResult.Error(
                NetworkError.Client.BadRequest("Unknown consent type — cannot choose an endpoint"),
            )
        val result = when (type) {
            ConsentType.DomesticSinglePayment -> pisp.getFundsConfirmation(consentId)
            ConsentType.InternationalSinglePayment ->
                pisp.getInternationalFundsConfirmation(consentId)

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

    override fun stagedDraft(): PaymentDraft? = paymentAuthSession.draft()

    override suspend fun submitPayment(
        draft: PaymentDraft,
        consentId: String,
    ): NetworkResult<PaymentReceipt, NetworkError> {
        val submitted: NetworkResult<PaymentReceipt, NetworkError> = if (draft.isInternational()) {
            when (
                val result = pisp.createInternationalPayment(
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
