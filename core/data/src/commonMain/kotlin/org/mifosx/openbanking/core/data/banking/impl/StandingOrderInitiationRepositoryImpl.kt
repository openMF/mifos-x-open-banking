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
import org.mifosx.openbanking.core.data.banking.StandingOrderInitiationRepository
import org.mifosx.openbanking.core.data.banking.mapper.toIntlStandingOrderConsentRequest
import org.mifosx.openbanking.core.data.banking.mapper.toIntlStandingOrderReceipt
import org.mifosx.openbanking.core.data.banking.mapper.toIntlStandingOrderRequest
import org.mifosx.openbanking.core.data.banking.mapper.toStandingOrderConsentRequest
import org.mifosx.openbanking.core.data.banking.mapper.toStandingOrderReceipt
import org.mifosx.openbanking.core.data.banking.mapper.toStandingOrderRequest
import org.mifosx.openbanking.core.data.callback.PaymentAuthSession
import org.mifosx.openbanking.core.model.banking.payment.ConsentType
import org.mifosx.openbanking.core.model.banking.payment.PaymentReceipt
import org.mifosx.openbanking.core.model.banking.payment.StagedConsent
import org.mifosx.openbanking.core.model.banking.payment.StandingOrderDraft
import org.mifosx.openbanking.core.network.api.Pisp
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult

/**
 * The standing-order write path.
 *
 * Structurally the scheduled repository, with the funds-confirmation absence made total and the
 * single execution date replaced by a mandate. The rail is chosen by `currencyOfTransfer` being
 * non-null, the same discriminator both sibling paths use.
 */
internal class StandingOrderInitiationRepositoryImpl(
    private val pisp: Pisp,
    private val paymentAuthSession: PaymentAuthSession,
    private val paymentHistoryRepository: PaymentHistoryRepository,
) : StandingOrderInitiationRepository {

    override suspend fun stageStandingOrder(
        draft: StandingOrderDraft,
    ): NetworkResult<StagedConsent, NetworkError> {
        // Clears any single-payment or scheduled draft too, so no two draft keys can both be set.
        paymentAuthSession.clear()

        val started = if (draft.isInternational()) {
            pisp.createInternationalStandingOrderConsent(
                draft.toIntlStandingOrderConsentRequest(),
                draft.consentIdempotencyKey,
            )
        } else {
            pisp.createDomesticStandingOrderConsent(draft.toStandingOrderConsentRequest(), draft.consentIdempotencyKey)
        }
        val staged = paymentAuthSession.recordStaged(started, draft.consentType())
        if (staged is NetworkResult.Success) paymentAuthSession.saveStandingOrderDraft(draft)
        return staged
    }

    override fun stagedDraft(): StandingOrderDraft? = paymentAuthSession.standingOrderDraft()

    override suspend fun submitStandingOrder(
        draft: StandingOrderDraft,
        consentId: String,
    ): NetworkResult<PaymentReceipt, NetworkError> {
        val submitted: NetworkResult<PaymentReceipt, NetworkError> = if (draft.isInternational()) {
            val request = draft.toIntlStandingOrderRequest(consentId)
            when (val r = pisp.createInternationalStandingOrder(request, draft.paymentIdempotencyKey)) {
                is NetworkResult.Success -> NetworkResult.Success(r.data.toIntlStandingOrderReceipt())
                is NetworkResult.Error -> r
            }
        } else {
            val request = draft.toStandingOrderRequest(consentId)
            when (val r = pisp.createDomesticStandingOrder(request, draft.paymentIdempotencyKey)) {
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

    private fun StandingOrderDraft.isInternational(): Boolean = currencyOfTransfer != null

    private fun StandingOrderDraft.consentType(): ConsentType =
        if (isInternational()) {
            ConsentType.InternationalStandingOrder
        } else {
            ConsentType.DomesticStandingOrder
        }
}
