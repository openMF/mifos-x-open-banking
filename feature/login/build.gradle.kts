/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
plugins {
    alias(libs.plugins.cmp.feature.convention)
}

android {
    namespace = "org.mifosx.openbanking.feature.login"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.data)
            implementation(projects.core.model)
            implementation(projects.core.ui)
            implementation(projects.coreBase.ui)
            implementation(projects.coreBase.network)
            implementation(projects.coreBase.platform)
            implementation(libs.kotlinx.datetime)

            implementation(compose.ui)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
        }

        androidMain.dependencies {
            // Custom Tabs: the in-app browser tab for bank authorisation (FR-056).
            implementation(libs.androidx.browser)
        }

        androidUnitTest.dependencies {
            implementation(libs.robolectric)
            implementation(libs.bundles.androidx.compose.ui.test)
        }

        androidInstrumentedTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.bundles.androidx.compose.ui.test)
            implementation(libs.androidx.test.ext.junit)
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.espresso.core)
        }
    }
}

android {
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

compose {
    resources {
        packageOfResClass = "org.mifosx.openbanking.feature.login.generated.resources"
    }
}

