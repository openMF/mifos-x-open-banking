/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */
package org.mifosx.openbanking.core.model.hsbcProduct

import kotlinx.serialization.Serializable

/**
 * What an account can be used for.
 *
 * Mostly the per-account AIS resources this app reads, plus [PaymentDebtor], which is a PISP
 * capability rather than a readable resource — an account either can fund a domestic payment or
 * cannot, and the bank will not tell us which until we try.
 */
enum class AccountEndpoint {
    Balances,
    Transactions,
    StandingOrders,
    DirectDebits,
    ScheduledPayments,
    Beneficiaries,
    Statements,
    Product,
    Party,

    /** Whether the account may appear as `DebtorAccount` on a domestic payment. */
    PaymentDebtor,

    /** Whether the account may be named as the payer on a variable recurring payment. */
    VrpPayer,
}

/**
 * Which HSBC products each AIS endpoint serves.
 *
 * HSBC does not expose this at runtime — OBIE has no per-account capability field — so the matrix
 * is transcribed from the `description:` of each path in HSBC's brand spec
 * `account-info-4.0-personal.yaml`. Every row below quotes the line that justifies it, so the table
 * can be re-audited against the spec when HSBC republishes it.
 *
 * Calling an unsupported combination returns HTTP 400 with `ErrorCode: U000`
 * ("This action is not allowed on the account type in the request"). That was confirmed against all
 * five sandbox accounts of `customer01`; every observed result matches this table.
 *
 * The table only ever *removes* capabilities, and nothing at runtime adds one back. So a row that
 * is wrongly restrictive is undiscoverable in production, while a wrongly permissive one
 * self-corrects on first use via `U000`. Prefer permissive when a spec line is ambiguous — that
 * asymmetry is also why [HsbcProductType.Unknown] supports everything.
 */
@Serializable
data class HsbcProductCapability(
    val endpoint: AccountEndpoint,
    val supportedBy: Set<HsbcProductType>,
) {
    companion object {
        private val ALL_PRODUCTS = setOf(
            HsbcProductType.PersonalCurrentAccount,
            HsbcProductType.Savings,
            HsbcProductType.CreditCard,
            HsbcProductType.ForeignCurrency,
            HsbcProductType.GlobalMoney,
        )

        val ALL: List<HsbcProductCapability> = listOf(
            // "Supports all product types. (Personal Current Account, Savings Account,
            //  Credit Cards, Foreign Currency Accounts, Global Money)"
            HsbcProductCapability(AccountEndpoint.Balances, ALL_PRODUCTS),
            HsbcProductCapability(AccountEndpoint.Transactions, ALL_PRODUCTS),
            HsbcProductCapability(AccountEndpoint.Party, ALL_PRODUCTS),
            HsbcProductCapability(AccountEndpoint.Product, ALL_PRODUCTS),

            // "Supported product types (Credit Cards)" — statements are a credit-card-only
            // resource; the other products expose no statement document over AIS.
            HsbcProductCapability(
                AccountEndpoint.Statements,
                setOf(HsbcProductType.CreditCard),
            ),

            // "Supported product types (Personal Current Account, Savings Account,
            //  Foreign Currency Accounts, Global Money)" — credit cards excluded.
            HsbcProductCapability(
                AccountEndpoint.Beneficiaries,
                ALL_PRODUCTS - HsbcProductType.CreditCard,
            ),
            HsbcProductCapability(
                AccountEndpoint.ScheduledPayments,
                ALL_PRODUCTS - HsbcProductType.CreditCard,
            ),

            // "Supportsed product types (Personal Current Account, Foreign Currency Accounts)"
            // [sic — the typo is HSBC's]. Standing orders can run from an FX account, but not from
            // a savings account, a credit card, or the Global Money wallet.
            HsbcProductCapability(
                AccountEndpoint.StandingOrders,
                setOf(HsbcProductType.PersonalCurrentAccount, HsbcProductType.ForeignCurrency),
            ),

            // "Supported product types (Personal Current Account)" — the narrowest row. Direct
            // debits are a sterling-scheme instrument under the Direct Debit Guarantee, so unlike
            // standing orders they are not offered even on a foreign-currency account.
            HsbcProductCapability(
                AccountEndpoint.DirectDebits,
                setOf(HsbcProductType.PersonalCurrentAccount),
            ),

            // Which products may FUND a domestic payment. Established empirically — the published
            // matrix covers AIS reads only — by staging a real consent and reading the refusal:
            //   400 U021 "the DebtorAccount.Identification value is incorrect for SchemeName
            //             'UK.OBIE.SortCodeAccountNumber'"
            //   Path: Data.Initiation.DebtorAccount.Identification
            // A card has no sort code and account number, and PaymentMapper can only express that
            // one scheme, so offering it as a payer guarantees a rejection.
            //
            // A Global Money wallet is refused the same way (400 U002, same path). It reports
            // AccountTypeCode CACC, indistinguishable from a current account, so it is recognised
            // only by its free-text Description — which HsbcProductType.resolve matches, and which
            // BankAccount now carries. Excluded here rather than left to the bank: the runtime
            // capability registry is in memory, so relying on it meant the same rejection on every
            // launch. The registry still backs this up for products the matrix cannot predict.
            //
            // Deliberately still NOT a complete list. Savings and foreign-currency accounts stay
            // payable — savings was confirmed accepted as a payer on both rails, and no non-GBP
            // payer has ever been tested, so excluding one would be a guess.
            HsbcProductCapability(
                AccountEndpoint.PaymentDebtor,
                ALL_PRODUCTS - HsbcProductType.CreditCard - HsbcProductType.GlobalMoney,
            ),

            HsbcProductCapability(
                AccountEndpoint.VrpPayer,
                ALL_PRODUCTS - HsbcProductType.CreditCard - HsbcProductType.GlobalMoney,
            ),
        )

        private val BY_ENDPOINT: Map<AccountEndpoint, Set<HsbcProductType>> =
            ALL.associate { it.endpoint to it.supportedBy }

        /**
         * Whether [productType] can serve [endpoint].
         *
         * [HsbcProductType.Unknown] returns `true` for everything: a product the resolver does not
         * recognise keeps its features and is corrected by `U000` if the call actually fails. An
         * endpoint missing from the table is likewise permitted, so adding a new [AccountEndpoint]
         * cannot silently hide a feature.
         */
        fun supports(endpoint: AccountEndpoint, productType: HsbcProductType): Boolean {
            val supported = BY_ENDPOINT[endpoint]
            return productType == HsbcProductType.Unknown ||
                supported == null ||
                productType in supported
        }
    }
}
