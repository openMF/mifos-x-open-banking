/*
 * Copyright 2024 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.mifosx.openbanking.di

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.mifosx.openbanking.AppViewModel
import org.mifosx.openbanking.authenticatednavbar.AuthenticatedNavbarNavigationViewModel
import org.mifosx.openbanking.core.data.di.DataModule
import org.mifosx.openbanking.core.database.di.DatabaseModule
import org.mifosx.openbanking.core.datastore.di.DatastoreModule
import org.mifosx.openbanking.core.store.di.appStoreModule
import org.mifosx.openbanking.feature.accounts.di.AccountsModule
import org.mifosx.openbanking.feature.atmlocator.di.AtmLocatorModule
import org.mifosx.openbanking.feature.beneficiaries.di.BeneficiariesModule
import org.mifosx.openbanking.feature.businessinsights.di.BusinessInsightsModule
import org.mifosx.openbanking.feature.cards.di.CardsModule
import org.mifosx.openbanking.feature.directdebits.di.DirectDebitsModule
import org.mifosx.openbanking.feature.fxrates.di.FxRatesModule
import org.mifosx.openbanking.feature.home.di.HomeModule
import org.mifosx.openbanking.feature.login.di.LoginModule
import org.mifosx.openbanking.feature.pfm.di.PfmModule
import org.mifosx.openbanking.feature.products.di.ProductsModule
import org.mifosx.openbanking.feature.profile.di.ProfileModule
import org.mifosx.openbanking.feature.sendmoney.di.SendMoneyModule
import org.mifosx.openbanking.feature.settings.SettingsModule
import org.mifosx.openbanking.feature.standingorders.di.StandingOrdersModule
import org.mifosx.openbanking.feature.transactions.di.TransactionsModule
import org.mifosx.openbanking.rootnav.RootNavViewModel
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
            LoginModule,
            ProfileModule,
            SettingsModule,
            AccountsModule,
            CardsModule,
            BeneficiariesModule,
            SendMoneyModule,
            StandingOrdersModule,
            TransactionsModule,
            PfmModule,
            BusinessInsightsModule,
            FxRatesModule,
            DirectDebitsModule,
            ProductsModule,
            AtmLocatorModule,
        )
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

    /**
     * Koin properties injected at startup. The OBP consumer key is now read from platform
     * encrypted storage at runtime (via core:network's ObpConfig), so it is never baked
     * into the APK — not even for dev builds.
     */
    val koinProperties: Map<String, Any> = emptyMap()
}
