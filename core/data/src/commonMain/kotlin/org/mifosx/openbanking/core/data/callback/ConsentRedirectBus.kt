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

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Carries HSBC's authorisation redirect from whichever platform surface catches it (Android intent,
 * iOS `onOpenURL`, desktop loopback server) into the Compose navigation graph.
 *
 * The redirect arrives from *outside* the Compose tree and outside Koin's ViewModel scope, so it
 * cannot be delivered by ordinary DI. Each platform's entry point calls [publish]; the root
 * navigator collects [redirects] and routes to the consent-callback destination.
 *
 * `replay = 1` is deliberate: on Android a cold start via intent publishes the URL before the
 * navigator subscribes, so without replay the redirect would be dropped and the PSU would land on
 * the splash screen with the consent silently abandoned.
 */
object ConsentRedirectBus {
    private val _redirects = MutableSharedFlow<String>(
        replay = 1,
        extraBufferCapacity = 1,
    )

    /** Raw redirect URLs, e.g. `org.mifosx.openbanking://callback?code=…&id_token=…&state=…`. */
    val redirects: SharedFlow<String> = _redirects

    /** Called by platform entry points the moment the OS hands over the redirect URL. */
    fun publish(url: String) {
        _redirects.tryEmit(url)
    }

    /**
     * Called by the navigator once it has routed [url]: drops it from the replay cache, so a later
     * subscriber is not handed a redirect that was already handled.
     *
     * Replay outlives the Activity. Back finishes the Activity but keeps the process, so a relaunch
     * builds a new navigator whose saved state is gone; replay then routed the old redirect again,
     * found its single-use PendingAuth already spent, and showed "Security check failed". Only the
     * routed URL is dropped — a newer redirect already in the cache is kept.
     */
    fun markRouted(url: String) {
        // ponytail: check-then-reset is not atomic; a publish landing between the two is lost. Redirects
        // arrive seconds apart (a human finishing at the bank), so a lock is not worth it yet.
        if (_redirects.replayCache.lastOrNull() == url) _redirects.resetReplayCache()
    }
}
