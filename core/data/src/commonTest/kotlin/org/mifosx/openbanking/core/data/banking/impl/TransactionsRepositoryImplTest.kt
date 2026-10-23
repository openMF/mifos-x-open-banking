/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.data.banking.impl

import io.github.mobilebytelabs.kmptoolkit.networkmonitor.NetworkInfo
import io.github.mobilebytelabs.kmptoolkit.networkmonitor.NetworkStatus
import io.github.mobilebytelabs.kmptoolkit.networkmonitor.NetworkType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.mifosx.openbanking.core.data.banking.store.FakeFetchedAtRepository
import org.mifosx.openbanking.core.data.banking.store.FakeNetworkMonitor
import org.mifosx.openbanking.core.model.banking.TransactionCategory
import org.mifosx.openbanking.core.model.banking.TransactionItem
import org.mifosx.openbanking.core.model.banking.TransactionsPage
import org.mifosx.openbanking.core.network.api.Aisp
import org.mobilenativefoundation.store.store5.Fetcher
import org.mobilenativefoundation.store.store5.Store
import template.core.base.common.screen.ScreenState
import template.core.base.network.NetworkResult
import template.core.base.store.infra.StoreFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verifies [TransactionsRepositoryImpl] streams the keyed transaction list as Content and computes
 * the per-account cache key `home:transactions:<accountId>` through the `cacheKeyFor` lambda, and that
 * the cursor pager ([TransactionsRepositoryImpl.firstPage] / [TransactionsRepositoryImpl.nextPage])
 * maps rows and surfaces the OBIE `Links.Next` cursor.
 */
class TransactionsRepositoryImplTest {

    private val onlineInfo = NetworkInfo(type = NetworkType.WiFi, isMetered = false)
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private val transactions = listOf(
        TransactionItem(
            transactionId = "t1",
            accountId = "acc-1",
            description = "TESCO STORES",
            bookingDateTime = "2026-06-27T10:00:00Z",
            amount = "42.17",
            currency = "GBP",
            isCredit = false,
        ),
    )

    private fun store(): Store<String, List<TransactionItem>> =
        StoreFactory.createMemoryStore(fetcher = Fetcher.of { _: String -> transactions })

    private fun mockClient(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = HttpClient(MockEngine(handler)) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private fun aisp(body: String, status: HttpStatusCode = HttpStatusCode.OK): Aisp =
        Aisp(mockClient { respond(body, status, jsonHeaders) })

    private fun repo(
        fetchedAt: FakeFetchedAtRepository,
        aisp: Aisp = aisp("{}"),
    ): TransactionsRepositoryImpl =
        TransactionsRepositoryImpl(
            store = store(),
            aisp = aisp,
            networkMonitor = FakeNetworkMonitor(NetworkStatus.Available(onlineInfo)),
            fetchedAtRepository = fetchedAt,
        )

    @Test
    fun transactionsStateEmitsContentAndUsesPerAccountCacheKey() = runTest(UnconfinedTestDispatcher()) {
        val fetchedAt = FakeFetchedAtRepository()
        val state = repo(fetchedAt)
            .transactionsStream(flowOf("acc-1"), backgroundScope).state
            .first { it is ScreenState.Content }

        val content = assertIs<ScreenState.Content<List<TransactionItem>>>(state)
        assertEquals(transactions, content.data)
        assertTrue("home:transactions:acc-1" in fetchedAt.readKeys)
    }

    /**
     * One repository instance serves every caller, so a second account must get its own stream and
     * its own cache key rather than the first account's.
     */
    @Test
    fun aSecondAccountOnTheSameInstanceUsesItsOwnCacheKey() = runTest(UnconfinedTestDispatcher()) {
        val fetchedAt = FakeFetchedAtRepository()
        val repo = repo(fetchedAt)

        repo.transactionsStream(flowOf("acc-1"), backgroundScope).state.first { it is ScreenState.Content }
        repo.transactionsStream(flowOf("acc-2"), backgroundScope).state.first { it is ScreenState.Content }

        assertTrue("home:transactions:acc-1" in fetchedAt.readKeys)
        assertTrue("home:transactions:acc-2" in fetchedAt.readKeys)
    }

    @Test
    fun refreshingAStreamReEmitsContent() = runTest(UnconfinedTestDispatcher()) {
        val stream = repo(FakeFetchedAtRepository()).transactionsStream(flowOf("acc-1"), backgroundScope)
        stream.state.first { it is ScreenState.Content }

        stream.refresh()

        assertIs<ScreenState.Content<List<TransactionItem>>>(stream.state.first { it is ScreenState.Content })
    }

    @Test
    fun firstPageMapsRowsAndSurfacesCursorAndTotalPages() = runTest {
        val next = "https://gateway.example/v1/ais/page?cursor=c-1"
        val body = """
            {"Data":{"Transaction":[
              {"AccountId":"acc-1","TransactionId":"t1","CreditDebitIndicator":"Debit","Status":"BOOK",
               "BookingDateTime":"2026-06-27T10:00:00Z","Amount":{"Amount":"42.17","Currency":"GBP"},
               "MerchantDetails":{"MerchantName":"TESCO STORES","MerchantCategoryCode":"5411"}}
            ]},"Links":{"Next":"$next"},"Meta":{"TotalPages":3}}
        """.trimIndent()

        val result = repo(FakeFetchedAtRepository(), aisp(body)).firstPage("acc-1")

        val page = assertIs<NetworkResult.Success<TransactionsPage>>(result).data
        assertEquals(1, page.items.size)
        assertEquals("TESCO STORES", page.items.first().description)
        assertEquals(TransactionCategory.GROCERIES, page.items.first().category)
        assertEquals(next, page.nextLink)
        assertEquals(3, page.totalPages)
        assertTrue(page.hasNextPage)
    }

    @Test
    fun firstPageErrorSurfacesNetworkError() = runTest {
        val result = repo(FakeFetchedAtRepository(), aisp("boom", HttpStatusCode.InternalServerError))
            .firstPage("acc-1")

        assertIs<NetworkResult.Error<*>>(result)
    }

    @Test
    fun nextPageFollowsCursorAndReportsEndOfListWhenNextAbsent() = runTest {
        val body = """
            {"Data":{"Transaction":[
              {"AccountId":"acc-1","TransactionId":"t2","CreditDebitIndicator":"Credit","Status":"PDNG",
               "BookingDateTime":"2026-06-26T09:00:00Z","Amount":{"Amount":"2400.00","Currency":"GBP"},
               "TransactionInformation":"SALARY ACME LTD"}
            ]},"Meta":{"TotalPages":3}}
        """.trimIndent()

        val result = repo(FakeFetchedAtRepository(), aisp(body))
            .nextPage("https://gateway.example/v1/ais/page?cursor=c-2")

        val page = assertIs<NetworkResult.Success<TransactionsPage>>(result).data
        assertEquals("t2", page.items.first().transactionId)
        assertTrue(page.items.first().isPending)
        assertNull(page.nextLink)
        assertFalse(page.hasNextPage)
    }

    @Test
    fun cursorPagerFollowsNextLinkAcrossTwoPagesThenStops() = runTest {
        // The Worker has already rewritten the bank link to its own opaque cursor.
        val page1Url = "https://gateway.example/v1/ais/page?cursor=c-1"
        val page0Body = """
            {"Data":{"Transaction":[
              {"TransactionId":"a","AccountId":"acc-1","CreditDebitIndicator":"Debit","Status":"BOOK",
               "BookingDateTime":"2026-06-27T10:00:00Z","Amount":{"Amount":"1.00","Currency":"GBP"}}
            ]},"Links":{"Next":"$page1Url"},"Meta":{"TotalPages":2}}
        """.trimIndent()
        val page1Body = """
            {"Data":{"Transaction":[
              {"TransactionId":"b","AccountId":"acc-1","CreditDebitIndicator":"Credit","Status":"BOOK",
               "BookingDateTime":"2026-06-26T10:00:00Z","Amount":{"Amount":"2.00","Currency":"GBP"}}
            ]},"Meta":{"TotalPages":2}}
        """.trimIndent()
        val routingAisp = Aisp(
            mockClient { request ->
                val body = if (request.url.parameters["cursor"] == "c-1") page1Body else page0Body
                respond(body, HttpStatusCode.OK, jsonHeaders)
            },
        )
        val repo = repo(FakeFetchedAtRepository(), routingAisp)

        val first = assertIs<NetworkResult.Success<TransactionsPage>>(repo.firstPage("acc-1")).data
        assertEquals("a", first.items.single().transactionId)
        assertEquals(page1Url, first.nextLink)
        assertTrue(first.hasNextPage)

        val second = assertIs<NetworkResult.Success<TransactionsPage>>(repo.nextPage(first.nextLink!!)).data
        assertEquals("b", second.items.single().transactionId)
        assertNull(second.nextLink)
        assertFalse(second.hasNextPage)
    }
}
