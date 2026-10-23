/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
plugins {
    alias(libs.plugins.kmp.library.convention)
}

android {
    namespace = "org.mifosx.openbanking.core.data"
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.database)
            implementation(projects.core.datastore)
            implementation(projects.core.model)
            implementation(projects.core.network)
            implementation(projects.core.analytics)

            implementation(projects.coreBase.common)
            implementation(projects.coreBase.network)
            api(projects.core.store)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            api(libs.cmp.network.monitor)

            // `api`, not `implementation`: SettingsPendingAuthStore takes a Settings in its
            // constructor, so the type is part of this module's public surface.
            api(libs.multiplatform.settings)
        }

        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.multiplatform.settings.test)
        }

        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.tracing.ktx)
            implementation(libs.koin.android)
        }
    }
}