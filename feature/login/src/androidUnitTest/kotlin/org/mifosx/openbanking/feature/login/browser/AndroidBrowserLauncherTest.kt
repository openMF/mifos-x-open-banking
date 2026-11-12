/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.login.browser

import android.app.Activity
import android.app.Application
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val ROBOLECTRIC_SDK = 35
private const val AUTHORIZE_URL = "https://sandbox.ob.hsbc.co.uk/obie/open-banking/v1.1/oauth2/authorize?request=jwt"

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [ROBOLECTRIC_SDK])
class AndroidBrowserLauncherTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `the custom tab opens inside the app's task, from the resumed activity`() {
        val launcher = AndroidBrowserLauncher(app)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        launcher.launch(AUTHORIZE_URL)

        val started = assertNotNull(
            shadowOf(activity).nextStartedActivity,
            "launched from the activity, not the app context",
        )
        assertEquals(AUTHORIZE_URL, started.dataString)
        assertTrue(
            started.hasExtra(CustomTabsIntent.EXTRA_SESSION),
            "must be a Custom Tabs intent, not a plain browser intent",
        )
        assertEquals(
            0,
            started.flags and Intent.FLAG_ACTIVITY_NEW_TASK,
            "NEW_TASK would put the tab in Chrome's own task",
        )
    }

    @Test
    fun `with no visible activity it still opens, as a new task from the app context`() {
        val launcher = AndroidBrowserLauncher(app)

        launcher.launch(AUTHORIZE_URL)

        val started = assertNotNull(shadowOf(app).nextStartedActivity)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }
}
