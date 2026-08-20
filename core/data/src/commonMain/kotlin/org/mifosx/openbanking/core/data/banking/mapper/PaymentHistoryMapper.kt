/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.data.banking.mapper

import org.mifosx.openbanking.core.database.banking.entity.PaymentHistoryEntity
import org.mifosx.openbanking.core.model.banking.BankAccount
import org.mifosx.openbanking.core.model.banking.payment.ConsentType
import org.mifosx.openbanking.core.model.banking.payment.PaymentDraft
import org.mifosx.openbanking.core.model.banking.payment.PaymentHistoryRow
import org.mifosx.openbanking.core.model.banking.payment.PaymentReceipt
import org.mifosx.openbanking.core.model.banking.payment.PaymentStatus
import org.mifosx.openbanking.core.model.banking.payment.ScheduledPaymentDraft
import org.mifosx.openbanking.core.model.banking.payment.StandingOrderDraft
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val PAYMENT_TYPE_DOMESTIC = "domestic_payment"
private const val PAYMENT_TYPE_INTERNATIONAL = "international_payment"
private const val PAYMENT_TYPE_DOMESTIC_SCHEDULED = "domestic_scheduled_payment"
private const val PAYMENT_TYPE_INTERNATIONAL_SCHEDULED = "international_scheduled_payment"
private const val PAYMENT_TYPE_DOMESTIC_STANDING_ORDER = "domestic_standing_order"
private const val PAYMENT_TYPE_INTERNATIONAL_STANDING_ORDER = "international_standing_order"

/**
 * The payer's three columns, blank when the PSU left the account to the bank.
 *
 * The entity's debtor columns are non-null, so a draft with no debtor writes empty strings rather
 * than widening the schema. Blank here means "not chosen by us" — for a submitted payment the bank's
 * own choice is on the receipt's `debtorIdentification`, and that is what the detail screen reads.
 */
private fun BankAccount?.historyAccountId(): String = this?.accountId.orEmpty()

private fun BankAccount?.historyName(): String = this?.displayName().orEmpty()

private fun BankAccount?.historyIdentification(): String =
    this?.let { it.rawIdentification.ifBlank { it.sortCode + it.accountNumber } }.orEmpty()

/** The rail a draft was built for. `CurrencyOfTransfer` is set on international drafts only. */
private fun PaymentDraft.paymentType(): String =
    if (currencyOfTransfer != null) PAYMENT_TYPE_INTERNATIONAL else PAYMENT_TYPE_DOMESTIC

/**
 * The rail a stored row was sent on.
 *
 * Domestic is the fallback for an unrecognised value because every row written before v5 was
 * labelled domestic regardless of the rail it actually used, so an unknown string is far more
 * likely to be an old domestic row than a new international one.
 */
internal fun String?.toConsentType(): ConsentType? =
    if (isNullOrBlank()) ConsentType.DomesticSinglePayment else ConsentType.fromWire(this)

internal fun PaymentReceipt.toEntity(
    draft: PaymentDraft,
    approvedAt: String? = null,
    submittedAt: String? = null,
): PaymentHistoryEntity =
    PaymentHistoryEntity(
        id = domesticPaymentId,
        paymentId = domesticPaymentId,
        errorKind = null,
        errorDescription = null,
        status = status.name,
        debtorAccountId = draft.debtorAccount.historyAccountId(),
        debtorName = draft.debtorAccount.historyName(),
        // The bank's chosen payer when we sent none, else the one the PSU picked.
        debtorIdentification = debtorIdentification.ifBlank {
            draft.debtorAccount.historyIdentification()
        },
        creditorName = draft.creditor.name,
        creditorIdentification = draft.creditor.identification,
        amountMinorUnits = draft.amountMinorUnits,
        currency = draft.currency,
        reference = draft.reference,
        creationDateTime = creationDateTime,
        approvedAt = approvedAt,
        submittedAt = submittedAt,
        settlementDateTime = settlementDateTime.takeIf { it.isNotBlank() },
        chargeBearer = draft.chargeBearer?.wireValue,
        currencyOfTransfer = draft.currencyOfTransfer,
        // Derived, not assumed: the status read-back has to hit the matching rail's endpoint.
        paymentType = draft.paymentType(),
        syncedAt = null,
    )

/** The rail a scheduled draft was built for, on the same discriminator. */
private fun ScheduledPaymentDraft.paymentType(): String =
    if (currencyOfTransfer != null) PAYMENT_TYPE_INTERNATIONAL_SCHEDULED else PAYMENT_TYPE_DOMESTIC_SCHEDULED

/**
 * The scheduled equivalent, carrying the one column an immediate payment has no value for.
 *
 * `settlementDateTime` stays null here whatever the receipt says: on this rail the bank returns a
 * settlement date equal to the creation date, and the truthful date is the requested one.
 */
internal fun PaymentReceipt.toEntity(
    draft: ScheduledPaymentDraft,
    approvedAt: String? = null,
    submittedAt: String? = null,
): PaymentHistoryEntity =
    PaymentHistoryEntity(
        id = domesticPaymentId,
        paymentId = domesticPaymentId,
        errorKind = null,
        errorDescription = null,
        status = status.name,
        debtorAccountId = draft.debtorAccount.historyAccountId(),
        debtorName = draft.debtorAccount.historyName(),
        debtorIdentification = debtorIdentification.ifBlank {
            draft.debtorAccount.historyIdentification()
        },
        creditorName = draft.creditor.name,
        creditorIdentification = draft.creditor.identification,
        amountMinorUnits = draft.amountMinorUnits,
        currency = draft.currency,
        reference = draft.reference,
        creationDateTime = creationDateTime,
        approvedAt = approvedAt,
        submittedAt = submittedAt,
        settlementDateTime = null,
        chargeBearer = draft.chargeBearer?.wireValue,
        currencyOfTransfer = draft.currencyOfTransfer,
        requestedExecutionDateTime = requestedExecutionDateTime.takeIf { it.isNotBlank() }
            ?: draft.requestedExecutionDate,
        paymentType = draft.paymentType(),
        syncedAt = null,
    )

/** A scheduled instruction the bank refused before it became a payment. */
internal fun ScheduledPaymentDraft.toFailureEntity(
    errorKind: String,
    errorDescription: String,
): PaymentHistoryEntity = PaymentHistoryEntity(
    id = errorId(),
    paymentId = null,
    errorKind = errorKind,
    errorDescription = errorDescription,
    status = null,
    debtorAccountId = debtorAccount.historyAccountId(),
    debtorName = debtorAccount.historyName(),
    debtorIdentification = debtorAccount.historyIdentification(),
    creditorName = creditor.name,
    creditorIdentification = creditor.identification,
    amountMinorUnits = amountMinorUnits,
    currency = currency,
    reference = reference,
    creationDateTime = "",
    settlementDateTime = null,
    chargeBearer = chargeBearer?.wireValue,
    currencyOfTransfer = currencyOfTransfer,
    requestedExecutionDateTime = requestedExecutionDate,
    paymentType = paymentType(),
    syncedAt = null,
)

/** The rail a mandate was built for, on the same discriminator both siblings use. */
private fun StandingOrderDraft.paymentType(): String =
    if (currencyOfTransfer != null) {
        PAYMENT_TYPE_INTERNATIONAL_STANDING_ORDER
    } else {
        PAYMENT_TYPE_DOMESTIC_STANDING_ORDER
    }

/**
 * The standing-order equivalent, carrying the two columns only a mandate has a value for.
 *
 * `settlementDateTime` stays null, and here that is not a workaround for a bank quirk but the plain
 * fact: a mandate has no single settlement. The first payment date reuses `requestedExecutionDateTime`
 * — the same meaning as on a scheduled payment — and [PaymentHistoryEntity.frequency] is what tells a
 * reader the row repeats.
 */
internal fun PaymentReceipt.toEntity(
    draft: StandingOrderDraft,
    approvedAt: String? = null,
    submittedAt: String? = null,
): PaymentHistoryEntity =
    PaymentHistoryEntity(
        id = domesticPaymentId,
        paymentId = domesticPaymentId,
        errorKind = null,
        errorDescription = null,
        status = status.name,
        debtorAccountId = draft.debtorAccount.historyAccountId(),
        debtorName = draft.debtorAccount.historyName(),
        debtorIdentification = debtorIdentification.ifBlank {
            draft.debtorAccount.historyIdentification()
        },
        creditorName = draft.creditor.name,
        creditorIdentification = draft.creditor.identification,
        amountMinorUnits = draft.firstPaymentAmountMinorUnits,
        currency = draft.currency,
        reference = draft.reference,
        creationDateTime = creationDateTime,
        approvedAt = approvedAt,
        submittedAt = submittedAt,
        settlementDateTime = null,
        chargeBearer = draft.chargeBearer?.wireValue,
        currencyOfTransfer = draft.currencyOfTransfer,
        requestedExecutionDateTime = requestedExecutionDateTime.takeIf { it.isNotBlank() }
            ?: draft.firstPaymentDate,
        frequency = draft.frequency.wireValue,
        finalPaymentDateTime = draft.finalPaymentDate,
        paymentType = draft.paymentType(),
        syncedAt = null,
    )

/** A mandate the bank refused before it became a standing order. */
internal fun StandingOrderDraft.toFailureEntity(
    errorKind: String,
    errorDescription: String,
): PaymentHistoryEntity = PaymentHistoryEntity(
    id = errorId(),
    paymentId = null,
    errorKind = errorKind,
    errorDescription = errorDescription,
    status = null,
    debtorAccountId = debtorAccount.historyAccountId(),
    debtorName = debtorAccount.historyName(),
    debtorIdentification = debtorAccount.historyIdentification(),
    creditorName = creditor.name,
    creditorIdentification = creditor.identification,
    amountMinorUnits = firstPaymentAmountMinorUnits,
    currency = currency,
    reference = reference,
    creationDateTime = "",
    settlementDateTime = null,
    chargeBearer = chargeBearer?.wireValue,
    currencyOfTransfer = currencyOfTransfer,
    requestedExecutionDateTime = firstPaymentDate,
    frequency = frequency.wireValue,
    finalPaymentDateTime = finalPaymentDate,
    paymentType = paymentType(),
    syncedAt = null,
)

internal fun PaymentDraft.toFailureEntity(
    errorKind: String,
    errorDescription: String,
): PaymentHistoryEntity = PaymentHistoryEntity(
    id = errorId(),
    paymentId = null,
    errorKind = errorKind,
    errorDescription = errorDescription,
    status = null,
    debtorAccountId = debtorAccount.historyAccountId(),
    debtorName = debtorAccount.historyName(),
    debtorIdentification = debtorAccount.historyIdentification(),
    creditorName = creditor.name,
    creditorIdentification = creditor.identification,
    amountMinorUnits = amountMinorUnits,
    currency = currency,
    reference = reference,
    creationDateTime = "",
    settlementDateTime = null,
    chargeBearer = chargeBearer?.wireValue,
    currencyOfTransfer = currencyOfTransfer,
    paymentType = paymentType(),
    syncedAt = null,
)

/**
 * A stored row as a history entry, or null when it is not one.
 *
 * Null for a row with no bank id or an unrecognised [PaymentHistoryEntity.paymentType].
 */
internal fun PaymentHistoryEntity.toHistoryRow(): PaymentHistoryRow? {
    val bankId = paymentId
    val type = paymentType.toConsentType()
    return if (bankId == null || type == null) {
        null
    } else {
        PaymentHistoryRow(
            paymentId = bankId,
            consentType = type,
            status = status.toPaymentStatus(),
            amountMinorUnits = amountMinorUnits,
            currency = currency,
            creditorName = creditorName,
            submittedAt = submittedAt.orEmpty(),
            reference = reference,
            requestedExecutionDateTime = requestedExecutionDateTime,
            frequency = frequency,
            finalPaymentDateTime = finalPaymentDateTime,
        )
    }
}

/** The status a stored string names, matching the persisted enum name before the wire codes. */
private fun String?.toPaymentStatus(): PaymentStatus =
    PaymentStatus.entries.firstOrNull { it.name == this } ?: PaymentStatus.fromWire(this)

@OptIn(ExperimentalUuidApi::class)
private fun errorId(): String = Uuid.random().toString()

private fun org.mifosx.openbanking.core.model.banking.BankAccount.displayName(): String =
    nickname.takeIf { it.isNotBlank() }
        ?: accountSubType.ifBlank { "Account" }
