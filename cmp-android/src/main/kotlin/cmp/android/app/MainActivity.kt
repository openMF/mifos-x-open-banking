/*
 * Copyright 2024 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package cmp.android.app

import android.content.Intent
import android.content.res.Resources
import android.net.Uri
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
import cmp.shared.SharedApp
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init
import org.koin.android.ext.android.inject
import org.mifosx.openbanking.core.data.callback.ConsentRedirectBus
import org.mifosx.openbanking.core.data.infra.NetworkMonitor
import org.mifosx.openbanking.core.data.user.UserDataRepository
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

    private val networkMonitor: NetworkMonitor by inject()

    private val analyticsHelper: AnalyticsHelper by inject()
    private val lifecycleTracker by lazy { analyticsHelper.lifecycleTracker() }

    override fun onCreate(savedInstanceState: Bundle?) {
        var shouldShowSplashScreen = true
        installSplashScreen().setKeepOnScreenCondition { shouldShowSplashScreen }

        super.onCreate(savedInstanceState)
        appUpdateManager = AppUpdateManagerImpl(this)

        publishConsentRedirect(intent)

        val darkThemeConfigFlow = userPreferencesRepository.observeDarkThemeConfig

        setupEdgeToEdge(darkThemeConfigFlow)

        ShareUtils.setActivityProvider { return@setActivityProvider this }
        FileKit.init(this)

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

    /**
     * The redirect's other entry point, and the usual one.
     *
     * With `launchMode="singleTask"` the OS delivers the callback here whenever this Activity is
     * still alive — which is the normal case, since the PSU only backgrounded us to authorise in the
     * browser. [onCreate] covers only the opposite case, where Android killed the app while it was
     * backgrounded and the redirect cold-starts it. Both are required; either alone drops half the
     * journeys.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        publishConsentRedirect(intent)
    }

    /**
     * Forwards HSBC's consent redirect, and only that. Runs on every launch, so
     * [consumeConsentRedirectUrl] filters out everything that is not our callback.
     */
    private fun publishConsentRedirect(intent: Intent?) {
        val url = intent?.consumeConsentRedirectUrl(packageName) ?: return
        val sibling = siblingRedirectTarget(url, packageName)
        if (sibling == null) {
            ConsentRedirectBus.publish(url)
            return
        }
        // Another build on this phone started the flow (the App Link landed here): hand it over, and
        // close this task if it only exists to carry the redirect.
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sibling))) }
        if (isTaskRoot) finish()
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
