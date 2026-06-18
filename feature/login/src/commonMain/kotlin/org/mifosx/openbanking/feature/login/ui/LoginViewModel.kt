/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.mifosx.openbanking.feature.login.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.mifosx.openbanking.core.data.auth.ObpAuthRepository
import org.mifosx.openbanking.core.data.auth.OidcCallbackBus
import org.mifosx.openbanking.core.data.user.UserDataRepository
import org.mifosx.openbanking.core.model.obp.ObpException
import org.mifosx.openbanking.core.network.obp.ObpConfig
import template.core.base.ui.viewmodel.BaseViewModel

/**
 * Login ViewModel. Owns dual OBP authentication:
 *
 * - **DirectLogin** — submits username/password through [ObpAuthRepository.login].
 * - **OAuth/OIDC** — authorization_code + PKCE (S256). [onOAuthLoginClicked] asks the repository to
 *   discover the provider + register a public client and return the authorize URL, which the screen
 *   opens in a browser ([LoginEvent.LaunchOidcAuth]). The redirect comes back through
 *   [OidcCallbackBus] (filled by the platform deep-link handler); this ViewModel collects it and runs
 *   [ObpAuthRepository.completeOidc] for the token exchange.
 *
 * On success the session token is held by the repository's `ObpTokenProvider` and the user is
 * marked authenticated in [UserDataRepository]; `RootNavViewModel` observes that and flips the
 * root graph to the authenticated destination — login never navigates to home imperatively.
 */
class LoginViewModel(
    private val authRepository: ObpAuthRepository,
    private val userDataRepository: UserDataRepository,
    private val oidcCallbackBus: OidcCallbackBus,
    private val obpConfig: ObpConfig,
) : BaseViewModel<LoginState, LoginEvent, LoginAction>(
    initialState = LoginState(),
) {

    init {
        viewModelScope.launch {
            oidcCallbackBus.callbacks.collect { callback ->
                sendAction(LoginAction.OAuthCallback(callback.code, callback.state))
            }
        }
    }

    override fun handleAction(action: LoginAction) {
        when (action) {
            is LoginAction.UsernameChanged ->
                updateState { copy(username = action.value, errorMessage = null) }

            is LoginAction.PasswordChanged ->
                updateState { copy(password = action.value, errorMessage = null) }

            LoginAction.RememberMeToggled ->
                updateState { copy(rememberMe = !rememberMe) }

            LoginAction.PasswordVisibilityToggled ->
                updateState { copy(isPasswordVisible = !isPasswordVisible) }

            is LoginAction.ConsumerKeyChanged ->
                updateState { copy(consumerKey = action.value) }

            LoginAction.DirectLoginClicked -> onDirectLoginClicked()

            LoginAction.OAuthLoginClicked -> onOAuthLoginClicked()

            is LoginAction.OAuthCallback -> onOAuthCallback(action.code, action.state)

            LoginAction.ForgotPasswordClicked ->
                sendEvent(LoginEvent.NavigateToForgotPassword)

            is LoginAction.Internal.DirectLoginResultReceive ->
                onAuthResult(action.result, AuthMethod.DIRECT_LOGIN)

            is LoginAction.Internal.OAuthResultReceive ->
                onAuthResult(action.result, AuthMethod.OAUTH_OIDC)
        }
    }

    private fun onDirectLoginClicked() {
        val current = state
        if (!current.isFormValid || current.isLoading) return
        updateState {
            copy(isLoading = true, errorMessage = null, authMethod = AuthMethod.DIRECT_LOGIN)
        }
        viewModelScope.launch {
            val key = current.consumerKey.trim()
            if (key.isNotBlank()) {
                obpConfig.consumerKey = key
                userDataRepository.setConsumerKey(key)
            }
            val result = authRepository.login(current.username.trim(), current.password)
            sendAction(LoginAction.Internal.DirectLoginResultReceive(result))
        }
    }

    private fun onOAuthLoginClicked() {
        if (state.isLoading || state.oauthPhase != OAuthPhase.NONE) return
        updateState {
            copy(authMethod = AuthMethod.OAUTH_OIDC, oauthPhase = OAuthPhase.REDIRECTING, errorMessage = null)
        }
        viewModelScope.launch {
            authRepository.prepareOidcAuthorization().fold(
                onSuccess = { authUrl -> sendEvent(LoginEvent.LaunchOidcAuth(authUrl)) },
                onFailure = { error ->
                    updateState {
                        copy(
                            oauthPhase = OAuthPhase.NONE,
                            authMethod = AuthMethod.NONE,
                            errorMessage = error.toLoginErrorMessage(AuthMethod.OAUTH_OIDC),
                        )
                    }
                },
            )
        }
    }

    private fun onOAuthCallback(code: String, returnedState: String) {
        if (state.oauthPhase != OAuthPhase.REDIRECTING) return
        updateState { copy(oauthPhase = OAuthPhase.EXCHANGING) }
        viewModelScope.launch {
            val result = authRepository.completeOidc(code, returnedState)
            sendAction(LoginAction.Internal.OAuthResultReceive(result))
        }
    }

    private fun onAuthResult(result: Result<Unit>, method: AuthMethod) {
        result.fold(
            onSuccess = {
                viewModelScope.launch {
                    userDataRepository.setIsAuthenticated(true)
                    userDataRepository.setIsUnlocked(true)
                }
            },
            onFailure = { error ->
                updateState {
                    copy(
                        isLoading = false,
                        oauthPhase = OAuthPhase.NONE,
                        authMethod = AuthMethod.NONE,
                        errorMessage = error.toLoginErrorMessage(method),
                    )
                }
            },
        )
    }
}

/**
 * Maps an [ObpException.reason] (or unknown failure) to the user-facing message defined in the
 * login feature SPEC. OAuth token-exchange failures use the OAuth-specific copy.
 */
private fun Throwable.toLoginErrorMessage(method: AuthMethod): String =
    when ((this as? ObpException)?.reason) {
        "UNAUTHORIZED", "BAD_REQUEST" ->
            if (method == AuthMethod.OAUTH_OIDC) {
                "Authentication failed. Please try signing in again."
            } else {
                "Invalid username or password. Please check your credentials and try again."
            }

        "REQUEST_TIMEOUT", "TOO_MANY_REQUESTS", "UNKNOWN" ->
            "Could not connect to banking services. Please try again."

        else ->
            "Something went wrong on our end. Please try again in a moment."
    }

/** Immutable UI state for the Login screen. Field set mirrors the login SPEC State Model. */
@Immutable
data class LoginState(
    val username: String = "",
    val password: String = "",
    val consumerKey: String = "",
    val rememberMe: Boolean = false,
    val isPasswordVisible: Boolean = false,
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
    val authMethod: AuthMethod = AuthMethod.NONE,
    val oauthPhase: OAuthPhase = OAuthPhase.NONE,
) {
    /** DirectLogin is enabled only when both credential fields are non-blank. */
    val isFormValid: Boolean get() = username.isNotBlank() && password.isNotBlank()
}

/** Which authentication path is in flight. */
enum class AuthMethod { NONE, DIRECT_LOGIN, OAUTH_OIDC }

/** OAuth full-screen takeover phase. */
enum class OAuthPhase { NONE, REDIRECTING, EXCHANGING }

/** One-shot navigation/side-effect events emitted by [LoginViewModel]. */
sealed interface LoginEvent {
    /** Navigate to the forgot-password destination within the auth graph. */
    data object NavigateToForgotPassword : LoginEvent

    /** Open the system browser at the OBP OIDC authorize URL (platform handles the launch). */
    data class LaunchOidcAuth(val authUrl: String) : LoginEvent
}

/** Actions accepted by [LoginViewModel]. */
sealed interface LoginAction {
    data class UsernameChanged(val value: String) : LoginAction
    data class PasswordChanged(val value: String) : LoginAction
    data class ConsumerKeyChanged(val value: String) : LoginAction
    data object RememberMeToggled : LoginAction
    data object PasswordVisibilityToggled : LoginAction
    data object DirectLoginClicked : LoginAction
    data object OAuthLoginClicked : LoginAction
    data class OAuthCallback(val code: String, val state: String) : LoginAction
    data object ForgotPasswordClicked : LoginAction

    /** Internal actions posted from async work back onto the synchronous action stream. */
    sealed interface Internal : LoginAction {
        data class DirectLoginResultReceive(val result: Result<Unit>) : Internal
        data class OAuthResultReceive(val result: Result<Unit>) : Internal
    }
}
