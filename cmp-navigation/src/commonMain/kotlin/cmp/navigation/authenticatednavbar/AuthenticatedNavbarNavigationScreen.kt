/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package cmp.navigation.authenticatednavbar

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.SnackbarDuration.Indefinite
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import cmp.navigation.generated.resources.Res
import cmp.navigation.generated.resources.not_connected
import cmp.navigation.placeholder.AtmLocatorRoute
import cmp.navigation.placeholder.bankingPlaceholderDestinations
import cmp.navigation.ui.KptRootScaffold
import cmp.navigation.ui.ScaffoldNavigationData
import cmp.navigation.ui.rememberKptNavController
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.mifosx.openbanking.core.ui.NavigationItem
import org.mifosx.openbanking.feature.accountdetail.AccountDetailChip
import org.mifosx.openbanking.feature.accountdetail.AccountDetailRoute
import org.mifosx.openbanking.feature.accountdetail.accountDetailScreen
import org.mifosx.openbanking.feature.accountholder.AccountHolderRoute
import org.mifosx.openbanking.feature.accountholder.accountHolderScreen
import org.mifosx.openbanking.feature.accounts.accountsGraph
import org.mifosx.openbanking.feature.beneficiaries.BeneficiariesRoute
import org.mifosx.openbanking.feature.beneficiaries.beneficiariesScreen
import org.mifosx.openbanking.feature.consentdetail.ConsentDetailRoute
import org.mifosx.openbanking.feature.consentdetail.consentDetailScreen
import org.mifosx.openbanking.feature.consentlist.ConsentListRoute
import org.mifosx.openbanking.feature.consentlist.consentListScreen
import org.mifosx.openbanking.feature.directdebits.DirectDebitsRoute
import org.mifosx.openbanking.feature.directdebits.directDebitsScreen
import org.mifosx.openbanking.feature.home.HomeDestination
import org.mifosx.openbanking.feature.home.homeGraph
import org.mifosx.openbanking.feature.login.LoginRenewRoute
import org.mifosx.openbanking.feature.login.browser.BrowserLauncher
import org.mifosx.openbanking.feature.login.loginRenewScreen
import org.mifosx.openbanking.feature.paymentshub.paymentsHubGraph
import org.mifosx.openbanking.feature.paymentsschedulepayment.SchedulePaymentRoute
import org.mifosx.openbanking.feature.paymentsschedulepayment.schedulePaymentGraph
import org.mifosx.openbanking.feature.paymentstatus.PaymentStatusRoute
import org.mifosx.openbanking.feature.paymentstatus.paymentStatusScreen
import org.mifosx.openbanking.feature.product.ProductRoute
import org.mifosx.openbanking.feature.product.productScreen
import org.mifosx.openbanking.feature.scheduledpayments.ScheduledPaymentsRoute
import org.mifosx.openbanking.feature.scheduledpayments.scheduledPaymentsScreen
import org.mifosx.openbanking.feature.sendmoney.SendMoneyRoute
import org.mifosx.openbanking.feature.sendmoney.sendMoneyGraph
import org.mifosx.openbanking.feature.settings.LicencesRoute
import org.mifosx.openbanking.feature.settings.licencesScreen
import org.mifosx.openbanking.feature.settings.settingsScreen
import org.mifosx.openbanking.feature.standingorders.StandingOrdersRoute
import org.mifosx.openbanking.feature.standingorders.standingOrdersScreen
import org.mifosx.openbanking.feature.statementdetail.StatementDetailRoute
import org.mifosx.openbanking.feature.statementdetail.statementDetailScreen
import org.mifosx.openbanking.feature.statements.StatementsRoute
import org.mifosx.openbanking.feature.statements.statementsScreen
import org.mifosx.openbanking.feature.transactiondetail.TransactionDetailRoute
import org.mifosx.openbanking.feature.transactiondetail.transactionDetailScreen
import org.mifosx.openbanking.feature.transactions.TransactionsRoute
import org.mifosx.openbanking.feature.transactions.transactionsScreen
import template.core.base.ui.util.RootTransitionProviders

@Composable
internal fun AuthenticatedNavbarNavigationScreen(
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberKptNavController(
        name = "AuthenticatedNavbarScreen",
    ),
    viewModel: AuthenticatedNavbarNavigationViewModel = koinViewModel(),
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()

    val message = stringResource(Res.string.not_connected)
    LaunchedEffect(isOffline) {
        if (isOffline) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = message,
                    duration = Indefinite,
                )
            }
        }
    }

    AuthenticatedNavbarNavigationScreenContent(
        navController = navController,
        snackbarHostState = snackbarHostState,
        onLoggedOut = onLoggedOut,
        modifier = modifier,
    )
}

@Composable
internal fun AuthenticatedNavbarNavigationScreenContent(
    navController: NavHostController,
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val navigationItems = consumerNavBarTabs
    val startDestination: Any = HomeDestination
    val uriHandler = LocalUriHandler.current
    val browserLauncher: BrowserLauncher = koinInject()

    val navBackStackEntry by navController.currentBackStackEntryAsState()

    KptRootScaffold(
        contentWindowInsets = WindowInsets(0.dp),
        navigationData = ScaffoldNavigationData(
            navigationItems = navigationItems,
            selectedNavigationItem = navigationItems.find {
                navBackStackEntry.isCurrentRoute(route = it.graphRoute)
            },
            onNavigationClick = { navigationItem ->
                navController.navigateToTab(navigationItem)
            },
            shouldShowNavigation = navigationItems.any {
                navBackStackEntry.isCurrentRoute(route = it.startDestinationRoute)
            },
        ),
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        modifier = modifier,
    ) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            enterTransition = RootTransitionProviders.Enter.fadeIn,
            exitTransition = RootTransitionProviders.Exit.fadeOut,
            popEnterTransition = RootTransitionProviders.Enter.fadeIn,
            popExitTransition = RootTransitionProviders.Exit.fadeOut,
        ) {
            homeGraph(
                onNavigateToTransactions = { accountId -> navController.navigate(TransactionsRoute(accountId)) },
                onNavigateToTransactionDetail = { transactionId, accountId ->
                    navController.navigate(TransactionDetailRoute(transactionId, accountId))
                },
            )
            accountsGraph(
                onNavigateToAccountDetail = { accountId -> navController.navigate(AccountDetailRoute(accountId)) },
            )
            paymentsHubGraph(
                onNavigateToSendMoney = { navController.navigate(SendMoneyRoute) },
                onNavigateToSchedulePayment = { navController.navigate(SchedulePaymentRoute) },
                onNavigateToPaymentStatus = { paymentId ->
                    navController.navigate(PaymentStatusRoute(paymentId))
                },
            )
            sendMoneyGraph(
                onLaunchAuthorisation = { url -> runCatching { browserLauncher.launch(url) } },
                onNavigateToConsents = {},
            )
            // A sibling of the hub graph, like send-money: the scheduled rails own their own browser
            // hand-off, and the return leg lands at the root navigator, not here.
            schedulePaymentGraph(
                onLaunchAuthorisation = { url -> runCatching { browserLauncher.launch(url) } },
                onNavigateToConsents = {},
            )
            // Registered here as well as at the root, because a route has to exist in the host that
            // navigates to it. The root copy serves the authorisation return leg, which lands outside
            // this NavHost entirely; this copy serves the hub, which is inside it. Without this,
            // tapping a payment in the hub threw "Destination with route PaymentStatusRoute cannot be
            // found in navigation graph" and killed the app — the two hosts cannot see each other's
            // destinations in either direction.
            paymentStatusScreen(
                onBack = { navController.popBackStack() },
                onStartNewPayment = { navController.navigate(SendMoneyRoute) },
            )
            accountDetailScreen(
                onNavigateToChip = { chip, accountId -> navController.navigateFromChip(chip, accountId) },
                onBack = { navController.popBackStack() },
            )
            transactionsScreen(
                onNavigateToTransactionDetail = { transactionId, accountId ->
                    navController.navigate(TransactionDetailRoute(transactionId, accountId))
                },
                onBack = { navController.popBackStack() },
            )
            transactionDetailScreen(onBack = { navController.popBackStack() })
            scheduledPaymentsScreen(onBack = { navController.popBackStack() })
            beneficiariesScreen(
                onBack = { navController.popBackStack() },
                onNavigateToConsents = { navController.navigate(ConsentListRoute) },
            )
            consentListScreen(
                onBack = { navController.popBackStack() },
                onNavigateToDetail = { consentId -> navController.navigate(ConsentDetailRoute(consentId)) },
                // Connecting and re-authenticating both stage a fresh consent, which is the login
                // screen's job — reached here without the onboarding intro via LoginRenewRoute.
                onConnectBank = { navController.navigate(LoginRenewRoute) },
                onReauthenticate = { navController.navigate(LoginRenewRoute) },
            )
            consentDetailScreen(
                onBack = { navController.popBackStack() },
                // Reconfirm re-runs the consent authorisation on the same login screen.
                onReconfirm = { navController.navigate(LoginRenewRoute) },
                // Revoke is a full logout; the root navigator takes the user to onboarding.
                onLoggedOut = onLoggedOut,
            )
            loginRenewScreen(onBack = { navController.popBackStack() })
            statementDetailScreen(
                onBack = { navController.popBackStack() },
                onNavigateToTransactionDetail = { transactionId, accountId ->
                    navController.navigate(TransactionDetailRoute(transactionId, accountId))
                },
            )
            directDebitsScreen(onBack = { navController.popBackStack() })
            productScreen(onBack = { navController.popBackStack() })
            standingOrdersScreen(onBack = { navController.popBackStack() })
            statementsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToStatementDetail = { statementId, accountId ->
                    navController.navigate(StatementDetailRoute(accountId = accountId, statementId = statementId))
                },
            )
            settingsScreen(
                onNavigateToConsents = { navController.navigate(ConsentListRoute) },
                onNavigateToLicences = { navController.navigate(LicencesRoute) },
                onOpenUrl = { url -> uriHandler.openUri(url) },
            )
            accountHolderScreen(onBack = { navController.popBackStack() })
            licencesScreen(onBack = { navController.popBackStack() })
            bankingPlaceholderDestinations()
        }
    }
}

/**
 * Resolves an Explore chip to its destination, carrying the account id.
 *
 * Transactions, Direct Debits and Standing Orders have real feature modules today; the rest
 * resolve to their placeholder routes and are repointed as each feature ships.
 */
private fun NavHostController.navigateFromChip(chip: AccountDetailChip, accountId: String) {
    when (chip) {
        AccountDetailChip.Transactions -> navigate(TransactionsRoute(accountId))
        AccountDetailChip.Statements -> navigate(StatementsRoute(accountId))
        AccountDetailChip.StandingOrders -> navigate(StandingOrdersRoute(accountId))
        AccountDetailChip.DirectDebits -> navigate(DirectDebitsRoute(accountId))
        AccountDetailChip.ScheduledPayments -> navigate(ScheduledPaymentsRoute(accountId))
        AccountDetailChip.Beneficiaries -> navigate(BeneficiariesRoute(accountId))
        AccountDetailChip.AtmLocator -> navigate(AtmLocatorRoute)
        AccountDetailChip.Product -> navigate(ProductRoute(accountId))
        AccountDetailChip.Party -> navigate(AccountHolderRoute(accountId))
    }
}

private fun NavHostController.navigateToTab(tab: NavigationItem) {
    // Navigate to the tab's graph route, not its inner start-destination route. For the Home tab
    // those differ (`HomeDestination` graph vs `HomeRoute`), and `HomeRoute` is also the NavHost's
    // start destination — the same id this `popUpTo` targets. Navigating to that id with
    // `restoreState = true` restored the sibling tab's just-saved back stack instead of Home, so the
    // Home tab was unreachable. Targeting the graph route decouples the two. (Flat tabs — Transactions
    // placeholder, More — have graphRoute == startDestinationRoute, so they are unaffected.)
    navigate(route = tab.graphRoute) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavBackStackEntry?.isCurrentRoute(route: String): Boolean =
    this
        ?.destination
        ?.hierarchy
        ?.any { it.route == route } == true
