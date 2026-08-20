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

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import org.mifosx.openbanking.core.model.banking.payment.ChargeBearer
import org.mifosx.openbanking.core.model.banking.payment.PaymentRail
import org.mifosx.openbanking.feature.sendmoney.ui.OFFERED_CHARGE_BEARERS
import org.mifosx.openbanking.feature.sendmoney.ui.SendMoneyAmountProblem
import org.mifosx.openbanking.feature.sendmoney.ui.SendMoneyErrorKind
import org.mifosx.openbanking.feature.sendmoney.ui.SendMoneyStage
import kotlin.test.Test

/**
 * Renders each state through the stateless [SendMoneyScreenContent] on the desktop runner.
 *
 * The recurring assertion is which recovery buttons exist, because the whole design of the error
 * state is that the four are never interchangeable and never all shown.
 */
@OptIn(ExperimentalTestApi::class)
class SendMoneyScreenUiTest {

    /** TC-SEND-002: the skeleton, not a spinner — the app's loading convention. */
    @Test
    fun loadingRendersTheSkeleton() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(SendMoneyFixtures.loadingState(), {}, {})
        }
        onNodeWithTag(SendMoneyTestTags.SKELETON).assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.PAYER_PICKER).assertDoesNotExist()
    }

    /**
     * TC-SEND-001, restated for the collapsed picker.
     *
     * The payer is one row until it is asked to open, so the accounts are NOT on screen — that is
     * the whole point of the change, and asserting their absence is what stops the permanently
     * expanded list creeping back.
     */
    @Test
    fun theFormPageRendersTheCollapsedPayerAndThePayeeRow() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(SendMoneyFixtures.formState(), {}, {})
        }
        onNodeWithTag(SendMoneyTestTags.PAYER_PICKER).assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.DEBTOR_LIST).assertDoesNotExist()
        onNodeWithTag(SendMoneyTestTags.debtorRow(SendMoneyFixtures.CURRENT_ACCOUNT_ID)).assertDoesNotExist()
        onNodeWithTag(SendMoneyTestTags.CREDITOR_LIST).assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.MANUAL_ENTRY_BUTTON).assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.creditorRow(SendMoneyFixtures.JAMESON_ID)).assertIsDisplayed()
    }

    /** Expanded, the accounts and the bank choice are both there — and the bank choice is inside. */
    @Test
    fun expandingThePayerRevealsTheAccountsAndTheBankChoice() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(SendMoneyFixtures.formState(payerPickerExpanded = true), {}, {})
        }
        onNodeWithTag(SendMoneyTestTags.DEBTOR_LIST).assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.debtorRow(SendMoneyFixtures.CURRENT_ACCOUNT_ID)).assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.debtorRow(SendMoneyFixtures.SAVINGS_ACCOUNT_ID)).assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.PAYER_BANK_CHOICE).assertIsDisplayed()
    }

    /**
     * "Choose at my bank" is the only route by which a credit card or a Global Money wallet can fund
     * a payment — both are filtered out of the account list — so it must not be reachable ONLY while
     * the picker happens to be open by accident. Collapsed, it is genuinely absent; expanded, present.
     */
    @Test
    fun theBankChoiceIsInsideThePickerRatherThanBesideIt() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(SendMoneyFixtures.formState(debtorAccountId = null), {}, {})
        }
        onNodeWithTag(SendMoneyTestTags.PAYER_PICKER).assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.PAYER_BANK_CHOICE).assertDoesNotExist()
    }

    /**
     * The payer, the payee and the amount are one page, not three steps.
     *
     * This is the assertion the split into steps made impossible: the amount used to live behind a
     * transition, so nothing could check that all three are reachable without one.
     */
    @Test
    fun theFormPageCarriesTheAmountAndReferenceOnTheSameScroll() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(SendMoneyFixtures.formState(), {}, {})
        }
        onNodeWithTag(SendMoneyTestTags.FORM_PAGE).assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.RAIL_TOGGLE).assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.PAYER_PICKER).assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.AMOUNT_CARD).performScrollTo().assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.AMOUNT_FIELD).performScrollTo().assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.REFERENCE_FIELD).performScrollTo().assertIsDisplayed()
    }

    /**
     * The payee list is account-scoped, so before a payer exists there is nothing to show.
     *
     * The previous payer's payees must not survive into a form with no payer.
     */
    @Test
    fun noPayerChosenOffersNoPayees() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(SendMoneyFixtures.formState(debtorAccountId = null), {}, {})
        }
        onNodeWithTag(SendMoneyTestTags.creditorRow(SendMoneyFixtures.JAMESON_ID)).assertDoesNotExist()
    }

    /** Sending no payer is something to choose, not something left undone — offered among the accounts. */
    @Test
    fun theBankChoiceIsOfferedAmongTheAccounts() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(
                SendMoneyFixtures.formState(debtorAccountId = null, payerPickerExpanded = true),
                {},
                {},
            )
        }
        onNodeWithTag(SendMoneyTestTags.DEBTOR_LIST).assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.PAYER_BANK_CHOICE).assertIsDisplayed()
    }

    /** Asking the bank to choose still leaves the payee unanswerable from saved payees. */
    @Test
    fun lettingTheBankChooseStillNeedsAManualPayee() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(
                SendMoneyFixtures.formState(debtorAccountId = null, letBankChoosePayer = true),
                {},
                {},
            )
        }
        onNodeWithTag(SendMoneyTestTags.MANUAL_ENTRY_BUTTON).assertIsDisplayed()
    }

    /** The amount is instructed in sterling, so a non-sterling payer is worth saying out loud. */
    @Test
    fun aNonSterlingPayerIsCalledOut() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(SendMoneyFixtures.formState(debtorCurrency = "USD"), {}, {})
        }
        onNodeWithTag(SendMoneyTestTags.NON_GBP_NOTICE).assertIsDisplayed()
    }

    @Test
    fun aSterlingPayerGetsNoConversionNotice() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(SendMoneyFixtures.formState(), {}, {})
        }
        onNodeWithTag(SendMoneyTestTags.NON_GBP_NOTICE).assertDoesNotExist()
    }

    /**
     * The two rails ask for different fields, because the bank accepts different fields.
     *
     * Reference is refused internationally with `U005`, and ChargeBearer is refused domestically —
     * so neither is a preference, and showing the wrong one would invite a request that cannot be
     * sent.
     */
    @Test
    fun theDomesticRailAsksForAReferenceAndNoCharges() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(SendMoneyFixtures.formState(rail = PaymentRail.Domestic), {}, {})
        }
        onNodeWithTag(SendMoneyTestTags.REFERENCE_FIELD).performScrollTo().assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.CHARGE_BEARER_PICKER).assertDoesNotExist()
        onNodeWithTag(SendMoneyTestTags.INSTRUCTED_CURRENCY_PICKER).assertDoesNotExist()
    }

    @Test
    fun theInternationalRailAsksForChargesAndCurrencyAndNoReference() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(
                SendMoneyFixtures.formState(rail = PaymentRail.International),
                {},
                {},
            )
        }
        onNodeWithTag(SendMoneyTestTags.INSTRUCTED_CURRENCY_PICKER).performScrollTo().assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.CHARGE_BEARER_PICKER).performScrollTo().assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.REFERENCE_FIELD).assertDoesNotExist()
    }

    /** Three of the four OBIE values are offered. */
    @Test
    fun everyAcceptedChargeBearerIsOffered() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(
                SendMoneyFixtures.formState(rail = PaymentRail.International),
                {},
                {},
            )
        }
        onNodeWithTag(SendMoneyTestTags.CHARGE_BEARER_PICKER).performScrollTo().performClick()

        OFFERED_CHARGE_BEARERS.forEach { bearer ->
            onNodeWithTag(SendMoneyTestTags.chargeBearerOption(bearer), useUnmergedTree = true).assertExists()
        }
    }

    /**
     * The regression that matters: `FollowingServiceLevel` must not be offered.
     *
     * HSBC's implementation guide restricts ChargeBearer to three values and refuses the fourth with
     * `400 UK.OBIE.Field.Invalid`. It stays in the enum — that is the OBIE codeset, and `fromWire`
     * has to parse it — so nothing about the model stops it reappearing in the picker except this.
     */
    @Test
    fun followingServiceLevelIsNotOffered() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(
                SendMoneyFixtures.formState(rail = PaymentRail.International),
                {},
                {},
            )
        }
        onNodeWithTag(SendMoneyTestTags.CHARGE_BEARER_PICKER).performScrollTo().performClick()

        onNodeWithTag(
            SendMoneyTestTags.chargeBearerOption(ChargeBearer.FollowingServiceLevel),
            useUnmergedTree = true,
        ).assertDoesNotExist()
    }

    /**
     * HSBC's nineteen documented routing currencies, sterling included, in the form's ONE menu.
     *
     * GBP used to be asserted absent, on the belief that Global Money accepts only USD and EUR. INT-04
     * disproves it: `CurrencyOfTransfer: GBP` stages `201`/`AWAU`, so excluding it withheld a
     * currency the bank accepts.
     */
    @Test
    fun theCurrencyControlOffersHsbcsRoutingListIncludingSterling() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(
                SendMoneyFixtures.formState(rail = PaymentRail.International),
                {},
                {},
            )
        }
        onNodeWithTag(SendMoneyTestTags.INSTRUCTED_CURRENCY_PICKER).performScrollTo().performClick()

        listOf("GBP", "USD", "EUR", "THB").forEach { code ->
            onNodeWithTag(SendMoneyTestTags.instructedCurrencyOption(code), useUnmergedTree = true).assertExists()
        }
    }

    /**
     * One currency control, and it is on the amount.
     *
     * The "Recipient receives" selector that used to sit below the amount is gone: with the transfer
     * currency derived from the instructed one, a second menu asked the same question twice.
     *
     * It no longer claims the domestic rail has no control at all — that rail now names sterling in
     * a box of the same shape, which [theDomesticRailStatesSterlingWithoutOfferingAChoice] covers.
     * What is still true, and what this asserts, is that only one control anywhere is a *choice*.
     */
    @Test
    fun theAmountCarriesTheOnlyCurrencyChoiceAndNothingRestatesIt() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(
                SendMoneyFixtures.formState(rail = PaymentRail.International),
                {},
                {},
            )
        }
        onNodeWithTag(SendMoneyTestTags.INSTRUCTED_CURRENCY_PICKER).performScrollTo().assertIsDisplayed()
        onNodeWithText("Recipient receives").assertDoesNotExist()
    }

    /**
     * The domestic rail names its currency without offering a choice about it.
     *
     * It used to do neither: no control at all, which left `250.00` on screen with nothing saying
     * sterling. Both halves are asserted because either one alone is the old bug or a new one — a
     * box that opens a menu of one option, or a rail that still will not say what it is sending.
     */
    @Test
    fun theDomesticRailStatesSterlingWithoutOfferingAChoice() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(SendMoneyFixtures.formState(), {}, {})
        }
        onNodeWithTag(SendMoneyTestTags.STATIC_CURRENCY_BOX).performScrollTo().assertIsDisplayed()
        onNodeWithText("£").assertExists()
        onNodeWithTag(SendMoneyTestTags.INSTRUCTED_CURRENCY_PICKER).assertDoesNotExist()
    }

    /** And the international rail swaps the one for the other, in the same place. */
    @Test
    fun theInternationalRailSwapsTheStaticBoxForThePicker() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(
                SendMoneyFixtures.formState(rail = PaymentRail.International),
                {},
                {},
            )
        }
        onNodeWithTag(SendMoneyTestTags.INSTRUCTED_CURRENCY_PICKER).performScrollTo().assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.STATIC_CURRENCY_BOX).assertDoesNotExist()
    }

    /**
     * Any currency the control offers reaches review, because there is no longer a pair to disagree.
     *
     * This replaces a case asserting that USD instructed against EUR received blocked the review: two
     * selectors could reach the combination HSBC refuses, one cannot.
     */
    @Test
    fun aNonSterlingAmountReachesReviewWithNothingToDisagreeWith() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(
                SendMoneyFixtures.filledInternationalFormState(instructedCurrency = "USD"),
                {},
                {},
            )
        }
        onNodeWithTag(SendMoneyTestTags.REVIEW_BUTTON).assertIsEnabled()
    }

    /** Each rail identifies a creditor its own way, and refuses the other's scheme with `U027`. */
    @Test
    fun manualEntryAsksForAnIbanOnTheInternationalRail() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(
                SendMoneyFixtures.formState(rail = PaymentRail.International, manualEntryVisible = true),
                {},
                {},
            )
        }
        onNodeWithTag(SendMoneyTestTags.MANUAL_IBAN).performScrollTo().assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.MANUAL_SORT_CODE).assertDoesNotExist()
        onNodeWithTag(SendMoneyTestTags.MANUAL_ACCOUNT_NUMBER).assertDoesNotExist()
    }

    /**
     * The review describes the rail it is actually reviewing.
     *
     * "Sent via" read "Faster Payments" on both rails, which is untrue of an international payment,
     * and the field that rail turns on had no row at all — so a customer could approve a charge
     * arrangement the review never mentioned.
     */
    @Test
    fun theDomesticReviewNamesFasterPaymentsAndShowsTheReference() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(SendMoneyFixtures.reviewState(rail = PaymentRail.Domestic), {}, {})
        }
        onNodeWithTag(SendMoneyTestTags.REVIEW_REFERENCE).performScrollTo().assertIsDisplayed()
        onNodeWithText("Faster Payments").assertExists()
        onNodeWithTag(SendMoneyTestTags.REVIEW_CHARGE_BEARER).assertDoesNotExist()
    }

    @Test
    fun theInternationalReviewShowsTheChargesAndNoReference() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(
                SendMoneyFixtures.reviewState(rail = PaymentRail.International),
                {},
                {},
            )
        }
        onNodeWithTag(SendMoneyTestTags.REVIEW_CHARGE_BEARER).performScrollTo().assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.REVIEW_REFERENCE).assertDoesNotExist()
        onNodeWithText("Faster Payments").assertDoesNotExist()
    }

    /**
     * The row that stated the currency a second time is gone, and the amount still states it once.
     *
     * "Recipient receives: US Dollar (USD)" earned its place only while the two currencies could
     * differ. They cannot now, and `formatMinorUnits` renders USD as `$`, so the figure beside it
     * already said everything the row did.
     */
    @Test
    fun theInternationalReviewStatesTheCurrencyOnceThroughTheAmount() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(
                SendMoneyFixtures.reviewState(rail = PaymentRail.International, instructedCurrency = "USD"),
                {},
                {},
            )
        }
        onNodeWithText("Recipient receives").assertDoesNotExist()
        onNodeWithText("US Dollar (USD)").assertDoesNotExist()
        // By tag, not by text: "$850.00" is on the hero, the total and the confirm button, and
        // onNodeWithText insists on exactly one match.
        onNodeWithTag(SendMoneyTestTags.REVIEW_AMOUNT).assertTextEquals("$850.00")
    }

    /** With no list, hand-keying the payee is the only route to a payment, so it survives a failure. */
    @Test
    fun aFailedPayeeReadKeepsPayNew() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(SendMoneyFixtures.formState(payeesFailed = true), {}, {})
        }
        onNodeWithTag(SendMoneyTestTags.CREDITOR_LIST).assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.MANUAL_ENTRY_BUTTON).assertIsDisplayed()
    }

    /** A read still running shows placeholders rather than answering for the bank. */
    @Test
    fun aPayeeReadStillRunningShowsPlaceholders() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(SendMoneyFixtures.formState(payeesLoading = true), {}, {})
        }
        onNodeWithTag(SendMoneyTestTags.PAYEES_LOADING).performScrollTo().assertIsDisplayed()
    }

    /**
     * And waiting does not cost the customer the one route that never needed the list.
     *
     * "Pay new" is the first item of the avatar row, which is rendered above the notice rather than
     * inside it — so the wait must not be able to take it away, any more than a failure can.
     */
    @Test
    fun aPayeeReadStillRunningKeepsPayNewAvailable() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(SendMoneyFixtures.formState(payeesLoading = true), {}, {})
        }
        onNodeWithTag(SendMoneyTestTags.CREDITOR_LIST).assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.MANUAL_ENTRY_BUTTON).assertIsDisplayed()
    }

    /**
     * With no payer nothing is in flight, so the row must not shimmer either.
     */
    @Test
    fun noPayerChosenDoesNotRenderAsLoading() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(SendMoneyFixtures.formState(debtorAccountId = null), {}, {})
        }
        onNodeWithTag(SendMoneyTestTags.PAYEES_LOADING).assertDoesNotExist()
    }

    /** A list that loaded and happens to be empty is settled, so it does not shimmer. */
    @Test
    fun anEmptyPayeeListDoesNotRenderAsLoading() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(SendMoneyFixtures.formState(beneficiaries = emptyList()), {}, {})
        }
        onNodeWithTag(SendMoneyTestTags.PAYEES_LOADING).assertDoesNotExist()
    }

    /** With no payer of our own the row says so, rather than rendering as an empty field. */
    @Test
    fun theReviewSaysWhenTheBankWillChooseThePayer() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(SendMoneyFixtures.reviewState(debtorAccountRow = null), {}, {})
        }
        onNodeWithTag(SendMoneyTestTags.REVIEW_FROM).performScrollTo().assertIsDisplayed()
        onNodeWithText("You’ll choose at your bank").assertExists()
    }

    /** Pinned, so it is reachable however far the form has been scrolled. */
    @Test
    fun theFormActionBarStaysOutsideTheScroll() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(SendMoneyFixtures.filledFormState(), {}, {})
        }
        onNodeWithTag(SendMoneyTestTags.FORM_ACTIONS).assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.REVIEW_BUTTON).assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.FORM_TRUST_NOTE).assertIsDisplayed()
    }

    /** The rail cannot be changed once a specific payment is being reviewed. */
    @Test
    fun theReviewPageDropsTheRailToggleAndTheFormActions() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(SendMoneyFixtures.reviewState(), {}, {})
        }
        onNodeWithTag(SendMoneyTestTags.REVIEW_PAGE).assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.RAIL_TOGGLE).assertDoesNotExist()
        onNodeWithTag(SendMoneyTestTags.FORM_ACTIONS).assertDoesNotExist()
        onNodeWithTag(SendMoneyTestTags.FORM_PAGE).assertDoesNotExist()
    }

    /** TC-SEND-013: no payees is not a dead end. */
    @Test
    fun anEmptyPayeeListKeepsManualEntryAvailable() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(SendMoneyFixtures.formState(beneficiaries = emptyList()), {}, {})
        }
        // The row survives an empty list on purpose: its first item is the only way to pay someone
        // who is not saved, so dropping it would take the escape away exactly when it is needed.
        onNodeWithTag(SendMoneyTestTags.CREDITOR_LIST).assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.MANUAL_ENTRY_BUTTON).assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.creditorRow(SendMoneyFixtures.JAMESON_ID)).assertDoesNotExist()
    }

    /** The manual fields sit below the payee list, so they need scrolling to before asserting. */
    @Test
    fun theManualFieldsAppearOnlyWhenAskedFor() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(SendMoneyFixtures.formState(manualEntryVisible = true), {}, {})
        }
        onNodeWithTag(SendMoneyTestTags.MANUAL_SORT_CODE).performScrollTo().assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.MANUAL_ACCOUNT_NUMBER).performScrollTo().assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.MANUAL_CONFIRM).performScrollTo().assertIsDisplayed()
    }

    /**
     * TC-SEND-003: an unpayable amount blocks the way forward rather than failing later.
     *
     * The error reads from the unmerged tree because it lives in the text field's `supportingText`
     * slot, whose semantics merge into the field itself.
     */
    @Test
    fun anAmountBeyondTheBalanceDisablesReview() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(
                SendMoneyFixtures.filledFormState(problem = SendMoneyAmountProblem.ExceedsAvailableBalance),
                {},
                {},
            )
        }
        onNodeWithTag(SendMoneyTestTags.AMOUNT_ERROR, useUnmergedTree = true).assertExists()
        onNodeWithTag(SendMoneyTestTags.REVIEW_BUTTON).assertIsNotEnabled()
    }

    /** TC-SMC-001: the amount leads, the detail rows confirm it, and both controls are present. */
    @Test
    fun theReviewPageLeadsWithTheAmountAndListsEveryDetail() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(SendMoneyFixtures.reviewState(), {}, {})
        }
        onNodeWithTag(SendMoneyTestTags.REVIEW_HERO).assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.REVIEW_AMOUNT).assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.REVIEW_PAYEE_CHIP).assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.REVIEW_SUMMARY).assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.REVIEW_TO).assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.REVIEW_FROM).assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.REVIEW_REFERENCE).assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.REVIEW_SENT_VIA).assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.REVIEW_TOTAL).assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.REVIEW_AUTH_NOTICE).assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.CONFIRM_BUTTON).assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.EDIT_PAYMENT_BUTTON).assertIsDisplayed()
    }

    /**
     * TC-SMC-005. A fee figure here could only be hardcoded — charges arrive on the consent
     * response, which does not exist until Confirm is tapped — and the sandbox quotes charges on
     * some payments, so a printed £0.00 would sometimes be false.
     */
    @Test
    fun theReviewPageShowsNoFeeFigureItCannotKnow() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(SendMoneyFixtures.reviewState(), {}, {})
        }
        onNodeWithText("£0.00").assertDoesNotExist()
    }

    /**
     * The submit guard, rendered. Confirming unmounts the control rather than disabling it, so there
     * is no button on screen at all to tap a second time.
     */
    @Test
    fun submittingRendersNoCallToActionAtAll() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(SendMoneyFixtures.submittingState(), {}, {})
        }
        onNodeWithTag(SendMoneyTestTags.SUBMITTING_INDICATOR).assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.SUBMITTING_AMOUNT).assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.SUBMITTING_LOCK_NOTE).assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.CONFIRM_BUTTON).assertDoesNotExist()
        onNodeWithTag(SendMoneyTestTags.CANCEL_BUTTON).assertDoesNotExist()
        onNodeWithTag(SendMoneyTestTags.REVIEW_BUTTON).assertDoesNotExist()
    }

    @Test
    fun submittingNamesTheStageItHasReached() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(
                SendMoneyFixtures.submittingState(stage = SendMoneyStage.AwaitingAuthorisation),
                {},
                {},
            )
        }
        onNodeWithTag(SendMoneyTestTags.SUBMITTING_INDICATOR).assertIsDisplayed()
    }

    /** TC-SEND-009: U014 offers exactly one recovery, and it is not Retry. */
    @Test
    fun anOutOfLimitsFailureOffersOnlyChangeAmount() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(
                SendMoneyFixtures.errorState(kind = SendMoneyErrorKind.OutsideControlParameters),
                {},
                {},
            )
        }
        onNodeWithTag(SendMoneyTestTags.EDIT_AMOUNT_BUTTON).assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.RETRY_BUTTON).assertDoesNotExist()
        onNodeWithTag(SendMoneyTestTags.REAUTHORISE_BUTTON).assertDoesNotExist()
        onNodeWithTag(SendMoneyTestTags.VIEW_CONSENTS_BUTTON).assertDoesNotExist()
    }

    /** TC-SEND-007: a client defect gets no button, only the reference to quote. */
    @Test
    fun aSignatureFailureOffersNoRecoveryAtAll() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(
                SendMoneyFixtures.errorState(kind = SendMoneyErrorKind.SignatureMissing),
                {},
                {},
            )
        }
        onNodeWithTag(SendMoneyTestTags.ERROR_SUPPORT_REFERENCE).assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.RETRY_BUTTON).assertDoesNotExist()
        onNodeWithTag(SendMoneyTestTags.REAUTHORISE_BUTTON).assertDoesNotExist()
        onNodeWithTag(SendMoneyTestTags.VIEW_CONSENTS_BUTTON).assertDoesNotExist()
        onNodeWithTag(SendMoneyTestTags.EDIT_AMOUNT_BUTTON).assertDoesNotExist()
    }

    /** TC-SEND-008. */
    @Test
    fun anUnauthorisedConsentOffersOnlyReauthorise() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(
                SendMoneyFixtures.errorState(kind = SendMoneyErrorKind.ConsentNotAuthorised),
                {},
                {},
            )
        }
        onNodeWithTag(SendMoneyTestTags.REAUTHORISE_BUTTON).assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.RETRY_BUTTON).assertDoesNotExist()
        onNodeWithTag(SendMoneyTestTags.EDIT_AMOUNT_BUTTON).assertDoesNotExist()
    }

    @Test
    fun aRevokedConsentOffersOnlyViewConsents() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(
                SendMoneyFixtures.errorState(kind = SendMoneyErrorKind.ConsentRevoked),
                {},
                {},
            )
        }
        onNodeWithTag(SendMoneyTestTags.VIEW_CONSENTS_BUTTON).assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.RETRY_BUTTON).assertDoesNotExist()
        onNodeWithTag(SendMoneyTestTags.EDIT_AMOUNT_BUTTON).assertDoesNotExist()
    }

    @Test
    fun aTransportFailureOffersRetry() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(
                SendMoneyFixtures.errorState(kind = SendMoneyErrorKind.NetworkError, supportReference = null),
                {},
                {},
            )
        }
        onNodeWithTag(SendMoneyTestTags.RETRY_BUTTON).assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.ERROR_SUPPORT_REFERENCE).assertDoesNotExist()
    }

    /** TC-SEND-011: an insufficient balance is fixed by changing the amount, not by retrying. */
    @Test
    fun insufficientFundsOffersOnlyChangeAmount() = runComposeUiTest {
        setContent {
            SendMoneyScreenContent(
                SendMoneyFixtures.errorState(kind = SendMoneyErrorKind.InsufficientFunds),
                {},
                {},
            )
        }
        onNodeWithTag(SendMoneyTestTags.EDIT_AMOUNT_BUTTON).assertIsDisplayed()
        onNodeWithTag(SendMoneyTestTags.RETRY_BUTTON).assertDoesNotExist()
    }
}
