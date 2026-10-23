/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.login.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.mifosx.openbanking.core.data.callback.PendingAuthStore
import org.mifosx.openbanking.core.data.login.LoginRepository
import org.mifosx.openbanking.core.model.hsbcPermission.OBPermission
import org.mifosx.openbanking.feature.login.browser.BrowserLaunchException
import org.mifosx.openbanking.feature.login.browser.BrowserLauncher
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult
import template.core.base.ui.viewmodel.BackgroundEvent
import template.core.base.ui.viewmodel.BaseViewModel
import kotlin.time.Clock

sealed interface LoginUiState {
    data object Loading : LoginUiState
    data class Content(
        val permissions: List<OBPermission>,
        val expiryLabel: String,
    ) : LoginUiState

    data object Authorising : LoginUiState
    data class Error(val message: String) : LoginUiState
    data object Empty : LoginUiState
}

sealed interface LoginEvent {
    data object NavigateBack : LoginEvent, BackgroundEvent
}

sealed interface LoginAction {
    data object LoadPermissionsConfig : LoginAction
    data object StartOAuth : LoginAction
    data object Cancel : LoginAction
    data object Retry : LoginAction
}

class LoginViewModel(
    private val loginRepository: LoginRepository,
    private val browserLauncher: BrowserLauncher,
    private val pendingAuthStore: PendingAuthStore,
) : BaseViewModel<LoginUiState, LoginEvent, LoginAction>(
    initialState = LoginUiState.Loading,
) {
    init {
        trySendAction(LoginAction.LoadPermissionsConfig)
    }

    override fun handleAction(action: LoginAction) {
        when (action) {
            LoginAction.LoadPermissionsConfig -> handleLoadConfig()
            LoginAction.StartOAuth -> handleStartOAuth()
            LoginAction.Cancel -> handleCancel()
            LoginAction.Retry -> handleRetry()
        }
    }

    private fun handleLoadConfig() {
        updateState { LoginUiState.Loading }
        val permissions = loginRepository.getPermissions()
        if (permissions.isEmpty()) {
            updateState { LoginUiState.Empty }
            return
        }
        updateState {
            LoginUiState.Content(
                permissions = permissions,
                expiryLabel = buildExpiryLabel(),
            )
        }
    }

    /**
     * Refuses up front on platforms with no redirect receiver: the PSU would otherwise authorise
     * successfully at HSBC and the callback would never arrive, leaving a live consent stranded.
     *
     * On success the `state`/`flowId`/`consentId` are persisted BEFORE handing off to the browser.
     * The PSU leaves the app entirely and the process may be killed while backgrounded, so the
     * redirect can arrive on a cold start — these are the values it is authenticated against.
     *
     * Any pending auth left by a previous, abandoned attempt (the PSU cancelled at HSBC and killed
     * the app, so no redirect ever ran [PendingAuthStore.consume]) is purged first — a fresh attempt
     * must never inherit an orphaned entry. This is safe: a legitimate cold-start redirect is handled
     * through [PendingAuthStore.consume] in the callback layer, never through here.
     */
    private fun handleStartOAuth() {
        if (state !is LoginUiState.Content) return

        if (!browserLauncher.isSupported) {
            updateState { LoginUiState.Error(UNSUPPORTED_PLATFORM_MESSAGE) }
            return
        }

        pendingAuthStore.clear()
        updateState { LoginUiState.Loading }
        viewModelScope.launch {
            when (val result = loginRepository.createConsentAndBuildAuthorizationUrl()) {
                is NetworkResult.Success -> {
                    pendingAuthStore.save(
                        state = result.data.state,
                        flowId = result.data.flowId,
                        consentId = result.data.consentId,
                    )
                    // Authorising means "the PSU is away at HSBC" and is only cleared by the
                    // redirect coming back. Entering it when no browser actually opened would
                    // strand them there forever, so the transition is gated on the launch.
                    try {
                        browserLauncher.launch(result.data.authorizationUrl)
                        updateState { LoginUiState.Authorising }
                    } catch (e: BrowserLaunchException) {
                        pendingAuthStore.clear()
                        updateState { LoginUiState.Error(BROWSER_LAUNCH_FAILED_MESSAGE) }
                    }
                }
                is NetworkResult.Error -> {
                    updateState { LoginUiState.Error(mapErrorToMessage(result.error)) }
                }
            }
        }
    }

    private fun handleCancel() {
        sendEvent(LoginEvent.NavigateBack)
    }

    private fun handleRetry() {
        trySendAction(LoginAction.LoadPermissionsConfig)
    }

    private fun buildExpiryLabel(): String {
        val expiryDate = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
            .plus(90, DateTimeUnit.DAY)
        val month = expiryDate.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
        return "Expires ${expiryDate.day} $month ${expiryDate.year}"
    }

    companion object {
        const val UNSUPPORTED_PLATFORM_MESSAGE =
            "Connecting to HSBC isn't supported on this platform. Please use the Android, iOS or desktop app."

        const val BROWSER_LAUNCH_FAILED_MESSAGE =
            "Couldn't open your browser to continue with HSBC. Please check that a " +
                "default browser is set, then try again."

        fun mapErrorToMessage(error: NetworkError): String = when (error) {
            is NetworkError.Client.Unauthorized -> "Authorisation failed. The app may need to re-register with HSBC."
            is NetworkError.Client.BadRequest -> "HSBC rejected the consent request. Please try again."
            is NetworkError.Server -> "HSBC is temporarily unavailable. Try again in a moment."
            is NetworkError.Network -> "Unable to reach HSBC. Check your connection and try again."
            else -> "Could not connect to HSBC. Please try again."
        }
    }
}
