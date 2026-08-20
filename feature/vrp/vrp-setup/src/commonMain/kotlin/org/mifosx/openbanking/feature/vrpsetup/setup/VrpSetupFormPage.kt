/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.feature.vrpsetup.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import org.jetbrains.compose.resources.stringResource
import org.mifosx.openbanking.core.common.formatIsoDate
import org.mifosx.openbanking.core.ui.account.MifosAccountOption
import org.mifosx.openbanking.core.ui.account.MifosAccountPicker
import org.mifosx.openbanking.core.ui.account.MifosBankChoiceRow
import org.mifosx.openbanking.core.ui.payee.MifosPayeeAvatarRow
import org.mifosx.openbanking.core.ui.payee.MifosPayeeOption
import org.mifosx.openbanking.feature.vrpsetup.amountErrorLabel
import org.mifosx.openbanking.feature.vrpsetup.components.VrpAmountField
import org.mifosx.openbanking.feature.vrpsetup.components.VrpEndDatePickerDialog
import org.mifosx.openbanking.feature.vrpsetup.components.VrpEndDateRow
import org.mifosx.openbanking.feature.vrpsetup.components.VrpPeriodDropdown
import org.mifosx.openbanking.feature.vrpsetup.components.VrpTextField
import org.mifosx.openbanking.feature.vrpsetup.formatPayeeIdentification
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.Res
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_payee_label
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_payee_new_account_number_label
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_payee_new_name_label
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_payee_new_sort_code_label
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_payer_card_warning
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_payer_choose_at_bank
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_payer_choose_at_bank_supporting
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_payer_label
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_per_payment_label
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_period_label
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_periodic_label
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_valid_to_hint
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_valid_to_label
import org.mifosx.openbanking.feature.vrpsetup.payeeErrorLabel
import org.mifosx.openbanking.feature.vrpsetup.todayUtc
import template.core.base.designsystem.theme.KptTheme

/** The entry phase: who pays, who is paid, the two ceilings, and when it stops. */
@Composable
internal fun VrpSetupFormPage(
    form: SetupFormUi,
    onAction: (VrpSetupAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(KptTheme.spacing.md)
            .testTag(VrpSetupTestTags.FORM),
        verticalArrangement = Arrangement.spacedBy(KptTheme.spacing.lg),
    ) {
        PayerSection(form, onAction)
        PayeeSection(form, onAction)
        PerPaymentSection(form, onAction)
        PeriodicSection(form, onAction)
        EndDateSection(form, onAction)
    }
}

@Composable
private fun PayerSection(
    form: SetupFormUi,
    onAction: (VrpSetupAction) -> Unit,
) {
    Column {
        SectionLabel(stringResource(Res.string.feature_vrp_setup_payer_label))

        MifosAccountPicker(
            options = form.payerOptions.map { it.toPickerOption() },
            selectedId = form.selectedPayerId,
            expanded = form.payerExpanded,
            onToggle = { onAction(VrpSetupAction.PayerToggled) },
            onSelect = { onAction(VrpSetupAction.PayerSelected(it)) },
            modifier = Modifier
                .padding(top = KptTheme.spacing.sm)
                .testTag(VrpSetupTestTags.PAYER_PICKER),
            unselectedLabel = if (form.chooseAtBank) {
                stringResource(Res.string.feature_vrp_setup_payer_choose_at_bank)
            } else {
                null
            },
            unselectedSupporting = if (form.chooseAtBank) {
                stringResource(Res.string.feature_vrp_setup_payer_choose_at_bank_supporting)
            } else {
                ""
            },
            extraOptions = {
                MifosBankChoiceRow(
                    selected = form.chooseAtBank,
                    onClick = { onAction(VrpSetupAction.ChooseAtBankSelected) },
                    testTag = VrpSetupTestTags.PAYER_BANK_CHOICE,
                )
            },
        )

        if (form.chooseAtBank) {
            Text(
                text = stringResource(Res.string.feature_vrp_setup_payer_card_warning),
                style = KptTheme.typography.bodySmall,
                color = KptTheme.colorScheme.error,
                modifier = Modifier
                    .padding(top = KptTheme.spacing.sm)
                    .testTag(VrpSetupTestTags.PAYER_CARD_WARNING),
            )
        }
    }
}

@Composable
private fun PayeeSection(
    form: SetupFormUi,
    onAction: (VrpSetupAction) -> Unit,
) {
    Column {
        SectionLabel(stringResource(Res.string.feature_vrp_setup_payee_label))

        MifosPayeeAvatarRow(
            payees = form.payeeOptions.map { it.toPayeeOption() },
            selectedId = form.selectedPayeeId,
            payNewSelected = form.payNewSelected,
            onSelect = { onAction(VrpSetupAction.PayeeSelected(it)) },
            onPayNew = { onAction(VrpSetupAction.PayNewSelected) },
            modifier = Modifier
                .padding(top = KptTheme.spacing.sm)
                .testTag(VrpSetupTestTags.PAYEE_ROW),
            payNewTestTag = VrpSetupTestTags.PAYEE_PAY_NEW,
            payeeTestTag = VrpSetupTestTags::payeeAvatar,
        )

        form.selectedPayee?.let { payee ->
            Text(
                text = formatPayeeIdentification(payee.identification),
                style = KptTheme.typography.bodyMedium,
                color = KptTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = KptTheme.spacing.md)
                    .testTag(VrpSetupTestTags.PAYEE_IDENTIFICATION),
            )
        }

        if (form.payNewSelected) {
            NewPayeeFields(form, onAction)
        }

        payeeErrorLabel(form.payeeProblem)?.let {
            Text(
                text = it,
                style = KptTheme.typography.bodySmall,
                color = KptTheme.colorScheme.error,
                modifier = Modifier
                    .padding(top = KptTheme.spacing.sm)
                    .testTag(VrpSetupTestTags.PAYEE_ERROR),
            )
        }
    }
}

/** The three entry fields, rendered only once the customer asks to pay someone new. */
@Composable
private fun NewPayeeFields(
    form: SetupFormUi,
    onAction: (VrpSetupAction) -> Unit,
) {
    Surface(
        shape = KptTheme.shapes.medium,
        color = KptTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = KptTheme.spacing.md),
    ) {
        Column(
            modifier = Modifier.padding(KptTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(KptTheme.spacing.md),
        ) {
            VrpTextField(
                label = stringResource(Res.string.feature_vrp_setup_payee_new_name_label),
                value = form.newPayeeName,
                onValueChange = { onAction(VrpSetupAction.NewPayeeNameChanged(it)) },
                error = payeeErrorLabel(form.newPayeeNameProblem),
                testTag = VrpSetupTestTags.NEW_PAYEE_NAME,
            )
            VrpTextField(
                label = stringResource(Res.string.feature_vrp_setup_payee_new_sort_code_label),
                value = form.newPayeeSortCode,
                onValueChange = { onAction(VrpSetupAction.NewPayeeSortCodeChanged(it)) },
                error = payeeErrorLabel(form.newPayeeSortCodeProblem),
                testTag = VrpSetupTestTags.NEW_PAYEE_SORT_CODE,
                keyboardType = KeyboardType.Number,
            )
            VrpTextField(
                label = stringResource(Res.string.feature_vrp_setup_payee_new_account_number_label),
                value = form.newPayeeAccountNumber,
                onValueChange = { onAction(VrpSetupAction.NewPayeeAccountNumberChanged(it)) },
                error = payeeErrorLabel(form.newPayeeAccountNumberProblem),
                testTag = VrpSetupTestTags.NEW_PAYEE_ACCOUNT_NUMBER,
                keyboardType = KeyboardType.Number,
            )
        }
    }
}

@Composable
private fun PerPaymentSection(
    form: SetupFormUi,
    onAction: (VrpSetupAction) -> Unit,
) {
    Column {
        SectionLabel(stringResource(Res.string.feature_vrp_setup_per_payment_label))
        VrpAmountField(
            value = form.perPaymentAmount,
            onValueChange = { onAction(VrpSetupAction.PerPaymentAmountChanged(it)) },
            error = amountErrorLabel(form.perPaymentProblem),
            testTag = VrpSetupTestTags.PER_PAYMENT_AMOUNT,
            errorTestTag = VrpSetupTestTags.PER_PAYMENT_ERROR,
            modifier = Modifier.padding(top = KptTheme.spacing.sm),
        )
    }
}

@Composable
private fun PeriodicSection(
    form: SetupFormUi,
    onAction: (VrpSetupAction) -> Unit,
) {
    Column {
        SectionLabel(stringResource(Res.string.feature_vrp_setup_periodic_label))
        Row(
            modifier = Modifier.padding(top = KptTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VrpAmountField(
                value = form.periodicAmount,
                onValueChange = { onAction(VrpSetupAction.PeriodicAmountChanged(it)) },
                error = amountErrorLabel(form.periodicProblem),
                testTag = VrpSetupTestTags.PERIODIC_AMOUNT,
                errorTestTag = VrpSetupTestTags.PERIODIC_ERROR,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(Res.string.feature_vrp_setup_period_label),
                style = KptTheme.typography.bodyLarge,
                color = KptTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = KptTheme.spacing.sm),
            )
            VrpPeriodDropdown(
                selected = form.periodType,
                onSelect = { onAction(VrpSetupAction.PeriodTypeSelected(it)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun EndDateSection(
    form: SetupFormUi,
    onAction: (VrpSetupAction) -> Unit,
) {
    Column {
        SectionLabel(stringResource(Res.string.feature_vrp_setup_valid_to_label))
        VrpEndDateRow(
            date = form.validTo?.let { formatIsoDate(it.toString()) },
            onOpen = { onAction(VrpSetupAction.DatePickerOpened) },
            onClear = { onAction(VrpSetupAction.ValidToCleared) },
            modifier = Modifier.padding(top = KptTheme.spacing.sm),
        )
        Text(
            text = stringResource(
                Res.string.feature_vrp_setup_valid_to_hint,
                formatIsoDate(form.earliestSelectableDate.toString()),
            ),
            style = KptTheme.typography.bodySmall,
            color = KptTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = KptTheme.spacing.xs)
                .testTag(VrpSetupTestTags.VALID_TO_HINT),
        )
    }

    if (form.datePickerOpen) {
        VrpEndDatePickerDialog(
            today = todayUtc(),
            selected = form.validTo,
            onSelect = { onAction(VrpSetupAction.ValidToSelected(it)) },
            onDismiss = { onAction(VrpSetupAction.DatePickerDismissed) },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = KptTheme.typography.titleMedium,
        color = KptTheme.colorScheme.onSurface,
    )
}

private fun PayeeOptionUi.toPayeeOption(): MifosPayeeOption = MifosPayeeOption(
    payeeId = payeeId,
    shortName = shortName,
    initials = initials,
)

private fun PayerOptionUi.toPickerOption(): MifosAccountOption = MifosAccountOption(
    accountId = accountId,
    accountSubType = accountSubType,
    accountNumber = accountNumber,
    rawIdentification = identification,
    availableBalance = availableBalance,
)
