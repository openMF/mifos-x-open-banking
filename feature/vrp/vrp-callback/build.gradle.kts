/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
import org.jetbrains.compose.ExperimentalComposeLibrary

plugins {
    alias(libs.plugins.cmp.feature.convention)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "org.mifosx.openbanking.feature.vrpcallback"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.data)
            implementation(projects.core.model)
            implementation(projects.core.ui)
            implementation(projects.coreBase.network)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)

            implementation(compose.ui)
            implementation(compose.material3)
            implementation(compose.foundation)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
        }

        commonTest.dependencies {
            implementation(projects.core.network)
            @OptIn(ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }

        desktopTest.dependencies {
            implementation(compose.desktop.uiTestJUnit4)
            implementation(compose.desktop.currentOs)
        }

        androidUnitTest.dependencies {
            implementation(libs.robolectric)
            implementation(libs.bundles.androidx.compose.ui.test)
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.compose)
            implementation(libs.roborazzi.junit.rule)
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

/**
 * The commonTest Compose UI classes also compile into androidUnitTest, where there is no
 * Robolectric runner to stand up a composition — every case there NPEs. They are meant for the
 * desktop runner, so the JVM unit-test tasks skip them.
 */
tasks.withType<Test>().configureEach {
    if (name.endsWith("UnitTest")) {
        filter {
            excludeTestsMatching("*ScreenUiTest")
            excludeTestsMatching("*ActionTest")
            isFailOnNoMatchingTests = false
        }
    }
}

compose {
    resources {
        packageOfResClass = "org.mifosx.openbanking.feature.vrpcallback.generated.resources"
    }
}
