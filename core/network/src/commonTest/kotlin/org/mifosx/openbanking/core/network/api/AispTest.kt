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

import com.russhwolf.settings.MapSettings
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.mifosx.openbanking.core.network.FakeAttestation
import org.mifosx.openbanking.core.network.Recorder
import org.mifosx.openbanking.core.network.SESSION_KEY
import org.mifosx.openbanking.core.network.TEST_SESSION
import org.mifosx.openbanking.core.network.gatewayHttpClient
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AispTest {

    private val rec = Recorder()
    private val aisp = Aisp(
        gatewayHttpClient(MapSettings(SESSION_KEY to TEST_SESSION), FakeAttestation("desktop"), rec.engine),
    )

    private fun lastPath() = rec.requests.last().first.url.encodedPath

    @Test
    fun everyReadIsASessionGetUnderV1Ais() = runTest {
        aisp.getAccounts()
        assertEquals("/v1/ais/accounts", lastPath())
        aisp.getAccountDetails("a1")
        assertEquals("/v1/ais/accounts/a1", lastPath())
        aisp.getBalances("a1")
        assertEquals("/v1/ais/accounts/a1/balances", lastPath())
        aisp.getBeneficiaries("a1")
        assertEquals("/v1/ais/accounts/a1/beneficiaries", lastPath())
        aisp.getDirectDebits("a1")
        assertEquals("/v1/ais/accounts/a1/direct-debits", lastPath())
        aisp.getProduct("a1")
        assertEquals("/v1/ais/accounts/a1/product", lastPath())
        aisp.getParty("a1")
        assertEquals("/v1/ais/accounts/a1/party", lastPath())
        aisp.getParties("a1")
        assertEquals("/v1/ais/accounts/a1/parties", lastPath())
        aisp.getTransactions("a1")
        assertEquals("/v1/ais/accounts/a1/transactions", lastPath())
        aisp.getStandingOrders("a1")
        assertEquals("/v1/ais/accounts/a1/standing-orders", lastPath())
        aisp.getScheduledPayments("a1")
        assertEquals("/v1/ais/accounts/a1/scheduled-payments", lastPath())
        aisp.getStatements("a1")
        assertEquals("/v1/ais/accounts/a1/statements", lastPath())
        aisp.getStatementDetails("a1", "s1")
        assertEquals("/v1/ais/accounts/a1/statements/s1", lastPath())
        aisp.getStatementTransactions("a1", "s1")
        assertEquals("/v1/ais/accounts/a1/statements/s1/transactions", lastPath())

        assertTrue(rec.requests.all { it.first.method == HttpMethod.Get })
        assertTrue(rec.requests.all { it.first.headers[HttpHeaders.Authorization] == "Session $TEST_SESSION" })
    }

    @Test
    fun consentReadAndRevokeAreSessionScoped() = runTest {
        aisp.getConsent("cid-1")
        assertEquals(HttpMethod.Get, rec.requests.last().first.method)
        assertEquals("/v1/consents/ais/cid-1", lastPath())

        aisp.deleteConsent("cid-1")
        assertEquals(HttpMethod.Delete, rec.requests.last().first.method)
        assertEquals("/v1/consents/ais/cid-1", lastPath())
    }

    @Test
    fun theNextPageIsFetchedByItsOpaqueCursor() = runTest {
        aisp.getTransactionsPage("https://worker.example/v1/ais/page?cursor=abc_DEF-123")

        val url = rec.requests.last().first.url
        assertEquals("/v1/ais/page", url.encodedPath)
        assertEquals("abc_DEF-123", url.parameters["cursor"])
    }

    @Test
    fun aPageLinkWithoutACursorIsRefusedWithoutAnyRequest() = runTest {
        val result = aisp.getTransactionsPage("https://evil.example/steal")

        assertIs<NetworkResult.Error<NetworkError>>(result)
        assertTrue(rec.requests.isEmpty())
    }

    @Test
    fun theStatementFileAsksForPdfOrCsvAndReturnsTheRawBytes() = runTest {
        val result = aisp.getStatementFile("a1", "s1")

        assertIs<NetworkResult.Success<ByteArray>>(result)
        assertEquals("/v1/ais/accounts/a1/statements/s1/file", lastPath())
        val accept = rec.requests.last().first.headers.getAll(HttpHeaders.Accept).orEmpty().joinToString(",")
        assertTrue("application/pdf" in accept && "text/csv" in accept)
    }

    @Test
    fun aWorkerErrorKeepsItsStatus() = runTest {
        val failing = Recorder { HttpStatusCode.Unauthorized to """{"error":"session_expired"}""" }
        val result = Aisp(gatewayHttpClient(MapSettings(), FakeAttestation("web"), failing.engine)).getAccounts()

        assertIs<NetworkError.Client.Unauthorized>(assertIs<NetworkResult.Error<NetworkError>>(result).error)
    }
}
