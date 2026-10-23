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
import org.mifosx.openbanking.core.network.api.Pisp
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult

/**
 * The scheduled write path.
 *
 * Structurally the immediate repository minus funds confirmation, which does not exist on either
 * scheduled rail. The rail is chosen by `currencyOfTransfer` being non-null, the same discriminator
 * the immediate path uses.
 */
internal class ScheduledPaymentInitiationRepositoryImpl(
    private val pisp: Pisp,
    private val paymentAuthSession: PaymentAuthSession,
    private val paymentHistoryRepository: PaymentHistoryRepository,
) : ScheduledPaymentInitiationRepository {

    override suspend fun stagePayment(
        draft: ScheduledPaymentDraft,
    ): NetworkResult<StagedConsent, NetworkError> {
        // Clears any single-payment draft too, so the two keys can never both be populated.
        paymentAuthSession.clear()

        val started = if (draft.isInternational()) {
            pisp.createInternationalScheduledPaymentConsent(
                draft.toIntlScheduledConsentRequest(),
                draft.consentIdempotencyKey,
            )
        } else {
            pisp.createDomesticScheduledPaymentConsent(draft.toScheduledConsentRequest(), draft.consentIdempotencyKey)
        }
        val staged = paymentAuthSession.recordStaged(started, draft.consentType())
        if (staged is NetworkResult.Success) paymentAuthSession.saveScheduledDraft(draft)
        return staged
    }

    override fun stagedDraft(): ScheduledPaymentDraft? = paymentAuthSession.scheduledDraft()

    override suspend fun submitPayment(
        draft: ScheduledPaymentDraft,
        consentId: String,
    ): NetworkResult<PaymentReceipt, NetworkError> {
        val submitted: NetworkResult<PaymentReceipt, NetworkError> = if (draft.isInternational()) {
            val request = draft.toIntlScheduledPaymentRequest(consentId)
            when (val r = pisp.createInternationalScheduledPayment(request, draft.paymentIdempotencyKey)) {
                is NetworkResult.Success -> NetworkResult.Success(r.data.toIntlScheduledPaymentReceipt())
                is NetworkResult.Error -> r
            }
        } else {
            val request = draft.toScheduledPaymentRequest(consentId)
            when (val r = pisp.createDomesticScheduledPayment(request, draft.paymentIdempotencyKey)) {
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

    private fun ScheduledPaymentDraft.isInternational(): Boolean = currencyOfTransfer != null

    private fun ScheduledPaymentDraft.consentType(): ConsentType =
        if (isInternational()) {
            ConsentType.InternationalScheduledPayment
        } else {
            ConsentType.DomesticScheduledPayment
        }
}
