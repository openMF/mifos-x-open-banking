/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.vrpcallback.callback

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.mifosx.openbanking.core.data.vrp.VrpAuthValidation
import org.mifosx.openbanking.core.model.callback.ConsentStatus
import org.mifosx.openbanking.core.model.vrp.AccountIdentity
import org.mifosx.openbanking.core.model.vrp.Money
import org.mifosx.openbanking.core.model.vrp.PeriodType
import org.mifosx.openbanking.core.model.vrp.PeriodicLimit
import org.mifosx.openbanking.core.model.vrp.VrpConsent
import org.mifosx.openbanking.core.model.vrp.VrpControlParameters
import org.mifosx.openbanking.feature.vrpcallback.FakeVrpAuthRepository
import org.mifosx.openbanking.feature.vrpcallback.FakeVrpConsentRepository
import template.core.base.network.NetworkError
import template.core.base.network.NetworkResult
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val REDIRECT_URL = "mifosx://vrp-callback?code=abc123&state=xyz"
private const val CONSENT_ID = "45411"
private const val CODE = "abc123"
private const val SORT_CODE_ACCOUNT_NUMBER = "UK.OBIE.SortCodeAccountNumber"

class VrpCallbackViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var auth: FakeVrpAuthRepository
    private lateinit var consents: FakeVrpConsentRepository

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        auth = FakeVrpAuthRepository(validation = VrpAuthValidation.Valid(CODE, CONSENT_ID))
        consents = FakeVrpConsentRepository(NetworkResult.Success(consent()))
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = VrpCallbackViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(VrpCallbackViewModel.REDIRECT_URL_ARG to REDIRECT_URL),
        ),
        auth = auth,
        consents = consents,
    )

    private fun failure(vm: VrpCallbackViewModel) =
        assertIs<VrpCallbackUiState.Failed>(vm.stateFlow.value.uiState)

    // the four steps, in order

    /** Validation makes no call, so the screen is already exchanging by the first frame. */
    @Test
    fun theScreenIsExchangingBeforeAnythingIsDispatched() {
        val vm = viewModel()

        assertEquals(VrpCallbackUiState.Working(CallbackStage.Exchanging), vm.stateFlow.value.uiState)
        assertEquals(REDIRECT_URL, vm.stateFlow.value.redirectUrl)
    }

    /** An invalid redirect never reaches a working stage at all. */
    @Test
    fun anInvalidRedirectFailsBeforeAnythingIsDispatched() {
        auth.validationReturns(VrpAuthValidation.NoPending)
        val vm = viewModel()

        assertEquals(CallbackErrorKind.CallbackInvalid, failure(vm).kind)
    }

    @Test
    fun aValidRedirectIsExchangedThenReadBack() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        assertContentEquals(listOf(REDIRECT_URL), auth.validatedUrls)
        assertContentEquals(listOf(CODE to CONSENT_ID), auth.exchanges)
        assertContentEquals(listOf(CONSENT_ID), consents.refreshedIds)
        assertEquals(
            VrpCallbackUiState.Success(CONSENT_ID, "Sarah Chen"),
            vm.stateFlow.value.uiState,
        )
    }

    // step 1 — validation

    /** Nothing is exchanged for a redirect that does not match the authorisation in flight. */
    @Test
    fun aMismatchedRedirectExchangesNothing() = runTest {
        auth.validationReturns(VrpAuthValidation.SecurityError)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(CallbackErrorKind.CallbackInvalid, failure(vm).kind)
        assertContentEquals(emptyList(), auth.exchanges)
    }

    @Test
    fun decliningAtTheBankExchangesNothing() = runTest {
        auth.validationReturns(VrpAuthValidation.AccessDenied)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(CallbackErrorKind.CallbackInvalid, failure(vm).kind)
        assertContentEquals(emptyList(), auth.exchanges)
    }

    @Test
    fun aRedirectWithNoAuthorisationInFlightExchangesNothing() = runTest {
        auth.validationReturns(VrpAuthValidation.NoPending)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(CallbackErrorKind.CallbackInvalid, failure(vm).kind)
        assertContentEquals(emptyList(), auth.exchanges)
    }

    @Test
    fun aRedirectCarryingNoCodeExchangesNothing() = runTest {
        auth.validationReturns(VrpAuthValidation.MissingCode)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(CallbackErrorKind.CallbackInvalid, failure(vm).kind)
        assertContentEquals(emptyList(), auth.exchanges)
    }

    @Test
    fun aBankReportedErrorExchangesNothing() = runTest {
        auth.validationReturns(VrpAuthValidation.Error("server_error"))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(CallbackErrorKind.CallbackInvalid, failure(vm).kind)
        assertContentEquals(emptyList(), auth.exchanges)
    }

    // steps 2 and 3 — exchange and store

    /** A code is single-use and short-lived, so a refused exchange is almost always a spent one. */
    @Test
    fun aRefusedExchangeIsReportedAsASpentCode() = runTest {
        auth.exchangeReturns(NetworkResult.Error(NetworkError.Client.BadRequest(null)))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(CallbackErrorKind.CodeExpiredOrUsed, failure(vm).kind)
        assertContentEquals(emptyList(), consents.refreshedIds)
    }

    @Test
    fun anUnauthorizedExchangeIsReportedAsASpentCode() = runTest {
        auth.exchangeReturns(NetworkResult.Error(NetworkError.Client.Unauthorized(null)))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(CallbackErrorKind.CodeExpiredOrUsed, failure(vm).kind)
    }

    @Test
    fun anExchangeThatDidNotReachTheBankIsANetworkFailure() = runTest {
        auth.exchangeReturns(NetworkResult.Error(NetworkError.Network(IllegalStateException("offline"))))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(CallbackErrorKind.NetworkUnavailable, failure(vm).kind)
    }

    /**
     * Anything else may have left the bank with a live consent this app cannot use, which nothing
     * afterwards can detect. It is never folded into a generic failure.
     */
    @Test
    fun anUnclassifiedExchangeFailureIsReportedAsAnUnsavedAuthority() = runTest {
        auth.exchangeReturns(NetworkResult.Error(NetworkError.Server(statusCode = 500)))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(CallbackErrorKind.AuthorityNotSaved, failure(vm).kind)
        assertEquals(false, failure(vm).kind.leavesAUsableConsent)
    }

    // step 4 — read back

    /** The credential is already stored, so only the read-back failed and the VRP is usable. */
    @Test
    fun aFailedReadBackStillLeavesAUsableConsent() = runTest {
        consents.refreshReturns(NetworkResult.Error(NetworkError.Network(IllegalStateException("offline"))))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(CallbackErrorKind.ConfirmationFailed, failure(vm).kind)
        assertTrue(failure(vm).kind.leavesAUsableConsent)
    }

    /** A payer chosen at the bank that is not a sort code and account number is a card. */
    @Test
    fun aCardPayerRendersUnusableRatherThanSuccess() = runTest {
        consents.refreshReturns(
            NetworkResult.Success(
                consent(
                    payer = AccountIdentity(
                        schemeName = "UK.OBIE.PAN",
                        identification = "4444333322221111",
                        name = "Credit Card",
                    ),
                ),
            ),
        )
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(VrpCallbackUiState.Unusable(CONSENT_ID), vm.stateFlow.value.uiState)
    }

    @Test
    fun aSortCodeAndAccountNumberPayerRendersSuccess() = runTest {
        consents.refreshReturns(
            NetworkResult.Success(
                consent(
                    payer = AccountIdentity(
                        schemeName = SORT_CODE_ACCOUNT_NUMBER,
                        identification = "80200110204021",
                        name = "Everyday Current Account",
                    ),
                ),
            ),
        )
        val vm = viewModel()
        advanceUntilIdle()

        assertIs<VrpCallbackUiState.Success>(vm.stateFlow.value.uiState)
    }

    // leaving

    @Test
    fun leavingAfterSuccessReportsTheStoredConsent() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.trySendAction(VrpCallbackAction.Dismiss)
        advanceUntilIdle()

        assertEquals(VrpCallbackEvent.Completed(CONSENT_ID), vm.eventFlow.first())
    }

    @Test
    fun leavingAfterAnUnusablePayerStillReportsTheStoredConsent() = runTest {
        consents.refreshReturns(
            NetworkResult.Success(
                consent(
                    payer = AccountIdentity(
                        schemeName = "UK.OBIE.PAN",
                        identification = "4444333322221111",
                        name = "Credit Card",
                    ),
                ),
            ),
        )
        val vm = viewModel()
        advanceUntilIdle()

        vm.trySendAction(VrpCallbackAction.Dismiss)
        advanceUntilIdle()

        assertEquals(VrpCallbackEvent.Completed(CONSENT_ID), vm.eventFlow.first())
    }

    /** The consent exists and will appear in the list, so leaving must not report it abandoned. */
    @Test
    fun leavingAfterAFailedReadBackStillReportsTheStoredConsent() = runTest {
        consents.refreshReturns(NetworkResult.Error(NetworkError.Network(IllegalStateException("offline"))))
        val vm = viewModel()
        advanceUntilIdle()

        vm.trySendAction(VrpCallbackAction.Dismiss)
        advanceUntilIdle()

        assertEquals(VrpCallbackEvent.Completed(CONSENT_ID), vm.eventFlow.first())
    }

    @Test
    fun leavingAfterAFailureThatStoredNothingReportsAbandonment() = runTest {
        auth.exchangeReturns(NetworkResult.Error(NetworkError.Client.BadRequest(null)))
        val vm = viewModel()
        advanceUntilIdle()

        vm.trySendAction(VrpCallbackAction.Dismiss)
        advanceUntilIdle()

        assertEquals(VrpCallbackEvent.Abandoned, vm.eventFlow.first())
    }

    @Test
    fun startingAgainReportsAbandonment() = runTest {
        auth.validationReturns(VrpAuthValidation.AccessDenied)
        val vm = viewModel()
        advanceUntilIdle()

        vm.trySendAction(VrpCallbackAction.StartAgain)
        advanceUntilIdle()

        assertEquals(VrpCallbackEvent.Abandoned, vm.eventFlow.first())
    }

    private fun consent(payer: AccountIdentity? = null) = VrpConsent(
        consentId = CONSENT_ID,
        status = ConsentStatus.Authorised,
        createdAt = Instant.parse("2026-08-19T12:00:00Z"),
        controlParameters = VrpControlParameters(
            maximumIndividualAmount = Money(200_00L, "GBP"),
            periodicLimits = listOf(PeriodicLimit(PeriodType.Month, Money(500_00L, "GBP"))),
            interactionType = "UK.OBIE.VRPType.Sweeping",
        ),
        payee = AccountIdentity(
            schemeName = SORT_CODE_ACCOUNT_NUMBER,
            identification = "40478412345678",
            name = "Sarah Chen",
        ),
        payer = payer,
    )
}
