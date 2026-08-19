/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.vrpsetup

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.mifosx.openbanking.core.data.banking.AccountCapabilityRegistry
import org.mifosx.openbanking.core.data.banking.AccountsOverviewRepository
import org.mifosx.openbanking.core.data.banking.BeneficiariesRepository
import org.mifosx.openbanking.core.data.vrp.VrpAuthRepository
import org.mifosx.openbanking.core.data.vrp.VrpAuthValidation
import org.mifosx.openbanking.core.data.vrp.VrpConsentRepository
import org.mifosx.openbanking.core.model.banking.AccountWithBalance
import org.mifosx.openbanking.core.model.banking.BeneficiaryItem
import org.mifosx.openbanking.core.model.hsbcProduct.AccountEndpoint
import org.mifosx.openbanking.core.model.vrp.VrpConsent
import org.mifosx.openbanking.core.model.vrp.VrpConsentDraft
import template.core.base.common.screen.DataFreshness
import template.core.base.common.screen.ScreenState
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult
import template.core.base.store.screen.ScreenDataStream

private const val REFRESH_REPLAY = 16

/** The accounts the setup screen may offer as payers. */
class FakeAccountsOverviewRepository(
    initial: ScreenState<List<AccountWithBalance>> = ScreenState.Loading,
) : AccountsOverviewRepository {

    private val states = MutableStateFlow(initial)

    var refreshCount: Int = 0
        private set

    fun emit(state: ScreenState<List<AccountWithBalance>>) {
        states.value = state
    }

    override fun overviewState(scope: CoroutineScope): Flow<ScreenState<List<AccountWithBalance>>> = states

    override fun refresh() {
        refreshCount++
    }
}

/** The payees saved against whichever account the screen asked about. */
class FakeBeneficiariesRepository(
    initial: ScreenState<List<BeneficiaryItem>> =
        ScreenState.Content(emptyList(), DataFreshness.FRESH),
) : BeneficiariesRepository {

    private val states = MutableStateFlow(initial)
    private val refreshes = MutableSharedFlow<Unit>(replay = REFRESH_REPLAY)

    /** Every account this has been asked about, in order. */
    val requestedAccountIds = mutableListOf<String>()

    fun emit(state: ScreenState<List<BeneficiaryItem>>) {
        states.value = state
    }

    override fun beneficiariesStream(
        accountId: String,
        scope: CoroutineScope,
    ): ScreenDataStream<List<BeneficiaryItem>> {
        requestedAccountIds += accountId
        return ScreenDataStream(state = states, refreshTrigger = refreshes)
    }
}

/** What the bank has refused this session. */
class FakeAccountCapabilityRegistry(
    initial: Map<String, Set<AccountEndpoint>> = emptyMap(),
) : AccountCapabilityRegistry {

    private val unsupported = MutableStateFlow(initial)

    override fun unsupportedStream(accountId: String): Flow<Set<AccountEndpoint>> =
        unsupported.map { it[accountId].orEmpty() }

    override fun unsupportedStream(): Flow<Map<String, Set<AccountEndpoint>>> = unsupported

    override fun markUnsupported(accountId: String, endpoint: AccountEndpoint) {
        unsupported.value = unsupported.value + (accountId to unsupported.value[accountId].orEmpty() + endpoint)
    }

    override fun clear() {
        unsupported.value = emptyMap()
    }
}

/** Records the drafts staged, and answers with whatever the test set. */
class FakeVrpConsentRepository(
    private var stageResult: NetworkResult<VrpConsent, NetworkError> =
        NetworkResult.Error(NetworkError.Client.BadRequest("not set")),
) : VrpConsentRepository {

    val stagedDrafts = mutableListOf<VrpConsentDraft>()

    fun stageReturns(result: NetworkResult<VrpConsent, NetworkError>) {
        stageResult = result
    }

    override fun observeActive(): Flow<List<VrpConsent>> = MutableStateFlow(emptyList())

    override fun observeById(consentId: String): Flow<VrpConsent?> = MutableStateFlow(null)

    override suspend fun stageConsent(draft: VrpConsentDraft): NetworkResult<VrpConsent, NetworkError> {
        stagedDrafts += draft
        return stageResult
    }

    override suspend fun refreshStatus(consentId: String): NetworkResult<VrpConsent, NetworkError> =
        NetworkResult.Error(NetworkError.Client.BadRequest("not used"))

    override suspend fun revoke(consentId: String): NetworkResult<Unit, NetworkError> =
        NetworkResult.Success(Unit)
}

/** Records the consents an authorisation was begun for. */
class FakeVrpAuthRepository(
    private var authorisationResult: NetworkResult<String, NetworkError> =
        NetworkResult.Success("https://bank.test/authorize"),
) : VrpAuthRepository {

    val begunFor = mutableListOf<String>()

    fun authorisationReturns(result: NetworkResult<String, NetworkError>) {
        authorisationResult = result
    }

    override suspend fun beginAuthorisation(consentId: String): NetworkResult<String, NetworkError> {
        begunFor += consentId
        return authorisationResult
    }

    override fun isVrpRedirect(redirectUrl: String): Boolean = false

    override fun validateCallback(redirectUrl: String): VrpAuthValidation = VrpAuthValidation.NoPending

    override suspend fun completeAuthorisation(
        code: String,
        consentId: String,
    ): NetworkResult<VrpConsent, NetworkError> =
        NetworkResult.Error(NetworkError.Client.BadRequest("not used"))

    override fun pendingConsentId(): String? = null

    override fun discardAuthorisation() = Unit
}
