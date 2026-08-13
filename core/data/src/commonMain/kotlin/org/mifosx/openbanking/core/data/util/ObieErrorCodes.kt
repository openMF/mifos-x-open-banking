/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.data.util

import kotlinx.serialization.json.Json
import org.mifosx.openbanking.core.network.model.ais.error.ObErrorResponse
import template.core.base.network.NetworkError

/**
 * HSBC's code for "this product does not serve this endpoint".
 *
 * Despite the wording of the accompanying message ("This action is not allowed on the account type
 * in the request") it is *not* about OBIE's `AccountTypeCode` — a `CACC` current account can
 * receive it while another `CACC` does not. It tracks HSBC's finer **product** classification; see
 * `HsbcProductCapability`.
 */
const val OBIE_UNSUPPORTED_PRODUCT_CODE = "U000"

private val lenientJson = Json { ignoreUnknownKeys = true }

/**
 * Whether this error says the account's product cannot serve the endpoint.
 *
 * Both halves of the guard matter. The status alone is too broad — a 400 has other causes — and
 * the code alone is too loose, since a bare substring match would fire on any body that happened
 * to contain the token.
 */
fun NetworkError.isUnsupportedForProduct(): Boolean =
    this is NetworkError.Client.BadRequest && body.carriesUnsupportedProductCode()

/** Unwraps [RemoteException] so callers holding a `ScreenState.Error` can ask the same question. */
fun Throwable.isUnsupportedForProduct(): Boolean =
    (this as? RemoteException)?.networkError?.isUnsupportedForProduct() == true

/**
 * The ASPSP's own explanation, or `null` when the body carried none.
 *
 * Preferred over app-authored copy: the bank knows why it refused, and inventing a parallel
 * vocabulary means maintaining a translation of someone else's error catalogue.
 */
fun NetworkError.obieMessage(): String? = body?.parseObError()
    ?.errors
    ?.firstNotNullOfOrNull { it.message?.takeIf(String::isNotBlank) }

/** As [obieMessage], unwrapping [RemoteException]. */
fun Throwable.obieMessage(): String? = (this as? RemoteException)?.networkError?.obieMessage()

/**
 * The ASPSP's own error code, e.g. `U008` or `U014`.
 *
 * The payment flow needs this rather than the HTTP status: four of its ten failure modes arrive as
 * `400`, and only the code distinguishes "the amount is outside your limits" from "the instruction
 * diverged from the one you approved" — recoveries that are not interchangeable.
 */
fun NetworkError.obieErrorCode(): String? = body?.parseObError()
    ?.errors
    ?.firstNotNullOfOrNull { it.errorCode?.takeIf(String::isNotBlank) }

/** As [obieErrorCode], unwrapping [RemoteException]. */
fun Throwable.obieErrorCode(): String? = (this as? RemoteException)?.networkError?.obieErrorCode()

/**
 * The envelope's top-level `Id` — the bank's own reference for this failure.
 *
 * Surfaced to the PSU rather than only logged: when a payment fails for a reason they cannot act on,
 * this is the one thing that makes the call traceable in support.
 */
fun NetworkError.obieSupportReference(): String? = body?.parseObError()
    ?.id
    ?.takeIf(String::isNotBlank)

/** As [obieSupportReference], unwrapping [RemoteException]. */
fun Throwable.obieSupportReference(): String? =
    (this as? RemoteException)?.networkError?.obieSupportReference()

/**
 * Which request field the bank objected to, e.g. `Data.Initiation.DebtorAccount.Identification`.
 *
 * More precise than the error code alone, because one code covers many fields: `U002` ("Invalid
 * Field") says nothing about *which*. The path is what lets a refusal be attributed to the payer
 * rather than the payee, the amount or the reference — and therefore what lets the app learn that a
 * particular account cannot fund a payment instead of guessing from its product type.
 */
fun NetworkError.obieErrorPath(): String? = body?.parseObError()
    ?.errors
    ?.firstNotNullOfOrNull { it.path?.takeIf(String::isNotBlank) }

/** As [obieErrorPath], unwrapping [RemoteException]. */
fun Throwable.obieErrorPath(): String? = (this as? RemoteException)?.networkError?.obieErrorPath()

/**
 * Whether the bank refused the request because of the DEBTOR account specifically.
 *
 * Observed on two products, with different codes and the same path:
 *  - credit card    `U021` — a card has no sort code and account number
 *  - Global Money   `U002` — the identification was exactly what the bank publishes; the wallet is
 *                            simply not a permitted payer
 *
 * Matched on the path rather than either code, so a future product refused under some third code is
 * handled without another round of enumeration.
 */
fun Throwable.isDebtorAccountRefusal(): Boolean =
    obieErrorPath()?.contains(DEBTOR_ACCOUNT_PATH_FRAGMENT, ignoreCase = true) == true

private const val DEBTOR_ACCOUNT_PATH_FRAGMENT = "DebtorAccount"

/**
 * Whether the bank refused the request because of the requested EXECUTION DATE specifically.
 *
 * Three codes reach this one field, and matching on the path catches all of them at once:
 *  - `U003` — the date is today or earlier ("a date in the past"; the bank counts today as past)
 *  - `U002` — the date is beyond the accepted window, reported as "Invalid Field"
 *  - `U004` — the field was omitted altogether
 *
 * Matched on the path for the same reason as [isDebtorAccountRefusal], and with more force: `U002`
 * alone says only "Invalid Field" and is raised for refused payers and refused schemes too. Reading
 * the code without the path would land a date beyond the window on "check your details" — advice that
 * points at everything except the one field the customer has to change.
 */
fun Throwable.isExecutionDateRefusal(): Boolean =
    obieErrorPath()?.contains(EXECUTION_DATE_PATH_FRAGMENT, ignoreCase = true) == true

private const val EXECUTION_DATE_PATH_FRAGMENT = "RequestedExecutionDateTime"

/**
 * Whether the bank refused a standing order because of its FIRST payment date.
 *
 * A separate predicate from [isExecutionDateRefusal] rather than a widening of it, because a standing
 * order never sends `RequestedExecutionDateTime` at all — that predicate silently never fires on this
 * product, which is worse than it failing, since a refused date then reads as a generic failure.
 *
 * Kept apart from [isFinalPaymentDateRefusal] because the customer has two dates and only one of them
 * is wrong. The bank cannot tell them apart for us: its `U003` message recites three rules at once —
 * not today or tomorrow, within twelve months, and after the first payment date — whichever was
 * actually broken.
 */
fun Throwable.isFirstPaymentDateRefusal(): Boolean =
    obieErrorPath()?.contains(FIRST_PAYMENT_DATE_PATH_FRAGMENT, ignoreCase = true) == true

private const val FIRST_PAYMENT_DATE_PATH_FRAGMENT = "FirstPaymentDateTime"

/** Whether the bank refused a standing order because of its FINAL payment date. */
fun Throwable.isFinalPaymentDateRefusal(): Boolean =
    obieErrorPath()?.contains(FINAL_PAYMENT_DATE_PATH_FRAGMENT, ignoreCase = true) == true

private const val FINAL_PAYMENT_DATE_PATH_FRAGMENT = "FinalPaymentDateTime"

/**
 * Whether the bank rejected the repeat interval.
 *
 * `U002 "Invalid value"` at `…MandateRelatedInformation.Frequency.Type`. The app should never provoke
 * this — the frequency is a closed enum of the five accepted codes — so reaching it means a defect
 * rather than something the customer can correct.
 */
fun Throwable.isFrequencyRefusal(): Boolean =
    obieErrorPath()?.contains(FREQUENCY_PATH_FRAGMENT, ignoreCase = true) == true

private const val FREQUENCY_PATH_FRAGMENT = "Frequency"

private fun String?.carriesUnsupportedProductCode(): Boolean {
    if (this.isNullOrBlank()) return false
    val errors = parseObError()?.errors
    return if (errors != null) {
        errors.any { it.errorCode == OBIE_UNSUPPORTED_PRODUCT_CODE }
    } else {
        // Error bodies are not contract-guaranteed — a gateway can return HTML or a truncated
        // payload. Falling back to a substring keeps a malformed body from being read as "supported"
        // and sending the user back into a call that cannot succeed.
        contains(OBIE_UNSUPPORTED_PRODUCT_CODE)
    }
}

private fun String.parseObError(): ObErrorResponse? =
    runCatching { lenientJson.decodeFromString<ObErrorResponse>(this) }.getOrNull()
