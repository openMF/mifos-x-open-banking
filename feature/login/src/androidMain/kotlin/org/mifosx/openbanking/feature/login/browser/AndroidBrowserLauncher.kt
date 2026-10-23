/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.login.browser

import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsIntent
import java.lang.ref.WeakReference

/**
 * Opens the bank's authorisation page in a Custom Tab — the in-app browser tab that RFC 8252 and
 * FAPI allow for OAuth (a separate browser process the app cannot read), unlike an embedded WebView.
 *
 * The tab is launched from the app's currently resumed Activity, so it sits inside the app's own
 * task on top of the screen that opened it — not as a separate Chrome window. When the verified
 * App Link (or the relay's custom-scheme fallback) brings the singleTask MainActivity back to the
 * front, Android clears the tab above it. Only with no visible activity does it fall back to the
 * application context, which needs NEW_TASK; the external browser is used only when no Custom Tabs
 * provider is installed.
 */
class AndroidBrowserLauncher(private val context: Context) : BrowserLauncher {

    private var resumed: WeakReference<Activity>? = null

    init {
        (context.applicationContext as? Application)?.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    resumed = WeakReference(activity)
                }

                override fun onActivityPaused(activity: Activity) {
                    if (resumed?.get() === activity) resumed = null
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityStarted(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }

    override fun launch(url: String) {
        val uri = Uri.parse(url)
        val tab = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
            .build()
        val activity = resumed?.get()
        val host: Context = activity ?: context.applicationContext.also {
            tab.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            tab.launchUrl(host, uri)
        } catch (_: ActivityNotFoundException) {
            val view = Intent(Intent.ACTION_VIEW, uri)
            if (activity == null) view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            host.startActivity(view)
        }
    }
}
