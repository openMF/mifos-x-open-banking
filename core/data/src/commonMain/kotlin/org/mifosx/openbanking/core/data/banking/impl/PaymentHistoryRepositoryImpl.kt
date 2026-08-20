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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.mifosx.openbanking.core.data.banking.PaymentHistoryRepository
import org.mifosx.openbanking.core.data.banking.mapper.toConsentType
import org.mifosx.openbanking.core.data.banking.mapper.toEntity
import org.mifosx.openbanking.core.data.banking.mapper.toFailureEntity
import org.mifosx.openbanking.core.data.banking.mapper.toHistoryRow
import org.mifosx.openbanking.core.data.callback.PaymentAuthSession
import org.mifosx.openbanking.core.database.banking.dao.PaymentHistoryDao
import org.mifosx.openbanking.core.model.banking.payment.ConsentType
import org.mifosx.openbanking.core.model.banking.payment.PaymentDraft
import org.mifosx.openbanking.core.model.banking.payment.PaymentHistoryRow
import org.mifosx.openbanking.core.model.banking.payment.PaymentReceipt
import org.mifosx.openbanking.core.model.banking.payment.PaymentStageTimestamps
import org.mifosx.openbanking.core.model.banking.payment.ScheduledPaymentDraft
import org.mifosx.openbanking.core.model.banking.payment.StandingOrderDraft
import kotlin.time.Clock

/**
 * Writes payment outcomes and reads them back for the feature history lists.
 *
 * Stateless — the only thing it holds are its injected collaborators.
 */
internal class PaymentHistoryRepositoryImpl(
    private val dao: PaymentHistoryDao,
    private val paymentAuthSession: PaymentAuthSession,
) : PaymentHistoryRepository {

    /**
     * Stamps the two stage times the bank does not report.
     *
     * OBIE returns a single `CreationDateTime`, so approval and submission are only knowable from
     * what this app observed: the approval instant was recorded by the callback leg and is read back
     * off the session, and submission is now, because this is called the moment the POST succeeds.
     */
    override suspend fun saveSubmitted(receipt: PaymentReceipt, draft: PaymentDraft) {
        dao.upsert(
            receipt.toEntity(
                draft = draft,
                approvedAt = paymentAuthSession.approvedAt(),
                submittedAt = Clock.System.now().toString(),
            ),
        )
    }

    override suspend fun saveSubmitted(receipt: PaymentReceipt, draft: ScheduledPaymentDraft) {
        dao.upsert(
            receipt.toEntity(
                draft = draft,
                approvedAt = paymentAuthSession.approvedAt(),
                submittedAt = Clock.System.now().toString(),
            ),
        )
    }

    override suspend fun saveSubmitted(receipt: PaymentReceipt, draft: StandingOrderDraft) {
        dao.upsert(
            receipt.toEntity(
                draft = draft,
                approvedAt = paymentAuthSession.approvedAt(),
                submittedAt = Clock.System.now().toString(),
            ),
        )
    }

    override suspend fun saveFailed(
        draft: StandingOrderDraft,
        errorKind: String,
        errorDescription: String,
    ) {
        val now = Clock.System.now().toEpochMilliseconds().toString()
        dao.upsert(draft.toFailureEntity(errorKind, errorDescription).copy(creationDateTime = now))
    }

    override suspend fun saveFailed(
        draft: ScheduledPaymentDraft,
        errorKind: String,
        errorDescription: String,
    ) {
        val now = Clock.System.now().toEpochMilliseconds().toString()
        dao.upsert(draft.toFailureEntity(errorKind, errorDescription).copy(creationDateTime = now))
    }

    override suspend fun saveFailed(draft: PaymentDraft, errorKind: String, errorDescription: String) {
        val now = Clock.System.now().toEpochMilliseconds().toString()
        dao.upsert(draft.toFailureEntity(errorKind, errorDescription).copy(creationDateTime = now))
    }

    /** Null when no row exists, so a caller cannot mistake "unknown" for "domestic". */
    override suspend fun consentTypeOf(paymentId: String): ConsentType? =
        dao.observeById(paymentId).first()?.let { it.paymentType.toConsentType() }

    /**
     * Null when no row exists, and null columns within it when the stage was never observed — the
     * detail timeline draws an undated stage rather than filling the gap with a nearby timestamp.
     */
    override suspend fun stageTimestampsOf(paymentId: String): PaymentStageTimestamps? =
        dao.observeById(paymentId).first()?.let {
            PaymentStageTimestamps(approvedAt = it.approvedAt, submittedAt = it.submittedAt)
        }

    /** Rows the mapper cannot resolve are dropped rather than shown as an unknown product. */
    override fun observeHistory(
        types: Set<ConsentType>,
        limit: Int,
    ): Flow<List<PaymentHistoryRow>> =
        dao.observeByType(types.map { it.wireValue }, limit)
            .map { rows -> rows.mapNotNull { it.toHistoryRow() } }

    override suspend fun recordStatus(paymentId: String, receipt: PaymentReceipt) {
        dao.updateStatus(
            paymentId = paymentId,
            status = receipt.status.name,
            settledAt = receipt.settlementDateTime.takeIf { it.isNotBlank() },
            syncedAt = Clock.System.now().toString(),
        )
    }
}
