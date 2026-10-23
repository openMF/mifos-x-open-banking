/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.data.callback

/**
 * In-memory [PendingAuthStore] that keeps the real single-use contract: [consume] clears.
 * [SettingsPendingAuthStoreTest] covers the TTL and persistence behaviour separately.
 */
class FakePendingAuthStore : PendingAuthStore {

    private var pending: PendingAuth? = null

    var saveCount: Int = 0
        private set

    override fun save(state: String, flowId: String, consentId: String) {
        saveCount++
        pending = PendingAuth(state = state, flowId = flowId, consentId = consentId)
    }

    override fun consume(): PendingAuth? = pending.also { pending = null }

    override fun clear() {
        pending = null
    }
}
