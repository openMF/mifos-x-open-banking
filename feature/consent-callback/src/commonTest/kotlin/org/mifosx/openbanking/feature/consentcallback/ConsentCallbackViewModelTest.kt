/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.consentcallback

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.mifosx.openbanking.core.data.callback.CallbackSecurityRejected
import org.mifosx.openbanking.core.data.callback.ConsentRejectedAtBank
import org.mifosx.openbanking.core.data.callback.SettingsConsentSession
import org.mifosx.openbanking.core.data.callback.ValidationResult
import org.mifosx.openbanking.feature.consentcallback.ui.ConsentCallbackAction
import org.mifosx.openbanking.feature.consentcallback.ui.ConsentCallbackEvent
import org.mifosx.openbanking.feature.consentcallback.ui.ConsentCallbackUiState
import org.mifosx.openbanking.feature.consentcallback.ui.ConsentCallbackViewModel
import template.core.base.common.screen.DataFreshness
import template.core.base.common.screen.ScreenState
import template.core.base.ui.viewmodel.BackgroundEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ConsentCallbackViewModelTest {

    private val repository = FakeConsentCallbackRepository()
    private val secureSettings = MapSettings()

    /** The real session, so the tests pin the storage contract the navigator reads. */
    private val consentSession = SettingsConsentSession(secureSettings)

    private fun createViewModel(): ConsentCallbackViewModel {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        return ConsentCallbackViewModel(
            repository = repository,
            consentSession = consentSession,
        )
    }

    private fun ConsentCallbackViewModel.processCallback(url: String = CALLBACK_URL) =
        trySendAction(ConsentCallbackAction.ProcessCallback(url))

    @Test
    fun `initial state is Loading`() = runTest {
        val vm = createViewModel()

        assertIs<ConsentCallbackUiState.Loading>(vm.stateFlow.value)
    }

    @Test
    fun `ProcessCallback forwards the raw redirect url to the repository`() = runTest {
        val vm = createViewModel()

        vm.processCallback("org.mifosx.openbanking://callback?code=xyz&state=st-1")

        assertEquals(
            listOf("org.mifosx.openbanking://callback?code=xyz&state=st-1"),
            repository.validatedUrls,
        )
    }

    @Test
    fun `a valid callback exchanges the code and reaches Content`() = runTest {
        repository.validateResult = FakeConsentCallbackRepository.VALID
        val vm = createViewModel()

        vm.processCallback()

        assertIs<ConsentCallbackUiState.Content>(vm.stateFlow.first { it is ConsentCallbackUiState.Content })
        assertEquals(listOf("auth-code"), repository.exchangedCodes)
        assertTrue(repository.polledConsentIds.isEmpty(), "auth/complete already carries the consent status")
    }

    @Test
    fun `a valid callback persists the worker session and no bank token`() = runTest {
        val vm = createViewModel()

        vm.processCallback()
        vm.stateFlow.first { it is ConsentCallbackUiState.Content }

        assertEquals("worker-session", secureSettings.getStringOrNull("gateway_session"))
        assertTrue(consentSession.isActive())
        assertTrue(secureSettings.keys.none { "token" in it }, "keys: ${secureSettings.keys}")
    }

    @Test
    fun `an authorised consent persists the consent id and expiry`() = runTest {
        val vm = createViewModel()

        vm.processCallback()
        vm.stateFlow.first { it is ConsentCallbackUiState.Content }

        assertEquals("cn-1", consentSession.consentId())
        assertEquals("2026-07-01T00:00:00Z", secureSettings.getStringOrNull("hsbc_consent_expiration"))
    }

    @Test
    fun `an authorised consent navigates home after the success dwell`() = runTest {
        val vm = createViewModel()

        vm.processCallback()
        vm.stateFlow.first { it is ConsentCallbackUiState.Content }
        advanceTimeBy(2_000)

        val event = vm.eventFlow.first()
        assertIs<ConsentCallbackEvent.NavigateToHome>(event)
        assertIs<BackgroundEvent>(event)
    }

    @Test
    fun `SecurityError validation surfaces the SecurityError state`() = runTest {
        repository.validateResult = ValidationResult.SecurityError
        val vm = createViewModel()

        vm.processCallback()

        assertIs<ConsentCallbackUiState.SecurityError>(
            vm.stateFlow.first { it is ConsentCallbackUiState.SecurityError },
        )
        assertTrue(repository.exchangedCodes.isEmpty(), "a failed check must never exchange the code")
    }

    @Test
    fun `AccessDenied validation surfaces the AccessDenied state`() = runTest {
        repository.validateResult = ValidationResult.AccessDenied
        val vm = createViewModel()

        vm.processCallback()

        assertIs<ConsentCallbackUiState.AccessDenied>(
            vm.stateFlow.first { it is ConsentCallbackUiState.AccessDenied },
        )
        assertTrue(repository.exchangedCodes.isEmpty())
    }

    @Test
    fun `MissingCode validation surfaces an explanatory Error`() = runTest {
        repository.validateResult = ValidationResult.MissingCode
        val vm = createViewModel()

        vm.processCallback()

        val state = vm.stateFlow.first { it is ConsentCallbackUiState.Error }
        assertIs<ConsentCallbackUiState.Error>(state)
        assertTrue(state.message.contains("No authorisation code"))
    }

    @Test
    fun `an Error validation surfaces the repository message`() = runTest {
        repository.validateResult = ValidationResult.Error("HSBC is down")
        val vm = createViewModel()

        vm.processCallback()

        val state = vm.stateFlow.first { it is ConsentCallbackUiState.Error }
        assertIs<ConsentCallbackUiState.Error>(state)
        assertEquals("HSBC is down", state.message)
    }

    @Test
    fun `a security rejection from the worker shows the SecurityError state, not a decline`() = runTest {
        repository.exchangeResult = ScreenState.Error(CallbackSecurityRejected("invalid_id_token"))
        val vm = createViewModel()

        vm.processCallback()

        assertIs<ConsentCallbackUiState.SecurityError>(
            vm.stateFlow.first { it is ConsentCallbackUiState.SecurityError },
        )
    }

    @Test
    fun `a consent the bank rejected clears the session and says so`() = runTest {
        consentSession.saveConsentMeta("cn-old", "")
        repository.exchangeResult = ScreenState.Error(ConsentRejectedAtBank())
        val vm = createViewModel()

        vm.processCallback()

        val state = assertIs<ConsentCallbackUiState.Error>(vm.stateFlow.first { it is ConsentCallbackUiState.Error })
        assertTrue(state.message.contains("rejected"))
        assertTrue(!consentSession.isActive())
    }

    @Test
    fun `a failed token exchange surfaces an Error and does not poll`() = runTest {
        repository.exchangeResult = ScreenState.Error(FakeConsentCallbackRepository.NETWORK_ERROR)
        val vm = createViewModel()

        vm.processCallback()

        assertIs<ConsentCallbackUiState.Error>(vm.stateFlow.first { it is ConsentCallbackUiState.Error })
        assertTrue(repository.polledConsentIds.isEmpty())
    }

    @Test
    fun `an unauthenticated token exchange surfaces an Error`() = runTest {
        repository.exchangeResult = ScreenState.Unauthenticated
        val vm = createViewModel()

        vm.processCallback()

        val state = vm.stateFlow.first { it is ConsentCallbackUiState.Error }
        assertIs<ConsentCallbackUiState.Error>(state)
        assertTrue(state.message.contains("Authorisation failed"))
    }

    @Test
    fun `an offline token exchange surfaces a reachability Error`() = runTest {
        repository.exchangeResult = ScreenState.NoNetwork(isCaptivePortal = false)
        val vm = createViewModel()

        vm.processCallback()

        val state = vm.stateFlow.first { it is ConsentCallbackUiState.Error }
        assertIs<ConsentCallbackUiState.Error>(state)
        assertTrue(state.message.contains("Unable to reach HSBC"))
    }

    @Test
    fun `an awaiting consent status still records the consent and completes sign-in`() = runTest {
        // The Worker issued a session, so the PSU authorised; the bank reporting AwaitingAuthorisation
        // a beat later is a propagation lag, so it must not block — this is the fix for a fresh
        // consent staying invisible until a second authorisation.
        repository.exchangeResult = ScreenState.Content(
            FakeConsentCallbackRepository.COMPLETED_AUTH.copy(consentStatus = "AwaitingAuthorisation"),
            DataFreshness.FRESH,
        )
        val vm = createViewModel()

        vm.processCallback()
        vm.stateFlow.first { it is ConsentCallbackUiState.Content }

        assertEquals("cn-1", consentSession.consentId())
        assertEquals("2026-07-01T00:00:00Z", secureSettings.getStringOrNull("hsbc_consent_expiration"))
    }

    @Test
    fun `an unknown consent expiry still completes sign-in with an empty expiry`() = runTest {
        // The Worker could not read the consent (consentStatus Unknown, no expiry); that must not block.
        repository.exchangeResult = ScreenState.Content(
            FakeConsentCallbackRepository.COMPLETED_AUTH.copy(consentStatus = "Unknown", consentExpiresAt = null),
            DataFreshness.FRESH,
        )
        val vm = createViewModel()

        vm.processCallback()
        vm.stateFlow.first { it is ConsentCallbackUiState.Content }

        assertEquals("cn-1", consentSession.consentId())
        assertEquals("", secureSettings.getStringOrNull("hsbc_consent_expiration"))
    }

    @Test
    fun `PollConsentStatus before any callback is a no-op`() = runTest {
        val vm = createViewModel()

        vm.trySendAction(ConsentCallbackAction.PollConsentStatus)

        assertTrue(repository.polledConsentIds.isEmpty())
        assertIs<ConsentCallbackUiState.Loading>(vm.stateFlow.value)
    }

    @Test
    fun `NavigateRetry emits NavigateToLogin and keeps the session`() = runTest {
        secureSettings.putString("gateway_session", "existing")
        val vm = createViewModel()

        vm.trySendAction(ConsentCallbackAction.NavigateRetry)

        assertIs<ConsentCallbackEvent.NavigateToLogin>(vm.eventFlow.first())
        assertEquals("existing", secureSettings.getStringOrNull("gateway_session"))
    }

    @Test
    fun `NavigateLogin clears the secure session before emitting NavigateToLogin`() = runTest {
        secureSettings.putString("gateway_session", "existing")
        val vm = createViewModel()

        vm.trySendAction(ConsentCallbackAction.NavigateLogin)

        assertIs<ConsentCallbackEvent.NavigateToLogin>(vm.eventFlow.first())
        assertTrue(
            secureSettings.keys.isEmpty(),
            "a security failure must not leave credentials behind",
        )
    }

    private companion object {
        const val REDIRECT_URI = "https://thekalpeshpawar.github.io/obp-callback/callback/"
        const val CALLBACK_URL = "org.mifosx.openbanking://callback?code=auth-code&state=st-1"
    }
}
