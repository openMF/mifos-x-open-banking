/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.data.user.impl

import org.mifosx.openbanking.core.data.banking.ConsentRevokeRepository
import org.mifosx.openbanking.core.data.callback.ConsentSession
import org.mifosx.openbanking.core.data.callback.PaymentAuthSession
import org.mifosx.openbanking.core.data.user.AppLogout
import org.mifosx.openbanking.core.data.user.UserDataRepository
import org.mifosx.openbanking.core.data.vrp.VrpAuthSession
import org.mifosx.openbanking.core.data.vrp.VrpConsentRepository
import org.mifosx.openbanking.core.database.banking.dao.PaymentHistoryDao
import org.mifosx.openbanking.core.database.vrp.dao.VrpConsentDao
import org.mifosx.openbanking.core.database.vrp.dao.VrpPaymentDao
import org.mifosx.openbanking.core.store.infra.StoreCacheManager

/**
 * The one logout sequence, in an order that matters.
 *
 * Not routed through [org.mifosx.openbanking.core.data.user.UserLogoutManager]: that takes a `Long`
 * user id this AISP app does not have (the active user id is a `String`) and emits a logout event
 * nothing consumes. Its actual work is exactly the two clears below, so they are called directly.
 */
internal class AppLogoutImpl(
    private val consentRevokeRepository: ConsentRevokeRepository,
    private val consentSession: ConsentSession,
    private val paymentAuthSession: PaymentAuthSession,
    private val userDataRepository: UserDataRepository,
    private val storeCacheManager: StoreCacheManager,
    private val paymentHistoryDao: PaymentHistoryDao,
    private val vrpConsentRepository: VrpConsentRepository,
    private val vrpConsentDao: VrpConsentDao,
    private val vrpPaymentDao: VrpPaymentDao,
    private val vrpAuthSession: VrpAuthSession,
) : AppLogout {

    override suspend fun logOut() {
        // 1. Best-effort revoke. The result is deliberately ignored: an expired, revoked (404),
        //    forbidden, or unreachable consent must not stop the user signing out. No id → nothing
        //    to revoke.
        consentSession.consentId()?.let { consentId ->
            runCatching { consentRevokeRepository.revokeConsent(consentId) }
        }

        // 2. Forget the session. This removes the PSU tokens from secure storage, which is what
        //    actually makes ConsentSession.isActive() false — clearing UserData below cannot, the
        //    tokens do not live there.
        consentSession.forgetAll()

        // 2b. Drop any payment authorisation too. It keeps its own keys so that clearing one leg
        //     never disturbs the other, which means signing out has to clear both explicitly — a
        //     surviving payments token would outlive the session that authorised it.
        paymentAuthSession.clear()

        // 3. Clear local data. clearUserData() writes the DataStore-backed UserData StateFlow the
        //    root navigator observes; that emission makes it re-read isActive() (now false) and route
        //    to the auth graph. Must run after step 2 so it reads the already-cleared token state.
        userDataRepository.clearUserData()
        storeCacheManager.clearAll()

        // 4. Drop payment history snapshots — the next session's hub should start empty.
        paymentHistoryDao.clear()

        endStandingAuthorities()
    }

    /** Ends every VRP consent at the bank and clears what this app held for it. */
    private suspend fun endStandingAuthorities() {
        vrpConsentDao.findActive().forEach { consent ->
            runCatching { vrpConsentRepository.revoke(consent.consentId) }
        }

        vrpPaymentDao.clear()
        vrpConsentDao.clear()
        vrpAuthSession.clear()
    }
}
