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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.stringResource
import org.mifosx.openbanking.core.ui.account.accountTypeLabel
import org.mifosx.openbanking.core.ui.account.maskedAccountNumber
import org.mifosx.openbanking.feature.vrpsetup.formatPayeeIdentification
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.Res
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_payer_choose_at_bank
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_review_irreversible
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_review_limits
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_review_no_end_date
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_review_payee
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_review_payer
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_review_per_payment
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_review_per_period
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_review_up_to
import org.mifosx.openbanking.feature.vrpsetup.generated.resources.feature_vrp_setup_review_validity
import org.mifosx.openbanking.feature.vrpsetup.periodLabel
import template.core.base.designsystem.theme.KptTheme

/**
 * The check phase: what was entered, restated, with the notice that none of it can be changed.
 *
 * The notice sits above the primary action rather than below it.
 */
@Composable
internal fun VrpSetupReviewPage(form: SetupFormUi) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(KptTheme.spacing.md)
            .testTag(VrpSetupTestTags.REVIEW),
        verticalArrangement = Arrangement.spacedBy(KptTheme.spacing.md),
    ) {
        PartiesCard(form)
        LimitsCard(form)

        Text(
            text = stringResource(Res.string.feature_vrp_setup_review_irreversible),
            style = KptTheme.typography.bodyMedium,
            color = KptTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(VrpSetupTestTags.REVIEW_IRREVERSIBLE),
        )
    }
}

@Composable
private fun PartiesCard(form: SetupFormUi) {
    ReviewCard {
        ReviewHeading(stringResource(Res.string.feature_vrp_setup_review_payer))
        Text(
            text = form.payerHeadline(),
            style = KptTheme.typography.titleMedium,
            color = KptTheme.colorScheme.onSurface,
            modifier = Modifier.testTag(VrpSetupTestTags.REVIEW_PAYER),
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = KptTheme.spacing.md))

        ReviewHeading(stringResource(Res.string.feature_vrp_setup_review_payee))
        Text(
            text = form.payeeName(),
            style = KptTheme.typography.titleMedium,
            color = KptTheme.colorScheme.onSurface,
            modifier = Modifier.testTag(VrpSetupTestTags.REVIEW_PAYEE),
        )
        Text(
            text = formatPayeeIdentification(form.payeeIdentification),
            style = KptTheme.typography.bodyMedium,
            color = KptTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LimitsCard(form: SetupFormUi) {
    ReviewCard {
        ReviewHeading(stringResource(Res.string.feature_vrp_setup_review_limits))

        LimitLine(
            amount = form.perPaymentAmount,
            caption = stringResource(Res.string.feature_vrp_setup_review_per_payment),
            testTag = VrpSetupTestTags.REVIEW_PER_PAYMENT,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = KptTheme.spacing.md))
        LimitLine(
            amount = form.periodicAmount,
            caption = stringResource(
                Res.string.feature_vrp_setup_review_per_period,
                periodLabel(form.periodType),
            ),
            testTag = VrpSetupTestTags.REVIEW_PERIODIC,
        )

        Surface(
            shape = KptTheme.shapes.small,
            color = KptTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = KptTheme.spacing.md),
        ) {
            Text(
                text = form.validTo
                    ?.let { stringResource(Res.string.feature_vrp_setup_review_validity, it.toString()) }
                    ?: stringResource(Res.string.feature_vrp_setup_review_no_end_date),
                style = KptTheme.typography.bodyMedium,
                color = KptTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(KptTheme.spacing.md)
                    .testTag(VrpSetupTestTags.REVIEW_VALIDITY),
            )
        }
    }
}

@Composable
private fun LimitLine(
    amount: String,
    caption: String,
    testTag: String,
) {
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = stringResource(Res.string.feature_vrp_setup_review_up_to),
                style = KptTheme.typography.bodyLarge,
                color = KptTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = KptTheme.spacing.sm, bottom = KptTheme.spacing.xs),
            )
            Text(
                text = "£$amount",
                style = KptTheme.typography.headlineMedium,
                fontWeight = FontWeight.Light,
                color = KptTheme.colorScheme.onSurface,
                modifier = Modifier.testTag(testTag),
            )
        }
        Text(
            text = caption,
            style = KptTheme.typography.bodyMedium,
            color = KptTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReviewCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = KptTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = KptTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = KptTheme.elevation.level1),
    ) {
        Column(modifier = Modifier.padding(KptTheme.spacing.md)) { content() }
    }
}

@Composable
private fun ReviewHeading(text: String) {
    Text(
        text = text.uppercase(),
        style = KptTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = KptTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = KptTheme.spacing.xs),
    )
}

/** The payer as the review names it: the masked number and type, or the bank's own choice. */
@Composable
private fun SetupFormUi.payerHeadline(): String {
    val payer = selectedPayer ?: return stringResource(Res.string.feature_vrp_setup_payer_choose_at_bank)
    val masked = maskedAccountNumber(payer.accountSubType, payer.accountNumber, payer.identification)
    return "$masked · ${accountTypeLabel(payer.accountSubType)}"
}

private fun SetupFormUi.payeeName(): String =
    if (payNewSelected) newPayeeName else selectedPayee?.displayName.orEmpty()
