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
import org.mifosx.openbanking.core.ui.components.MifosAmountCard
import org.mifosx.openbanking.core.ui.components.MifosDropdownBox
import org.mifosx.openbanking.core.ui.components.MifosDropdownField
import org.mifosx.openbanking.core.ui.components.MifosRailToggle
import org.mifosx.openbanking.core.ui.account.MifosAccountOption
import org.mifosx.openbanking.core.ui.account.MifosAccountPicker
import org.mifosx.openbanking.core.ui.account.MifosBankChoiceRow
import org.mifosx.openbanking.core.ui.generated.resources.core_ui_account_picker_bank_choice
import org.mifosx.openbanking.core.ui.generated.resources.core_ui_account_picker_bank_choice_supporting
import org.mifosx.openbanking.core.ui.payee.MifosPayeeAvatarRow
import org.mifosx.openbanking.core.ui.payee.MifosPayeeOption
import org.mifosx.openbanking.feature.sendmoney.ui.SendMoneyAccountRow
import org.mifosx.openbanking.feature.sendmoney.ui.SendMoneyPickerRow
import org.mifosx.openbanking.core.ui.generated.resources.Res as CoreRes
import org.mifosx.openbanking.feature.sendmoney.components.chargeBearerLabel
import org.mifosx.openbanking.feature.sendmoney.components.currencyName
import org.mifosx.openbanking.feature.sendmoney.generated.resources.Res
import org.mifosx.openbanking.feature.sendmoney.generated.resources.feature_send_money_amount_error_exceeds_balance
import org.mifosx.openbanking.feature.sendmoney.generated.resources.feature_send_money_amount_error_not_a_number
import org.mifosx.openbanking.feature.sendmoney.generated.resources.feature_send_money_amount_error_not_positive
import org.mifosx.openbanking.feature.sendmoney.generated.resources.feature_send_money_amount_error_too_many_decimals
import org.mifosx.openbanking.feature.sendmoney.generated.resources.feature_send_money_charges_heading
import org.mifosx.openbanking.feature.sendmoney.generated.resources.feature_send_money_creditor_heading
import org.mifosx.openbanking.feature.sendmoney.generated.resources.feature_send_money_debtor_heading
import org.mifosx.openbanking.feature.sendmoney.generated.resources.feature_send_money_form_trust_note
import org.mifosx.openbanking.feature.sendmoney.generated.resources.feature_send_money_manual_account_number
import org.mifosx.openbanking.feature.sendmoney.generated.resources.feature_send_money_manual_account_number_error
import org.mifosx.openbanking.feature.sendmoney.generated.resources.feature_send_money_manual_confirm
import org.mifosx.openbanking.feature.sendmoney.generated.resources.feature_send_money_manual_iban
import org.mifosx.openbanking.feature.sendmoney.generated.resources.feature_send_money_manual_iban_error
import org.mifosx.openbanking.feature.sendmoney.generated.resources.feature_send_money_manual_name
import org.mifosx.openbanking.feature.sendmoney.generated.resources.feature_send_money_manual_sort_code
import org.mifosx.openbanking.feature.sendmoney.generated.resources.feature_send_money_manual_sort_code_error
import org.mifosx.openbanking.feature.sendmoney.generated.resources.feature_send_money_no_saved_payees_body
import org.mifosx.openbanking.feature.sendmoney.generated.resources.feature_send_money_no_saved_payees_title
import org.mifosx.openbanking.feature.sendmoney.generated.resources.feature_send_money_non_gbp_notice
import org.mifosx.openbanking.feature.sendmoney.generated.resources.feature_send_money_payee_needs_payer
import org.mifosx.openbanking.feature.sendmoney.generated.resources.feature_send_money_payees_failed_body
import org.mifosx.openbanking.feature.sendmoney.generated.resources.feature_send_money_payees_failed_title
import org.mifosx.openbanking.feature.sendmoney.generated.resources.feature_send_money_payees_loading_a11y
import org.mifosx.openbanking.feature.sendmoney.generated.resources.feature_send_money_reference_helper
import org.mifosx.openbanking.feature.sendmoney.generated.resources.feature_send_money_reference_label
import org.mifosx.openbanking.feature.sendmoney.generated.resources.feature_send_money_retry
import org.mifosx.openbanking.feature.sendmoney.generated.resources.feature_send_money_review_button
import org.mifosx.openbanking.feature.sendmoney.generated.resources.feature_send_money_selected_a11y
import org.mifosx.openbanking.feature.sendmoney.ui.OFFERED_CHARGE_BEARERS
import org.mifosx.openbanking.feature.sendmoney.ui.SendMoneyAction
import org.mifosx.openbanking.feature.sendmoney.ui.SendMoneyAmountProblem
import org.mifosx.openbanking.feature.sendmoney.ui.SendMoneyStep
import org.mifosx.openbanking.feature.sendmoney.ui.SendMoneyUiState
import template.core.base.designsystem.theme.KptTheme

private const val REFERENCE_MAX_LENGTH = 35

/**
 * The form and its review, as two pages of one state.
 *
 * A step field rather than two screen states: what the customer chose stays live across the pages,
 * and coming back from a failure must not discard it.
 */
@Composable
internal fun SendMoneyContent(
    state: SendMoneyUiState.Content,
    onAction: (SendMoneyAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state.step) {
        SendMoneyStep.Form -> SendMoneyFormPage(state, onAction, modifier)
        SendMoneyStep.Review -> SendMoneyReviewPage(state, onAction, modifier)
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
private fun SendMoneyFormPage(
    state: SendMoneyUiState.Content,
    onAction: (SendMoneyAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().testTag(SendMoneyTestTags.FORM_PAGE),
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
                    onSelect = { onAction(SendMoneyAction.SelectRail(it)) },
                    modifier = Modifier.testTag(SendMoneyTestTags.RAIL_TOGGLE),
                    optionTestTag = SendMoneyTestTags::railOption,
                )
                PayerSection(state, onAction)
                PayeeSection(state, onAction)
                AmountSection(state, onAction)
            }
        }
        FormActions(state, onAction)
    }
}

@Composable
private fun PayerSection(
    state: SendMoneyUiState.Content,
    onAction: (SendMoneyAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(HeadingGap)) {
        SectionHeading(stringResource(Res.string.feature_send_money_debtor_heading))
        MifosAccountPicker(
            options = state.debtorRows.map { it.toPickerOption() },
            selectedId = state.debtorAccountId,
            expanded = state.payerPickerExpanded,
            onToggle = { onAction(SendMoneyAction.TogglePayerPicker) },
            onSelect = { onAction(SendMoneyAction.SelectDebtorAccount(it)) },
            modifier = Modifier.testTag(SendMoneyTestTags.PAYER_PICKER),
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
                    onClick = { onAction(SendMoneyAction.LetBankChoosePayer) },
                    testTag = SendMoneyTestTags.PAYER_BANK_CHOICE,
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
            .testTag(SendMoneyTestTags.NON_GBP_NOTICE)
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
            text = stringResource(Res.string.feature_send_money_non_gbp_notice, instructedCurrency),
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
    state: SendMoneyUiState.Content,
    onAction: (SendMoneyAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(HeadingGap)) {
        SectionHeading(stringResource(Res.string.feature_send_money_creditor_heading))
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
            onSelect = { onAction(SendMoneyAction.SelectCreditor(it)) },
            onPayNew = { onAction(SendMoneyAction.ShowManualCreditorEntry) },
            modifier = Modifier.testTag(SendMoneyTestTags.CREDITOR_LIST),
            loading = state.payeesLoading,
            loadingContentDescription = stringResource(Res.string.feature_send_money_payees_loading_a11y),
            payNewTestTag = SendMoneyTestTags.MANUAL_ENTRY_BUTTON,
            loadingTestTag = SendMoneyTestTags.PAYEES_LOADING,
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
    state: SendMoneyUiState.Content,
    onAction: (SendMoneyAction) -> Unit,
) {
    // The bar itself spans the window so the surface behind it is unbroken; only its contents are
    // held to the form's width, which is what keeps the CTA above the fields it acts on.
    Column(
        modifier = Modifier
            .testTag(SendMoneyTestTags.FORM_ACTIONS)
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
                label = stringResource(Res.string.feature_send_money_review_button),
                onClick = { onAction(SendMoneyAction.ReviewPayment) },
                testTag = SendMoneyTestTags.REVIEW_BUTTON,
                enabled = state.canReview,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(NoticeGap),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.testTag(SendMoneyTestTags.FORM_TRUST_NOTE),
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = KptTheme.colorScheme.outline,
                    modifier = Modifier.size(NoticeIconSize),
                )
                Text(
                    text = stringResource(Res.string.feature_send_money_form_trust_note),
                    style = KptTheme.typography.bodySmall,
                    color = KptTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun ManualCreditorFields(
    state: SendMoneyUiState.Content,
    onAction: (SendMoneyAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(RowGap)) {
        OutlinedTextField(
            value = state.manualName,
            onValueChange = { onAction(SendMoneyAction.EnterManualName(it)) },
            label = { Text(stringResource(Res.string.feature_send_money_manual_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(SendMoneyTestTags.MANUAL_NAME),
        )
        // The rails identify a creditor differently — sort code and account number against an IBAN —
        // and each refuses the other's scheme with U027, so only one set is ever offered.
        if (state.rail == PaymentRail.Domestic) {
            OutlinedTextField(
                value = state.manualSortCode,
                onValueChange = { onAction(SendMoneyAction.EnterManualSortCode(it)) },
                label = { Text(stringResource(Res.string.feature_send_money_manual_sort_code)) },
                isError = state.fieldErrors.sortCodeInvalid,
                supportingText = {
                    if (state.fieldErrors.sortCodeInvalid) {
                        Text(stringResource(Res.string.feature_send_money_manual_sort_code_error))
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(SendMoneyTestTags.MANUAL_SORT_CODE),
            )
            OutlinedTextField(
                value = state.manualAccountNumber,
                onValueChange = { onAction(SendMoneyAction.EnterManualAccountNumber(it)) },
                label = { Text(stringResource(Res.string.feature_send_money_manual_account_number)) },
                isError = state.fieldErrors.accountNumberInvalid,
                supportingText = {
                    if (state.fieldErrors.accountNumberInvalid) {
                        Text(stringResource(Res.string.feature_send_money_manual_account_number_error))
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(SendMoneyTestTags.MANUAL_ACCOUNT_NUMBER),
            )
        } else {
            OutlinedTextField(
                value = state.manualIban,
                onValueChange = { onAction(SendMoneyAction.EnterManualIban(it)) },
                label = { Text(stringResource(Res.string.feature_send_money_manual_iban)) },
                isError = state.fieldErrors.ibanInvalid,
                supportingText = {
                    if (state.fieldErrors.ibanInvalid) {
                        Text(
                            text = stringResource(Res.string.feature_send_money_manual_iban_error),
                            modifier = Modifier.testTag(SendMoneyTestTags.MANUAL_IBAN_ERROR),
                        )
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(SendMoneyTestTags.MANUAL_IBAN),
            )
        }
        MifosFilledPillButton(
            label = stringResource(Res.string.feature_send_money_manual_confirm),
            onClick = { onAction(SendMoneyAction.ConfirmManualCreditor) },
            testTag = SendMoneyTestTags.MANUAL_CONFIRM,
        )
    }
}

@Composable
private fun AmountSection(
    state: SendMoneyUiState.Content,
    onAction: (SendMoneyAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SectionGap)) {
        MifosAmountCard(
            amount = state.amountInput,
            balanceLabel = state.availableBalanceLabel,
            errorMessage = state.amountProblem?.let { stringResource(it.messageResource()) },
            onAmountChange = { onAction(SendMoneyAction.EnterAmount(it)) },
            modifier = Modifier.testTag(SendMoneyTestTags.AMOUNT_CARD),
            fieldTestTag = SendMoneyTestTags.AMOUNT_FIELD,
            errorTestTag = SendMoneyTestTags.AMOUNT_ERROR,
            balanceTestTag = SendMoneyTestTags.AMOUNT_BALANCE,
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
                onValueChange = { onAction(SendMoneyAction.EnterReference(it.take(REFERENCE_MAX_LENGTH))) },
                label = { Text(stringResource(Res.string.feature_send_money_reference_label)) },
                supportingText = { Text(stringResource(Res.string.feature_send_money_reference_helper)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(SendMoneyTestTags.REFERENCE_FIELD),
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
    state: SendMoneyUiState.Content,
    onAction: (SendMoneyAction) -> Unit,
) {
    MifosDropdownBox(
        label = state.instructedCurrency,
        selected = state.instructedCurrency,
        options = state.offeredCurrencies,
        optionLabel = { currencyName(it) },
        optionTestTag = SendMoneyTestTags::instructedCurrencyOption,
        onSelect = { onAction(SendMoneyAction.SelectInstructedCurrency(it)) },
        modifier = Modifier.fillMaxSize().testTag(SendMoneyTestTags.INSTRUCTED_CURRENCY_PICKER),
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
            .testTag(SendMoneyTestTags.STATIC_CURRENCY_BOX)
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
    state: SendMoneyUiState.Content,
    onAction: (SendMoneyAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(HeadingGap)) {
        SectionHeading(stringResource(Res.string.feature_send_money_charges_heading))
        MifosDropdownField(
            label = chargeBearerLabel(state.chargeBearer),
            selected = state.chargeBearer,
            options = OFFERED_CHARGE_BEARERS,
            optionLabel = { chargeBearerLabel(it) },
            optionTestTag = SendMoneyTestTags::chargeBearerOption,
            onSelect = { onAction(SendMoneyAction.SelectChargeBearer(it)) },
            modifier = Modifier.testTag(SendMoneyTestTags.CHARGE_BEARER_PICKER),
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
private fun PayeesFailedNotice(onAction: (SendMoneyAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SendMoneyTestTags.PAYEES_FAILED)
            .clip(RoundedCornerShape(NoticeCorner))
            .background(KptTheme.colorScheme.surfaceContainer)
            .padding(NoticePadding),
        verticalArrangement = Arrangement.spacedBy(HeadingGap),
    ) {
        Text(
            text = stringResource(Res.string.feature_send_money_payees_failed_title),
            style = KptTheme.typography.titleSmall,
            color = KptTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(Res.string.feature_send_money_payees_failed_body),
            style = KptTheme.typography.bodySmall,
            color = KptTheme.colorScheme.onSurfaceVariant,
        )
        MifosTonalPillButton(
            label = stringResource(Res.string.feature_send_money_retry),
            onClick = { onAction(SendMoneyAction.RetryPayees) },
            testTag = SendMoneyTestTags.PAYEES_RETRY_BUTTON,
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
            .testTag(SendMoneyTestTags.PAYEE_NEEDS_PAYER)
            .clip(RoundedCornerShape(NoticeCorner))
            .background(KptTheme.colorScheme.surfaceContainer)
            .padding(NoticePadding),
        verticalArrangement = Arrangement.spacedBy(HeroGap),
    ) {
        Text(
            text = stringResource(Res.string.feature_send_money_payee_needs_payer),
            style = KptTheme.typography.bodyMedium,
            color = KptTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NoSavedPayees() {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(SendMoneyTestTags.NO_SAVED_PAYEES),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HeadingGap),
    ) {
        Text(
            text = stringResource(Res.string.feature_send_money_no_saved_payees_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(Res.string.feature_send_money_no_saved_payees_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun SendMoneyAccountRow.toPickerOption(): MifosAccountOption = MifosAccountOption(
    accountId = id,
    accountSubType = accountSubType,
    accountNumber = accountNumber,
    rawIdentification = rawIdentification,
    availableBalance = supporting,
)

private fun SendMoneyPickerRow.toPayeeOption(): MifosPayeeOption = MifosPayeeOption(
    payeeId = id,
    shortName = shortName.ifBlank { headline },
    initials = initials,
)

private fun SendMoneyAmountProblem.messageResource(): StringResource = when (this) {
    SendMoneyAmountProblem.NotANumber -> Res.string.feature_send_money_amount_error_not_a_number
    SendMoneyAmountProblem.TooManyDecimals -> Res.string.feature_send_money_amount_error_too_many_decimals
    SendMoneyAmountProblem.NotPositive -> Res.string.feature_send_money_amount_error_not_positive
    SendMoneyAmountProblem.ExceedsAvailableBalance -> Res.string.feature_send_money_amount_error_exceeds_balance
}
