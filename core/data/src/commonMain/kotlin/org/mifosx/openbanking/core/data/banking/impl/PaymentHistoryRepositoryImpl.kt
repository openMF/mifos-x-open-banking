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
import org.mifosx.openbanking.core.data.banking.mapper.toPaymentHistoryItem
import org.mifosx.openbanking.core.data.callback.PaymentAuthSession
import org.mifosx.openbanking.core.database.banking.dao.PaymentHistoryDao
import org.mifosx.openbanking.core.model.banking.payment.ConsentType
import org.mifosx.openbanking.core.model.banking.payment.PaymentDisposition
import org.mifosx.openbanking.core.model.banking.payment.PaymentDraft
import org.mifosx.openbanking.core.model.banking.payment.PaymentHistoryItem
import org.mifosx.openbanking.core.model.banking.payment.PaymentReceipt
import org.mifosx.openbanking.core.model.banking.payment.PaymentStageTimestamps
import org.mifosx.openbanking.core.model.banking.payment.PaymentStatus
import org.mifosx.openbanking.core.model.banking.payment.ScheduledPaymentDraft
import org.mifosx.openbanking.core.network.api.ConsentCreationScope
import org.mifosx.openbanking.core.network.api.OAuth
import org.mifosx.openbanking.core.network.api.Pisp
import template.core.base.network.NetworkResult
import kotlin.time.Clock

/**
 * Reads [PaymentHistoryDao.observeRecent] for the hub, writes on payment outcomes, and refreshes
 * in-flight statuses via [Pisp.getDomesticPayment].
 *
 * Stateless — the only thing it holds are its injected collaborators.
 */
internal class PaymentHistoryRepositoryImpl(
    private val dao: PaymentHistoryDao,
    private val pisp: Pisp,
    private val oauth: OAuth,
    private val paymentAuthSession: PaymentAuthSession,
) : PaymentHistoryRepository {

    override fun observeRecent(): Flow<List<PaymentHistoryItem>> =
        dao.observeRecent().map { entities -> entities.map { it.toPaymentHistoryItem() } }

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

    /**
     * Refreshes every in-flight submitted payment.
     *
     * Only rows whose status resolves to [PaymentStatus.PaymentDisposition.InProgress] are
     * refreshed; terminal successes, pre-submission failures, and rejected payments are skipped.
     * Each refreshed row is re-upserted with the updated status and [syncedAt] timestamp.
     */
    @Suppress("ReturnCount")
    override suspend fun refreshStatuses() {
        val entities = dao.observeRecent().first()
        val token = when (val result = oauth.clientCredentialsToken(ConsentCreationScope.PAYMENTS)) {
            is template.core.base.network.NetworkResult.Success -> result.data.accessToken
            is template.core.base.network.NetworkResult.Error -> return
        }

        entities
            .filter { it.paymentId != null && it.errorKind == null }
            .filter { e ->
                val resolved = e.status?.let(PaymentStatus.Companion::fromWire)
                resolved?.disposition == PaymentDisposition.InProgress
            }
            .forEach { entity ->
                val now = Clock.System.now().toEpochMilliseconds().toString()
                val type = entity.paymentType.toConsentType()
                val receipt = type?.let { fetchReceipt(token, entity.paymentId!!, it) }
                dao.upsert(
                    if (receipt == null) {
                        // Keep the last-known status; mark that we tried.
                        entity.copy(syncedAt = now)
                    } else {
                        entity.copy(
                            status = receipt.status.name,
                            settlementDateTime = receipt.settlementDateTime.takeIf { it.isNotBlank() },
                            syncedAt = now,
                        )
                    },
                )
            }
    }

    /**
     * Reads a payment's status from the endpoint belonging to its own consent family.
     *
     * The endpoints are not interchangeable: an id issued by one answers 404 against another, so
     * every international payment used to fail its own status refresh. Null on any failure — a
     * refresh that cannot reach the bank leaves the stored status alone rather than overwriting it.
     *
     * A row whose stored type this build does not recognise never reaches here: the caller skips it
     * rather than guessing an endpoint, which would report another product's answer as this one's.
     */
    private suspend fun fetchReceipt(
        token: String,
        paymentId: String,
        type: ConsentType,
    ): PaymentReceipt? = (pisp.readReceipt(token, paymentId, type) as? NetworkResult.Success)?.data
}
