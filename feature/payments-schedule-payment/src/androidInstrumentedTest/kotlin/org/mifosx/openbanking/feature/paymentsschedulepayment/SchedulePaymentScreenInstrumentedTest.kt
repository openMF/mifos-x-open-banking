/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.paymentsschedulepayment

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.mifosx.openbanking.core.model.banking.BankAccount
import org.mifosx.openbanking.core.model.banking.BeneficiaryScheme
import org.mifosx.openbanking.core.model.banking.payment.CreditorSelection
import org.mifosx.openbanking.core.model.banking.payment.PaymentRail
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.SchedulePaymentAccountRow
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.SchedulePaymentAction
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.SchedulePaymentPickerRow
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.SchedulePaymentState
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.SchedulePaymentStep
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.SchedulePaymentUiState
import kotlin.test.assertEquals

/**
 * The on-device mirror of [SchedulePaymentScreenRobolectricTest].
 *
 * Deliberately small: Robolectric already covers the rendering, and this exists to catch the things
 * that only a real device disagrees about — measurement, scrolling and touch dispatch on the date
 * row, which is the one control this feature adds.
 *
 * The fixtures are **inlined by design**. `androidInstrumentedTest` is a separate compilation and
 * cannot see `commonTest`, so `SchedulePaymentFixtures` is unreachable from here; copying the two
 * states it needs is the shape the other feature modules use.
 */
class SchedulePaymentScreenInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<SchedulePaymentAction>()

    private val account = BankAccount(
        accountId = "acc-1",
        nickname = "",
        accountSubType = "CurrentAccount",
        currency = "GBP",
        sortCode = "802001",
        accountNumber = "10203349",
        rawIdentification = "80200110203349",
    )

    private val accountRow = SchedulePaymentAccountRow(
        id = "acc-1",
        nickname = "",
        accountSubType = "CurrentAccount",
        accountNumber = "10203349",
        rawIdentification = "80200110203349",
        supporting = "£21,530.92",
    )

    private fun formState(
        rail: PaymentRail = PaymentRail.Domestic,
        executionDate: LocalDate? = null,
        step: SchedulePaymentStep = SchedulePaymentStep.Form,
    ) = SchedulePaymentState(
        uiState = SchedulePaymentUiState.Content(
            step = step,
            rail = rail,
            debtorAccounts = listOf(account),
            debtorRows = listOf(accountRow),
            debtorAccountRow = accountRow,
            debtorAccountId = "acc-1",
            beneficiaries = listOf(
                SchedulePaymentPickerRow(
                    id = "ben-1",
                    initials = "DC",
                    headline = "Mr Dharani C",
                    supporting = "Sort Code · 80-20-01 10203350",
                    shortName = "Dharani C",
                ),
            ),
            creditor = CreditorSelection(
                name = "Mr Dharani C",
                scheme = BeneficiaryScheme.SortCode,
                identification = "80200110203350",
            ),
            creditorLabel = "Mr Dharani C",
            creditorSupporting = "Sort Code · 80-20-01 10203350",
            amountInput = "250",
            amountLabel = "£250.00",
            today = LocalDate(2026, 8, 12),
            executionDate = executionDate,
            executionDateLabel = executionDate?.let { "Friday, 14 August 2026" }.orEmpty(),
            availableBalanceMinorUnits = 2_153_092L,
            availableBalanceLabel = "£21,530.92",
        ),
    )

    private fun render(state: SchedulePaymentState) {
        composeRule.setContent {
            SchedulePaymentScreenContent(
                state = state,
                onAction = { actions.add(it) },
                onNavigateToConsents = {},
            )
        }
    }

    @Test
    fun theDateFieldIsReachableOnADeviceSizedScreen() {
        render(formState())

        composeRule.onNodeWithTag(SchedulePaymentTestTags.DATE_FIELD).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun tappingTheDateFieldAsksToOpenThePicker() {
        render(formState())

        composeRule.onNodeWithTag(SchedulePaymentTestTags.DATE_FIELD).performScrollTo().performClick()

        assertEquals(listOf(SchedulePaymentAction.OpenDatePicker), actions)
    }

    @Test
    fun theInternationalFormOffersNoReferenceField() {
        render(formState(rail = PaymentRail.International))

        composeRule.onNodeWithTag(SchedulePaymentTestTags.REFERENCE_FIELD).assertDoesNotExist()
    }

    @Test
    fun theReviewStatesTheDate() {
        render(formState(executionDate = LocalDate(2026, 8, 14), step = SchedulePaymentStep.Review))

        composeRule.onNodeWithTag(SchedulePaymentTestTags.REVIEW_DATE_ROW).assertIsDisplayed()
    }

    @Test
    fun theReviewCarriesTheNotYetMadeNotice() {
        render(formState(executionDate = LocalDate(2026, 8, 14), step = SchedulePaymentStep.Review))

        composeRule.onNodeWithTag(SchedulePaymentTestTags.REVIEW_NOT_YET_MADE).performScrollTo()
            .assertIsDisplayed()
    }
}
