/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.mifosx.openbanking.feature.login

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.mifosx.openbanking.core.designsystem.icon.AppIcons
import org.mifosx.openbanking.core.ui.card.HeroGradientCard
import org.mifosx.openbanking.feature.login.ui.LoginAction
import org.mifosx.openbanking.feature.login.ui.LoginEvent
import org.mifosx.openbanking.feature.login.ui.LoginState
import org.mifosx.openbanking.feature.login.ui.LoginViewModel
import org.mifosx.openbanking.feature.login.ui.OAuthPhase

/**
 * Stateful Login container. Binds [LoginViewModel] state + one-shot events to the stateless
 * [LoginContent]. Login success is reactive (the root nav observes the authenticated session),
 * so this composable only forwards the forgot-password navigation + OAuth browser launch.
 */
@Composable
internal fun LoginScreen(
    onNavigateToForgotPassword: () -> Unit,
    onLaunchOidcAuth: (authUrl: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = koinViewModel(),
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                LoginEvent.NavigateToForgotPassword -> onNavigateToForgotPassword()
                is LoginEvent.LaunchOidcAuth -> onLaunchOidcAuth(event.authUrl)
            }
        }
    }

    LoginContent(
        state = state,
        onAction = viewModel::trySendAction,
        modifier = modifier,
    )
}

@Composable
private fun LoginContent(
    state: LoginState,
    onAction: (LoginAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when (state.oauthPhase) {
            OAuthPhase.REDIRECTING -> OAuthTakeover(
                title = "Redirecting to Open Bank Project",
                message = "Opening your browser for secure OAuth authentication…",
            )

            OAuthPhase.EXCHANGING -> OAuthTakeover(
                title = "Completing Sign In",
                message = "Exchanging authorization code for your session token…",
            )

            OAuthPhase.NONE -> LoginForm(state = state, onAction = onAction)
        }
    }
}

@Composable
private fun LoginForm(
    state: LoginState,
    onAction: (LoginAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LoginHero()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-28).dp)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LoginCard(state = state, onAction = onAction)
            LoginAlternativeAuth(state = state, onAction = onAction)
            Text(
                text = "Powered by Mifos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp, bottom = 40.dp),
            )
        }
    }
}

/** Green gradient hero with the Mifos X badge + welcome copy. Reuses the shared [HeroGradientCard]. */
@Composable
private fun LoginHero(modifier: Modifier = Modifier) {
    HeroGradientCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 72.dp, bottom = 56.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.White.copy(alpha = 0.18f),
            ) {
                Text(
                    text = "Mifos X",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
                )
            }
            Text(
                text = "Welcome back",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "Sign in to your sandbox account",
                style = MaterialTheme.typography.bodyMedium,
                color = LocalContentColor.current.copy(alpha = 0.85f),
            )
        }
    }
}

/** The white "Direct Login" card: username + password + Sign In. */
@Composable
private fun LoginCard(
    state: LoginState,
    onAction: (LoginAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Direct Login",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.primary,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary,
            )
            OutlinedTextField(
                value = state.username,
                onValueChange = { onAction(LoginAction.UsernameChanged(it)) },
                label = { Text("Username") },
                placeholder = { Text("Enter your username") },
                singleLine = true,
                enabled = !state.isLoading,
                isError = state.errorMessage != null,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.Next,
                ),
                colors = fieldColors,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = { onAction(LoginAction.PasswordChanged(it)) },
                label = { Text("Password") },
                singleLine = true,
                enabled = !state.isLoading,
                isError = state.errorMessage != null,
                visualTransformation = if (state.isPasswordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                trailingIcon = {
                    IconButton(onClick = { onAction(LoginAction.PasswordVisibilityToggled) }) {
                        Icon(
                            imageVector = if (state.isPasswordVisible) {
                                AppIcons.VisibilityOff
                            } else {
                                AppIcons.Visibility
                            },
                            contentDescription = if (state.isPasswordVisible) {
                                "Hide password"
                            } else {
                                "Show password"
                            },
                        )
                    }
                },
                colors = fieldColors,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.consumerKey,
                onValueChange = { onAction(LoginAction.ConsumerKeyChanged(it)) },
                label = { Text("Consumer Key") },
                placeholder = { Text("OBP consumer key (prod builds fetch this automatically)") },
                singleLine = true,
                enabled = !state.isLoading,
                visualTransformation = PasswordVisualTransformation(),
                colors = fieldColors,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = state.rememberMe,
                    onCheckedChange = { onAction(LoginAction.RememberMeToggled) },
                    enabled = !state.isLoading,
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                )
                Text(
                    text = "Keep me signed in",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (state.errorMessage != null) {
                ErrorBanner(message = state.errorMessage)
            }
            Button(
                onClick = { onAction(LoginAction.DirectLoginClicked) },
                enabled = state.isFormValid && !state.isLoading,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Text("Login", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun LoginAlternativeAuth(
    state: LoginState,
    onAction: (LoginAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        OrDivider()
        Spacer(Modifier.height(24.dp))

        OutlinedButton(
            onClick = { onAction(LoginAction.OAuthLoginClicked) },
            enabled = !state.isLoading,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = AppIcons.SendRightTilted,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text("Sign in with OBP Account", style = MaterialTheme.typography.labelLarge)
        }
        Text(
            text = "Redirects to Open Bank Project for secure authentication",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )

        Spacer(Modifier.height(16.dp))
        TextButton(
            onClick = { onAction(LoginAction.ForgotPasswordClicked) },
            modifier = Modifier.heightIn(min = 44.dp),
        ) {
            Text(
                text = "Forgot Password?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun ErrorBanner(message: String, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = AppIcons.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun OrDivider(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Text(
            text = "OR",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@Composable
private fun OAuthTakeover(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = AppIcons.Bank,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(80.dp)
                .padding(bottom = 24.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp),
        )
    }
}
