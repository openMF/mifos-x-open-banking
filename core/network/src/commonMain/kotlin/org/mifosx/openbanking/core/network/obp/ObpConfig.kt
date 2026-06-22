/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.mifosx.openbanking.core.network.obp

/**
 * OBP connection settings. [baseUrl] + [bankId] default to the OBP sandbox;
 *
 * [consumerKey] is read from platform encrypted storage at Koin startup and can be
 * updated at runtime when the user enters a new key in Settings. It is never baked
 * into the APK — not even for dev builds.
 */
data class ObpConfig(
    val baseUrl: String = "https://apisandbox.openbankproject.com/obp/",
    val bankId: String = "ac.bank.uk",
    /** Set at Koin startup from encrypted storage; updated at runtime when the user saves a new key. */
    var consumerKey: String = "",
) {
    /**
     * True when pointed at an OBP sandbox. Gates the sandbox-only SANDBOX_TAN payment rail, whose
     * challenge can be self-answered by the maker (real-payment rails enforce maker/checker, so the
     * challenge would otherwise be uncompletable by a single user — OBP-30279).
     */
    val isSandbox: Boolean get() = baseUrl.contains("sandbox", ignoreCase = true)
}
