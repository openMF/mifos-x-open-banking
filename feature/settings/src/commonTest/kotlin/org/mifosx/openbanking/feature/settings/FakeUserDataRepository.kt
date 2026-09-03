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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import org.mifosx.openbanking.core.data.user.UserDataRepository
import org.mifosx.openbanking.core.model.user.DarkThemeConfig
import org.mifosx.openbanking.core.model.user.LanguageConfig
import org.mifosx.openbanking.core.model.user.ThemeBrand
import org.mifosx.openbanking.core.model.user.UserData

/**
 * Hand-written [UserDataRepository] backed by an in-memory [UserData].
 *
 * Written rather than mocked — this project ships no mocking framework. A write is fed back into
 * the observed flow, as the real store does.
 */
class FakeUserDataRepository(
    initialTheme: DarkThemeConfig = DarkThemeConfig.FOLLOW_SYSTEM,
    initialAccountId: String = "",
) : UserDataRepository {

    private val users = MutableStateFlow(
        UserData.DEFAULT.copy(
            darkThemeConfig = initialTheme,
            selectedAccountId = initialAccountId,
        ),
    )

    private val writes = mutableListOf<DarkThemeConfig>()

    /** Every theme written, in the order the view model wrote it. */
    val writtenThemes: List<DarkThemeConfig> get() = writes.toList()

    override val userData: StateFlow<UserData> = users.asStateFlow()

    override val passcode: String get() = users.value.passcode

    override val observeLanguage: Flow<LanguageConfig> = users.map { it.appLanguage }

    override val observeDynamicColorPreference: Flow<Boolean> = users.map { it.useDynamicColor }

    override val observeScreenCapturePreference: Flow<Boolean> = users.map { it.enableScreenCapture }

    override val observeDarkThemeConfig: Flow<DarkThemeConfig> = users.map { it.darkThemeConfig }

    /** Pushes a stored value in from outside the view model, as another writer would. */
    fun emitTheme(config: DarkThemeConfig) {
        users.value = users.value.copy(darkThemeConfig = config)
    }

    override suspend fun setDarkThemeConfig(darkThemeConfig: DarkThemeConfig) {
        writes += darkThemeConfig
        users.value = users.value.copy(darkThemeConfig = darkThemeConfig)
    }

    override suspend fun setLanguage(language: LanguageConfig) {
        users.value = users.value.copy(appLanguage = language)
    }

    override suspend fun setThemeBrand(themeBrand: ThemeBrand) {
        users.value = users.value.copy(themeBrand = themeBrand)
    }

    override suspend fun setDynamicColorPreference(useDynamicColor: Boolean) {
        users.value = users.value.copy(useDynamicColor = useDynamicColor)
    }

    override suspend fun setIsAuthenticated(isAuthenticated: Boolean) {
        users.value = users.value.copy(isAuthenticated = isAuthenticated)
    }

    override suspend fun setIsUnlocked(isUnlocked: Boolean) {
        users.value = users.value.copy(isUnlocked = isUnlocked)
    }

    override suspend fun setIsPasscodeEnabled(isPasscodeEnabled: Boolean) {
        users.value = users.value.copy(isPasscodeEnabled = isPasscodeEnabled)
    }

    override suspend fun setIsBiometricsEnabled(isBiometricsEnabled: Boolean) {
        users.value = users.value.copy(isBiometricsEnabled = isBiometricsEnabled)
    }

    override suspend fun setSelectedAccountId(accountId: String) {
        users.value = users.value.copy(selectedAccountId = accountId)
    }

    override suspend fun clearUserData() {
        users.value = UserData.DEFAULT
    }
}
