/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.paymentsschedulepayment

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.mifosx.openbanking.core.data.banking.AccountCapabilityRegistry
import org.mifosx.openbanking.core.data.banking.AccountsOverviewRepository
import org.mifosx.openbanking.core.data.banking.BeneficiariesRepository
import org.mifosx.openbanking.core.data.banking.ScheduledPaymentInitiationRepository
import org.mifosx.openbanking.core.model.banking.AccountWithBalance
import org.mifosx.openbanking.core.model.banking.BeneficiaryItem
import org.mifosx.openbanking.core.model.banking.payment.PaymentReceipt
import org.mifosx.openbanking.core.model.banking.payment.ScheduledPaymentDraft
import org.mifosx.openbanking.core.model.banking.payment.StagedConsent
import org.mifosx.openbanking.core.model.hsbcProduct.AccountEndpoint
import template.core.base.common.screen.DataFreshness
import template.core.base.common.screen.ScreenState
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult
import template.core.base.store.screen.ScreenDataStream

private const val REFRESH_REPLAY = 16

class FakeAccountsOverviewRepository(
    initial: ScreenState<List<AccountWithBalance>> = ScreenState.Content(
        SchedulePaymentFixtures.accounts(),
        DataFreshness.FRESH,
    ),
) : AccountsOverviewRepository {

    val states = MutableStateFlow(initial)

    var refreshCount: Int = 0
        private set

    override fun overviewState(scope: CoroutineScope): Flow<ScreenState<List<AccountWithBalance>>> = states

    override fun refresh() {
        refreshCount++
    }
}

class FakeBeneficiariesRepository(
    initial: ScreenState<List<BeneficiaryItem>> = ScreenState.Content(
        SchedulePaymentFixtures.beneficiaries(),
        DataFreshness.FRESH,
    ),
) : BeneficiariesRepository {

    val states = MutableStateFlow(initial)

    /** Which account ids the screen asked for, in order — proves the list re-keys on the payer. */
    val requestedAccountIds = mutableListOf<String>()

    /**
     * The trigger must be buffered: `refresh()` is a `tryEmit`, so a bare `MutableSharedFlow` drops
     * it silently and the recorded count never moves.
     */
    private val refreshes = MutableSharedFlow<Unit>(replay = REFRESH_REPLAY)

    /**
     * How many times the screen asked for a re-read, counted off the replay buffer.
     *
     * `ScreenDataStream` is a final framework class, so `refresh()` cannot be intercepted; the
     * buffer it emits into is the only observable record of the call.
     */
    val refreshCount: Int get() = refreshes.replayCache.size

    override fun beneficiariesStream(
        accountId: String,
        scope: CoroutineScope,
    ): ScreenDataStream<List<BeneficiaryItem>> {
        requestedAccountIds += accountId
        return ScreenDataStream(state = states, refreshTrigger = refreshes)
    }

    /**
     * Moves the stream to its next state, so a test can watch one read change its mind.
     *
     * The constructor's [states] alone can only pose a stream that was born in one state and stays
     * there, which is enough for a read that succeeds or a read that has already failed — but the
     * two cases this exists for are transitions. `Loading` then `Content` is the first fetch after
     * a payer is chosen; `Error` then `Loading` is Retry, where the real `ScreenDataStream` has no
     * cached content to preserve and so genuinely goes back through `Loading`. Neither can be
     * expressed by a starting value.
     */
    fun emit(state: ScreenState<List<BeneficiaryItem>>) {
        states.value = state
    }
}

/**
 * Records every call so the tests can assert what this screen is responsible for: that the draft is
 * built once, carries the chosen date, and is staged under two distinct idempotency keys.
 *
 * There is no funds-confirmation member to record, because the interface has none — neither
 * scheduled rail offers that endpoint. Submission exists only to satisfy the interface: this screen
 * hands off to the browser and the returning leg submits, so a test that sees [submittedDrafts] grow
 * has caught the submission drifting back to the wrong screen.
 */
class FakeScheduledPaymentInitiationRepository(
    private var stageResult: NetworkResult<StagedConsent, NetworkError> =
        NetworkResult.Success(SchedulePaymentFixtures.stagedConsent()),
    private var submitResult: NetworkResult<PaymentReceipt, NetworkError> =
        NetworkResult.Success(SchedulePaymentFixtures.receipt()),
) : ScheduledPaymentInitiationRepository {

    val stagedDrafts = mutableListOf<ScheduledPaymentDraft>()
    val submittedDrafts = mutableListOf<ScheduledPaymentDraft>()
    val submittedConsentIds = mutableListOf<String>()

    override suspend fun stagePayment(
        draft: ScheduledPaymentDraft,
    ): NetworkResult<StagedConsent, NetworkError> {
        stagedDrafts += draft
        return stageResult
    }

    override suspend fun submitPayment(
        draft: ScheduledPaymentDraft,
        consentId: String,
    ): NetworkResult<PaymentReceipt, NetworkError> {
        submittedDrafts += draft
        submittedConsentIds += consentId
        return submitResult
    }

    override fun stagedDraft(): ScheduledPaymentDraft? = stagedDrafts.lastOrNull()

    fun stageReturns(result: NetworkResult<StagedConsent, NetworkError>) {
        stageResult = result
    }

    fun submitReturns(result: NetworkResult<PaymentReceipt, NetworkError>) {
        submitResult = result
    }
}

/**
 * The capability registry, behaving like the real in-memory one rather than only spying.
 *
 * Written locally rather than imported from `core/data`'s tests: test source sets do not cross
 * modules, so the shape is copied by design.
 */
class FakeAccountCapabilityRegistry : AccountCapabilityRegistry {

    private val state = MutableStateFlow<Map<String, Set<AccountEndpoint>>>(emptyMap())

    override fun unsupportedStream(accountId: String): Flow<Set<AccountEndpoint>> =
        state.map { it[accountId].orEmpty() }

    override fun unsupportedStream(): Flow<Map<String, Set<AccountEndpoint>>> = state

    override fun markUnsupported(accountId: String, endpoint: AccountEndpoint) {
        state.update { current ->
            current + (accountId to current[accountId].orEmpty() + endpoint)
        }
    }

    override fun clear() {
        state.value = emptyMap()
    }
}
