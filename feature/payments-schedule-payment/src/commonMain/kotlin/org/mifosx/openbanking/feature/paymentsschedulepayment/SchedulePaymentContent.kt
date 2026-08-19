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
import org.mifosx.openbanking.core.ui.components.MifosFilledPillButton
import org.mifosx.openbanking.core.ui.components.MifosTonalPillButton
import org.mifosx.openbanking.feature.paymentsschedulepayment.components.DateField
import org.mifosx.openbanking.feature.paymentsschedulepayment.components.ExecutionDatePickerDialog
import org.mifosx.openbanking.feature.paymentsschedulepayment.components.RailToggle
import org.mifosx.openbanking.feature.paymentsschedulepayment.components.SchedulePaymentAmountCard
import org.mifosx.openbanking.feature.paymentsschedulepayment.components.SchedulePaymentDropdownBox
import org.mifosx.openbanking.feature.paymentsschedulepayment.components.SchedulePaymentDropdownField
import org.mifosx.openbanking.feature.paymentsschedulepayment.components.SchedulePaymentPayeeAvatarRow
import org.mifosx.openbanking.feature.paymentsschedulepayment.components.SchedulePaymentPayerPicker
import org.mifosx.openbanking.feature.paymentsschedulepayment.components.chargeBearerLabel
import org.mifosx.openbanking.feature.paymentsschedulepayment.components.currencyName
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.Res
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_amount_error_exceeds_balance
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_amount_error_not_a_number
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_amount_error_not_positive
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_amount_error_too_many_decimals
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_charges_caveat
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_charges_heading
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_creditor_heading
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_date_heading
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_debtor_heading
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_form_trust_note
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_manual_account_number
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_manual_account_number_error
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_manual_confirm
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_manual_iban
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_manual_iban_error
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_manual_name
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_manual_sort_code
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_manual_sort_code_error
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_no_saved_payees_body
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_no_saved_payees_title
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_non_gbp_notice
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_payee_needs_payer
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_payees_failed_body
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_payees_failed_title
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_payees_loading_a11y
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_reference_helper
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_reference_label
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_retry
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_review_button
import org.mifosx.openbanking.feature.paymentsschedulepayment.generated.resources.feature_payments_schedule_payment_selected_a11y
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.OFFERED_CHARGE_BEARERS
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.SchedulePaymentAction
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.SchedulePaymentAmountProblem
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.SchedulePaymentStep
import org.mifosx.openbanking.feature.paymentsschedulepayment.ui.SchedulePaymentUiState
import template.core.base.designsystem.theme.KptTheme

private const val REFERENCE_MAX_LENGTH = 35

/**
 * The form and its review, as two pages of one state.
 *
 * A step field rather than two screen states: what the customer chose stays live across the pages,
 * and coming back from a failure must not discard it.
 */
@Composable
internal fun SchedulePaymentContent(
    state: SchedulePaymentUiState.Content,
    onAction: (SchedulePaymentAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state.step) {
        SchedulePaymentStep.Form -> SchedulePaymentFormPage(state, onAction, modifier)
        SchedulePaymentStep.Review -> SchedulePaymentReviewPage(state, onAction, modifier)
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
private fun SchedulePaymentFormPage(
    state: SchedulePaymentUiState.Content,
    onAction: (SchedulePaymentAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().testTag(SchedulePaymentTestTags.FORM_PAGE),
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
                RailToggle(
                    rail = state.rail,
                    onSelect = { onAction(SchedulePaymentAction.SelectRail(it)) },
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
    if (state.datePickerVisible) {
        ExecutionDatePickerDialog(
            today = state.today,
            rail = state.rail,
            selected = state.executionDate,
            onSelect = { onAction(SchedulePaymentAction.SelectExecutionDate(it)) },
            onDismiss = { onAction(SchedulePaymentAction.DismissDatePicker) },
        )
    }
}

@Composable
private fun DateSection(
    state: SchedulePaymentUiState.Content,
    onAction: (SchedulePaymentAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(HeadingGap)) {
        SectionHeading(stringResource(Res.string.feature_payments_schedule_payment_date_heading))
        DateField(
            dateLabel = state.executionDateLabel,
            rail = state.rail,
            onClick = { onAction(SchedulePaymentAction.OpenDatePicker) },
        )
    }
}

@Composable
private fun PayerSection(
    state: SchedulePaymentUiState.Content,
    onAction: (SchedulePaymentAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(HeadingGap)) {
        SectionHeading(stringResource(Res.string.feature_payments_schedule_payment_debtor_heading))
        SchedulePaymentPayerPicker(
            rows = state.debtorRows,
            selectedId = state.debtorAccountId,
            letBankChoose = state.letBankChoosePayer,
            expanded = state.payerPickerExpanded,
            onToggle = { onAction(SchedulePaymentAction.TogglePayerPicker) },
            onSelect = { onAction(SchedulePaymentAction.SelectDebtorAccount(it)) },
            onLetBankChoose = { onAction(SchedulePaymentAction.LetBankChoosePayer) },
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
            .testTag(SchedulePaymentTestTags.NON_GBP_NOTICE)
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
            text = stringResource(Res.string.feature_payments_schedule_payment_non_gbp_notice, instructedCurrency),
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
    state: SchedulePaymentUiState.Content,
    onAction: (SchedulePaymentAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(HeadingGap)) {
        SectionHeading(stringResource(Res.string.feature_payments_schedule_payment_creditor_heading))
        SchedulePaymentPayeeAvatarRow(
            // Empty whenever there is no payer, whatever the list happens to hold. Beneficiaries are
            // an account-scoped resource: with no account they are not "not loaded yet", they are
            // the previous account's, and showing them under a payer that no longer exists is the
            // defect this guard closes.
            payees = if (state.payeesUnavailable) emptyList() else state.beneficiaries,
            selectedId = state.creditor?.identification,
            selectedLabel = stringResource(Res.string.feature_payments_schedule_payment_selected_a11y),
            onSelect = { onAction(SchedulePaymentAction.SelectCreditor(it)) },
            onPayNew = { onAction(SchedulePaymentAction.ShowManualCreditorEntry) },
            modifier = Modifier.testTag(SchedulePaymentTestTags.CREDITOR_LIST),
            loading = state.payeesLoading,
            loadingContentDescription = stringResource(
                Res.string.feature_payments_schedule_payment_payees_loading_a11y,
            ),
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
    state: SchedulePaymentUiState.Content,
    onAction: (SchedulePaymentAction) -> Unit,
) {
    // The bar itself spans the window so the surface behind it is unbroken; only its contents are
    // held to the form's width, which is what keeps the CTA above the fields it acts on.
    Column(
        modifier = Modifier
            .testTag(SchedulePaymentTestTags.FORM_ACTIONS)
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
                label = stringResource(Res.string.feature_payments_schedule_payment_review_button),
                onClick = { onAction(SchedulePaymentAction.ReviewPayment) },
                testTag = SchedulePaymentTestTags.REVIEW_BUTTON,
                enabled = state.canReview,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(NoticeGap),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.testTag(SchedulePaymentTestTags.FORM_TRUST_NOTE),
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = KptTheme.colorScheme.outline,
                    modifier = Modifier.size(NoticeIconSize),
                )
                Text(
                    text = stringResource(Res.string.feature_payments_schedule_payment_form_trust_note),
                    style = KptTheme.typography.bodySmall,
                    color = KptTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun ManualCreditorFields(
    state: SchedulePaymentUiState.Content,
    onAction: (SchedulePaymentAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(RowGap)) {
        OutlinedTextField(
            value = state.manualName,
            onValueChange = { onAction(SchedulePaymentAction.EnterManualName(it)) },
            label = { Text(stringResource(Res.string.feature_payments_schedule_payment_manual_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(SchedulePaymentTestTags.MANUAL_NAME),
        )
        // The rails identify a creditor differently — sort code and account number against an IBAN —
        // and each refuses the other's scheme with U027, so only one set is ever offered.
        if (state.rail == PaymentRail.Domestic) {
            OutlinedTextField(
                value = state.manualSortCode,
                onValueChange = { onAction(SchedulePaymentAction.EnterManualSortCode(it)) },
                label = { Text(stringResource(Res.string.feature_payments_schedule_payment_manual_sort_code)) },
                isError = state.fieldErrors.sortCodeInvalid,
                supportingText = {
                    if (state.fieldErrors.sortCodeInvalid) {
                        Text(stringResource(Res.string.feature_payments_schedule_payment_manual_sort_code_error))
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(SchedulePaymentTestTags.MANUAL_SORT_CODE),
            )
            OutlinedTextField(
                value = state.manualAccountNumber,
                onValueChange = { onAction(SchedulePaymentAction.EnterManualAccountNumber(it)) },
                label = { Text(stringResource(Res.string.feature_payments_schedule_payment_manual_account_number)) },
                isError = state.fieldErrors.accountNumberInvalid,
                supportingText = {
                    if (state.fieldErrors.accountNumberInvalid) {
                        Text(stringResource(Res.string.feature_payments_schedule_payment_manual_account_number_error))
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(SchedulePaymentTestTags.MANUAL_ACCOUNT_NUMBER),
            )
        } else {
            OutlinedTextField(
                value = state.manualIban,
                onValueChange = { onAction(SchedulePaymentAction.EnterManualIban(it)) },
                label = { Text(stringResource(Res.string.feature_payments_schedule_payment_manual_iban)) },
                isError = state.fieldErrors.ibanInvalid,
                supportingText = {
                    if (state.fieldErrors.ibanInvalid) {
                        Text(
                            text = stringResource(Res.string.feature_payments_schedule_payment_manual_iban_error),
                            modifier = Modifier.testTag(SchedulePaymentTestTags.MANUAL_IBAN_ERROR),
                        )
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(SchedulePaymentTestTags.MANUAL_IBAN),
            )
        }
        MifosFilledPillButton(
            label = stringResource(Res.string.feature_payments_schedule_payment_manual_confirm),
            onClick = { onAction(SchedulePaymentAction.ConfirmManualCreditor) },
            testTag = SchedulePaymentTestTags.MANUAL_CONFIRM,
        )
    }
}

@Composable
private fun AmountSection(
    state: SchedulePaymentUiState.Content,
    onAction: (SchedulePaymentAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SectionGap)) {
        SchedulePaymentAmountCard(
            amount = state.amountInput,
            balanceLabel = state.availableBalanceLabel,
            errorMessage = state.amountProblem?.let { stringResource(it.messageResource()) },
            onAmountChange = { onAction(SchedulePaymentAction.EnterAmount(it)) },
        ) {
            // Both rails fill the slot, so the figure begins at the same x-position on each and
            // switching rails does not shift it. What differs is only whether the box can be
            // opened: domestically sterling is the only option there is to open it onto.
            when (state.rail) {
                PaymentRail.Domestic -> StaticCurrencyBox(state.instructedCurrency)
                PaymentRail.International -> InstructedCurrencyControl(state, onAction)
            }
        }

        // Domestic only. International refuses RemittanceInformation outright with U005, so showing
        // the field there would invite someone to type a reference the recipient never sees.
        if (state.rail == PaymentRail.Domestic) {
            OutlinedTextField(
                value = state.reference,
                onValueChange = { onAction(SchedulePaymentAction.EnterReference(it.take(REFERENCE_MAX_LENGTH))) },
                label = { Text(stringResource(Res.string.feature_payments_schedule_payment_reference_label)) },
                supportingText = {
                    Text(stringResource(Res.string.feature_payments_schedule_payment_reference_helper))
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(SchedulePaymentTestTags.REFERENCE_FIELD),
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
    state: SchedulePaymentUiState.Content,
    onAction: (SchedulePaymentAction) -> Unit,
) {
    SchedulePaymentDropdownBox(
        label = state.instructedCurrency,
        selected = state.instructedCurrency,
        options = state.offeredCurrencies,
        optionLabel = { currencyName(it) },
        optionTestTag = SchedulePaymentTestTags::instructedCurrencyOption,
        onSelect = { onAction(SchedulePaymentAction.SelectInstructedCurrency(it)) },
        modifier = Modifier.fillMaxSize().testTag(SchedulePaymentTestTags.INSTRUCTED_CURRENCY_PICKER),
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
            .testTag(SchedulePaymentTestTags.STATIC_CURRENCY_BOX)
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
    state: SchedulePaymentUiState.Content,
    onAction: (SchedulePaymentAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(HeadingGap)) {
        SectionHeading(stringResource(Res.string.feature_payments_schedule_payment_charges_heading))
        SchedulePaymentDropdownField(
            label = chargeBearerLabel(state.chargeBearer),
            selected = state.chargeBearer,
            options = OFFERED_CHARGE_BEARERS,
            optionLabel = { chargeBearerLabel(it) },
            optionTestTag = SchedulePaymentTestTags::chargeBearerOption,
            onSelect = { onAction(SchedulePaymentAction.SelectChargeBearer(it)) },
            modifier = Modifier.testTag(SchedulePaymentTestTags.CHARGE_BEARER_PICKER),
        )
        // Says what the app cannot: this rail declares no charge until the payment resource exists,
        // which is after the customer has authorised. Without this line the row reads as though the
        // fee were settled here.
        Text(
            text = stringResource(Res.string.feature_payments_schedule_payment_charges_caveat),
            style = KptTheme.typography.bodySmall,
            color = KptTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(SchedulePaymentTestTags.CHARGES_ROW),
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
private fun PayeesFailedNotice(onAction: (SchedulePaymentAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SchedulePaymentTestTags.PAYEES_FAILED)
            .clip(RoundedCornerShape(NoticeCorner))
            .background(KptTheme.colorScheme.surfaceContainer)
            .padding(NoticePadding),
        verticalArrangement = Arrangement.spacedBy(HeadingGap),
    ) {
        Text(
            text = stringResource(Res.string.feature_payments_schedule_payment_payees_failed_title),
            style = KptTheme.typography.titleSmall,
            color = KptTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(Res.string.feature_payments_schedule_payment_payees_failed_body),
            style = KptTheme.typography.bodySmall,
            color = KptTheme.colorScheme.onSurfaceVariant,
        )
        MifosTonalPillButton(
            label = stringResource(Res.string.feature_payments_schedule_payment_retry),
            onClick = { onAction(SchedulePaymentAction.RetryPayees) },
            testTag = SchedulePaymentTestTags.PAYEES_RETRY_BUTTON,
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
            .testTag(SchedulePaymentTestTags.PAYEE_NEEDS_PAYER)
            .clip(RoundedCornerShape(NoticeCorner))
            .background(KptTheme.colorScheme.surfaceContainer)
            .padding(NoticePadding),
        verticalArrangement = Arrangement.spacedBy(HeroGap),
    ) {
        Text(
            text = stringResource(Res.string.feature_payments_schedule_payment_payee_needs_payer),
            style = KptTheme.typography.bodyMedium,
            color = KptTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NoSavedPayees() {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(SchedulePaymentTestTags.NO_SAVED_PAYEES),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HeadingGap),
    ) {
        Text(
            text = stringResource(Res.string.feature_payments_schedule_payment_no_saved_payees_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(Res.string.feature_payments_schedule_payment_no_saved_payees_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun SchedulePaymentAmountProblem.messageResource(): StringResource = when (this) {
    SchedulePaymentAmountProblem.NotANumber -> Res.string.feature_payments_schedule_payment_amount_error_not_a_number
    SchedulePaymentAmountProblem.TooManyDecimals ->
        Res.string.feature_payments_schedule_payment_amount_error_too_many_decimals
    SchedulePaymentAmountProblem.NotPositive -> Res.string.feature_payments_schedule_payment_amount_error_not_positive
    SchedulePaymentAmountProblem.ExceedsAvailableBalance ->
        Res.string.feature_payments_schedule_payment_amount_error_exceeds_balance
}
