/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.mifosx.openbanking.core.datastore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.mifosx.openbanking.core.model.user.DarkThemeConfig
import org.mifosx.openbanking.core.model.user.LanguageConfig
import org.mifosx.openbanking.core.model.user.ThemeBrand
import org.mifosx.openbanking.core.model.user.UserData

/**
 * Repository interface for managing user preferences with reactive
 * capabilities.
 *
 * This interface provides reactive access to user preferences including
 * theme settings, dark mode configuration, and dynamic color preferences.
 */
interface UserPreferencesRepository {

    val userData: StateFlow<UserData>

    val authToken: String?

    val passcode: String

    val observeLanguage: Flow<LanguageConfig>

    val observeDarkThemeConfig: Flow<DarkThemeConfig>

    val observeDynamicColorPreference: Flow<Boolean>

    val observeScreenCapturePreference: Flow<Boolean>

    val observePushNotificationsEnabled: Flow<Boolean>

    val observeTransactionAlertsEnabled: Flow<Boolean>

    val observeMarketingEnabled: Flow<Boolean>

    /** The user's default account id (set from the Home hero card); "" when unset. */
    val observeDefaultAccountId: Flow<String>

    suspend fun setLanguage(language: LanguageConfig)

    suspend fun setThemeBrand(themeBrand: ThemeBrand)

    suspend fun setDarkThemeConfig(darkThemeConfig: DarkThemeConfig)

    suspend fun setDynamicColorPreference(useDynamicColor: Boolean)

    suspend fun setIsAuthenticated(isAuthenticated: Boolean)

    suspend fun setIsUnlocked(isUnlocked: Boolean)

    suspend fun setIsPasscodeEnabled(isPasscodeEnabled: Boolean)

    suspend fun setIsBiometricsEnabled(isBiometricsEnabled: Boolean)

    suspend fun setPushNotificationsEnabled(isEnabled: Boolean)

    suspend fun setTransactionAlertsEnabled(isEnabled: Boolean)

    suspend fun setMarketingEnabled(isEnabled: Boolean)

    suspend fun setShowOnboarding(showOnboarding: Boolean)

    suspend fun setFirstTimeState(firstTimeState: Boolean)

    suspend fun setPasscode(passcode: String)

    suspend fun setScreenCapturePreference(isScreenCaptureEnabled: Boolean)

    /** Persists the default account chosen on the Home hero card. */
    suspend fun setDefaultAccountId(accountId: String)

    /** Persists (or clears, when null) the OBP DirectLogin session token in secure storage. */
    suspend fun setAuthToken(token: String?)

    /** The OBP consumer key stored in encrypted platform storage. Empty when not yet configured. */
    val consumerKey: String

    /** Persists the OBP consumer key to encrypted platform storage. */
    suspend fun setConsumerKey(key: String)

    suspend fun clearUserData()
}
