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

import com.russhwolf.settings.MapSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.mifosx.openbanking.core.data.TestSigningKey
import org.mifosx.openbanking.core.database.vrp.dao.VrpConsentDao
import org.mifosx.openbanking.core.database.vrp.entity.VrpConsentEntity
import org.mifosx.openbanking.core.model.callback.ConsentStatus
import org.mifosx.openbanking.core.model.vrp.AccountIdentity
import org.mifosx.openbanking.core.model.vrp.Money
import org.mifosx.openbanking.core.model.vrp.PeriodType
import org.mifosx.openbanking.core.model.vrp.PeriodicLimit
import org.mifosx.openbanking.core.model.vrp.VrpConsent
import org.mifosx.openbanking.core.model.vrp.VrpConsentDraft
import org.mifosx.openbanking.core.model.vrp.VrpControlParameters
import org.mifosx.openbanking.core.network.api.OAuth
import org.mifosx.openbanking.core.network.api.Vrp
import template.core.base.network.NetworkResult
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val TOKEN_URL = "https://secure.example.test/oauth2/token"
private const val CONSENT_ID = "45411"

/** The credential the client-credentials grant returns. Every consent call presents this one. */
private const val TPP_TOKEN = "tpp-client-credentials-token"

/** The bank's code for a consent that no longer resolves. */
private const val RESOURCE_GONE_BODY =
    """{"Id":"ref-1","Code":"UK.OBIE.Resource.NotFound","Errors":[{"ErrorCode":"U011",""" +
        """"Message":"Resource cannot be found"}]}"""

/**
 * Covers [VrpConsentRepositoryImpl] at the wire.
 *
 * Two rules carry the weight. A staged consent is written nowhere until the customer has approved
 * it, so an abandoned setup leaves nothing behind; and a removal drops the local authority whatever
 * the bank answers, so the app can never keep paying under one it has tried to end.
 */
class VrpConsentRepositoryImplTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private data class Recorded(
        val path: String,
        val method: String,
        val authorization: String?,
    )

    private fun consentJson(status: String) = """
        {
          "Data": {
            "ConsentId": "$CONSENT_ID",
            "Status": "$status",
            "CreationDateTime": "2026-08-15T17:51:00+00:00",
            "StatusUpdateDateTime": "2026-08-15T17:51:00+00:00",
            "ControlParameters": {
              "MaximumIndividualAmount": { "Amount": "10.00", "Currency": "GBP" },
              "PeriodicLimits": [
                { "PeriodType": "Day", "PeriodAlignment": "Consent",
                  "Amount": "50.00", "Currency": "GBP" }
              ],
              "VRPType": ["UK.OBIE.VRPType.Sweeping"],
              "PSUAuthenticationMethods": ["UK.OBIE.SCANotRequired"],
              "PSUInteractionTypes": ["OffSession"]
            },
            "Initiation": {
              "CreditorAccount": {
                "SchemeName": "UK.OBIE.SortCodeAccountNumber",
                "Identification": "80200110203350",
                "Name": "Mr Dharani C"
              }
            }
          }
        }
    """.trimIndent()

    private class FakeConsentDao(seed: VrpConsentEntity? = null) : VrpConsentDao {
        val rows = MutableStateFlow(listOfNotNull(seed))
        val revokedIds = mutableListOf<String>()

        override fun observeActive(): Flow<List<VrpConsentEntity>> = rows
        override fun observeById(consentId: String): Flow<VrpConsentEntity?> =
            rows.map { list -> list.firstOrNull { it.consentId == consentId } }

        override suspend fun findActive(): List<VrpConsentEntity> =
            rows.value.filter { it.revokedAt == null }

        override suspend fun findById(consentId: String): VrpConsentEntity? =
            rows.value.firstOrNull { it.consentId == consentId }

        override suspend fun upsert(consent: VrpConsentEntity) {
            rows.value = rows.value.filterNot { it.consentId == consent.consentId } + consent
        }

        override suspend fun updateStatus(consentId: String, status: String, syncedAt: String) = Unit

        override suspend fun markRevoked(consentId: String, revokedAt: String) {
            revokedIds += consentId
        }

        override suspend fun clear() {
            rows.value = emptyList()
        }
    }

    private fun draft() = VrpConsentDraft(
        payee = AccountIdentity(
            schemeName = "UK.OBIE.SortCodeAccountNumber",
            identification = "80200110203350",
            name = "Mr Dharani C",
        ),
        controlParameters = VrpControlParameters(
            maximumIndividualAmount = Money(10_00L, "GBP"),
            periodicLimits = listOf(PeriodicLimit(PeriodType.Day, Money(50_00L, "GBP"))),
            interactionType = "UK.OBIE.VRPType.Sweeping",
        ),
        idempotencyKey = "stage-key-1",
        payerAccountId = null,
    )

    private fun storedConsent(status: ConsentStatus = ConsentStatus.Authorised) = VrpConsent(
        consentId = CONSENT_ID,
        status = status,
        createdAt = Instant.parse("2026-08-15T17:51:00Z"),
        controlParameters = VrpControlParameters(
            maximumIndividualAmount = Money(10_00L, "GBP"),
            periodicLimits = listOf(PeriodicLimit(PeriodType.Day, Money(50_00L, "GBP"))),
            interactionType = "UK.OBIE.VRPType.Sweeping",
        ),
        payee = AccountIdentity(
            schemeName = "UK.OBIE.SortCodeAccountNumber",
            identification = "80200110203350",
            name = "Mr Dharani C",
        ),
    )

    /**
     * @param resourceStatus Applied to the consent calls only; the token endpoint always succeeds,
     *   so a failure case exercises the refusal path rather than dying at authentication.
     */
    private suspend fun repository(
        dao: FakeConsentDao = FakeConsentDao(),
        consentStatus: String = "AWAU",
        resourceStatus: HttpStatusCode = HttpStatusCode.Created,
        resourceBody: String? = null,
        refuseToken: Boolean = false,
    ): Triple<VrpConsentRepositoryImpl, FakeConsentDao, MutableList<Recorded>> {
        val captured = mutableListOf<Recorded>()
        val client = HttpClient(
            MockEngine { request: HttpRequestData ->
                val path = request.url.encodedPath
                if (path.contains("oauth2/token")) {
                    if (refuseToken) {
                        respond("", HttpStatusCode.Unauthorized, jsonHeaders)
                    } else {
                        respond(
                            """{"access_token":"$TPP_TOKEN","token_type":"Bearer","expires_in":299,""" +
                                """"scope":"payments"}""",
                            HttpStatusCode.OK,
                            jsonHeaders,
                        )
                    }
                } else {
                    captured += Recorded(
                        path = path,
                        method = request.method.value,
                        authorization = request.headers[HttpHeaders.Authorization],
                    )
                    respond(
                        resourceBody ?: consentJson(consentStatus),
                        resourceStatus,
                        jsonHeaders,
                    )
                }
            },
        ) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val session = SettingsVrpAuthSession(MapSettings())
        val oauth = OAuth(client, TOKEN_URL, "test-client", "test-kid", TestSigningKey.pem())
        val repository = VrpConsentRepositoryImpl(
            vrp = Vrp(
                httpClient = client,
                kid = "test-kid",
                signingKeyPem = TestSigningKey.pem(),
                financialId = "",
                signingIssuer = "mifos_init_00000/0000000000000000000000",
            ),
            oauth = oauth,
            dao = dao,
            session = session,
            tokens = VrpTokenProviderImpl(oauth, session),
        )
        return Triple(repository, dao, captured)
    }

    // staging

    @Test
    fun stagesOnTheTppCredential() = runTest {
        val (repository, _, calls) = repository()

        val result = repository.stageConsent(draft())

        assertIs<NetworkResult.Success<VrpConsent>>(result)
        val call = assertNotNull(calls.firstOrNull { it.method == "POST" })
        assertTrue(call.path.endsWith("domestic-vrp-consents"))
        assertEquals("Bearer $TPP_TOKEN", call.authorization)
    }

    /** An abandoned setup must leave nothing behind, so nothing is written until it is approved. */
    @Test
    fun stagingWritesNothingLocally() = runTest {
        val (repository, dao, _) = repository()

        repository.stageConsent(draft())

        assertContentEquals(emptyList(), dao.rows.value)
    }

    @Test
    fun aRefusedStagingIsReportedAndWritesNothing() = runTest {
        val (repository, dao, _) = repository(resourceStatus = HttpStatusCode.BadRequest)

        val result = repository.stageConsent(draft())

        assertIs<NetworkResult.Error<*>>(result)
        assertContentEquals(emptyList(), dao.rows.value)
    }

    @Test
    fun aConsentResponseThatCannotBeReadIsAnError() = runTest {
        val (repository, _, _) = repository(resourceBody = """{"Data":{}}""")

        val result = repository.stageConsent(draft())

        assertIs<NetworkResult.Error<*>>(result)
    }

    // reading back

    /** The row appears only once the customer has approved it. */
    @Test
    fun readingBackAnUnapprovedConsentStoresNothing() = runTest {
        val (repository, dao, _) = repository(consentStatus = "AWAU")

        val result = repository.refreshStatus(CONSENT_ID)

        assertIs<NetworkResult.Success<VrpConsent>>(result)
        assertContentEquals(emptyList(), dao.rows.value)
    }

    @Test
    fun readingBackAnApprovedConsentStoresIt() = runTest {
        val (repository, dao, _) = repository(consentStatus = "AUTH")

        repository.refreshStatus(CONSENT_ID)

        assertEquals(listOf(CONSENT_ID), dao.rows.value.map { it.consentId })
    }

    /** Once stored, a row stays current whatever the status becomes. */
    @Test
    fun readingBackAnAlreadyStoredConsentKeepsItCurrent() = runTest {
        val dao = FakeConsentDao(storedConsent().toEntity())
        val (repository, _, _) = repository(dao = dao, consentStatus = "EXPD")

        repository.refreshStatus(CONSENT_ID)

        assertEquals(
            ConsentStatus.Expired,
            assertNotNull(dao.rows.value.singleOrNull()).toVrpConsent().status,
        )
    }

    @Test
    fun readsBackOnTheTppCredential() = runTest {
        val (repository, _, calls) = repository(consentStatus = "AUTH")

        repository.refreshStatus(CONSENT_ID)

        val call = assertNotNull(calls.firstOrNull { it.method == "GET" })
        assertEquals("Bearer $TPP_TOKEN", call.authorization)
    }

    /** A consent that no longer resolves at the bank cannot be paid under, so it is dropped here. */
    @Test
    fun aConsentTheBankNoLongerHasIsForgotten() = runTest {
        val dao = FakeConsentDao(storedConsent().toEntity())
        val (repository, _, _) = repository(
            dao = dao,
            resourceStatus = HttpStatusCode.BadRequest,
            resourceBody = RESOURCE_GONE_BODY,
        )

        val result = repository.refreshStatus(CONSENT_ID)

        assertIs<NetworkResult.Error<*>>(result)
        assertContentEquals(listOf(CONSENT_ID), dao.revokedIds)
    }

    /** Any other refusal leaves what is stored intact — the authority may still be good. */
    @Test
    fun anotherRefusalLeavesTheStoredConsentAlone() = runTest {
        val dao = FakeConsentDao(storedConsent().toEntity())
        val (repository, _, _) = repository(dao = dao, resourceStatus = HttpStatusCode.InternalServerError)

        repository.refreshStatus(CONSENT_ID)

        assertContentEquals(emptyList(), dao.revokedIds)
    }

    // removal

    @Test
    fun removesOnTheTppCredential() = runTest {
        val dao = FakeConsentDao(storedConsent().toEntity())
        val (repository, _, calls) = repository(dao = dao, resourceStatus = HttpStatusCode.NoContent)

        val result = repository.revoke(CONSENT_ID)

        assertIs<NetworkResult.Success<Unit>>(result)
        val call = assertNotNull(calls.firstOrNull { it.method == "DELETE" })
        assertEquals("Bearer $TPP_TOKEN", call.authorization)
    }

    @Test
    fun aRemovalDropsTheLocalAuthority() = runTest {
        val dao = FakeConsentDao(storedConsent().toEntity())
        val (repository, _, _) = repository(dao = dao, resourceStatus = HttpStatusCode.NoContent)

        repository.revoke(CONSENT_ID)

        assertContentEquals(listOf(CONSENT_ID), dao.revokedIds)
    }

    /**
     * The app must never keep paying under an authority it has tried to end, so the local record
     * goes whatever the bank answers.
     */
    @Test
    fun aRefusedRemovalStillDropsTheLocalAuthority() = runTest {
        val dao = FakeConsentDao(storedConsent().toEntity())
        val (repository, _, _) = repository(dao = dao, resourceStatus = HttpStatusCode.InternalServerError)

        val result = repository.revoke(CONSENT_ID)

        assertIs<NetworkResult.Error<*>>(result)
        assertContentEquals(listOf(CONSENT_ID), dao.revokedIds)
    }

    @Test
    fun aRemovalThatCannotEvenBeAuthenticatedStillDropsTheLocalAuthority() = runTest {
        val dao = FakeConsentDao(storedConsent().toEntity())
        val (repository, _, _) = repository(dao = dao, refuseToken = true)

        val result = repository.revoke(CONSENT_ID)

        assertIs<NetworkResult.Error<*>>(result)
        assertContentEquals(listOf(CONSENT_ID), dao.revokedIds)
    }

    // reading storage

    @Test
    fun readsStoredConsentsBackAsDomainObjects() = runTest {
        val dao = FakeConsentDao(storedConsent().toEntity())
        val (repository, _, _) = repository(dao = dao)

        val consents = repository.observeActive().first()

        assertEquals(CONSENT_ID, consents.single().consentId)
        assertEquals("Mr Dharani C", consents.single().payee.name)
    }

    @Test
    fun readsOneStoredConsentBackById() = runTest {
        val dao = FakeConsentDao(storedConsent().toEntity())
        val (repository, _, _) = repository(dao = dao)

        assertNotNull(repository.observeById(CONSENT_ID).first())
        assertNull(repository.observeById("other").first())
    }
}
