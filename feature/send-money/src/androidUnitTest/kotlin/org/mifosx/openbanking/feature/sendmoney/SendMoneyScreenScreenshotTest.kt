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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mifosx.openbanking.core.designsystem.theme.MifosXOpenBankingTheme
import org.mifosx.openbanking.core.model.banking.payment.PaymentRail
import org.mifosx.openbanking.feature.sendmoney.ui.SendMoneyAmountProblem
import org.mifosx.openbanking.feature.sendmoney.ui.SendMoneyErrorKind
import org.mifosx.openbanking.feature.sendmoney.ui.SendMoneyState
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val ROBOLECTRIC_SDK = 34

/**
 * A tall device, because the form scrolls well past a phone screen.
 *
 * This is a Robolectric qualifier rather than a Surface size: Roborazzi captures the composition
 * root, which Robolectric sizes from the device, so constraining the Surface changes nothing about
 * what lands in the image. At the default height the visible region stopped inside the payer list,
 * which made the domestic and international goldens differ only by which toggle segment was
 * highlighted — every field the two rails actually disagree about was below the fold.
 */
private const val TALL_DEVICE = "w412dp-h1800dp"

/**
 * Golden-image coverage for [SendMoneyScreenContent], captured with Roborazzi under Robolectric's
 * native graphics.
 *
 * Goldens are **not tracked** — `recordRoborazziDemoDebug` writes them under
 * `build/outputs/roborazzi/` for local review. So this suite fails on a composition crash
 * and lets a change be eyeballed, but `verifyRoborazziDemoDebug` can only detect drift against a
 * baseline recorded on the same machine; a clean checkout has nothing to compare against.
 *
 * The two rails get separate goldens because they are not a styling variation — each asks for fields
 * the other refuses, so the difference between them is the thing most worth being able to see.
 *
 * These also guard the templated strings: a literal `%%` renders as `%%` in Compose Multiplatform
 * resources, and no tag or count assertion catches that.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(manifest = Config.NONE, sdk = [ROBOLECTRIC_SDK], qualifiers = TALL_DEVICE)
class SendMoneyScreenScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun domesticFormGolden() = capture("form_domestic", SendMoneyFixtures.formState())

    @Test
    fun internationalFormGolden() =
        capture("form_international", SendMoneyFixtures.formState(rail = PaymentRail.International))

    /** The payer question open: nothing chosen, the picker inviting a choice, no payees to list. */
    @Test
    fun noPayerChosenGolden() =
        capture("form_no_payer", SendMoneyFixtures.formState(debtorAccountId = null))

    /**
     * The picker expanded, which is the state no assertion can describe.
     *
     * A tag count proves the rows exist; only the image proves the collapsed card actually collapses
     * and that the accounts and the bank choice sit inside one bordered surface rather than three.
     */
    @Test
    fun expandedPayerPickerGolden() =
        capture("form_payer_expanded", SendMoneyFixtures.formState(payerPickerExpanded = true))

    /** A payee selected, so the ring and the check badge on the avatar row are in a golden. */
    @Test
    fun selectedPayeeGolden() = capture("form_payee_selected", SendMoneyFixtures.filledFormState())

    /** The recent payments under the form, one of each outcome the list can render. */
    @Test
    fun recentPaymentsGolden() = capture(
        "form_recent_payments",
        SendMoneyFixtures.formState(recentPayments = SendMoneyFixtures.paymentHistory()),
    )

    /** No saved payees: the avatar row survives, carrying only "Pay new". */
    @Test
    fun noSavedPayeesGolden() =
        capture("form_no_payees", SendMoneyFixtures.formState(beneficiaries = emptyList()))

    /**
     * The payee read that failed, which must not look like the golden above it.
     *
     * The two states are one boolean apart and were rendered identically, so the only way to check
     * they now read differently — and that "Pay new" survived the failure that makes it the only
     * route to a payment — is to put both in an image and look at them side by side.
     */
    @Test
    fun payeeLoadFailedGolden() =
        capture("form_payees_failed", SendMoneyFixtures.formState(payeesFailed = true))

    /**
     * The payee read in flight, which is the state no assertion can judge.
     *
     * A tag proves the shimmer is mounted. Only the image shows whether the placeholders are shaped
     * like the row that replaces them and whether the area is tall enough that the amount card
     * below does not jump when the real payees land — the reason the state was given a minimum
     * height at all. It also sits directly beside `form_no_payees`, which is the golden it must not
     * be mistakable for.
     */
    @Test
    fun payeeLoadInFlightGolden() =
        capture("form_payees_loading", SendMoneyFixtures.formState(payeesLoading = true))

    /**
     * An unpayable amount, where the message takes the balance line rather than being added below it.
     *
     * Worth a golden because the card must not grow taller as someone types — a card that reflows
     * under the caret is the sort of thing a tag assertion cannot see.
     */
    @Test
    fun amountProblemGolden() = capture(
        "form_amount_problem",
        SendMoneyFixtures.filledFormState(problem = SendMoneyAmountProblem.ExceedsAvailableBalance),
    )

    @Test
    fun nonSterlingPayerGolden() =
        capture("form_non_sterling_payer", SendMoneyFixtures.formState(debtorCurrency = "USD"))

    /**
     * The amount instructed in a currency that is not the payer's.
     *
     * The golden's purpose changed with the symbol. It existed to prove the mark on the figure was
     * `$` while the balance beneath stayed `£`; there is no mark now, so what it proves instead is
     * that the currency is stated **once**, by the control at the row's trailing edge, and that a
     * bare `850` is still legible with the `£` balance line and the conversion notice around it.
     */
    @Test
    fun instructedInAnotherCurrencyGolden() = capture(
        "form_instructed_currency",
        SendMoneyFixtures.filledInternationalFormState(instructedCurrency = "USD"),
    )

    /** The international review, whose extra row now reads in official terms. */
    @Test
    fun internationalReviewGolden() =
        capture("review_international", SendMoneyFixtures.reviewState(rail = PaymentRail.International))

    @Test
    fun manualIbanEntryGolden() = capture(
        "form_manual_iban",
        SendMoneyFixtures.formState(rail = PaymentRail.International, manualEntryVisible = true),
    )

    @Test
    fun reviewGolden() = capture("review", SendMoneyFixtures.reviewState())

    @Test
    fun loadingGolden() = capture("loading", SendMoneyFixtures.loadingState())

    @Test
    fun submittingGolden() = capture("submitting", SendMoneyFixtures.submittingState())

    /** The payer refusal, whose whole point is that it offers a different account and not Retry. */
    @Test
    fun payerNotSupportedGolden() =
        capture("error_payer", SendMoneyFixtures.errorState(SendMoneyErrorKind.PayerNotSupported))

    private fun capture(state: String, screenState: SendMoneyState) {
        composeRule.setContent {
            // The app's theme, not a bare KptTheme: that leaves MaterialTheme at its own defaults
            // and every golden renders in Material's baseline purple rather than the app palette.
            MifosXOpenBankingTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    SendMoneyScreenContent(state = screenState, onAction = {}, onNavigateToConsents = {})
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/send_money_$state.png")
    }
}
