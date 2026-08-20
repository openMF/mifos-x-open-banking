/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.paymentsschedulepayment.di

import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.SchedulePaymentHistoryViewModel
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.SchedulePaymentViewModel

/**
 * Koin bindings for the scheduled-payment feature. Included by `cmp-navigation`'s feature module.
 *
 * Constructed explicitly rather than with `viewModelOf`, because the ViewModel's `clock` is a
 * defaulted constructor parameter and `viewModelOf` does not honour Kotlin defaults — it resolves
 * every parameter from the graph, so it asks for a `kotlin.time.Clock` nobody registered and throws
 * `NoDefinitionFoundException` the moment the screen is opened. The clock exists to be overridden by
 * the date tests, not to be injected in production.
 *
 * `payment-status` carries the same note for the same reason. Nothing about this fails to compile,
 * and no unit test reaches it — the suites construct the ViewModel directly — so it surfaces only as
 * a crash on the first tap.
 */
val SchedulePaymentModule = module {
    viewModel {
        SchedulePaymentViewModel(
            accountsOverviewRepository = get(),
            beneficiariesRepository = get(),
            paymentInitiationRepository = get(),
            capabilityRegistry = get(),
            paymentHistoryRepository = get(),
            paymentStatusRepository = get(),
        )
    }
    viewModelOf(::SchedulePaymentHistoryViewModel)
}
