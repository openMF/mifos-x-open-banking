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

import org.mifosx.openbanking.core.data.callback.ConsentSession
import org.mifosx.openbanking.core.data.callback.PaymentAuthSession
import org.mifosx.openbanking.core.data.user.AppLogout
import org.mifosx.openbanking.core.data.user.UserDataRepository
import org.mifosx.openbanking.core.data.vrp.VrpAuthSession
import org.mifosx.openbanking.core.data.vrp.VrpConsentRepository
import org.mifosx.openbanking.core.database.banking.dao.PaymentHistoryDao
import org.mifosx.openbanking.core.database.vrp.dao.VrpConsentDao
import org.mifosx.openbanking.core.database.vrp.dao.VrpPaymentDao
import org.mifosx.openbanking.core.network.api.Session
import org.mifosx.openbanking.core.store.infra.StoreCacheManager

/**
 * The one logout sequence, in an order that matters.
 *
 * Every bank-side step runs through the Worker on the current session, so they all happen BEFORE the
 * session is wiped: a revoke sent after logout would carry no session and be refused.
 *
 * Not routed through [org.mifosx.openbanking.core.data.user.UserLogoutManager]: that takes a `Long`
 * user id this AISP app does not have and emits a logout event nothing consumes.
 */
internal class AppLogoutImpl(
    private val bffSession: Session,
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
        // 1. End every VRP consent at the bank (best effort, on the still-live session).
        endStandingAuthorities()

        // 2. End the Worker session, revoking the sign-in consent at the bank on the way (best effort:
        //    an expired, revoked or unreachable consent must not stop the user signing out).
        if (consentSession.isActive()) {
            runCatching { bffSession.logout(revoke = consentSession.consentId() != null) }
        }

        // 3. Forget the session locally. This is what makes ConsentSession.isActive() false.
        consentSession.forgetAll()

        // 3b. Drop any payment authorisation in flight too — it keeps its own keys.
        paymentAuthSession.clear()

        // 4. Clear local data. clearUserData() writes the UserData StateFlow the root navigator
        //    observes; it must run after step 3 so the navigator reads the cleared session.
        userDataRepository.clearUserData()
        storeCacheManager.clearAll()

        // 5. Drop payment history snapshots — the next session's hub should start empty.
        paymentHistoryDao.clear()
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
