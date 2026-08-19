/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package cmp.navigation.rootnav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import androidx.navigation.compose.NavHost
import androidx.navigation.navOptions
import cmp.navigation.authenticated.AuthenticatedGraphRoute
import cmp.navigation.authenticated.authenticatedGraph
import cmp.navigation.authenticated.navigateToAuthenticatedGraph
import cmp.navigation.splash.SplashRoute
import cmp.navigation.splash.navigateToSplash
import cmp.navigation.splash.splashDestination
import cmp.navigation.ui.rememberKptNavController
import cmp.navigation.utils.toObjectNavigationRoute
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.mifosx.openbanking.core.data.callback.AuthorisationLeg
import org.mifosx.openbanking.core.data.callback.ConsentRedirectBus
import org.mifosx.openbanking.core.data.callback.authorisationLegOf
import org.mifosx.openbanking.core.data.callback.PaymentAuthRepository
import org.mifosx.openbanking.core.data.vrp.VrpAuthRepository
import org.mifosx.openbanking.feature.consentcallback.ConsentCallbackRoute
import org.mifosx.openbanking.feature.consentcallback.consentCallbackDestination
import org.mifosx.openbanking.feature.consentcallback.navigateToConsentCallback
import org.mifosx.openbanking.feature.login.AuthGraphRoute
import org.mifosx.openbanking.feature.login.authGraph
import org.mifosx.openbanking.feature.login.navigateToAuthGraph
import org.mifosx.openbanking.feature.paymentconsent.PaymentConsentRoute
import org.mifosx.openbanking.feature.paymentconsent.paymentConsentScreen
import org.mifosx.openbanking.feature.paymentstatus.PaymentStatusRoute
import org.mifosx.openbanking.feature.paymentstatus.paymentStatusScreen
import org.mifosx.openbanking.feature.vrpcallback.callback.VrpCallbackRoute
import org.mifosx.openbanking.feature.vrpcallback.callback.vrpCallbackScreen
import template.core.base.ui.util.NonNullEnterTransitionProvider
import template.core.base.ui.util.NonNullExitTransitionProvider
import template.core.base.ui.util.RootTransitionProviders
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun RootNavScreen(
    modifier: Modifier = Modifier,
    viewModel: RootNavViewModel = koinViewModel(),
    navController: NavHostController = rememberKptNavController(name = "RootNavScreen"),
    onSplashScreenRemoved: () -> Unit = {},
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val previousStateReference = remember { AtomicReference(state) }
    val paymentAuthRepository: PaymentAuthRepository = koinInject()
    val vrpAuthRepository: VrpAuthRepository = koinInject()

    val isNotSplashScreen = state != RootNavState.Splash
    LaunchedEffect(isNotSplashScreen) {
        if (isNotSplashScreen) onSplashScreenRemoved()
    }

    /**
     * Built on demand, not up front.
     *
     * `popUpTo` needs `navController.graph`, which only exists once [NavHost] below has set it —
     * and `navOptions {}` runs its lambda eagerly. Evaluating this during composition therefore
     * threw `IllegalStateException: You must call setGraph() before calling getGraph()` and killed
     * the app on every launch. Every call site is a click handler or a LaunchedEffect, all of which
     * run after composition, so by then the graph is present.
     */
    val rootNavOptions: () -> NavOptions = {
        navOptions {
            popUpTo(navController.graph.id) {
                inclusive = false
                saveState = false
            }
            launchSingleTop = true
            restoreState = false
        }
    }

    NavHost(
        navController = navController,
        startDestination = SplashRoute,
        modifier = modifier,
        enterTransition = { toEnterTransition()(this) },
        exitTransition = { toExitTransition()(this) },
        popEnterTransition = { toEnterTransition()(this) },
        popExitTransition = { toExitTransition()(this) },
    ) {
        splashDestination()
        authGraph(navController)
        paymentConsentScreen(
            // A submitted payment goes to its receipt; an abandoned or restarted one goes back into
            // the app. Payment-status is registered here, at the root, rather than inside the navbar
            // graph — the navbar hosts its own NavHost, which this navigator cannot reach into.
            onPaymentSubmitted = { paymentId ->
                navController.navigate(PaymentStatusRoute(paymentId), rootNavOptions())
            },
            onRestartAuthorisation = { navController.navigateToAuthenticatedGraph(rootNavOptions()) },
            onAbandoned = { navController.navigateToAuthenticatedGraph(rootNavOptions()) },
        )
        paymentStatusScreen(
            onBack = { navController.navigateToAuthenticatedGraph(rootNavOptions()) },
            onStartNewPayment = { navController.navigateToAuthenticatedGraph(rootNavOptions()) },
        )
        consentCallbackDestination(
            onNavigateToHome = {
                navController.navigateToAuthenticatedGraph(rootNavOptions())
            },
            onNavigateToLogin = { navController.navigateToAuthGraph(rootNavOptions()) },
        )
        // The VRP return leg. Registered here rather than in the navbar because the redirect arrives
        // from outside the Compose tree; both outcomes route back into the authenticated graph,
        // which then resolves the VRP list.
        vrpCallbackScreen(
            onCompleted = { navController.navigateToAuthenticatedGraph(rootNavOptions()) },
            onAbandoned = { navController.navigateToAuthenticatedGraph(rootNavOptions()) },
        )
        authenticatedGraph(
            navController = navController,
            onLoggedOut = { navController.navigateToAuthGraph(rootNavOptions()) },
        )
//        userUnlockDestination()
    }

    /**
     * Routes HSBC's authorisation redirect, which arrives from outside the Compose tree entirely —
     * an Android intent, an iOS `onOpenURL`, or the desktop loopback listener. This is the only
     * caller of [navigateToConsentCallback]; without it the destination above is unreachable.
     *
     * The URL is forwarded raw. Parsing and authenticating it belong to the data layer.
     *
     * De-duplicated on the URL rather than by draining the bus: the replay cache is what makes an
     * Android cold start work at all (the intent is published before composition subscribes), so
     * clearing it here would defeat its purpose. Guarding on the last-routed URL instead keeps a
     * re-publish of the same redirect — which Android does on every Activity recreate, since it
     * hands back the same launch intent — from navigating twice and burning the single-use
     * PendingAuth, which would fail a legitimate consent with a SecurityError.
     */
    var lastRoutedRedirect by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        ConsentRedirectBus.redirects.collect { redirectUrl ->
            if (redirectUrl == lastRoutedRedirect) return@collect
            lastRoutedRedirect = redirectUrl

            // All three legs return through the same bus on the same registered redirect URI, so
            // they must be told apart before any of them is processed: the leg decides which session
            // the code is exchanged into, and exchanging into the wrong one corrupts it. The
            // ordering that resolves a redirect two legs would accept lives in authorisationLegOf.
            when (authorisationLegOf(redirectUrl, vrpAuthRepository, paymentAuthRepository)) {
                AuthorisationLeg.Vrp -> navController.navigate(
                    VrpCallbackRoute(redirectUrl = redirectUrl),
                    rootNavOptions(),
                )

                AuthorisationLeg.Payment -> navController.navigate(
                    PaymentConsentRoute(redirectUrl = redirectUrl),
                    rootNavOptions(),
                )

                AuthorisationLeg.SignIn -> navController.navigateToConsentCallback(
                    route = ConsentCallbackRoute(redirectUrl = redirectUrl),
                    navOptions = rootNavOptions(),
                )
            }
        }
    }

    val targetRoute = when (state) {
        RootNavState.Auth -> AuthGraphRoute
        RootNavState.Splash -> SplashRoute
        is RootNavState.UserUnlocked -> AuthenticatedGraphRoute
    }
    val currentRoute = navController.currentDestination?.rootLevelRoute()

    // Don't navigate if we are already at the correct root. This notably happens during process
    // death. In this case, the NavHost already restores state, so we don't have to navigate.
    // However, if the route is correct but the underlying state is different, we should still
    // proceed in order to get a fresh version of that route.
    if (currentRoute == targetRoute.toObjectNavigationRoute() &&
        previousStateReference.load() == state
    ) {
        previousStateReference.store(state)
        return
    }
    previousStateReference.store(state)

    // In some scenarios on an emulator the Activity can leak when recreated
    // if we don't first clear focus anytime we change the root destination.
    ClearFocus()

    // Use a LaunchedEffect to ensure we don't navigate too soon when the app first opens. This
    // avoids a bug that first appeared in Compose Material3 1.2.0-rc01 that causes the initial
    // transition to appear corrupted.
    LaunchedEffect(state) {
        when (state) {
            RootNavState.Splash -> navController.navigateToSplash(rootNavOptions())
            RootNavState.Auth -> navController.navigateToAuthGraph(rootNavOptions())
            is RootNavState.UserUnlocked -> navController.navigateToAuthenticatedGraph(
                navOptions = rootNavOptions(),
            )
        }
    }
}

private fun NavDestination?.rootLevelRoute(): String? = when {
    this == null -> null
    parent?.route == null -> route
    else -> parent.rootLevelRoute()
}

@Suppress("MaxLineLength")
private fun AnimatedContentTransitionScope<NavBackStackEntry>.toEnterTransition(): NonNullEnterTransitionProvider =
    when (targetState.destination.rootLevelRoute()) {
        SplashRoute.toObjectNavigationRoute() -> RootTransitionProviders.Enter.none
        else -> RootTransitionProviders.Enter.fadeIn
    }

@Suppress("MaxLineLength")
private fun AnimatedContentTransitionScope<NavBackStackEntry>.toExitTransition(): NonNullExitTransitionProvider {
    return when (initialState.destination.rootLevelRoute()) {
        // Disable transitions when coming from the splash screen
        SplashRoute.toObjectNavigationRoute() -> RootTransitionProviders.Exit.none
        else -> RootTransitionProviders.Exit.fadeOut
    }
}

@Composable
expect fun ClearFocus()
