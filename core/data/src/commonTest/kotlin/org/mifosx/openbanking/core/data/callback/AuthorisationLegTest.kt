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
import org.mifosx.openbanking.core.data.vrp.VrpAuthValidation
import org.mifosx.openbanking.core.model.banking.payment.ConsentType
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult
import kotlin.test.Test
import kotlin.test.assertEquals

private const val REDIRECT_URL = "org.mifosx.openbanking://callback?code=abc&state=xyz"

/**
 * Covers [authorisationLegOf], whose whole content is the order two questions are asked in.
 *
 * The legs share one registered redirect URI and one bus, and each repository can only speak for its
 * own session. A redirect both would accept therefore has to be resolved by the order, and the
 * consequence of resolving it the other way is silent: the code is exchanged into the wrong session
 * and the failure appears later, somewhere else.
 */
class AuthorisationLegTest {

    private fun vrpAuth(claims: Boolean) = object : StubVrpAuthRepository() {
        override fun isVrpRedirect(redirectUrl: String): Boolean = claims
    }

    private fun paymentAuth(claims: Boolean) = object : StubPaymentAuthRepository() {
        override fun isPaymentRedirect(redirectUrl: String): Boolean = claims
    }

    @Test
    fun aRedirectOnlyVrpClaimsIsVrp() {
        assertEquals(
            AuthorisationLeg.Vrp,
            authorisationLegOf(REDIRECT_URL, vrpAuth(claims = true), paymentAuth(claims = false)),
        )
    }

    @Test
    fun aRedirectOnlyPaymentClaimsIsPayment() {
        assertEquals(
            AuthorisationLeg.Payment,
            authorisationLegOf(REDIRECT_URL, vrpAuth(claims = false), paymentAuth(claims = true)),
        )
    }

    /** Neither leg holds a matching authorisation, so it belongs to the one that holds no state. */
    @Test
    fun aRedirectNeitherClaimsIsSignIn() {
        assertEquals(
            AuthorisationLeg.SignIn,
            authorisationLegOf(REDIRECT_URL, vrpAuth(claims = false), paymentAuth(claims = false)),
        )
    }

    /**
     * The ordering rule itself.
     *
     * Reversing the two branches would classify this as a payment, exchanging the code into the
     * payment session and leaving the per-consent refresh token — what every later VRP payment is
     * made on — unstored.
     */
    @Test
    fun aRedirectBothClaimIsVrp() {
        assertEquals(
            AuthorisationLeg.Vrp,
            authorisationLegOf(REDIRECT_URL, vrpAuth(claims = true), paymentAuth(claims = true)),
        )
    }

    /** VRP is asked first, so a redirect it claims is never offered to the payment leg at all. */
    @Test
    fun aVrpRedirectIsNeverOfferedToThePaymentLeg() {
        var paymentAsked = false
        val payment = object : StubPaymentAuthRepository() {
            override fun isPaymentRedirect(redirectUrl: String): Boolean {
                paymentAsked = true
                return true
            }
        }

        authorisationLegOf(REDIRECT_URL, vrpAuth(claims = true), payment)

        assertEquals(false, paymentAsked)
    }

    @Test
    fun theRedirectIsPassedThroughUnchanged() {
        var seenByVrp: String? = null
        val vrp = object : StubVrpAuthRepository() {
            override fun isVrpRedirect(redirectUrl: String): Boolean {
                seenByVrp = redirectUrl
                return true
            }
        }

        authorisationLegOf(REDIRECT_URL, vrp, paymentAuth(claims = false))

        assertEquals(REDIRECT_URL, seenByVrp)
    }
}

/** Answers nothing but the one question under test. */
private abstract class StubVrpAuthRepository : VrpAuthRepository {

    override suspend fun beginAuthorisation(consentId: String): NetworkResult<String, NetworkError> =
        NetworkResult.Error(NetworkError.Client.BadRequest("not used"))

    override fun validateCallback(redirectUrl: String): VrpAuthValidation = VrpAuthValidation.NoPending

    override suspend fun exchangeAndPersistCredential(
        code: String,
        consentId: String,
    ): NetworkResult<Unit, NetworkError> =
        NetworkResult.Error(NetworkError.Client.BadRequest("not used"))

    override fun pendingConsentId(): String? = null

    override fun discardAuthorisation() = Unit
}

/** Answers nothing but the one question under test. */
private abstract class StubPaymentAuthRepository : PaymentAuthRepository {

    override fun validateCallback(redirectUrl: String): PaymentAuthValidation =
        PaymentAuthValidation.NoPending

    override suspend fun exchangeCode(code: String): NetworkResult<Unit, NetworkError> =
        NetworkResult.Error(NetworkError.Client.BadRequest("not used"))

    override suspend fun consentStatus(consentId: String): NetworkResult<String, NetworkError> =
        NetworkResult.Error(NetworkError.Client.BadRequest("not used"))

    override fun pendingConsentType(): ConsentType? = null

    override fun recordApproved() = Unit

    override fun discardAuthorisation() = Unit
}
