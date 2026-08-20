/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.sendmoney

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mifosx.openbanking.core.model.banking.BankAccount
import org.mifosx.openbanking.core.model.banking.payment.ChargeBearer
import org.mifosx.openbanking.core.model.banking.payment.PaymentRail
import org.mifosx.openbanking.feature.sendmoney.ui.SendMoneyAccountRow
import org.mifosx.openbanking.feature.sendmoney.ui.SendMoneyAction
import org.mifosx.openbanking.feature.sendmoney.ui.SendMoneyPickerRow
import org.mifosx.openbanking.feature.sendmoney.ui.SendMoneyState
import org.mifosx.openbanking.feature.sendmoney.ui.SendMoneyStep
import org.mifosx.openbanking.feature.sendmoney.ui.SendMoneyUiState
import kotlin.test.assertEquals

private const val CURRENT_ACCOUNT_ID = "123456791"
private const val JAMESON_ID = "BEN-001"
private const val WEISS_ID = "BEN-101"

/**
 * androidInstrumentedTest does not see commonTest, so the state fixtures are inlined here.
 *
 * Kept deliberately small: this is the on-device mirror of [SendMoneyScreenRobolectricTest], and its
 * job is to prove the screen composes and responds on a real device — the exhaustive per-state
 * assertions live in the JVM suites, which are far cheaper to run.
 */
private fun currentAccount(): BankAccount = BankAccount(
    accountId = CURRENT_ACCOUNT_ID,
    nickname = "Current account ·· 3349",
    accountSubType = "CurrentAccount",
    currency = "GBP",
    sortCode = "802001",
    accountNumber = "10203349",
    rawIdentification = "80200110203349",
)

private fun debtorRows(): List<SendMoneyAccountRow> = listOf(
    SendMoneyAccountRow(
        id = CURRENT_ACCOUNT_ID,
        nickname = "Current account ·· 3349",
        accountSubType = "CurrentAccount",
        accountNumber = "10203349",
        rawIdentification = "80200110203349",
        supporting = "£21,530.92 available",
    ),
)

private fun domesticPayees(): List<SendMoneyPickerRow> = listOf(
    SendMoneyPickerRow(JAMESON_ID, "JL", "Jameson Lettings", "Sort Code · 40-12-09 65872310", "Jameson L."),
)

private fun internationalPayees(): List<SendMoneyPickerRow> = listOf(
    SendMoneyPickerRow(WEISS_ID, "KW", "Klara Weiss", "IBAN · DE89 3704 0044 0532 0130 00", "Klara W."),
)

/**
 * HSBC's documented routing currencies, copied because instrumented tests cannot see commonMain's
 * `OFFERED_CURRENCIES`. Only the first few are asserted on; the rest are here so the menu this suite
 * opens is the length the real one is.
 */
private val OFFERED_CURRENCIES = listOf(
    "GBP", "EUR", "USD", "AUD", "CAD", "CHF", "CNY", "HKD", "SGD", "NZD",
    "AED", "CZK", "DKK", "NOK", "PLN", "SAR", "SEK", "ZAR", "THB",
)

private fun formState(
    rail: PaymentRail = PaymentRail.Domestic,
    debtorAccountId: String? = CURRENT_ACCOUNT_ID,
    payerPickerExpanded: Boolean = false,
    payeesFailed: Boolean = false,
    payeesLoading: Boolean = false,
): SendMoneyState = SendMoneyState(
    uiState = SendMoneyUiState.Content(
        step = SendMoneyStep.Form,
        rail = rail,
        debtorAccounts = listOf(currentAccount()),
        debtorRows = debtorRows(),
        // A read that has not answered carries no list, exactly as a refused one does — `content()`
        // takes the list off `ScreenState.Content` and every other state yields an empty one.
        beneficiaries = when {
            payeesFailed || payeesLoading -> emptyList()
            rail == PaymentRail.Domestic -> domesticPayees()
            else -> internationalPayees()
        },
        debtorAccountId = debtorAccountId,
        payerPickerExpanded = payerPickerExpanded,
        payeesFailed = payeesFailed,
        payeesLoading = payeesLoading,
        availableBalanceLabel = if (debtorAccountId == null) "" else "£21,530.92",
        offeredCurrencies = if (rail == PaymentRail.International) OFFERED_CURRENCIES else emptyList(),
        debtorCurrency = if (debtorAccountId == null) "" else "GBP",
        availableBalanceMinorUnits = 2_153_092L,
    ),
)

@RunWith(AndroidJUnit4::class)
class SendMoneyScreenInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<SendMoneyAction>()

    private fun render(state: SendMoneyState) {
        composeRule.setContent {
            SendMoneyScreenContent(state = state, onAction = { actions.add(it) }, onNavigateToConsents = {})
        }
    }

    @Test
    fun theFormPageComposesWithItsPickersAndPinnedAction() {
        render(formState())

        composeRule.onNodeWithTag(SendMoneyTestTags.FORM_PAGE).assertIsDisplayed()
        composeRule.onNodeWithTag(SendMoneyTestTags.RAIL_TOGGLE).assertIsDisplayed()
        composeRule.onNodeWithTag(SendMoneyTestTags.PAYER_PICKER).assertIsDisplayed()
        composeRule.onNodeWithTag(SendMoneyTestTags.CREDITOR_LIST).assertIsDisplayed()
        composeRule.onNodeWithTag(SendMoneyTestTags.FORM_ACTIONS).assertIsDisplayed()
    }

    /** The rails ask for different fields, and each refuses the other's. */
    @Test
    fun theInternationalRailSwapsTheReferenceForChargesAndCurrency() {
        render(formState(rail = PaymentRail.International))

        composeRule.onNodeWithTag(SendMoneyTestTags.INSTRUCTED_CURRENCY_PICKER)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(SendMoneyTestTags.CHARGE_BEARER_PICKER).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(SendMoneyTestTags.REFERENCE_FIELD).assertDoesNotExist()
    }

    /**
     * On a real device too: a read still running shimmers.
     *
     * Worth a device test despite the JVM suites covering the same state, because the shimmer is an
     * infinite animation and a real device is where "the screen never settles" would actually bite
     * — the test rule waits for idle before it can assert anything at all.
     */
    @Test
    fun aPayeeReadStillRunningShowsPlaceholders() {
        render(formState(payeesLoading = true))

        composeRule.onNodeWithTag(SendMoneyTestTags.PAYEES_LOADING).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(SendMoneyTestTags.MANUAL_ENTRY_BUTTON).assertIsDisplayed()
    }

    /** The domestic rail names sterling in the box the international rail opens a menu from. */
    @Test
    fun theDomesticRailShowsAStaticCurrencyBoxInThePickersPlace() {
        render(formState())

        composeRule.onNodeWithTag(SendMoneyTestTags.STATIC_CURRENCY_BOX).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(SendMoneyTestTags.INSTRUCTED_CURRENCY_PICKER).assertDoesNotExist()
    }

    /** On a real device too: the options are behind a menu, so the field is opened first. */
    @Test
    fun choosingAChargeBearerFromTheMenuRoutesTheChoice() {
        render(formState(rail = PaymentRail.International))

        composeRule.onNodeWithTag(SendMoneyTestTags.CHARGE_BEARER_PICKER).performScrollTo().performClick()
        composeRule.onNodeWithTag(SendMoneyTestTags.chargeBearerOption(ChargeBearer.Shared)).performClick()

        assertEquals(listOf<SendMoneyAction>(SendMoneyAction.SelectChargeBearer(ChargeBearer.Shared)), actions)
    }

    /** Nineteen options render in a real popup window, which is the thing a device can disprove. */
    @Test
    fun theInstructedCurrencyMenuOpensOnTheAmountCard() {
        render(formState(rail = PaymentRail.International))

        composeRule.onNodeWithTag(SendMoneyTestTags.INSTRUCTED_CURRENCY_PICKER)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(SendMoneyTestTags.instructedCurrencyOption("USD")).performClick()

        assertEquals(listOf<SendMoneyAction>(SendMoneyAction.SelectInstructedCurrency("USD")), actions)
    }

    /** On a real device too: the accounts fold away until the summary is tapped. */
    @Test
    fun tappingTheCollapsedPickerRoutesTheToggle() {
        render(formState())

        composeRule.onNodeWithTag(SendMoneyTestTags.DEBTOR_LIST).assertDoesNotExist()
        composeRule.onNodeWithTag(SendMoneyTestTags.PAYER_PICKER).performClick()

        assertEquals(listOf<SendMoneyAction>(SendMoneyAction.TogglePayerPicker), actions)
    }

    @Test
    fun tappingChooseAtMyBankRoutesTheAction() {
        render(formState(debtorAccountId = null, payerPickerExpanded = true))

        composeRule.onNodeWithTag(SendMoneyTestTags.PAYER_BANK_CHOICE).performScrollTo().performClick()

        assertEquals(listOf<SendMoneyAction>(SendMoneyAction.LetBankChoosePayer), actions)
    }
}
