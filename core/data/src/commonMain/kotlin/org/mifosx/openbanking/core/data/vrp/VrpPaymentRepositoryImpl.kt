/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.data.vrp

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.mifosx.openbanking.core.database.vrp.dao.VrpConsentDao
import org.mifosx.openbanking.core.database.vrp.dao.VrpPaymentDao
import org.mifosx.openbanking.core.model.banking.payment.PaymentDisposition
import org.mifosx.openbanking.core.model.vrp.FundsAvailability
import org.mifosx.openbanking.core.model.vrp.Money
import org.mifosx.openbanking.core.model.vrp.PeriodUsage
import org.mifosx.openbanking.core.model.vrp.VrpConsent
import org.mifosx.openbanking.core.model.vrp.VrpPayment
import org.mifosx.openbanking.core.network.api.ConsentCreationScope
import org.mifosx.openbanking.core.network.api.OAuth
import org.mifosx.openbanking.core.network.api.Vrp
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult
import kotlin.time.Clock

class VrpPaymentRepositoryImpl(
    private val vrp: Vrp,
    private val oauth: OAuth,
    private val consentDao: VrpConsentDao,
    private val paymentDao: VrpPaymentDao,
    private val tokens: VrpTokenProvider,
) : VrpPaymentRepository {

    override fun observeForConsent(consentId: String): Flow<List<VrpPayment>> =
        paymentDao.observeForConsent(consentId).map { rows -> rows.map { it.toVrpPayment() } }

    override fun observeUsage(consentId: String): Flow<List<PeriodUsage>> =
        combine(
            consentDao.observeById(consentId),
            paymentDao.observeForConsent(consentId),
        ) { consent, payments ->
            consent?.let { row ->
                currentUsage(row.toVrpConsent(), payments.map { it.toVrpPayment() })
            }.orEmpty()
        }

    @Suppress("ReturnCount")
    override suspend fun checkFunds(
        consentId: String,
        amount: Money,
    ): NetworkResult<FundsAvailability, NetworkError> {
        val request = checkFundsRequest(consentId, amount)

        val result = callAsCustomer(consentId) { token ->
            vrp.checkFunds(token, consentId, request, fundsCheckKey(consentId, amount))
        }

        return when (result) {
            is NetworkResult.Error -> result

            is NetworkResult.Success -> {
                val funds = result.data.toFundsAvailability()
                    ?: return NetworkResult.Error(
                        NetworkError.Serialization(
                            IllegalStateException("Funds confirmation response was unusable"),
                        ),
                    )
                NetworkResult.Success(funds)
            }
        }
    }

    @Suppress("ReturnCount")
    override suspend fun pay(
        consent: VrpConsent,
        amount: Money,
        instructionIdentification: String,
        endToEndIdentification: String,
        idempotencyKey: String,
    ): NetworkResult<VrpPayment, NetworkError> {
        val request = consent.toInitiatePayment(
            amount = amount,
            instructionIdentification = instructionIdentification,
            endToEndIdentification = endToEndIdentification,
        )

        val result = callAsCustomer(consent.consentId) { token ->
            vrp.createPayment(token, request, idempotencyKey)
        }

        return when (result) {
            is NetworkResult.Error -> result

            is NetworkResult.Success -> {
                val payment = result.data
                    .toVrpPayment(localId = idempotencyKey, syncedAt = Clock.System.now())
                    ?: return NetworkResult.Error(
                        NetworkError.Serialization(IllegalStateException("Payment response was unusable")),
                    )
                paymentDao.upsert(payment.toEntity())
                NetworkResult.Success(payment)
            }
        }
    }

    @Suppress("ReturnCount")
    override suspend fun refreshStatus(payment: VrpPayment): NetworkResult<VrpPayment, NetworkError> {
        val paymentId = payment.paymentId ?: return NetworkResult.Success(payment)

        val token = when (val result = oauth.clientCredentialsToken(ConsentCreationScope.PAYMENTS)) {
            is NetworkResult.Success -> result.data.accessToken
            is NetworkResult.Error -> return result
        }

        return when (val result = vrp.getPayment(token, paymentId)) {
            is NetworkResult.Error -> result

            is NetworkResult.Success -> {
                val now = Clock.System.now()
                val fresh = result.data.toVrpPayment(localId = payment.localId, syncedAt = now)
                    ?: return NetworkResult.Error(
                        NetworkError.Serialization(IllegalStateException("Payment response was unusable")),
                    )
                val merged = fresh.copy(
                    settledAt = payment.settledAt ?: now.takeIf { fresh.hasSettled() },
                )
                paymentDao.upsert(merged.toEntity())
                NetworkResult.Success(merged)
            }
        }
    }

    /**
     * Runs [call] on the consent's access token, once more on a freshly minted one if the first
     * attempt is refused.
     *
     * An access token lives about five minutes while a consent lives indefinitely, so a token held
     * across a quiet period is expected to be refused rather than exceptional.
     */
    private suspend fun <T> callAsCustomer(
        consentId: String,
        call: suspend (String) -> NetworkResult<T, NetworkError>,
    ): NetworkResult<T, NetworkError> {
        val attempt = when (val token = tokens.accessToken(consentId)) {
            is NetworkResult.Success -> call(token.data)
            is NetworkResult.Error -> return token
        }

        val refused = attempt is NetworkResult.Error &&
            attempt.error is NetworkError.Client.Unauthorized

        return if (!refused) {
            attempt
        } else {
            when (val refreshed = tokens.refreshAccessToken(consentId)) {
                is NetworkResult.Success -> call(refreshed.data)
                is NetworkResult.Error -> attempt
            }
        }
    }
}

/**
 * Whether the bank has posted this payment.
 *
 * Read from the status rather than from `StatusUpdateDateTime`, which the bank writes once at
 * creation and never revises.
 */
private fun VrpPayment.hasSettled(): Boolean =
    status.disposition == PaymentDisposition.TerminalSuccess

/** Identifies a funds check, which initiates nothing and may be repeated. */
private fun fundsCheckKey(consentId: String, amount: Money): String =
    "$consentId-funds-${amount.minorUnits}"
