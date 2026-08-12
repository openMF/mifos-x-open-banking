/*
 * Copyright 2024 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package cmp.navigation.di

import cmp.navigation.AppViewModel
import cmp.navigation.authenticatednavbar.AuthenticatedNavbarNavigationViewModel
import cmp.navigation.rootnav.RootNavViewModel
import cmp.navigation.statements.platformStatementFileHandler
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.mifosx.openbanking.core.data.di.DataModule
import org.mifosx.openbanking.core.database.di.DatabaseModule
import org.mifosx.openbanking.core.datastore.di.DatastoreModule
import org.mifosx.openbanking.core.store.di.appStoreModule
import org.mifosx.openbanking.feature.accountdetail.di.AccountDetailModule
import org.mifosx.openbanking.feature.accountholder.di.AccountHolderModule
import org.mifosx.openbanking.feature.accounts.di.AccountsModule
import org.mifosx.openbanking.feature.beneficiaries.di.BeneficiariesModule
import org.mifosx.openbanking.feature.consentcallback.di.ConsentCallbackModule
import org.mifosx.openbanking.feature.consentdetail.di.ConsentDetailModule
import org.mifosx.openbanking.feature.consentlist.di.ConsentListModule
import org.mifosx.openbanking.feature.directdebits.di.DirectDebitsModule
import org.mifosx.openbanking.feature.home.di.HomeModule
import org.mifosx.openbanking.feature.login.di.LoginModule
import org.mifosx.openbanking.feature.paymentconsent.di.PaymentConsentModule
import org.mifosx.openbanking.feature.paymentshub.di.PaymentsHubModule
import org.mifosx.openbanking.feature.paymentsschedulepayment.di.SchedulePaymentModule
import org.mifosx.openbanking.feature.paymentstatus.di.PaymentStatusModule
import org.mifosx.openbanking.feature.product.di.ProductModule
import org.mifosx.openbanking.feature.scheduledpayments.di.ScheduledPaymentsModule
import org.mifosx.openbanking.feature.sendmoney.di.SendMoneyModule
import org.mifosx.openbanking.feature.settings.di.SettingsModule
import org.mifosx.openbanking.feature.standingorders.di.StandingOrdersModule
import org.mifosx.openbanking.feature.statementdetail.di.StatementDetailModule
import org.mifosx.openbanking.feature.statements.StatementFileHandler
import org.mifosx.openbanking.feature.statements.di.StatementsModule
import org.mifosx.openbanking.feature.transactiondetail.di.TransactionDetailModule
import org.mifosx.openbanking.feature.transactions.di.TransactionsModule
import template.core.base.analytics.di.analyticsModule
import template.core.base.common.di.CommonModule
import template.core.base.platform.di.platformModule
import template.core.base.security.di.SecurityModule

object KoinModules {
    private val dataModule = module {
        includes(DataModule, appStoreModule)
    }

    private val dispatcherModule = module {
        includes(CommonModule)
    }

    private val AppModule = module {
        includes(platformModule)

        viewModelOf(::AppViewModel)
        viewModelOf(::AuthenticatedNavbarNavigationViewModel)
        viewModelOf(::RootNavViewModel)
    }

    private val featureModule = module {
        includes(
            HomeModule,
            AccountsModule,
            AccountDetailModule,
            TransactionsModule,
            TransactionDetailModule,
            BeneficiariesModule,
            ConsentListModule,
            ConsentDetailModule,
            DirectDebitsModule,
            ScheduledPaymentsModule,
            StandingOrdersModule,
            ProductModule,
            StatementDetailModule,
            StatementsModule,
            SettingsModule,
            AccountHolderModule,
            LoginModule,
            ConsentCallbackModule,
            SchedulePaymentModule,
            SendMoneyModule,
            PaymentStatusModule,
            PaymentConsentModule,
            PaymentsHubModule,
        )

        // App-layer binding for the statements feature's platform delivery seam; the impl is
        // supplied per-platform (real FileKit on nonJs, no-op on web) via the expect/actual factory.
        single<StatementFileHandler> { platformStatementFileHandler() }
    }

    val allModules = listOf(
        SecurityModule,
        dataModule,
        DatabaseModule,
        dispatcherModule,
        analyticsModule,
        DatastoreModule,
        featureModule,
        AppModule,
    )
}
