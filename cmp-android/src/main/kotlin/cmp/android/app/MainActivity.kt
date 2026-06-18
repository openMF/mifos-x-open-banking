/*
 * Copyright 2024 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package cmp.android.app

import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init
import org.koin.android.ext.android.inject
import androidx.lifecycle.lifecycleScope
import org.mifosx.openbanking.SharedApp
import org.mifosx.openbanking.core.data.auth.OidcCallbackBus
import org.mifosx.openbanking.core.data.infra.NetworkMonitor
import org.mifosx.openbanking.core.data.user.UserDataRepository
import org.mifosx.openbanking.core.network.obp.ObpConfig
import template.core.base.analytics.AnalyticsHelper
import template.core.base.analytics.lifecycleTracker
import template.core.base.platform.update.AppUpdateManager
import template.core.base.platform.update.AppUpdateManagerImpl
import template.core.base.ui.util.ShareUtils
import java.util.Locale

/**
 * Main activity class. This class is used to set the content view of the
 * activity.
 *
 * @constructor Create empty Main activity
 * @see ComponentActivity
 */
@Suppress("UnusedPrivateProperty")
class MainActivity : AppCompatActivity() {

    private lateinit var appUpdateManager: AppUpdateManager

    private val userPreferencesRepository: UserDataRepository by inject()

    private val obpConfig: ObpConfig by inject()

    private val networkMonitor: NetworkMonitor by inject()

    private val oidcCallbackBus: OidcCallbackBus by inject()

    private val analyticsHelper: AnalyticsHelper by inject()
    private val lifecycleTracker by lazy { analyticsHelper.lifecycleTracker() }

    override fun onCreate(savedInstanceState: Bundle?) {
        var shouldShowSplashScreen = true
        installSplashScreen().setKeepOnScreenCondition { shouldShowSplashScreen }

        super.onCreate(savedInstanceState)
        appUpdateManager = AppUpdateManagerImpl(this)

        handleOidcRedirect(intent)

        val darkThemeConfigFlow = userPreferencesRepository.observeDarkThemeConfig

        setupEdgeToEdge(darkThemeConfigFlow)

        ShareUtils.setActivityProvider { return@setActivityProvider this }
        FileKit.init(this)

        FirebaseConsumerKeyProvider.fetch(lifecycleScope, obpConfig, userPreferencesRepository)

        analyticsHelper.setUserId(deviceData)

        setContent {
            val status by networkMonitor.isOnline.collectAsStateWithLifecycle(false)

            if (status) {
                appUpdateManager.checkForAppUpdate()
            }

            lifecycleTracker.markAppLaunchComplete()

            SharedApp(
                updateScreenCapture = ::updateScreenCapture,
                handleRecreate = ::handleRecreate,
                handleThemeMode = {
                    AppCompatDelegate.setDefaultNightMode(it)
                },
                handleAppLocale = { localeTag ->
                    val currentLocales = AppCompatDelegate.getApplicationLocales()
                    val newLocales = if (localeTag != null) {
                        LocaleListCompat.forLanguageTags(localeTag)
                    } else {
                        // System Default: clear app-specific locale
                        LocaleListCompat.getEmptyLocaleList()
                    }

                    // Only update if the locale has actually changed
                    if (currentLocales != newLocales) {
                        AppCompatDelegate.setApplicationLocales(newLocales)
                        // Update Locale.setDefault for non-UI formatting
                        if (localeTag != null) {
                            // Use forLanguageTag to properly parse locales like "en-GB", "pt-BR"
                            Locale.setDefault(Locale.forLanguageTag(localeTag))
                        } else {
                            // Reset to true system default locale from device configuration
                            // Use Resources.getSystem() to get device locale unaffected by app overrides
                            val systemLocale = Resources.getSystem().configuration.locales[0]
                            Locale.setDefault(systemLocale)
                        }
                    }
                },
                onSplashScreenRemoved = {
                    shouldShowSplashScreen = false
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        appUpdateManager.checkForResumeUpdateState()
        lifecycleTracker.markAppBackground()
    }

    override fun onStart() {
        super.onStart()
        lifecycleTracker.markAppLaunchStart()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOidcRedirect(intent)
    }

    /**
     * Forwards an OIDC OAuth redirect (`org.mifosx.openbanking://oauth/callback?code=…&state=…`) into
     * [oidcCallbackBus] so the login flow can validate state and exchange the code for tokens.
     */
    private fun handleOidcRedirect(intent: Intent) {
        val data = intent.data
        if (data == null) return
        if (data.scheme == "org.mifosx.openbanking" && data.host == "oauth") {
            val code = data.getQueryParameter("code")
            val state = data.getQueryParameter("state")
            if (code != null && state != null) {
                oidcCallbackBus.emit(code, state)
            }
        }
    }

    private fun handleRecreate() {
        recreate()
    }

    private fun updateScreenCapture(isScreenCaptureAllowed: Boolean) {
        if (isScreenCaptureAllowed) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
