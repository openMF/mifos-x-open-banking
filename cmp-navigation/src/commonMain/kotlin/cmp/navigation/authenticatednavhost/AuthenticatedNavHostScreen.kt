/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package cmp.navigation.authenticatednavhost

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.SnackbarDuration.Indefinite
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import cmp.navigation.generated.resources.Res
import cmp.navigation.generated.resources.not_connected
import cmp.navigation.placeholder.AtmLocatorRoute
import cmp.navigation.placeholder.bankingPlaceholderDestinations
import cmp.navigation.ui.KptRootScaffold
import cmp.navigation.ui.rememberKptNavController
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
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
import org.mifosx.openbanking.feature.paymentsschedulepayment.SchedulePaymentHistoryRoute
import org.mifosx.openbanking.feature.paymentsschedulepayment.SchedulePaymentRoute
import org.mifosx.openbanking.feature.paymentsschedulepayment.schedulePaymentGraph
import org.mifosx.openbanking.feature.paymentsstandingorder.StandingOrderHistoryRoute
import org.mifosx.openbanking.feature.paymentsstandingorder.StandingOrderRoute
import org.mifosx.openbanking.feature.paymentsstandingorder.standingOrderGraph
import org.mifosx.openbanking.feature.paymentstatus.PaymentStatusRoute
import org.mifosx.openbanking.feature.paymentstatus.paymentStatusScreen
import org.mifosx.openbanking.feature.product.ProductRoute
import org.mifosx.openbanking.feature.product.productScreen
import org.mifosx.openbanking.feature.scheduledpayments.ScheduledPaymentsRoute
import org.mifosx.openbanking.feature.scheduledpayments.scheduledPaymentsScreen
import org.mifosx.openbanking.feature.sendmoney.SendMoneyHistoryRoute
import org.mifosx.openbanking.feature.sendmoney.SendMoneyRoute
import org.mifosx.openbanking.feature.sendmoney.sendMoneyGraph
import org.mifosx.openbanking.feature.settings.LicencesRoute
import org.mifosx.openbanking.feature.settings.SettingsRoute
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
import org.mifosx.openbanking.feature.vrpconsents.consentList.navigateToVrpConsentList
import org.mifosx.openbanking.feature.vrpconsents.navigation.vrpConsentsDestination
import org.mifosx.openbanking.feature.vrppayment.payment.navigateToVrpPayment
import org.mifosx.openbanking.feature.vrppayment.payment.vrpPaymentScreen
import org.mifosx.openbanking.feature.vrpsetup.setup.navigateToVrpSetup
import org.mifosx.openbanking.feature.vrpsetup.setup.vrpSetupScreen
import template.core.base.ui.util.RootTransitionProviders

@Composable
internal fun AuthenticatedNavHostScreen(
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberKptNavController(
        name = "AuthenticatedNavHostScreen",
    ),
    viewModel: AuthenticatedNavHostViewModel = koinViewModel(),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()

    val message = stringResource(Res.string.not_connected)
    LaunchedEffect(isOffline) {
        if (isOffline) {
            snackbarHostState.showSnackbar(
                message = message,
                duration = Indefinite,
            )
        }
    }

    AuthenticatedNavHostScreenContent(
        navController = navController,
        snackbarHostState = snackbarHostState,
        onLoggedOut = onLoggedOut,
        modifier = modifier,
    )
}

@Composable
internal fun AuthenticatedNavHostScreenContent(
    navController: NavHostController,
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val startDestination: Any = HomeDestination
    val uriHandler = LocalUriHandler.current
    val browserLauncher: BrowserLauncher = koinInject()

    KptRootScaffold(
        contentWindowInsets = WindowInsets(0.dp),
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
                onNavigateToSendMoney = { navController.navigate(SendMoneyRoute) },
                onNavigateToSchedulePayment = { navController.navigate(SchedulePaymentRoute) },
                onNavigateToStandingOrder = { navController.navigate(StandingOrderRoute) },
                onNavigateToVrp = { navController.navigateToVrpConsentList() },
                onNavigateToAccountDetail = { accountId -> navController.navigate(AccountDetailRoute(accountId)) },
                onNavigateToSettings = { navController.navigate(SettingsRoute) },
            )
            accountsGraph(
                onNavigateToAccountDetail = { accountId -> navController.navigate(AccountDetailRoute(accountId)) },
            )
            paymentsHubGraph(
                onNavigateToSendMoney = { navController.navigate(SendMoneyRoute) },
                onNavigateToSchedulePayment = { navController.navigate(SchedulePaymentRoute) },
                onNavigateToStandingOrder = { navController.navigate(StandingOrderRoute) },
                onNavigateToVrp = { navController.navigateToVrpConsentList() },
            )
            vrpConsentsDestination(
                navController = navController,
                onBack = { navController.popBackStack() },
                onNavigateToSetup = { navController.navigateToVrpSetup() },
                onNavigateToPayment = { consentId -> navController.navigateToVrpPayment(consentId) },
            )
            vrpSetupScreen(
                onBack = { navController.popBackStack() },
                onLaunchAuthorisation = { url -> runCatching { browserLauncher.launch(url) } },
            )
            vrpPaymentScreen(onBack = { navController.popBackStack() })
            sendMoneyGraph(
                onLaunchAuthorisation = { url -> runCatching { browserLauncher.launch(url) } },
                onNavigateToConsents = {},
                onNavigateToPayment = { paymentId ->
                    navController.navigate(PaymentStatusRoute(paymentId))
                },
                onNavigateToHistory = { navController.navigate(SendMoneyHistoryRoute) },
                onBack = { navController.popBackStack() },
            )
            schedulePaymentGraph(
                onLaunchAuthorisation = { url -> runCatching { browserLauncher.launch(url) } },
                onNavigateToConsents = {},
                onNavigateToPayment = { paymentId ->
                    navController.navigate(PaymentStatusRoute(paymentId))
                },
                onNavigateToHistory = { navController.navigate(SchedulePaymentHistoryRoute) },
                onBack = { navController.popBackStack() },
            )

            // A sibling of the hub graph too, for the same reason: each payment product owns its own
            // browser hand-off, and the return leg lands at the root navigator rather than here.
            standingOrderGraph(
                onLaunchAuthorisation = { url -> runCatching { browserLauncher.launch(url) } },
                onNavigateToConsents = {},
                onNavigateToPayment = { paymentId ->
                    navController.navigate(PaymentStatusRoute(paymentId))
                },
                onNavigateToHistory = { navController.navigate(StandingOrderHistoryRoute) },
                onBack = { navController.popBackStack() },
            )

            paymentStatusScreen(onBack = { navController.popBackStack() })
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
            beneficiariesScreen(onBack = { navController.popBackStack() })
            consentListScreen(
                onBack = { navController.popBackStack() },
                onNavigateToDetail = { consentId -> navController.navigate(ConsentDetailRoute(consentId)) },
                onConnectBank = { navController.navigate(LoginRenewRoute) },
                onReauthenticate = { navController.navigate(LoginRenewRoute) },
            )
            consentDetailScreen(
                onBack = { navController.popBackStack() },
                onReconfirm = { navController.navigate(LoginRenewRoute) },
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
                onBack = { navController.popBackStack() },
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
