/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
@file:Suppress("MatchingDeclarationName")

package org.mifosx.openbanking.feature.paymentstatus

import androidx.navigation.NavGraphBuilder
import kotlinx.serialization.Serializable
import template.core.base.ui.nav.composableWithStayTransitions

/**
 * @property paymentId The bank's `DomesticPaymentId`. The property name is the `SavedStateHandle`
 *   key, so it must stay in step with `PaymentStatusViewModel.PAYMENT_ID_ARG`.
 */
@Serializable
data class PaymentStatusRoute(val paymentId: String)

fun NavGraphBuilder.paymentStatusScreen(onBack: () -> Unit) {
    composableWithStayTransitions<PaymentStatusRoute> {
        PaymentStatusScreen(onBack = onBack)
    }
}
