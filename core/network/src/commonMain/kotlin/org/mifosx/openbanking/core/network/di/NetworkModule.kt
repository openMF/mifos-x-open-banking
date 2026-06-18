/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.mifosx.openbanking.core.network.di

import com.russhwolf.settings.Settings
import de.jensklingenberg.ktorfit.Ktorfit
import io.ktor.client.HttpClient
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.mifosx.openbanking.core.network.api.AccountApplicationsApi
import org.mifosx.openbanking.core.network.api.AccountsApi
import org.mifosx.openbanking.core.network.api.AgentsApi
import org.mifosx.openbanking.core.network.api.AtmApi
import org.mifosx.openbanking.core.network.api.AuthApi
import org.mifosx.openbanking.core.network.api.AuthRecoveryApi
import org.mifosx.openbanking.core.network.api.BanksApi
import org.mifosx.openbanking.core.network.api.CardsApi
import org.mifosx.openbanking.core.network.api.ConsentsApi
import org.mifosx.openbanking.core.network.api.CustomerMessagesApi
import org.mifosx.openbanking.core.network.api.CustomersApi
import org.mifosx.openbanking.core.network.api.FxApi
import org.mifosx.openbanking.core.network.api.KycApi
import org.mifosx.openbanking.core.network.api.MeetingsApi
import org.mifosx.openbanking.core.network.api.PaymentsApi
import org.mifosx.openbanking.core.network.api.PfmApi
import org.mifosx.openbanking.core.network.api.ProductsApi
import org.mifosx.openbanking.core.network.api.ProfileApi
import org.mifosx.openbanking.core.network.api.StandingOrdersApi
import org.mifosx.openbanking.core.network.api.TransactionMetadataApi
import org.mifosx.openbanking.core.network.api.TransactionsApi
import org.mifosx.openbanking.core.network.api.UserAttributesApi
import org.mifosx.openbanking.core.network.api.createAccountApplicationsApi
import org.mifosx.openbanking.core.network.api.createAccountsApi
import org.mifosx.openbanking.core.network.api.createAgentsApi
import org.mifosx.openbanking.core.network.api.createAtmApi
import org.mifosx.openbanking.core.network.api.createAuthApi
import org.mifosx.openbanking.core.network.api.createAuthRecoveryApi
import org.mifosx.openbanking.core.network.api.createBanksApi
import org.mifosx.openbanking.core.network.api.createCardsApi
import org.mifosx.openbanking.core.network.api.createConsentsApi
import org.mifosx.openbanking.core.network.api.createCustomerMessagesApi
import org.mifosx.openbanking.core.network.api.createCustomersApi
import org.mifosx.openbanking.core.network.api.createFxApi
import org.mifosx.openbanking.core.network.api.createKycApi
import org.mifosx.openbanking.core.network.api.createMeetingsApi
import org.mifosx.openbanking.core.network.api.createPaymentsApi
import org.mifosx.openbanking.core.network.api.createPfmApi
import org.mifosx.openbanking.core.network.api.createProductsApi
import org.mifosx.openbanking.core.network.api.createProfileApi
import org.mifosx.openbanking.core.network.api.createStandingOrdersApi
import org.mifosx.openbanking.core.network.api.createTransactionMetadataApi
import org.mifosx.openbanking.core.network.api.createTransactionsApi
import org.mifosx.openbanking.core.network.api.createUserAttributesApi
import org.mifosx.openbanking.core.network.obp.ObpConfig
import org.mifosx.openbanking.core.network.obp.ObpTokenProvider
import org.mifosx.openbanking.core.network.obp.OidcApi
import org.mifosx.openbanking.core.network.obp.PersistentObpTokenProvider
import org.mifosx.openbanking.core.network.obp.obpHttpClient
import org.mifosx.openbanking.core.network.obp.obpKtorfit
import org.mifosx.openbanking.core.network.obp.oidcHttpClient

/**
 * OBP network graph: connection config, session-token holder, Ktor client (with the
 * DirectLogin auth plugin), the shared Ktorfit instance, and one Ktorfit service per
 * OBP endpoint group. New OBP services are registered here as their APIs are added.
 */
val NetworkModule = module {
    single {
        val secureSettings: Settings = get(named("secure"))
        ObpConfig(consumerKey = secureSettings.getStringOrNull("obp_consumer_key").orEmpty())
    }
    single(named("isSandbox")) { get<ObpConfig>().isSandbox }
    single<ObpTokenProvider> { PersistentObpTokenProvider(preferences = get()) }
    single<HttpClient> { obpHttpClient(config = get(), tokenProvider = get()) }
    single<Ktorfit> { obpKtorfit(client = get()) }

    single<HttpClient>(named("oidc")) { oidcHttpClient(config = get()) }
    single { OidcApi(client = get(named("oidc")), obpBaseUrl = get<ObpConfig>().baseUrl) }

    single<AuthApi> { get<Ktorfit>().createAuthApi() }
    single<AccountsApi> { get<Ktorfit>().createAccountsApi() }
    single<BanksApi> { get<Ktorfit>().createBanksApi() }
    single<TransactionsApi> { get<Ktorfit>().createTransactionsApi() }
    single<TransactionMetadataApi> { get<Ktorfit>().createTransactionMetadataApi() }
    single<UserAttributesApi> { get<Ktorfit>().createUserAttributesApi() }
    single<CardsApi> { get<Ktorfit>().createCardsApi() }
    single<PaymentsApi> { get<Ktorfit>().createPaymentsApi() }
    single<StandingOrdersApi> { get<Ktorfit>().createStandingOrdersApi() }
    single<FxApi> { get<Ktorfit>().createFxApi() }
    single<AtmApi> { get<Ktorfit>().createAtmApi() }
    single<ProductsApi> { get<Ktorfit>().createProductsApi() }
    single<CustomersApi> { get<Ktorfit>().createCustomersApi() }
    single<KycApi> { get<Ktorfit>().createKycApi() }
    single<ConsentsApi> { get<Ktorfit>().createConsentsApi() }
    single<AccountApplicationsApi> { get<Ktorfit>().createAccountApplicationsApi() }
    single<CustomerMessagesApi> { get<Ktorfit>().createCustomerMessagesApi() }
    single<MeetingsApi> { get<Ktorfit>().createMeetingsApi() }
    single<ProfileApi> { get<Ktorfit>().createProfileApi() }
    single<AgentsApi> { get<Ktorfit>().createAgentsApi() }
    single<PfmApi> { get<Ktorfit>().createPfmApi() }
    single<AuthRecoveryApi> { get<Ktorfit>().createAuthRecoveryApi() }
}
