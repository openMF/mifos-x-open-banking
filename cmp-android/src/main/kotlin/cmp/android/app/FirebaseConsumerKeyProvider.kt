/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package cmp.android.app

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.mifosx.openbanking.core.data.user.UserDataRepository
import org.mifosx.openbanking.core.network.obp.ObpConfig

/**
 * Fetches the OBP consumer key from Firebase Remote Config and caches it in
 * platform encrypted storage.
 *
 * On **demo** builds Firebase Remote Config is typically not configured, so the
 * fetch will return an empty value — the user pastes the key on the login screen
 * instead. On **prod** builds the key is served from the Firebase Console and
 * applied transparently, with no user interaction needed.
 *
 * Remote config parameter name: `obp_consumer_key`
 */
object FirebaseConsumerKeyProvider {

    private const val REMOTE_KEY = "obp_consumer_key"

    /** Attempts to fetch the consumer key from Firebase Remote Config. */
    fun fetch(
        scope: CoroutineScope,
        obpConfig: ObpConfig,
        userDataRepo: UserDataRepository,
    ) {
        scope.launch {
            try {
                val remoteConfig = FirebaseRemoteConfig.getInstance().apply {
                    setConfigSettingsAsync(
                        FirebaseRemoteConfigSettings.Builder()
                            .setMinimumFetchIntervalInSeconds(3600)
                            .build(),
                    )
                }
                remoteConfig.fetchAndActivate().await()
                val key = remoteConfig.getString(REMOTE_KEY).trim()
                if (key.isNotBlank()) {
                    obpConfig.consumerKey = key
                    userDataRepo.setConsumerKey(key)
                }
            } catch (_: Exception) {
                // Firebase fetch failed — user can paste the key on the login screen.
            }
        }
    }
}
