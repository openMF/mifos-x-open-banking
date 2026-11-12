/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.login

import org.mifosx.openbanking.core.data.callback.PendingAuth
import org.mifosx.openbanking.core.data.callback.PendingAuthStore
import org.mifosx.openbanking.core.data.login.ConsentResult
import org.mifosx.openbanking.core.data.login.LoginRepository
import org.mifosx.openbanking.core.model.hsbcPermission.OBPermission
import org.mifosx.openbanking.feature.login.browser.BrowserLauncher
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult

class FakeLoginRepository : LoginRepository {
    @Suppress("ktlint:standard:property-naming")
    private var _permissions: List<OBPermission> = OBPermission.ALL
    var createConsentResult: NetworkResult<ConsentResult, NetworkError> = NetworkResult.Success(
        ConsentResult(
            authorizationUrl = "https://sandbox.test/authorize",
            state = "test-state",
            flowId = "test-flow",
            consentId = "test-consent-id",
        ),
    )
    var createConsentCallCount = 0
        private set

    override fun getPermissions(): List<OBPermission> = _permissions

    fun setPermissions(value: List<OBPermission>) {
        _permissions = value
    }

    override suspend fun createConsentAndBuildAuthorizationUrl(): NetworkResult<ConsentResult, NetworkError> {
        createConsentCallCount++
        return createConsentResult
    }
}

/**
 * Local to this module: test source sets are not shared across Gradle modules, so `core:data`'s
 * equivalent fake is not visible here. Keeps the real single-use contract — [consume] clears.
 */
class FakePendingAuthStore : PendingAuthStore {

    private var pending: PendingAuth? = null

    var saved: PendingAuth? = null
        private set

    var saveCount: Int = 0
        private set

    var clearCount: Int = 0
        private set

    override fun save(state: String, flowId: String, consentId: String) {
        saveCount++
        pending = PendingAuth(state = state, flowId = flowId, consentId = consentId)
        saved = pending
    }

    override fun consume(): PendingAuth? = pending.also { pending = null }

    override fun clear() {
        clearCount++
        pending = null
    }
}

open class FakeBrowserLauncher(
    override val isSupported: Boolean = true,
) : BrowserLauncher {
    var launchedUrl: String? = null
        private set

    var launchCount: Int = 0
        private set

    override fun launch(url: String) {
        launchCount++
        launchedUrl = url
    }
}
