/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.model.hsbcPermission

import kotlinx.serialization.Serializable

enum class PermissionId(val obieScope: String) {
    ReadAccountsBasic("ReadAccountsBasic"),
    ReadAccountsDetail("ReadAccountsDetail"),
    ReadBalances("ReadBalances"),
    ReadBeneficiariesBasic("ReadBeneficiariesBasic"),
    ReadBeneficiariesDetail("ReadBeneficiariesDetail"),
    ReadDirectDebits("ReadDirectDebits"),
    ReadOffers("ReadOffers"),
    ReadPAN("ReadPAN"),
    ReadParty("ReadParty"),
    ReadProducts("ReadProducts"),
    ReadRefundAccount("ReadRefundAccount"),
    ReadScheduledPaymentsBasic("ReadScheduledPaymentsBasic"),
    ReadScheduledPaymentsDetail("ReadScheduledPaymentsDetail"),
    ReadStandingOrdersBasic("ReadStandingOrdersBasic"),
    ReadStandingOrdersDetail("ReadStandingOrdersDetail"),
    ReadStatementsBasic("ReadStatementsBasic"),
    ReadStatementsDetail("ReadStatementsDetail"),
    ReadTransactionsBasic("ReadTransactionsBasic"),
    ReadTransactionsCredits("ReadTransactionsCredits"),
    ReadTransactionsDebits("ReadTransactionsDebits"),
    ReadTransactionsDetail("ReadTransactionsDetail"),
}

@Serializable
data class OBPermission(
    val id: PermissionId,
    val label: String,
    val description: String,
) {
    companion object {
        val ALL: List<OBPermission> = listOf(
            OBPermission(
                PermissionId.ReadAccountsBasic,
                "Basic account info",
                "Account number, sort code, and product type",
            ),
            OBPermission(
                PermissionId.ReadAccountsDetail,
                "Account details",
                "Account names, sort codes, IBANs, and currency for your HSBC accounts",
            ),
            OBPermission(
                PermissionId.ReadBalances,
                "Account balances",
                "Current, available, and credit-limit balances across all authorised accounts in real time",
            ),
            OBPermission(
                PermissionId.ReadBeneficiariesBasic,
                "Basic beneficiary info",
                "Beneficiary names linked to your accounts",
            ),
            OBPermission(
                PermissionId.ReadBeneficiariesDetail,
                "Beneficiary details",
                "Full beneficiary details including account references and addresses",
            ),
            OBPermission(
                PermissionId.ReadDirectDebits,
                "Direct debits",
                "Active direct-debit mandates and the most recent amounts collected on your accounts",
            ),
            OBPermission(
                PermissionId.ReadOffers,
                "Offers",
                "Available product offers linked to your HSBC profile",
            ),
            OBPermission(
                PermissionId.ReadPAN,
                "PAN",
                "Your masked Primary Account Number (PAN) for card-linked accounts",
            ),
            OBPermission(
                PermissionId.ReadParty,
                "Party",
                "Your registered name, contact details, and customer type with HSBC",
            ),
            OBPermission(
                PermissionId.ReadProducts,
                "Products",
                "Product type, tier, and features for each authorised account",
            ),
            OBPermission(
                PermissionId.ReadScheduledPaymentsBasic,
                "Basic scheduled payments",
                "Upcoming scheduled-payment dates and amounts",
            ),
            OBPermission(
                PermissionId.ReadScheduledPaymentsDetail,
                "Scheduled payment details",
                "Full scheduled-payment details including creditor references",
            ),
            OBPermission(
                PermissionId.ReadStandingOrdersBasic,
                "Basic standing orders",
                "Active standing-order dates and amounts",
            ),
            OBPermission(
                PermissionId.ReadStandingOrdersDetail,
                "Standing orders",
                "Scheduled recurring payment mandates you have set up on your HSBC accounts",
            ),
            OBPermission(
                PermissionId.ReadStatementsBasic,
                "Basic statements",
                "Statement dates and summary totals",
            ),
            OBPermission(
                PermissionId.ReadStatementsDetail,
                "Statements",
                "Monthly statement summaries, opening and closing balances, and available PDF references",
            ),
            OBPermission(
                PermissionId.ReadTransactionsBasic,
                "Basic transactions",
                "Recent debit and credit amounts with dates",
            ),
            OBPermission(
                PermissionId.ReadTransactionsCredits,
                "Transaction credits",
                "Incoming credit transactions with payer references",
            ),
            OBPermission(
                PermissionId.ReadTransactionsDebits,
                "Transaction debits",
                "Outgoing debit transactions with merchant names",
            ),
            OBPermission(
                PermissionId.ReadTransactionsDetail,
                "Transaction history",
                "Up to 90 days of debits and credits, merchant names, and transaction references",
            ),
        )
    }
}
