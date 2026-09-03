/*
 * Copyright 2024 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.data.di

import com.russhwolf.settings.Settings
import io.github.mobilebytelabs.kmptoolkit.networkmonitor.NetworkMonitorProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import org.mifosx.openbanking.core.data.banking.PaymentHistoryRepository
import org.mifosx.openbanking.core.data.banking.PaymentStatusRepository
import org.mifosx.openbanking.core.data.banking.ScheduledPaymentInitiationRepository
import org.mifosx.openbanking.core.data.banking.SinglePaymentInitiationRepository
import org.mifosx.openbanking.core.data.banking.StandingOrderInitiationRepository
import org.mifosx.openbanking.core.data.banking.di.BankingModule
import org.mifosx.openbanking.core.data.banking.impl.PaymentHistoryRepositoryImpl
import org.mifosx.openbanking.core.data.banking.impl.PaymentStatusRepositoryImpl
import org.mifosx.openbanking.core.data.banking.impl.ScheduledPaymentInitiationRepositoryImpl
import org.mifosx.openbanking.core.data.banking.impl.SinglePaymentInitiationRepositoryImpl
import org.mifosx.openbanking.core.data.banking.impl.StandingOrderInitiationRepositoryImpl
import org.mifosx.openbanking.core.data.callback.ConsentCallbackRepository
import org.mifosx.openbanking.core.data.callback.ConsentSession
import org.mifosx.openbanking.core.data.callback.PaymentAuthRepository
import org.mifosx.openbanking.core.data.callback.PaymentAuthSession
import org.mifosx.openbanking.core.data.callback.PendingAuthStore
import org.mifosx.openbanking.core.data.callback.SettingsConsentSession
import org.mifosx.openbanking.core.data.callback.SettingsPaymentAuthSession
import org.mifosx.openbanking.core.data.callback.SettingsPendingAuthStore
import org.mifosx.openbanking.core.data.callback.impl.ConsentCallbackRepositoryImpl
import org.mifosx.openbanking.core.data.callback.impl.PaymentAuthRepositoryImpl
import org.mifosx.openbanking.core.data.infra.DebouncedNetworkMonitor
import org.mifosx.openbanking.core.data.infra.NetworkMonitor
import org.mifosx.openbanking.core.data.infra.impl.RoomFetchedAtRepository
import org.mifosx.openbanking.core.data.login.LoginRepository
import org.mifosx.openbanking.core.data.login.impl.LoginRepositoryImpl
import org.mifosx.openbanking.core.data.openSourceLicence.OpenSourceLicenceRepo
import org.mifosx.openbanking.core.data.openSourceLicence.OpenSourceLicenceRepoImpl
import org.mifosx.openbanking.core.data.openSourceLicence.OpenSourceLicenceStore
import org.mifosx.openbanking.core.data.user.AppLogout
import org.mifosx.openbanking.core.data.user.UserDataRepository
import org.mifosx.openbanking.core.data.user.UserLogoutManager
import org.mifosx.openbanking.core.data.user.impl.AppLogoutImpl
import org.mifosx.openbanking.core.data.user.impl.UserDataRepositoryImpl
import org.mifosx.openbanking.core.data.user.impl.UserLogoutManagerImpl
import org.mifosx.openbanking.core.data.vrp.SettingsVrpAuthSession
import org.mifosx.openbanking.core.data.vrp.VrpAuthRepository
import org.mifosx.openbanking.core.data.vrp.VrpAuthRepositoryImpl
import org.mifosx.openbanking.core.data.vrp.VrpAuthSession
import org.mifosx.openbanking.core.data.vrp.VrpConsentRepository
import org.mifosx.openbanking.core.data.vrp.VrpConsentRepositoryImpl
import org.mifosx.openbanking.core.data.vrp.VrpPaymentRepository
import org.mifosx.openbanking.core.data.vrp.VrpPaymentRepositoryImpl
import org.mifosx.openbanking.core.data.vrp.VrpTokenProvider
import org.mifosx.openbanking.core.data.vrp.VrpTokenProviderImpl
import org.mifosx.openbanking.core.database.AppDatabase
import org.mifosx.openbanking.core.database.di.DatabaseModule
import org.mifosx.openbanking.core.datastore.di.DatastoreModule
import org.mifosx.openbanking.core.network.di.NetworkModule
import org.mifosx.openbanking.core.store.AppStoreRegistry
import org.mobilenativefoundation.store.store5.Store
import template.core.base.common.di.CommonModule
import template.core.base.store.infra.FetchedAtRepository
import kotlin.time.Clock

val DataModule = module {
    includes(platformModule, CommonModule, DatabaseModule, DatastoreModule, NetworkModule, BankingModule)

    single<NetworkMonitor> { DebouncedNetworkMonitor(NetworkMonitorProvider.install(), get()) }
    singleOf(::UserDataRepositoryImpl) bind UserDataRepository::class

    single<LoginRepository> {
        LoginRepositoryImpl(
            oauth = get(),
            aisp = get(),
            signingKeyPem = get(named("hsbcSigningKey")),
            clientId = get(named("hsbcClientId")),
            kid = get(named("hsbcKid")),
            bankHost = get(named("hsbcBankHost")),
            authorizeHost = get(named("hsbcAuthorizeHost")),
            redirectUri = get(named("hsbcRedirectUri")),
        )
    }

    single<ConsentSession> { SettingsConsentSession(secureSettings = get<Settings>(named("secure"))) }

    single<PendingAuthStore> {
        SettingsPendingAuthStore(
            secureSettings = get<Settings>(named("secure")),
            nowEpochSeconds = { Clock.System.now().epochSeconds },
        )
    }

    single<ConsentCallbackRepository> {
        ConsentCallbackRepositoryImpl(
            oauth = get(),
            aisp = get(),
            pendingAuthStore = get(),
        )
    }

    single<PaymentAuthSession> { SettingsPaymentAuthSession(secureSettings = get<Settings>(named("secure"))) }

    single<VrpAuthSession> { SettingsVrpAuthSession(secureSettings = get<Settings>(named("secure"))) }

    single<VrpTokenProvider> {
        VrpTokenProviderImpl(
            oauth = get(),
            session = get(),
        )
    }

    single<VrpConsentRepository> {
        VrpConsentRepositoryImpl(
            vrp = get(),
            oauth = get(),
            dao = get(),
            session = get(),
            tokens = get(),
        )
    }

    single<VrpAuthRepository> {
        VrpAuthRepositoryImpl(
            oauth = get(),
            session = get(),
            signingKeyPem = get(named("hsbcSigningKey")),
            clientId = get(named("hsbcClientId")),
            kid = get(named("hsbcKid")),
            bankHost = get(named("hsbcBankHost")),
            authorizeHost = get(named("hsbcAuthorizeHost")),
            redirectUri = get(named("hsbcRedirectUri")),
        )
    }

    single<VrpPaymentRepository> {
        VrpPaymentRepositoryImpl(
            vrp = get(),
            oauth = get(),
            consentDao = get(),
            paymentDao = get(),
            tokens = get(),
        )
    }

    single<PaymentAuthRepository> {
        PaymentAuthRepositoryImpl(
            oauth = get(),
            pisp = get(),
            paymentAuthSession = get(),
            redirectUri = get(named("hsbcRedirectUri")),
        )
    }

    single<SinglePaymentInitiationRepository> {
        SinglePaymentInitiationRepositoryImpl(
            pisp = get(),
            oauth = get(),
            paymentAuthSession = get(),
            signingKeyPem = get(named("hsbcSigningKey")),
            clientId = get(named("hsbcClientId")),
            kid = get(named("hsbcKid")),
            bankHost = get(named("hsbcBankHost")),
            authorizeHost = get(named("hsbcAuthorizeHost")),
            redirectUri = get(named("hsbcRedirectUri")),
            paymentHistoryRepository = get(),
        )
    }

    single<ScheduledPaymentInitiationRepository> {
        ScheduledPaymentInitiationRepositoryImpl(
            pisp = get(),
            oauth = get(),
            paymentAuthSession = get(),
            signingKeyPem = get(named("hsbcSigningKey")),
            clientId = get(named("hsbcClientId")),
            kid = get(named("hsbcKid")),
            bankHost = get(named("hsbcBankHost")),
            authorizeHost = get(named("hsbcAuthorizeHost")),
            redirectUri = get(named("hsbcRedirectUri")),
            paymentHistoryRepository = get(),
        )
    }

    single<StandingOrderInitiationRepository> {
        StandingOrderInitiationRepositoryImpl(
            pisp = get(),
            oauth = get(),
            paymentAuthSession = get(),
            signingKeyPem = get(named("hsbcSigningKey")),
            clientId = get(named("hsbcClientId")),
            kid = get(named("hsbcKid")),
            bankHost = get(named("hsbcBankHost")),
            authorizeHost = get(named("hsbcAuthorizeHost")),
            redirectUri = get(named("hsbcRedirectUri")),
            paymentHistoryRepository = get(),
        )
    }

    single<PaymentStatusRepository> {
        PaymentStatusRepositoryImpl(
            pisp = get(),
            oauth = get(),
            paymentHistoryRepository = get(),
        )
    }

    single<PaymentHistoryRepository> {
        PaymentHistoryRepositoryImpl(
            dao = get(),
            paymentAuthSession = get(),
        )
    }

    single<FetchedAtRepository> { RoomFetchedAtRepository(get<AppDatabase>().fetchedAtDao) }

    single { get<AppDatabase>().draftDao }

    single<UserLogoutManager> { UserLogoutManagerImpl(get(), get(), get()) }

    single<AppLogout> {
        AppLogoutImpl(
            consentRevokeRepository = get(),
            consentSession = get(),
            paymentAuthSession = get(),
            userDataRepository = get(),
            storeCacheManager = get(),
            paymentHistoryDao = get(),
            vrpConsentRepository = get(),
            vrpConsentDao = get(),
            vrpPaymentDao = get(),
            vrpAuthSession = get(),
        )
    }

    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    single<Store<String, String>>(AppStoreRegistry.OpenSourceLicence) {
        OpenSourceLicenceStore.licenceStore(
            openSourceLicenceAPI = get(),
            userPreferencesRepository = get(),
        )
    }

    single<OpenSourceLicenceRepo> {
        OpenSourceLicenceRepoImpl(
            store = get(AppStoreRegistry.OpenSourceLicence),
            networkMonitor = get(),
            fetchedAtRepository = get(),
        )
    }
}

expect val platformModule: Module
