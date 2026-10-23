/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.network.api
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.http.Url
import org.mifosx.openbanking.core.network.model.ais.accountDetails.AccountDetailsResponse
import org.mifosx.openbanking.core.network.model.ais.accounts.AccountsResponse
import org.mifosx.openbanking.core.network.model.ais.balances.BalancesResponse
import org.mifosx.openbanking.core.network.model.ais.beneficiaries.BeneficiariesResponse
import org.mifosx.openbanking.core.network.model.ais.directDebits.DirectDebitsResponse
import org.mifosx.openbanking.core.network.model.ais.parties.PartiesResponse
import org.mifosx.openbanking.core.network.model.ais.party.PartyResponse
import org.mifosx.openbanking.core.network.model.ais.product.ProductResponse
import org.mifosx.openbanking.core.network.model.ais.scheduledPayments.ScheduledPaymentsResponse
import org.mifosx.openbanking.core.network.model.ais.standingOrders.StandingOrdersResponse
import org.mifosx.openbanking.core.network.model.ais.statementDetails.StatementDetailsResponse
import org.mifosx.openbanking.core.network.model.ais.statementTransactions.StatementTransactionsResponse
import org.mifosx.openbanking.core.network.model.ais.statements.StatementsResponse
import org.mifosx.openbanking.core.network.model.ais.transactions.TransactionsResponse
import org.mifosx.openbanking.core.network.model.hsbcPermission.response.HSBCCreateConsentResponse
import org.mifosx.openbanking.core.network.result.toNetworkResult
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult

private const val AIS = "/v1/ais"
private const val CONSENTS = "/v1/consents/ais"

/**
 * The Worker's AIS surface (bff/src/routes.ts). One call per HSBC read; every call is session-gated
 * (`Authorization: Session`, attached by the client) and returns a [NetworkResult]. The Worker holds
 * the PSU token, refreshes it, and rewrites every `Links` URL to its own `/v1/ais/page?cursor=` form.
 * AIS consent creation lives in [Session.startAisConsent], because it runs before a session exists.
 */
@Suppress("TooManyFunctions")
class Aisp(
    private val httpClient: HttpClient,
) {

    /** The sign-in consent, read on the Worker's client-credentials token (own consent only). */
    suspend fun getConsent(consentId: String): NetworkResult<HSBCCreateConsentResponse, NetworkError> =
        httpClient.get("$CONSENTS/$consentId").toNetworkResult()

    suspend fun deleteConsent(consentId: String): NetworkResult<Unit, NetworkError> =
        httpClient.delete("$CONSENTS/$consentId").toNetworkResult()

    suspend fun getAccounts(): NetworkResult<AccountsResponse, NetworkError> =
        httpClient.get("$AIS/accounts").toNetworkResult()

    suspend fun getAccountDetails(accountId: String): NetworkResult<AccountDetailsResponse, NetworkError> =
        httpClient.get("$AIS/accounts/$accountId").toNetworkResult()

    suspend fun getBalances(accountId: String): NetworkResult<BalancesResponse, NetworkError> =
        httpClient.get("$AIS/accounts/$accountId/balances").toNetworkResult()

    suspend fun getBeneficiaries(accountId: String): NetworkResult<BeneficiariesResponse, NetworkError> =
        httpClient.get("$AIS/accounts/$accountId/beneficiaries").toNetworkResult()

    suspend fun getDirectDebits(accountId: String): NetworkResult<DirectDebitsResponse, NetworkError> =
        httpClient.get("$AIS/accounts/$accountId/direct-debits").toNetworkResult()

    suspend fun getProduct(accountId: String): NetworkResult<ProductResponse, NetworkError> =
        httpClient.get("$AIS/accounts/$accountId/product").toNetworkResult()

    suspend fun getParty(accountId: String): NetworkResult<PartyResponse, NetworkError> =
        httpClient.get("$AIS/accounts/$accountId/party").toNetworkResult()

    suspend fun getParties(accountId: String): NetworkResult<PartiesResponse, NetworkError> =
        httpClient.get("$AIS/accounts/$accountId/parties").toNetworkResult()

    suspend fun getTransactions(accountId: String): NetworkResult<TransactionsResponse, NetworkError> =
        httpClient.get("$AIS/accounts/$accountId/transactions").toNetworkResult()

    /**
     * Follows a `Links.Next` from a prior response. The Worker has already rewritten it to
     * `…/v1/ais/page?cursor=<opaque>`; only the cursor is taken from it and the request always goes to
     * the configured Worker, so a link can never carry the session to another host.
     */
    suspend fun getTransactionsPage(nextLink: String): NetworkResult<TransactionsResponse, NetworkError> {
        val cursor = runCatching { Url(nextLink).parameters["cursor"] }.getOrNull()
            ?: return NetworkResult.Error(NetworkError.Client.BadRequest("Next link carries no cursor"))
        return httpClient.get("$AIS/page") { parameter("cursor", cursor) }.toNetworkResult()
    }

    suspend fun getStandingOrders(accountId: String): NetworkResult<StandingOrdersResponse, NetworkError> =
        httpClient.get("$AIS/accounts/$accountId/standing-orders").toNetworkResult()

    suspend fun getScheduledPayments(accountId: String): NetworkResult<ScheduledPaymentsResponse, NetworkError> =
        httpClient.get("$AIS/accounts/$accountId/scheduled-payments").toNetworkResult()

    suspend fun getStatements(accountId: String): NetworkResult<StatementsResponse, NetworkError> =
        httpClient.get("$AIS/accounts/$accountId/statements").toNetworkResult()

    suspend fun getStatementDetails(
        accountId: String,
        statementId: String,
    ): NetworkResult<StatementDetailsResponse, NetworkError> =
        httpClient.get("$AIS/accounts/$accountId/statements/$statementId").toNetworkResult()

    suspend fun getStatementTransactions(
        accountId: String,
        statementId: String,
    ): NetworkResult<StatementTransactionsResponse, NetworkError> =
        httpClient.get("$AIS/accounts/$accountId/statements/$statementId/transactions").toNetworkResult()

    /**
     * The rendered statement file as raw bytes. The Worker accepts only `application/pdf` or
     * `text/csv` (406 otherwise) and pipes the bank's bytes through untouched.
     */
    suspend fun getStatementFile(
        accountId: String,
        statementId: String,
    ): NetworkResult<ByteArray, NetworkError> =
        httpClient.get("$AIS/accounts/$accountId/statements/$statementId/file") {
            accept(ContentType.Application.Pdf)
            accept(ContentType.parse("text/csv"))
        }.toNetworkResult()
}
