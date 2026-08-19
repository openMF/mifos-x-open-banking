/*
 * Copyright 2024 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

plugins {
    alias(libs.plugins.kmp.library.convention)
    alias(libs.plugins.cmp.feature.convention)
    alias(libs.plugins.kmp.koin.convention)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Core Modules
            implementation(projects.core.data)
            implementation(projects.core.database)
            implementation(projects.core.model)
            implementation(projects.core.common)
            implementation(projects.core.datastore)
            implementation(projects.core.network)

            implementation(projects.core.datastore)
            implementation(projects.coreBase.common)
            implementation(projects.coreBase.platform)
            implementation(projects.coreBase.security)

            implementation(projects.feature.home)
            implementation(projects.feature.accounts)
            implementation(projects.feature.accountDetail)
            implementation(projects.feature.transactions)
            implementation(projects.feature.transactionDetail)
            implementation(projects.feature.scheduledPayments)
            implementation(projects.feature.beneficiaries)
            implementation(projects.feature.product)
            implementation(projects.feature.consentList)
            implementation(projects.feature.consentDetail)
            implementation(projects.feature.directDebits)
            implementation(projects.feature.standingOrders)
            implementation(projects.feature.statementDetail)
            implementation(projects.feature.statements)
            implementation(projects.feature.settings)
            implementation(projects.feature.accountHolder)
            implementation(projects.feature.login)
            implementation(projects.feature.consentCallback)
            implementation(projects.feature.paymentsSchedulePayment)
            implementation(projects.feature.paymentsStandingOrder)
            implementation(projects.feature.sendMoney)
            implementation(projects.feature.paymentStatus)
            implementation(projects.feature.paymentConsent)
            implementation(projects.feature.paymentsHub)
            implementation(projects.feature.vrp.vrpConsents)
            implementation(projects.feature.vrp.vrpSetup)
            implementation(projects.feature.vrp.vrpCallback)
            implementation(projects.feature.vrp.vrpPayment)

            //put your multiplatform dependencies here
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.components.uiToolingPreview)
            implementation(compose.components.resources)
            implementation(libs.window.size)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }

        // FileKit provides the platform save/share primitives for the app-layer
        // StatementFileHandler. Its file-saver + write APIs are non-web only, so the
        // real implementation lives in nonJsCommonMain (Android, desktop, iOS); the
        // web targets fall back to the no-op actual in jsCommonMain.
        nonJsCommonMain.dependencies {
            implementation(libs.filekit.core)
            implementation(libs.filekit.dialog.compose)
        }
    }
}

android {
    namespace = "cmp.navigation"
}

compose.resources {
    publicResClass = true
    generateResClass = always
    packageOfResClass = "cmp.navigation.generated.resources"
}