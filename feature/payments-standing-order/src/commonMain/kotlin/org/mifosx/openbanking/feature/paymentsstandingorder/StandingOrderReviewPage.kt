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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import org.jetbrains.compose.resources.stringResource
import org.mifosx.openbanking.core.model.banking.payment.PaymentRail
import org.mifosx.openbanking.core.ui.account.accountDisplayName
import org.mifosx.openbanking.core.ui.components.MifosFilledPillButton
import org.mifosx.openbanking.core.ui.components.MifosTonalPillButton
import org.mifosx.openbanking.core.ui.payee.initialsOf
import org.mifosx.openbanking.feature.paymentsstandingorder.components.chargeBearerLabel
import org.mifosx.openbanking.feature.paymentsstandingorder.components.currencyName
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.Res
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_irreversible_body
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_irreversible_title
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_review_auth_notice
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_review_bank_charge
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_review_charge_at_setup
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_review_charge_bearer
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_review_charge_caveat
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_review_confirm
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_review_details_heading
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_review_edit
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_review_from
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_review_from_bank_choice
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_review_heading
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_review_not_yet_made_body
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_review_not_yet_made_title
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_review_reference
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_review_reference_empty
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_review_scheduled_for
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_review_sent_as
import org.mifosx.openbanking.feature.paymentsstandingorder.generated.resources.feature_payments_standing_order_review_to
import org.mifosx.openbanking.feature.paymentsstandingorder.ui.StandingOrderAction
import org.mifosx.openbanking.feature.paymentsstandingorder.ui.StandingOrderUiState
import template.core.base.designsystem.theme.KptTheme

/**
 * The last thing the customer reads before money moves.
 *
 * Split from [StandingOrderContent] because it is the other page of the form, and the two had grown far
 * enough apart that one file was describing two screens.
 */

/**
 * The last thing the customer reads before money moves, so it is built around one figure.
 *
 * The amount leads at display size with the payee immediately under it: everything else on this
 * step is detail that confirms *that* statement rather than competing with it.
 *
 * Deliberately absent is any fee or debit total. Charges arrive on the consent response, which does
 * not exist until Confirm is tapped, so a figure here could only be hardcoded — and the sandbox
 * quotes charges on some payments, which would make a printed £0.00 untrue on the one screen where
 * that matters most.
 */
@Composable
internal fun StandingOrderReviewPage(
    state: StandingOrderUiState.Content,
    onAction: (StandingOrderAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val payerLabel = state.debtorAccountRow?.let { row ->
        accountDisplayName(
            nickname = row.nickname,
            accountSubType = row.accountSubType,
            accountNumber = row.accountNumber,
            rawIdentification = row.rawIdentification,
        )
    }.orEmpty()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(StandingOrderTestTags.REVIEW_PAGE)
            .verticalScroll(rememberScrollState())
            .padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(SectionGap),
    ) {
        // The page lead. It earns a line of its own now that Review is a page rather than a step
        // behind an indicator that already said where the customer was.
        Text(
            text = stringResource(Res.string.feature_payments_standing_order_review_heading),
            style = KptTheme.typography.titleMedium,
            color = KptTheme.colorScheme.onSurface,
            modifier = Modifier.testTag(StandingOrderTestTags.REVIEW_LEAD),
        )

        ReviewHero(
            dateLabel = state.firstPaymentDateLabel,
            amountLabel = state.amountLabel,
            creditorName = state.creditorLabel,
        )

        Column(
            modifier = Modifier.fillMaxWidth().testTag(StandingOrderTestTags.REVIEW_SUMMARY),
            verticalArrangement = Arrangement.spacedBy(RowGap),
        ) {
            SectionHeading(stringResource(Res.string.feature_payments_standing_order_review_details_heading))
            // Blank when the PSU left the choice to the bank — which is a decision they made, so it
            // is stated rather than left as an empty row that reads like a missing field.
            ReviewRow(
                label = stringResource(Res.string.feature_payments_standing_order_review_from),
                value = payerLabel.ifBlank {
                    stringResource(Res.string.feature_payments_standing_order_review_from_bank_choice)
                },
                tag = StandingOrderTestTags.REVIEW_FROM,
            )
            ReviewRow(
                label = stringResource(Res.string.feature_payments_standing_order_review_to),
                value = state.creditorLabel,
                tag = StandingOrderTestTags.REVIEW_TO,
                secondary = state.creditorSupporting,
            )
            if (state.rail == PaymentRail.Domestic) {
                ReviewRow(
                    label = stringResource(Res.string.feature_payments_standing_order_review_reference),
                    value = state.reference.ifBlank {
                        stringResource(Res.string.feature_payments_standing_order_review_reference_empty)
                    },
                    tag = StandingOrderTestTags.REVIEW_REFERENCE,
                )
                // Both rails carry a charge row, because an omitted one reads as "no charge" — and
                // this is the rail that actually has one. HSBC quotes a real per-payment fee here
                // and returns it on the consent response, before authorisation; the international
                // rail declares nothing until the resource exists. So the rail that can eventually
                // name a figure must not be the silent one. It still cannot name it *yet* — the
                // consent is not staged until Confirm — so the row says when, not how much.
                ReviewRow(
                    label = stringResource(Res.string.feature_payments_standing_order_review_bank_charge),
                    value = stringResource(Res.string.feature_payments_standing_order_review_charge_at_setup),
                    tag = StandingOrderTestTags.REVIEW_CHARGE_ROW,
                )
            } else {
                // No "Recipient receives" row. It existed because the instructed currency and the
                // currency of transfer could differ; they no longer can — the transfer currency is
                // derived from the amount's — so the row restated the currency already carried by
                // the figure beside it. `formatMinorUnits` renders GBP, EUR and USD as £/€/$ and
                // every other code as the code itself, so the amount names its own currency.
                ReviewRow(
                    label = stringResource(Res.string.feature_payments_standing_order_review_sent_as),
                    value = currencyName(state.instructedCurrency),
                    tag = StandingOrderTestTags.REVIEW_SENT_AS,
                )
                // The bearer, and deliberately no figure. This rail declares its charge only when
                // the payment resource is created — which is after the customer has authorised — so
                // any number here would be invented. The caveat below says so in as many words.
                ReviewRow(
                    label = stringResource(Res.string.feature_payments_standing_order_review_charge_bearer),
                    value = chargeBearerLabel(state.chargeBearer),
                    tag = StandingOrderTestTags.REVIEW_CHARGE_ROW,
                )
            }
        }

        if (state.rail == PaymentRail.International) {
            Text(
                text = stringResource(Res.string.feature_payments_standing_order_review_charge_caveat),
                style = KptTheme.typography.bodySmall,
                color = KptTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(StandingOrderTestTags.REVIEW_CHARGE_CAVEAT),
            )
        }

        NotYetMadeNotice(dateLabel = state.firstPaymentDateLabel)

        IrreversibleNotice()

        AuthorisationNotice()

        MifosFilledPillButton(
            label = stringResource(Res.string.feature_payments_standing_order_review_confirm),
            onClick = { onAction(StandingOrderAction.ConfirmAndStageConsent) },
            icon = Icons.Filled.Lock,
            testTag = StandingOrderTestTags.CONFIRM_BUTTON,
        )
        MifosTonalPillButton(
            label = stringResource(Res.string.feature_payments_standing_order_review_edit),
            onClick = { onAction(StandingOrderAction.BackStep) },
            testTag = StandingOrderTestTags.EDIT_PAYMENT_BUTTON,
        )
    }
}

/**
 * The date first, then the amount, then who it is going to.
 *
 * The order is the difference between this screen and the immediate rail's. There, the amount is the
 * whole statement. Here the customer is confirming *when* as much as *how much* — a payment they will
 * not be able to cancel once approved — so the date leads and the amount follows it.
 */
@Composable
private fun ReviewHero(dateLabel: String, amountLabel: String, creditorName: String) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(StandingOrderTestTags.REVIEW_HERO),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HeroGap),
    ) {
        Text(
            text = stringResource(Res.string.feature_payments_standing_order_review_scheduled_for),
            style = KptTheme.typography.labelMedium,
            color = KptTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = dateLabel,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag(StandingOrderTestTags.REVIEW_SCHEDULE_ROW),
        )
        Text(
            text = amountLabel,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag(StandingOrderTestTags.REVIEW_AMOUNT),
        )
        if (creditorName.isNotBlank()) {
            PayeeChip(creditorName)
        }
    }
}

/**
 * The panel that refuses to overstate what has happened.
 *
 * Every word here is chosen against a specific way this screen could mislead. It does not say paid,
 * sent or complete, because nothing has moved and nothing will until the date. It names the date
 * again, because that is the commitment. And it says the payment cannot be cancelled from this app —
 * which is not a limitation of this build but of the bank: a scheduled consent answers `405` to a
 * delete and survives it, so an in-app cancel could never be honest.
 *
 * The only tinted surface on the page, so it reads as the caveat rather than as one more detail row.
 */
@Composable
private fun NotYetMadeNotice(dateLabel: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardCorner))
            .background(KptTheme.colorScheme.primaryContainer)
            .padding(CardPadding)
            .testTag(StandingOrderTestTags.REVIEW_NOT_YET_MADE),
        verticalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        Text(
            text = stringResource(Res.string.feature_payments_standing_order_review_not_yet_made_title),
            style = KptTheme.typography.titleSmall,
            color = KptTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            text = stringResource(
                Res.string.feature_payments_standing_order_review_not_yet_made_body,
                dateLabel,
            ),
            style = KptTheme.typography.bodySmall,
            color = KptTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/** Initials beside the name, reusing the same derivation the payee picker rows use. */
@Composable
private fun PayeeChip(creditorName: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(ChipCorner))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = ChipPaddingHorizontal, vertical = ChipPaddingVertical)
            .testTag(StandingOrderTestTags.REVIEW_PAYEE_CHIP),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ChipGap),
    ) {
        Box(
            modifier = Modifier
                .size(ChipAvatarSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initialsOf(creditorName),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(
            text = creditorName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Says what happens next, rather than asserting a check this app has not made.
 *
 * The original mockup carried "Confirmation of Payee passed" here. HSBC exposes no CoP result to
 * this app, so claiming one would be inventing a reassurance. What *is* true, and worth saying
 * before an irreversible action, is that nothing moves until the bank has authenticated the PSU.
 */
@Composable
private fun AuthorisationNotice() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NoticeCorner))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(NoticePadding)
            .testTag(StandingOrderTestTags.REVIEW_AUTH_NOTICE),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NoticeGap),
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(NoticeIconSize),
        )
        Text(
            text = stringResource(Res.string.feature_payments_standing_order_review_auth_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun ReviewRow(
    label: String,
    value: String,
    tag: String,
    modifier: Modifier = Modifier,
    secondary: String = "",
    emphasis: Boolean = false,
) {
    Column(modifier = modifier.fillMaxWidth().testTag(tag)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = if (emphasis) {
                MaterialTheme.typography.headlineSmall
            } else {
                MaterialTheme.typography.bodyLarge
            },
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (secondary.isNotBlank()) {
            Text(
                text = secondary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The one thing the customer must know before committing, and again afterwards.
 *
 * A standing order cannot be changed or cancelled through this app: `DELETE` on the consent answers
 * `405` and no amendment endpoint exists. Directing the customer to their bank's own channel is a
 * stated obligation on the PISP for this product, not a courtesy — so it sits directly above the
 * primary action rather than below the fold.
 *
 * Calm, not alarming: `secondaryContainer`, an info glyph, and never the error colour. This is
 * information about how the product works, not a report that something went wrong.
 */
@Composable
private fun IrreversibleNotice() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardCorner))
            .background(KptTheme.colorScheme.secondaryContainer)
            .padding(CardPadding)
            .testTag(StandingOrderTestTags.IRREVERSIBLE_NOTICE),
        horizontalArrangement = Arrangement.spacedBy(HeroGap),
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = KptTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(GlyphSize),
        )
        Column(verticalArrangement = Arrangement.spacedBy(RowGap)) {
            Text(
                text = stringResource(Res.string.feature_payments_standing_order_irreversible_title),
                style = KptTheme.typography.titleSmall,
                color = KptTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = stringResource(Res.string.feature_payments_standing_order_irreversible_body),
                style = KptTheme.typography.bodyMedium,
                color = KptTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
