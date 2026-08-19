/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.data.callback

import org.mifosx.openbanking.core.data.vrp.VrpAuthRepository

/** Which authorisation a redirect is returning from. */
enum class AuthorisationLeg {
    Vrp,
    Payment,

    /** The default. The sign-in leg holds no state this can match a redirect against. */
    SignIn,
}

/**
 * Which leg [redirectUrl] is returning from.
 *
 * All three return on the same registered redirect URI, so the URL alone cannot say; each repository
 * answers for its own session. The order they are asked in is what decides a URL both would accept:
 * a VRP return classified as a payment is exchanged into the payment session, and the per-consent
 * refresh token that makes later payments possible is never stored.
 */
fun authorisationLegOf(
    redirectUrl: String,
    vrpAuth: VrpAuthRepository,
    paymentAuth: PaymentAuthRepository,
): AuthorisationLeg = when {
    vrpAuth.isVrpRedirect(redirectUrl) -> AuthorisationLeg.Vrp
    paymentAuth.isPaymentRedirect(redirectUrl) -> AuthorisationLeg.Payment
    else -> AuthorisationLeg.SignIn
}
