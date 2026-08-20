/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.paymentsstandingorder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.mifosx.openbanking.core.common.currencySymbol
import org.mifosx.openbanking.core.model.banking.payment.PaymentRail
import org.mifosx.openbanking.core.ui.account.MifosAccountOption
import org.mifosx.openbanking.core.ui.account.MifosAccountPicker
import org.mifosx.openbanking.core.ui.account.MifosBankChoiceRow
import org.mifosx.openbanking.core.ui.components.MifosAmountCard
import org.mifosx.openbanking.core.ui.components.MifosDropdownBox
import org.mifosx.openbanking.core.ui.components.MifosDropdownField
import org.mifosx.openbanking.core.ui.components.MifosFilledPillButton
import org.mifosx.openbanking.core.ui.components.MifosRailToggle
import org.mifosx.openbanking.core.ui.components.MifosTonalPillButton
import org.mifosx.openbanking.core.ui.generated.resources.core_ui_account_picker_bank_choice
import org.mifosx.openbanking.core.ui.generated.resources.core_ui_account_picker_bank_choice_supporting
import org.mifosx.openbanking.core.ui.payee.MifosPayeeAvatarRow
import org.mifosx.openbanking.core.ui.payee.MifosPayeeOption
import org.mifosx.openbanking.feature.paymentsstandingorder.components.DateField
import org.mifosx.openbanking.feature.paymentsstandingorder.components.FrequencyField
import org.mifosx.openbanking.feature.paymentsstandingorder.components.StandingOrderDatePickerDialog
import org.mifosx.openbanking.feature.paymentsstandingorder.components.chargeBearerLabel
import org.mifosx.openbanking.feature.paymentsstandingorder.components.currencyName
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.Res
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_amount_error_exceeds_balance
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_amount_error_not_a_number
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_amount_error_not_positive
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_amount_error_too_many_decimals
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_amount_placeholder
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_charges_caveat
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_charges_heading
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_creditor_heading
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_date_heading
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_debtor_heading
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_final_amount_label
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_final_payment_empty
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_final_payment_helper
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_final_payment_label
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_final_payment_needs_first
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_first_payment_empty
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_first_payment_helper
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_first_payment_label
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_form_trust_note
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_manual_account_number
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_manual_account_number_error
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_manual_confirm
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_manual_iban
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_manual_iban_error
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_manual_name
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_manual_sort_code
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_manual_sort_code_error
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_no_saved_payees_body
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_no_saved_payees_title
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_non_gbp_notice
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_payee_needs_payer
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_payees_failed_body
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_payees_failed_title
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_payees_loading_a11y
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_recurring_amount_label
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_reference_helper
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_reference_label
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_retry
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_review_button
import org.mifosx.openbanking.feature.paymentsstandingorder.ui.OFFERED_CHARGE_BEARERS
import org.mifosx.openbanking.feature.paymentsstandingorder.ui.StandingOrderAccountRow
import org.mifosx.openbanking.feature.paymentsstandingorder.ui.StandingOrderAction
import org.mifosx.openbanking.feature.paymentsstandingorder.ui.StandingOrderAmountProblem
import org.mifosx.openbanking.feature.paymentsstandingorder.ui.StandingOrderDateRole
import org.mifosx.openbanking.feature.paymentsstandingorder.ui.StandingOrderPickerRow
import org.mifosx.openbanking.feature.paymentsstandingorder.ui.StandingOrderStep
import org.mifosx.openbanking.feature.paymentsstandingorder.ui.StandingOrderUiState
import template.core.base.designsystem.theme.KptTheme
import org.mifosx.openbanking.core.ui.generated.resources.Res as CoreRes

private const val REFERENCE_MAX_LENGTH = 35

/**
 * The form and its review, as two pages of one state.
 *
 * A step field rather than two screen states: what the customer chose stays live across the pages,
 * and coming back from a failure must not discard it.
 */
@Composable
internal fun StandingOrderContent(
    state: StandingOrderUiState.Content,
    onAction: (StandingOrderAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state.step) {
        StandingOrderStep.Form -> StandingOrderFormPage(state, onAction, modifier)
        StandingOrderStep.Review -> StandingOrderReviewPage(state, onAction, modifier)
    }
}

/**
 * Payer, payee and amount on one scroll, with the action pinned below it.
 *
 * The three used to be separate steps. Nothing about them needed sequencing — none of the three
 * constrains what the others may be — so walking the customer through them only hid how short the
 * form actually is. Pinning the action matters once they are combined: the page is now long enough
 * that a button at the end of the scroll would sit off screen on a phone.
 */
@Composable
private fun StandingOrderFormPage(
    state: StandingOrderUiState.Content,
    onAction: (StandingOrderAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().testTag(StandingOrderTestTags.FORM_PAGE),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = FormMaxWidth)
                    .fillMaxWidth()
                    .padding(ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(SectionGap),
            ) {
                MifosRailToggle(
                    rail = state.rail,
                    onSelect = { onAction(StandingOrderAction.SelectRail(it)) },
                    modifier = Modifier.testTag(StandingOrderTestTags.RAIL_TOGGLE),
                    optionTestTag = StandingOrderTestTags::railOption,
                )
                PayerSection(state, onAction)
                PayeeSection(state, onAction)
                // Before the amount, and after the payee, exactly as the mockups place it. The date
                // is what makes this a different journey from sending money now, so it sits in the
                // flow rather than tucked under the amount as an afterthought.
                DateSection(state, onAction)
                AmountSection(state, onAction)
            }
        }
        FormActions(state, onAction)
    }

    // The picker has no route of its own — it is a dialog over the form, so nothing about it belongs
    // in the back stack. Rendered here rather than inside DateSection so the calendar is not nested
    // in the scrolling column that opened it.
    state.datePickerRole?.let { role ->
        StandingOrderDatePickerDialog(
            today = state.today,
            role = role,
            firstPaymentDate = state.firstPaymentDate,
            selected = if (role == StandingOrderDateRole.First) {
                state.firstPaymentDate
            } else {
                state.finalPaymentDate
            },
            onSelect = { onAction(StandingOrderAction.SelectDate(role, it)) },
            onDismiss = { onAction(StandingOrderAction.DismissDatePicker) },
        )
    }
}

@Composable
private fun DateSection(
    state: StandingOrderUiState.Content,
    onAction: (StandingOrderAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(HeadingGap)) {
        SectionHeading(stringResource(Res.string.feature_payments_standing_order_date_heading))
        FrequencyField(
            selected = state.frequency,
            onSelect = { onAction(StandingOrderAction.SelectFrequency(it)) },
        )
        DateField(
            label = stringResource(Res.string.feature_payments_standing_order_first_payment_label),
            dateLabel = state.firstPaymentDateLabel,
            placeholder = stringResource(Res.string.feature_payments_standing_order_first_payment_empty),
            helper = stringResource(Res.string.feature_payments_standing_order_first_payment_helper),
            testTag = StandingOrderTestTags.FIRST_DATE_FIELD,
            onClick = { onAction(StandingOrderAction.OpenDatePicker(StandingOrderDateRole.First)) },
        )
        // Empty is a meaningful value here — it means the mandate runs until the customer stops it —
        // so this field carries a clear affordance where the first one has none.
        DateField(
            label = stringResource(Res.string.feature_payments_standing_order_final_payment_label),
            dateLabel = state.finalPaymentDateLabel,
            placeholder = stringResource(Res.string.feature_payments_standing_order_final_payment_empty),
            helper = if (state.firstPaymentDate == null) {
                stringResource(Res.string.feature_payments_standing_order_final_payment_needs_first)
            } else {
                stringResource(Res.string.feature_payments_standing_order_final_payment_helper)
            },
            testTag = StandingOrderTestTags.FINAL_DATE_FIELD,
            enabled = state.firstPaymentDate != null,
            onClear = { onAction(StandingOrderAction.ClearFinalDate) }.takeIf { !state.isOpenEnded },
            onClick = { onAction(StandingOrderAction.OpenDatePicker(StandingOrderDateRole.Final)) },
        )
    }
}

@Composable
private fun PayerSection(
    state: StandingOrderUiState.Content,
    onAction: (StandingOrderAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(HeadingGap)) {
        SectionHeading(stringResource(Res.string.feature_payments_standing_order_debtor_heading))
        MifosAccountPicker(
            options = state.debtorRows.map { it.toPickerOption() },
            selectedId = state.debtorAccountId,
            expanded = state.payerPickerExpanded,
            onToggle = { onAction(StandingOrderAction.TogglePayerPicker) },
            onSelect = { onAction(StandingOrderAction.SelectDebtorAccount(it)) },
            modifier = Modifier.testTag(StandingOrderTestTags.PAYER_PICKER),
            unselectedLabel = if (state.letBankChoosePayer) {
                stringResource(CoreRes.string.core_ui_account_picker_bank_choice)
            } else {
                null
            },
            unselectedSupporting = if (state.letBankChoosePayer) {
                stringResource(CoreRes.string.core_ui_account_picker_bank_choice_supporting)
            } else {
                ""
            },
            extraOptions = {
                MifosBankChoiceRow(
                    selected = state.letBankChoosePayer,
                    onClick = { onAction(StandingOrderAction.LetBankChoosePayer) },
                    testTag = StandingOrderTestTags.PAYER_BANK_CHOICE,
                )
            },
        )
        if (state.showsConversionAdvisory) {
            ConversionNotice(instructedCurrency = state.instructedCurrency)
        }
    }
}

/** The account holds one currency and the amount is instructed in another, so say so up front. */
@Composable
private fun ConversionNotice(instructedCurrency: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(StandingOrderTestTags.NON_GBP_NOTICE)
            .clip(RoundedCornerShape(NoticeCorner))
            .background(KptTheme.colorScheme.surfaceContainer)
            .padding(NoticePadding),
        horizontalArrangement = Arrangement.spacedBy(NoticeGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            tint = KptTheme.colorScheme.outline,
            modifier = Modifier.size(NoticeIconSize),
        )
        Text(
            text = stringResource(Res.string.feature_payments_standing_order_non_gbp_notice, instructedCurrency),
            style = KptTheme.typography.bodySmall,
            color = KptTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The payees, and the way in for someone who is not one.
 *
 * The avatar row is rendered in every case, including when there are none to show, because its first
 * item is "Pay new" — the only route to paying an unsaved account. Dropping the row when the list is
 * empty would take that away exactly when it is needed.
 *
 * Four cases with no list to show, and they are not interchangeable: no payer chosen yet, a read
 * still in flight, a read that failed, or a payer with nothing saved against it. Two of them used to
 * render as the last — so a bank refusing the beneficiaries endpoint read as the customer having no
 * payees, and so did the entire window between choosing a payer and the answer arriving.
 *
 * The order of the arms is the ordering of causes, not a preference. "No payer" comes first because
 * with no payer nothing is in flight to be loading. Loading comes before "none saved" because "you
 * have no saved payees" is a claim about the account that cannot be made until the bank has
 * answered — which is the whole defect, and it returns the moment these two are swapped.
 */
@Composable
private fun PayeeSection(
    state: StandingOrderUiState.Content,
    onAction: (StandingOrderAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(HeadingGap)) {
        SectionHeading(stringResource(Res.string.feature_payments_standing_order_creditor_heading))
        MifosPayeeAvatarRow(
            // Empty whenever there is no payer, whatever the list happens to hold. Beneficiaries are
            // an account-scoped resource: with no account they are not "not loaded yet", they are
            // the previous account's, and showing them under a payer that no longer exists is the
            // defect this guard closes.
            payees = if (state.payeesUnavailable) {
                emptyList()
            } else {
                state.beneficiaries.map { it.toPayeeOption() }
            },
            selectedId = state.creditor?.identification,
            payNewSelected = state.manualEntryVisible,
            onSelect = { onAction(StandingOrderAction.SelectCreditor(it)) },
            onPayNew = { onAction(StandingOrderAction.ShowManualCreditorEntry) },
            modifier = Modifier.testTag(StandingOrderTestTags.CREDITOR_LIST),
            loading = state.payeesLoading,
            loadingContentDescription = stringResource(
                Res.string.feature_payments_standing_order_payees_loading_a11y,
            ),
            payNewTestTag = StandingOrderTestTags.MANUAL_ENTRY_BUTTON,
            loadingTestTag = StandingOrderTestTags.PAYEES_LOADING,
        )
        when {
            // Beneficiaries are saved per account, so without one there is no list to read.
            // Saying so beats showing someone else's payees or an empty row that looks broken.
            state.payeesUnavailable -> ChoosePayerFirstNotice()
            state.payeesLoading -> Unit
            state.payeesFailed -> PayeesFailedNotice(onAction)
            !state.hasBeneficiaries -> NoSavedPayees()
        }

        if (state.manualEntryVisible) {
            ManualCreditorFields(state, onAction)
        }
    }
}

/** The action bar, pinned so it stays reachable however far the form has been scrolled. */
@Composable
private fun FormActions(
    state: StandingOrderUiState.Content,
    onAction: (StandingOrderAction) -> Unit,
) {
    // The bar itself spans the window so the surface behind it is unbroken; only its contents are
    // held to the form's width, which is what keeps the CTA above the fields it acts on.
    Column(
        modifier = Modifier
            .testTag(StandingOrderTestTags.FORM_ACTIONS)
            .fillMaxWidth()
            .background(KptTheme.colorScheme.surface),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = FormMaxWidth)
                .fillMaxWidth()
                .padding(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(HeroGap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MifosFilledPillButton(
                label = stringResource(Res.string.feature_payments_standing_order_review_button),
                onClick = { onAction(StandingOrderAction.ReviewStandingOrder) },
                testTag = StandingOrderTestTags.REVIEW_BUTTON,
                enabled = state.canReview,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(NoticeGap),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.testTag(StandingOrderTestTags.FORM_TRUST_NOTE),
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = KptTheme.colorScheme.outline,
                    modifier = Modifier.size(NoticeIconSize),
                )
                Text(
                    text = stringResource(Res.string.feature_payments_standing_order_form_trust_note),
                    style = KptTheme.typography.bodySmall,
                    color = KptTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun ManualCreditorFields(
    state: StandingOrderUiState.Content,
    onAction: (StandingOrderAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(RowGap)) {
        OutlinedTextField(
            value = state.manualName,
            onValueChange = { onAction(StandingOrderAction.EnterManualName(it)) },
            label = { Text(stringResource(Res.string.feature_payments_standing_order_manual_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(StandingOrderTestTags.MANUAL_NAME),
        )
        // The rails identify a creditor differently — sort code and account number against an IBAN —
        // and each refuses the other's scheme with U027, so only one set is ever offered.
        if (state.rail == PaymentRail.Domestic) {
            OutlinedTextField(
                value = state.manualSortCode,
                onValueChange = { onAction(StandingOrderAction.EnterManualSortCode(it)) },
                label = { Text(stringResource(Res.string.feature_payments_standing_order_manual_sort_code)) },
                isError = state.fieldErrors.sortCodeInvalid,
                supportingText = {
                    if (state.fieldErrors.sortCodeInvalid) {
                        Text(stringResource(Res.string.feature_payments_standing_order_manual_sort_code_error))
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(StandingOrderTestTags.MANUAL_SORT_CODE),
            )
            OutlinedTextField(
                value = state.manualAccountNumber,
                onValueChange = { onAction(StandingOrderAction.EnterManualAccountNumber(it)) },
                label = { Text(stringResource(Res.string.feature_payments_standing_order_manual_account_number)) },
                isError = state.fieldErrors.accountNumberInvalid,
                supportingText = {
                    if (state.fieldErrors.accountNumberInvalid) {
                        Text(stringResource(Res.string.feature_payments_standing_order_manual_account_number_error))
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(StandingOrderTestTags.MANUAL_ACCOUNT_NUMBER),
            )
        } else {
            OutlinedTextField(
                value = state.manualIban,
                onValueChange = { onAction(StandingOrderAction.EnterManualIban(it)) },
                label = { Text(stringResource(Res.string.feature_payments_standing_order_manual_iban)) },
                isError = state.fieldErrors.ibanInvalid,
                supportingText = {
                    if (state.fieldErrors.ibanInvalid) {
                        Text(
                            text = stringResource(Res.string.feature_payments_standing_order_manual_iban_error),
                            modifier = Modifier.testTag(StandingOrderTestTags.MANUAL_IBAN_ERROR),
                        )
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(StandingOrderTestTags.MANUAL_IBAN),
            )
        }
        MifosFilledPillButton(
            label = stringResource(Res.string.feature_payments_standing_order_manual_confirm),
            onClick = { onAction(StandingOrderAction.ConfirmManualCreditor) },
            testTag = StandingOrderTestTags.MANUAL_CONFIRM,
        )
    }
}

@Composable
private fun AmountSection(
    state: StandingOrderUiState.Content,
    onAction: (StandingOrderAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SectionGap)) {
        MifosAmountCard(
            amount = state.amountInput,
            balanceLabel = state.availableBalanceLabel,
            errorMessage = state.amountProblem?.let { stringResource(it.messageResource()) },
            onAmountChange = { onAction(StandingOrderAction.EnterAmount(it)) },
            modifier = Modifier.testTag(StandingOrderTestTags.AMOUNT_CARD),
            fieldTestTag = StandingOrderTestTags.AMOUNT_FIELD,
            errorTestTag = StandingOrderTestTags.AMOUNT_ERROR,
            balanceTestTag = StandingOrderTestTags.AMOUNT_BALANCE,
        ) {
            // Both rails fill the slot, so the figure begins at the same x-position on each and
            // switching rails does not shift it. What differs is only whether the box can be
            // opened: domestically sterling is the only option there is to open it onto.
            when (state.rail) {
                PaymentRail.Domestic -> StaticCurrencyBox(state.instructedCurrency)
                PaymentRail.International -> InstructedCurrencyControl(state, onAction)
            }
        }

        // The three fields only the domestic rail has wire members for, shown only on that rail.
        //
        // The two amounts are overrides on the figure above rather than fields of their own: OBIE asks
        // for the recurring and final amounts only where they differ from the first, and HSBC accepts
        // three unequal figures (1.00/2.00/3.00 → 201). The reference maps to `RemittanceInformation`,
        // which the international rail refuses outright with U005.
        //
        // Hidden rather than disabled on international. Note what that costs: a test asserting these
        // are absent passes just as happily against fields that were never built, which is exactly how
        // the two amount overrides went missing unnoticed once already. The guard is on the other side
        // — the domestic cases assert they are present and enabled, so a field that stops rendering
        // fails there instead.
        if (state.recurringAmountEnabled) {
            OptionalAmountField(
                label = stringResource(Res.string.feature_payments_standing_order_recurring_amount_label),
                value = state.recurringAmountInput,
                fieldTag = StandingOrderTestTags.RECURRING_AMOUNT_FIELD,
                onValueChange = { onAction(StandingOrderAction.EnterRecurringAmount(it)) },
            )
        }
        if (state.finalAmountEnabled) {
            OptionalAmountField(
                label = stringResource(Res.string.feature_payments_standing_order_final_amount_label),
                value = state.finalAmountInput,
                fieldTag = StandingOrderTestTags.FINAL_AMOUNT_FIELD,
                onValueChange = { onAction(StandingOrderAction.EnterFinalAmount(it)) },
            )
        }
        if (state.referenceEnabled) {
            OutlinedTextField(
                value = state.reference,
                onValueChange = { onAction(StandingOrderAction.EnterReference(it.take(REFERENCE_MAX_LENGTH))) },
                label = { Text(stringResource(Res.string.feature_payments_standing_order_reference_label)) },
                supportingText = {
                    Text(stringResource(Res.string.feature_payments_standing_order_reference_helper))
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(StandingOrderTestTags.REFERENCE_FIELD),
            )
        }

        // International only, and required there: ChargeBearer is refused on the domestic rail, and
        // omitting it on the international one earns U004.
        if (state.rail == PaymentRail.International) {
            ChargesSection(state, onAction)
        }
    }
}

/**
 * One of the two optional amount overrides. Only ever composed on the rail that can carry it.
 *
 * Left empty rather than pre-filled with the amount above. An override that arrives pre-filled would
 * put `RecurringPaymentAmount` on the wire for every mandate, and OBIE asks for it only when it
 * differs — sending it always would state a difference that is not there.
 *
 * Nothing is cleared here when the rail changes. The ViewModel already empties both inputs on a
 * switch, and `buildDraft` reads them only on the domestic rail — so a value typed before a switch
 * cannot reach the wire even though this composable has simply stopped existing. That second
 * guarantee is the one the bank sees, and it does not depend on this field being rendered at all.
 */
@Composable
private fun OptionalAmountField(
    label: String,
    value: String,
    fieldTag: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(stringResource(Res.string.feature_payments_standing_order_amount_placeholder)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().testTag(fieldTag),
    )
}

/**
 * The currency, in the box at the amount row's LEADING edge.
 *
 * The code alone on the control and the currency spelled out in the menu: beside a 36sp figure the
 * unit is read as part of the number, so "US Dollar (USD)" there would compete with it — but a menu
 * of nineteen bare codes would ask the customer to already know them.
 *
 * This is now the form's ONLY currency control. There was a second, "Recipient receives", choosing
 * `CurrencyOfTransfer` independently; two selectors made a combination the bank refuses reachable
 * and asked one decision twice, so the transfer currency is derived from this one.
 */
@Composable
private fun InstructedCurrencyControl(
    state: StandingOrderUiState.Content,
    onAction: (StandingOrderAction) -> Unit,
) {
    MifosDropdownBox(
        label = state.instructedCurrency,
        selected = state.instructedCurrency,
        options = state.offeredCurrencies,
        optionLabel = { currencyName(it) },
        optionTestTag = StandingOrderTestTags::instructedCurrencyOption,
        onSelect = { onAction(StandingOrderAction.SelectInstructedCurrency(it)) },
        modifier = Modifier.fillMaxSize().testTag(StandingOrderTestTags.INSTRUCTED_CURRENCY_PICKER),
    )
}

/**
 * The domestic rail's currency: the same box, saying `£`, with nothing to open.
 *
 * The rail was previously given no control at all, on the reasoning that a dead control invites a
 * tap. That was right about the tap and wrong about the alternative it chose — it left the figure
 * with nothing on screen naming sterling, so the one rail where the currency is certain was the one
 * rail that never said what it was.
 *
 * So: shown, and genuinely inert. No `clickable` and no `Role`, which is what keeps it out of the
 * tab order and out of a screen reader's list of controls — it reads as the label it is. It is
 * drawn in `surfaceContainer` rather than the field's `surfaceContainerLowest` so that it is
 * visibly not the openable box its international counterpart is, while keeping that box's corner,
 * border and height so the figure does not move between the two.
 *
 * The symbol comes from [instructedCurrency] rather than a literal `£`: `selectRail` pins the
 * domestic rail to `DOMESTIC_CURRENCY`, so the two cannot disagree, and reading the state means a
 * future rail with a different fixed currency cannot render the wrong mark here.
 */
@Composable
private fun StaticCurrencyBox(instructedCurrency: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(StandingOrderTestTags.STATIC_CURRENCY_BOX)
            .clip(RoundedCornerShape(CardCorner))
            .border(
                width = CardBorder,
                color = KptTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(CardCorner),
            )
            .background(KptTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = currencySymbol(instructedCurrency),
            style = KptTheme.typography.titleMedium,
            color = KptTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * Who pays the charges — the one field an international payment carries that the amount row cannot.
 *
 * It was a row of `FilterChip`s that wrapped onto a second line and left one option stranded, and it
 * had a currency dropdown for company until that decision moved onto the amount itself.
 *
 * There is deliberately no "recipient receives" figure anywhere near this. Charges are deducted
 * downstream and `ExchangeRateInformation` is refused `U005`, so no rate can be quoted before
 * authorisation — and with one currency there is no second one to name.
 */
@Composable
private fun ChargesSection(
    state: StandingOrderUiState.Content,
    onAction: (StandingOrderAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(HeadingGap)) {
        SectionHeading(stringResource(Res.string.feature_payments_standing_order_charges_heading))
        // No caption: the section heading above already names this control.
        MifosDropdownField(
            label = chargeBearerLabel(state.chargeBearer),
            selected = state.chargeBearer,
            options = OFFERED_CHARGE_BEARERS,
            optionLabel = { chargeBearerLabel(it) },
            optionTestTag = StandingOrderTestTags::chargeBearerOption,
            onSelect = { onAction(StandingOrderAction.SelectChargeBearer(it)) },
            modifier = Modifier.testTag(StandingOrderTestTags.CHARGE_BEARER_PICKER),
        )
        // Says what the app cannot: this rail declares no charge until the payment resource exists,
        // which is after the customer has authorised. Without this line the row reads as though the
        // fee were settled here.
        Text(
            text = stringResource(Res.string.feature_payments_standing_order_charges_caveat),
            style = KptTheme.typography.bodySmall,
            color = KptTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(StandingOrderTestTags.CHARGES_ROW),
        )
    }
}

/**
 * The saved payees could not be read, which is not the same as there being none.
 *
 * Says the read failed and offers to run it again, and leaves "Pay new" above it untouched — with no
 * list, hand-keying the details is the only route to a payment, so it must survive the failure that
 * makes it necessary. The cause is deliberately not named: `403` here is indistinguishable from an
 * expired token by the time it reaches this state, and guessing at the bank's reason would put words
 * in its mouth.
 */
@Composable
private fun PayeesFailedNotice(onAction: (StandingOrderAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(StandingOrderTestTags.PAYEES_FAILED)
            .clip(RoundedCornerShape(NoticeCorner))
            .background(KptTheme.colorScheme.surfaceContainer)
            .padding(NoticePadding),
        verticalArrangement = Arrangement.spacedBy(HeadingGap),
    ) {
        Text(
            text = stringResource(Res.string.feature_payments_standing_order_payees_failed_title),
            style = KptTheme.typography.titleSmall,
            color = KptTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(Res.string.feature_payments_standing_order_payees_failed_body),
            style = KptTheme.typography.bodySmall,
            color = KptTheme.colorScheme.onSurfaceVariant,
        )
        MifosTonalPillButton(
            label = stringResource(Res.string.feature_payments_standing_order_retry),
            onClick = { onAction(StandingOrderAction.RetryPayees) },
            testTag = StandingOrderTestTags.PAYEES_RETRY_BUTTON,
        )
    }
}

/**
 * Why the payee list is empty when no payer has been chosen.
 *
 * Not an error and not an empty state: the list is account-scoped, so it genuinely cannot be
 * fetched yet. [NoSavedPayees] is the different case where an account was chosen and has none.
 */
@Composable
private fun ChoosePayerFirstNotice() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(StandingOrderTestTags.PAYEE_NEEDS_PAYER)
            .clip(RoundedCornerShape(NoticeCorner))
            .background(KptTheme.colorScheme.surfaceContainer)
            .padding(NoticePadding),
        verticalArrangement = Arrangement.spacedBy(HeroGap),
    ) {
        Text(
            text = stringResource(Res.string.feature_payments_standing_order_payee_needs_payer),
            style = KptTheme.typography.bodyMedium,
            color = KptTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NoSavedPayees() {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(StandingOrderTestTags.NO_SAVED_PAYEES),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HeadingGap),
    ) {
        Text(
            text = stringResource(Res.string.feature_payments_standing_order_no_saved_payees_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(Res.string.feature_payments_standing_order_no_saved_payees_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun StandingOrderAccountRow.toPickerOption(): MifosAccountOption = MifosAccountOption(
    accountId = id,
    accountSubType = accountSubType,
    accountNumber = accountNumber,
    rawIdentification = rawIdentification,
    availableBalance = supporting,
)

private fun StandingOrderPickerRow.toPayeeOption(): MifosPayeeOption = MifosPayeeOption(
    payeeId = id,
    shortName = shortName.ifBlank { headline },
    initials = initials,
)

private fun StandingOrderAmountProblem.messageResource(): StringResource = when (this) {
    StandingOrderAmountProblem.NotANumber -> Res.string.feature_payments_standing_order_amount_error_not_a_number
    StandingOrderAmountProblem.TooManyDecimals ->
        Res.string.feature_payments_standing_order_amount_error_too_many_decimals
    StandingOrderAmountProblem.NotPositive -> Res.string.feature_payments_standing_order_amount_error_not_positive
    StandingOrderAmountProblem.ExceedsAvailableBalance ->
        Res.string.feature_payments_standing_order_amount_error_exceeds_balance
}
