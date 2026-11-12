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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.mifosx.openbanking.core.data.login.ConsentResult
import org.mifosx.openbanking.feature.login.browser.BrowserLaunchException
import org.mifosx.openbanking.feature.login.ui.LoginAction
import org.mifosx.openbanking.feature.login.ui.LoginEvent
import org.mifosx.openbanking.feature.login.ui.LoginUiState
import org.mifosx.openbanking.feature.login.ui.LoginViewModel
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult
import template.core.base.ui.viewmodel.BackgroundEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoginViewModelTest {

    private fun successResult() = NetworkResult.Success(
        ConsentResult(
            authorizationUrl = "https://sandbox.test/auth",
            state = "state-abc",
            flowId = "flow-xyz",
            consentId = "consent-123",
        ),
    )

    private val pendingAuthStore = FakePendingAuthStore()

    private fun createViewModel(
        repo: FakeLoginRepository = FakeLoginRepository(),
        browser: FakeBrowserLauncher = FakeBrowserLauncher(),
    ): Pair<LoginViewModel, FakeBrowserLauncher> {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val vm = LoginViewModel(repo, browser, pendingAuthStore)
        return vm to browser
    }

    @Test
    fun `init loads permissions and transitions to Content`() = runTest {
        val (vm, _) = createViewModel()
        val state = vm.stateFlow.first { it is LoginUiState.Content }
        assertIs<LoginUiState.Content>(state)
        assertEquals(20, state.permissions.size)
        assertTrue(state.expiryLabel.startsWith("Expires"))
    }

    @Test
    fun `init with empty permissions transitions to Empty`() = runTest {
        val repo = FakeLoginRepository().apply { setPermissions(emptyList()) }
        val (vm, _) = createViewModel(repo)
        val state = vm.stateFlow.first { it is LoginUiState.Empty }
        assertIs<LoginUiState.Empty>(state)
    }

    @Test
    fun `StartOAuth on success transitions to Authorising and launches browser`() = runTest {
        val repo = FakeLoginRepository().apply { createConsentResult = successResult() }
        val (vm, browser) = createViewModel(repo)
        vm.stateFlow.first { it is LoginUiState.Content }
        vm.trySendAction(LoginAction.StartOAuth)
        val state = vm.stateFlow.first { it is LoginUiState.Authorising }
        assertIs<LoginUiState.Authorising>(state)
        assertEquals("https://sandbox.test/auth", browser.launchedUrl)
        assertEquals(1, repo.createConsentCallCount)
    }

    @Test
    fun `StartOAuth on 401 maps to Error`() = runTest {
        val repo = FakeLoginRepository().apply {
            createConsentResult = NetworkResult.Error(NetworkError.Client.Unauthorized("invalid"))
        }
        val (vm, _) = createViewModel(repo)
        vm.stateFlow.first { it is LoginUiState.Content }
        vm.trySendAction(LoginAction.StartOAuth)
        val state = vm.stateFlow.first { it is LoginUiState.Error }
        assertIs<LoginUiState.Error>(state)
        assertTrue(state.message.contains("Authorisation failed"))
    }

    @Test
    fun `StartOAuth on 400 maps to Error`() = runTest {
        val repo = FakeLoginRepository().apply {
            createConsentResult = NetworkResult.Error(NetworkError.Client.BadRequest("malformed"))
        }
        val (vm, _) = createViewModel(repo)
        vm.stateFlow.first { it is LoginUiState.Content }
        vm.trySendAction(LoginAction.StartOAuth)
        val state = vm.stateFlow.first { it is LoginUiState.Error }
        assertIs<LoginUiState.Error>(state)
        assertTrue(state.message.contains("HSBC rejected"))
    }

    @Test
    fun `StartOAuth on 500 maps to Error`() = runTest {
        val repo = FakeLoginRepository().apply {
            createConsentResult = NetworkResult.Error(NetworkError.Server(500, "down"))
        }
        val (vm, _) = createViewModel(repo)
        vm.stateFlow.first { it is LoginUiState.Content }
        vm.trySendAction(LoginAction.StartOAuth)
        val state = vm.stateFlow.first { it is LoginUiState.Error }
        assertIs<LoginUiState.Error>(state)
        assertTrue(state.message.contains("temporarily unavailable"))
    }

    @Test
    fun `StartOAuth on network error maps to Error`() = runTest {
        val repo = FakeLoginRepository().apply {
            createConsentResult = NetworkResult.Error(NetworkError.Network(RuntimeException("timeout")))
        }
        val (vm, _) = createViewModel(repo)
        vm.stateFlow.first { it is LoginUiState.Content }
        vm.trySendAction(LoginAction.StartOAuth)
        val state = vm.stateFlow.first { it is LoginUiState.Error }
        assertIs<LoginUiState.Error>(state)
        assertTrue(state.message.contains("Unable to reach HSBC"))
    }

    @Test
    fun `Cancel emits NavigateBack event`() = runTest {
        val (vm, _) = createViewModel()
        vm.stateFlow.first { it is LoginUiState.Content }
        vm.trySendAction(LoginAction.Cancel)
        val event = vm.eventFlow.first { it is LoginEvent.NavigateBack }
        assertIs<LoginEvent.NavigateBack>(event)
        assertIs<BackgroundEvent>(event)
    }

    @Test
    fun `StartOAuth clears any orphaned pending auth before beginning`() = runTest {
        pendingAuthStore.save("stale-state", "stale-flow", "stale-consent")
        val repo = FakeLoginRepository().apply { createConsentResult = successResult() }
        val (vm, _) = createViewModel(repo)
        vm.stateFlow.first { it is LoginUiState.Content }

        val clearsBefore = pendingAuthStore.clearCount
        vm.trySendAction(LoginAction.StartOAuth)
        vm.stateFlow.first { it is LoginUiState.Authorising }

        assertTrue(pendingAuthStore.clearCount > clearsBefore, "a new attempt must purge stale pending auth")
        assertEquals("state-abc", pendingAuthStore.saved?.state)
    }

    @Test
    fun `Retry reloads config from Error state`() = runTest {
        val repo = FakeLoginRepository().apply {
            createConsentResult = NetworkResult.Error(NetworkError.Network(RuntimeException("fail")))
        }
        val (vm, _) = createViewModel(repo)
        vm.stateFlow.first { it is LoginUiState.Content }
        vm.trySendAction(LoginAction.StartOAuth)
        vm.stateFlow.first { it is LoginUiState.Error }
        repo.createConsentResult = successResult()
        vm.trySendAction(LoginAction.Retry)
        val state = vm.stateFlow.first { it is LoginUiState.Content }
        assertIs<LoginUiState.Content>(state)
    }

    @Test
    fun `StartOAuth from Authorising state is a no-op`() = runTest {
        val repo = FakeLoginRepository().apply { createConsentResult = successResult() }
        val (vm, _) = createViewModel(repo)
        vm.stateFlow.first { it is LoginUiState.Content }
        vm.trySendAction(LoginAction.StartOAuth)
        vm.stateFlow.first { it is LoginUiState.Authorising }
        vm.trySendAction(LoginAction.StartOAuth)
        assertIs<LoginUiState.Authorising>(vm.stateFlow.value)
    }

    @Test
    fun `StartOAuth from Error state is a no-op`() = runTest {
        val repo = FakeLoginRepository().apply {
            createConsentResult = NetworkResult.Error(NetworkError.Network(RuntimeException("fail")))
        }
        val (vm, _) = createViewModel(repo)
        vm.stateFlow.first { it is LoginUiState.Content }
        vm.trySendAction(LoginAction.StartOAuth)
        vm.stateFlow.first { it is LoginUiState.Error }
        vm.trySendAction(LoginAction.StartOAuth)
        assertIs<LoginUiState.Error>(vm.stateFlow.value)
    }

    @Test
    fun `StartOAuth persists the state flow and consentId for the callback to validate against`() = runTest {
        val repo = FakeLoginRepository().apply { createConsentResult = successResult() }
        val (vm, _) = createViewModel(repo)
        vm.stateFlow.first { it is LoginUiState.Content }

        vm.trySendAction(LoginAction.StartOAuth)
        vm.stateFlow.first { it is LoginUiState.Authorising }

        val saved = pendingAuthStore.saved
        assertNotNull(saved, "without this the callback has nothing to authenticate the redirect against")
        assertEquals("state-abc", saved.state)
        assertEquals("flow-xyz", saved.flowId)
        assertEquals("consent-123", saved.consentId)
    }

    @Test
    fun `StartOAuth persists before opening the browser`() = runTest {
        val repo = FakeLoginRepository().apply { createConsentResult = successResult() }
        val browser = object : FakeBrowserLauncher() {
            var storeHadValueAtLaunch: Boolean = false

            override fun launch(url: String) {
                storeHadValueAtLaunch = pendingAuthStore.saved != null
                super.launch(url)
            }
        }
        val (vm, _) = createViewModel(repo, browser)
        vm.stateFlow.first { it is LoginUiState.Content }

        vm.trySendAction(LoginAction.StartOAuth)
        vm.stateFlow.first { it is LoginUiState.Authorising }

        assertTrue(
            browser.storeHadValueAtLaunch,
            "the PSU can leave for the browser the instant it opens; persisting after would race",
        )
    }

    @Test
    fun `StartOAuth does not persist when consent creation fails`() = runTest {
        val repo = FakeLoginRepository().apply {
            createConsentResult = NetworkResult.Error(NetworkError.Network(RuntimeException("fail")))
        }
        val (vm, _) = createViewModel(repo)
        vm.stateFlow.first { it is LoginUiState.Content }

        vm.trySendAction(LoginAction.StartOAuth)
        vm.stateFlow.first { it is LoginUiState.Error }

        assertEquals(0, pendingAuthStore.saveCount)
    }

    @Test
    fun `StartOAuth errors without contacting HSBC when the platform cannot receive the redirect`() = runTest {
        val repo = FakeLoginRepository().apply { createConsentResult = successResult() }
        val browser = FakeBrowserLauncher(isSupported = false)
        val (vm, _) = createViewModel(repo, browser)
        vm.stateFlow.first { it is LoginUiState.Content }

        vm.trySendAction(LoginAction.StartOAuth)

        val state = vm.stateFlow.first { it is LoginUiState.Error }
        assertIs<LoginUiState.Error>(state)
        assertEquals(LoginViewModel.UNSUPPORTED_PLATFORM_MESSAGE, state.message)
        assertEquals(
            0,
            repo.createConsentCallCount,
            "creating a consent we can never complete would strand it at the bank",
        )
        assertEquals(0, browser.launchCount)
        assertEquals(0, pendingAuthStore.saveCount)
    }

    @Test
    fun `StartOAuth surfaces an error instead of Authorising when the browser cannot be opened`() = runTest {
        val repo = FakeLoginRepository().apply { createConsentResult = successResult() }
        val browser = object : FakeBrowserLauncher() {
            override fun launch(url: String) {
                throw BrowserLaunchException("no browser")
            }
        }
        val (vm, _) = createViewModel(repo, browser)
        vm.stateFlow.first { it is LoginUiState.Content }

        vm.trySendAction(LoginAction.StartOAuth)

        val state = vm.stateFlow.first { it is LoginUiState.Error }
        assertIs<LoginUiState.Error>(state)
        assertEquals(LoginViewModel.BROWSER_LAUNCH_FAILED_MESSAGE, state.message)
    }

    @Test
    fun `StartOAuth clears the pending auth when the browser cannot be opened`() = runTest {
        val repo = FakeLoginRepository().apply { createConsentResult = successResult() }
        val browser = object : FakeBrowserLauncher() {
            override fun launch(url: String) {
                throw BrowserLaunchException("no browser")
            }
        }
        val (vm, _) = createViewModel(repo, browser)
        vm.stateFlow.first { it is LoginUiState.Content }

        vm.trySendAction(LoginAction.StartOAuth)
        vm.stateFlow.first { it is LoginUiState.Error }

        // A saved state/nonce that no redirect can consume would be inherited by the next attempt
        // and fail its CSRF check, so the aborted attempt must leave nothing behind. Asserted via
        // consume() rather than `saved`, which is a spy of the last write and survives clear().
        assertNull(pendingAuthStore.consume(), "an unreachable pending auth must not outlive the attempt")
    }

    @Test
    fun `mapErrorToMessage covers all error types`() {
        assertNotNull(LoginViewModel.mapErrorToMessage(NetworkError.Client.Unauthorized("")))
        assertNotNull(LoginViewModel.mapErrorToMessage(NetworkError.Client.BadRequest("")))
        assertNotNull(LoginViewModel.mapErrorToMessage(NetworkError.Client.Forbidden("")))
        assertNotNull(LoginViewModel.mapErrorToMessage(NetworkError.Server(500, "")))
        assertNotNull(LoginViewModel.mapErrorToMessage(NetworkError.Network(RuntimeException(""))))
        assertNotNull(LoginViewModel.mapErrorToMessage(NetworkError.Unknown()))
    }
}
