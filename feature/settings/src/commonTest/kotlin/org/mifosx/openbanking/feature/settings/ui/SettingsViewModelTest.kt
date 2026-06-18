/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.mifosx.openbanking.feature.settings.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.mifosx.openbanking.core.data.auth.AuthRecoveryRepository
import org.mifosx.openbanking.core.data.auth.ObpAuthRepository
import org.mifosx.openbanking.core.data.profile.ProfileRepository
import org.mifosx.openbanking.core.data.user.UserDataRepository
import org.mifosx.openbanking.core.model.obp.ProfileUpdateRequest
import org.mifosx.openbanking.core.model.obp.UserProfile
import org.mifosx.openbanking.core.model.user.DarkThemeConfig
import org.mifosx.openbanking.core.model.user.LanguageConfig
import org.mifosx.openbanking.core.model.user.ThemeBrand
import org.mifosx.openbanking.core.model.user.UserData
import template.core.base.store.screen.ScreenState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Stateful fake — mutates its backing [userData] StateFlow on each setter so the ViewModel,
 * which derives its [SettingsUiState] reactively from [userData], observes the change.
 */
private class FakeUserDataRepository(
    initial: UserData = UserData.DEFAULT,
) : UserDataRepository {
    private val _userData = MutableStateFlow(initial)
    override val userData: StateFlow<UserData> = _userData
    override val authToken: String? = null
    override val passcode: String = ""
    override val observeLanguage: Flow<LanguageConfig> = _userData.map { it.appLanguage }
    override val observeDarkThemeConfig: Flow<DarkThemeConfig> = _userData.map { it.darkThemeConfig }
    override val observeDynamicColorPreference: Flow<Boolean> = _userData.map { it.useDynamicColor }
    override val observeScreenCapturePreference: Flow<Boolean> = _userData.map { it.enableScreenCapture }
    override val observePushNotificationsEnabled: Flow<Boolean> = _userData.map { it.isPushNotificationsEnabled }
    override val observeTransactionAlertsEnabled: Flow<Boolean> = _userData.map { it.isTransactionAlertsEnabled }
    override val observeMarketingEnabled: Flow<Boolean> = _userData.map { it.isMarketingEnabled }

    override suspend fun setLanguage(language: LanguageConfig) {
        _userData.value = _userData.value.copy(appLanguage = language)
    }
    override suspend fun setThemeBrand(themeBrand: ThemeBrand) {
        _userData.value = _userData.value.copy(themeBrand = themeBrand)
    }
    override suspend fun setDarkThemeConfig(darkThemeConfig: DarkThemeConfig) {
        _userData.value = _userData.value.copy(darkThemeConfig = darkThemeConfig)
    }
    override suspend fun setDynamicColorPreference(useDynamicColor: Boolean) {
        _userData.value = _userData.value.copy(useDynamicColor = useDynamicColor)
    }
    override suspend fun setIsAuthenticated(isAuthenticated: Boolean) {
        _userData.value = _userData.value.copy(isAuthenticated = isAuthenticated)
    }
    override suspend fun setIsUnlocked(isUnlocked: Boolean) {
        _userData.value = _userData.value.copy(isUnlocked = isUnlocked)
    }
    override suspend fun setIsPasscodeEnabled(isPasscodeEnabled: Boolean) {
        _userData.value = _userData.value.copy(isPasscodeEnabled = isPasscodeEnabled)
    }
    override suspend fun setIsBiometricsEnabled(isBiometricsEnabled: Boolean) {
        _userData.value = _userData.value.copy(isBiometricsEnabled = isBiometricsEnabled)
    }
    override suspend fun setPushNotificationsEnabled(isEnabled: Boolean) {
        _userData.value = _userData.value.copy(isPushNotificationsEnabled = isEnabled)
    }
    override suspend fun setTransactionAlertsEnabled(isEnabled: Boolean) {
        _userData.value = _userData.value.copy(isTransactionAlertsEnabled = isEnabled)
    }
    override suspend fun setMarketingEnabled(isEnabled: Boolean) {
        _userData.value = _userData.value.copy(isMarketingEnabled = isEnabled)
    }
    override suspend fun setShowOnboarding(showOnboarding: Boolean) {
        _userData.value = _userData.value.copy(showOnboarding = showOnboarding)
    }
    override suspend fun setFirstTimeState(firstTimeState: Boolean) {
        _userData.value = _userData.value.copy(firstTimeUser = firstTimeState)
    }
    override suspend fun setPasscode(passcode: String) {
        _userData.value = _userData.value.copy(passcode = passcode)
    }
    private var _consumerKey: String = ""
    override val consumerKey: String get() = _consumerKey
    override suspend fun setConsumerKey(key: String) { _consumerKey = key }
    override suspend fun clearUserData() {
        _userData.value = UserData.DEFAULT
    }
}

private class FakeProfileRepository(
    private val result: Result<UserProfile> = Result.success(
        UserProfile(username = "Aisha Saleh", email = "aisha.s@example.com"),
    ),
) : ProfileRepository {
    override suspend fun current(): Result<UserProfile> = result
    override suspend fun update(request: ProfileUpdateRequest): Result<UserProfile> = result
}

private class FakeAuthRecoveryRepository(
    var initiateResult: Result<String> = Result.success("ok"),
) : AuthRecoveryRepository {
    var lastUsername: String? = null
    var lastEmail: String? = null
    override suspend fun initiateReset(username: String, email: String): Result<String> {
        lastUsername = username
        lastEmail = email
        return initiateResult
    }

    override suspend fun confirmReset(token: String, newPassword: String): Result<String> =
        Result.success("ok")
}

private class FakeObpAuthRepository : ObpAuthRepository {
    var loggedOut = false
    override suspend fun login(username: String, password: String): Result<Unit> = Result.success(Unit)
    override suspend fun prepareOidcAuthorization(): Result<String> = Result.success("")
    override suspend fun completeOidc(code: String, state: String): Result<Unit> = Result.success(Unit)
    override fun logout() {
        loggedOut = true
    }
    override fun isLoggedIn(): Boolean = false
}

class SettingsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun content(vm: SettingsViewModel): SettingsUiState =
        (vm.uiState.value as ScreenState.Content).data

    private fun settingsVm(
        userData: UserDataRepository = FakeUserDataRepository(),
        profile: ProfileRepository = FakeProfileRepository(),
        recovery: AuthRecoveryRepository = FakeAuthRecoveryRepository(),
        auth: ObpAuthRepository = FakeObpAuthRepository(),
        biometricAvailable: Boolean = true,
    ) = SettingsViewModel(userData, profile, recovery, auth, biometricAvailable = biometricAvailable)

    @Test
    fun initialState_hydratesDefaults_andClearsLoading() = runTest {
        val vm = SettingsViewModel(
            FakeUserDataRepository(),
            FakeProfileRepository(),
            FakeAuthRecoveryRepository(),
            FakeObpAuthRepository(),
            biometricAvailable = true,
            appVersion = "v1.0.0",
        )
        val s = content(vm)
        assertFalse(s.isLoading)
        assertEquals(DarkThemeConfig.FOLLOW_SYSTEM, s.themeConfig)
        assertEquals("en", s.selectedLanguage)
        assertFalse(s.isBiometricLoginEnabled)
        assertTrue(s.isBiometricAvailableOnDevice)
        assertEquals("v1.0.0", s.appVersion)
    }

    @Test
    fun onThemeConfigSelected_persistsEachMode() = runTest {
        val repo = FakeUserDataRepository()
        val vm = settingsVm(repo)
        vm.onThemeConfigSelected(DarkThemeConfig.DARK)
        assertEquals(DarkThemeConfig.DARK, repo.userData.value.darkThemeConfig)
        assertEquals(DarkThemeConfig.DARK, content(vm).themeConfig)
        vm.onThemeConfigSelected(DarkThemeConfig.LIGHT)
        assertEquals(DarkThemeConfig.LIGHT, repo.userData.value.darkThemeConfig)
        assertEquals(DarkThemeConfig.LIGHT, content(vm).themeConfig)
        vm.onThemeConfigSelected(DarkThemeConfig.FOLLOW_SYSTEM)
        assertEquals(DarkThemeConfig.FOLLOW_SYSTEM, repo.userData.value.darkThemeConfig)
        assertEquals(DarkThemeConfig.FOLLOW_SYSTEM, content(vm).themeConfig)
    }

    @Test
    fun onLanguageSelected_persistsMappedLocale() = runTest {
        val repo = FakeUserDataRepository()
        val vm = settingsVm(repo)
        vm.onLanguageSelected("es")
        assertEquals("es", content(vm).selectedLanguage)
        assertEquals(LanguageConfig.SPANISH, repo.userData.value.appLanguage)
    }

    @Test
    fun onBiometricToggled_persistsBiometricFlag() = runTest {
        val repo = FakeUserDataRepository()
        val vm = settingsVm(repo)
        vm.onBiometricToggled()
        assertTrue(content(vm).isBiometricLoginEnabled)
        assertTrue(repo.userData.value.isBiometricsEnabled)
    }

    @Test
    fun biometricUnavailable_reflectedInState() = runTest {
        val vm = settingsVm(biometricAvailable = false)
        assertFalse(content(vm).isBiometricAvailableOnDevice)
    }

    @Test
    fun profileHeader_populatesFromRepository() = runTest {
        val vm = settingsVm()
        val s = content(vm)
        assertEquals("Aisha Saleh", s.profileName)
        assertEquals("aisha.s@example.com", s.profileEmail)
        assertEquals("AS", s.profileInitials)
    }

    @Test
    fun onSignOut_clearsSessionAndToken() = runTest {
        val repo = FakeUserDataRepository(UserData.DEFAULT.copy(isAuthenticated = true))
        val auth = FakeObpAuthRepository()
        val vm = settingsVm(repo, auth = auth)
        vm.onSignOut()
        assertFalse(repo.userData.value.isAuthenticated)
        assertTrue(auth.loggedOut)
    }

    @Test
    fun onResetPassword_requestsResetForProfileIdentity() = runTest {
        val recovery = FakeAuthRecoveryRepository(initiateResult = Result.success("sent"))
        val vm = settingsVm(recovery = recovery)
        vm.onResetPassword()
        assertEquals("Aisha Saleh", recovery.lastUsername)
        assertEquals("aisha.s@example.com", recovery.lastEmail)
        assertEquals(
            "If your account is valid, a password reset link has been emailed to aisha.s@example.com.",
            vm.resetMessage.value,
        )
        vm.onResetMessageConsumed()
        assertNull(vm.resetMessage.value)
    }
}
