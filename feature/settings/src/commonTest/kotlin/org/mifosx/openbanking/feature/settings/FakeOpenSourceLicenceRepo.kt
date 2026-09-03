/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.mifosx.openbanking.core.data.openSourceLicence.OpenSourceLicenceRepo
import template.core.base.common.screen.ScreenState
import template.core.base.store.screen.ScreenDataStream

/** Number of retries the fake records before dropping the oldest. */
private const val RETRY_REPLAY = 16

/**
 * Hand-written [OpenSourceLicenceRepo] over a stream the test drives with [emit].
 *
 * Written rather than mocked — this project ships no mocking framework.
 */
class FakeOpenSourceLicenceRepo(
    initialState: ScreenState<String> = ScreenState.Loading,
) : OpenSourceLicenceRepo {

    private val states = MutableStateFlow(initialState)

    private val refreshes = MutableSharedFlow<Unit>(replay = RETRY_REPLAY)

    /** How many times the screen asked for a retry. */
    val retryCount: Int get() = refreshes.replayCache.size

    override fun getLicence(scope: CoroutineScope): ScreenDataStream<String> =
        ScreenDataStream(state = states, refreshTrigger = refreshes)

    /** Pushes the next stream state, as the store would. */
    fun emit(state: ScreenState<String>) {
        states.value = state
    }
}
