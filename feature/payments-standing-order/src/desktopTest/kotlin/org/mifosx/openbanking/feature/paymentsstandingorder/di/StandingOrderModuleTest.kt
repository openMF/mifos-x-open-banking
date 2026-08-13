/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.paymentsstandingorder.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.mifosx.openbanking.core.data.banking.AccountCapabilityRegistry
import org.mifosx.openbanking.core.data.banking.AccountsOverviewRepository
import org.mifosx.openbanking.core.data.banking.BeneficiariesRepository
import org.mifosx.openbanking.core.data.banking.StandingOrderInitiationRepository
import org.mifosx.openbanking.core.model.banking.AccountWithBalance
import org.mifosx.openbanking.core.model.banking.BeneficiaryItem
import org.mifosx.openbanking.core.model.banking.payment.PaymentReceipt
import org.mifosx.openbanking.core.model.banking.payment.StagedConsent
import org.mifosx.openbanking.core.model.banking.payment.StandingOrderDraft
import org.mifosx.openbanking.core.model.hsbcProduct.AccountEndpoint
import org.mifosx.openbanking.feature.paymentsstandingorder.ui.StandingOrderViewModel
import template.core.base.common.screen.ScreenState
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult
import template.core.base.store.screen.ScreenDataStream
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * The ViewModel this module declares can actually be built.
 *
 * This exists because of a crash, not in anticipation of one. The module was first written as
 * `viewModelOf(::StandingOrderViewModel)`, which resolves **every** constructor parameter from the
 * graph and does not honour Kotlin defaults — so it asked for a `kotlin.time.Clock` that nothing
 * registers and threw `NoDefinitionFoundException` the instant the screen opened.
 *
 * Nothing caught it. It compiles; every other suite here constructs the ViewModel directly and so
 * never consults Koin; and `cmp-navigation`, which wires the graph, has no test source set at all.
 * The first evidence was the app dying on the Payments Hub's Schedule card.
 *
 * **It resolves the definition rather than calling `Module.verify()`.** `verify()` was tried first
 * and passes even with the broken `viewModelOf` form: it walks constructor parameters reflectively
 * and treats a defaulted one as optional, which is precisely the case that fails at runtime. Only
 * actually asking Koin for the instance reproduces what the screen does.
 */
class StandingOrderModuleTest {

    private val stubs = module {
        single<AccountsOverviewRepository> {
            object : AccountsOverviewRepository {
                override fun overviewState(scope: CoroutineScope): Flow<ScreenState<List<AccountWithBalance>>> =
                    flowOf(ScreenState.Loading)

                override fun refresh() = Unit
            }
        }
        single<BeneficiariesRepository> {
            object : BeneficiariesRepository {
                override fun beneficiariesStream(
                    accountId: String,
                    scope: CoroutineScope,
                ): ScreenDataStream<List<BeneficiaryItem>> = ScreenDataStream(
                    state = MutableStateFlow(ScreenState.Loading),
                    refreshTrigger = MutableStateFlow(Unit),
                )
            }
        }
        single<StandingOrderInitiationRepository> {
            object : StandingOrderInitiationRepository {
                override suspend fun stageStandingOrder(
                    draft: StandingOrderDraft,
                ): NetworkResult<StagedConsent, NetworkError> = error("not called")

                override suspend fun submitStandingOrder(
                    draft: StandingOrderDraft,
                    consentId: String,
                ): NetworkResult<PaymentReceipt, NetworkError> = error("not called")

                override fun stagedDraft(): StandingOrderDraft? = null
            }
        }
        single<AccountCapabilityRegistry> {
            object : AccountCapabilityRegistry {
                override fun unsupportedStream(accountId: String): Flow<Set<AccountEndpoint>> = flowOf(emptySet())
                override fun unsupportedStream(): Flow<Map<String, Set<AccountEndpoint>>> = flowOf(emptyMap())
                override fun markUnsupported(accountId: String, endpoint: AccountEndpoint) = Unit
                override fun clear() = Unit
            }
        }
    }

    /**
     * The clock is deliberately **not** registered in [stubs].
     *
     * That absence is the whole test. It mirrors production, where nothing registers a
     * `kotlin.time.Clock` either — so if this module ever goes back to resolving one from the graph,
     * this fails on the JVM instead of on a customer's phone.
     */
    @Test
    fun theViewModelResolvesFromTheGraph() {
        val koin = koinApplication { modules(stubs, StandingOrderModule) }.koin

        assertNotNull(koin.get<StandingOrderViewModel>())
    }
}
