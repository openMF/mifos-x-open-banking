/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

/*
 * The closed route table — the Worker's single allowlist (D7). Anything not listed here is a 404 that
 * never reaches HSBC. `session` rows need `Authorization: Session <id>` (gate.ts); `rateLimit` rows are
 * counted per install + IP (limits.ts). Both are enforced in index.ts before any handler runs. `signJws`
 * rows forward the client's bytes unchanged with a detached JWS over exactly those bytes (handlers/payment.ts).
 * `attest` rows (every consent POST and auth/complete) need a device attestation from android / ios
 * clients (attest/gate.ts), checked after the rate limit and session.
 */

export type TokenKind = "cc:accounts" | "cc:payments" | "psu:ais" | "psu:payment" | "psu:vrp" | "none";
export type Method = "GET" | "POST" | "DELETE";

export interface Route {
  id: string;
  method: Method;
  path: string; // template, e.g. "/v1/accounts/:accountId/balances"
  hsbcOp: { method: Method; path: string }; // HSBC-side operation, relative to /obie/open-banking
  token: TokenKind;
  signJws: boolean;
  maxBodyBytes: number;
  attest: boolean;
  session: boolean;
  rateLimit?: { period: 10 | 60; limit: number };
  handler?: string; // HANDLERS key; defaults to id
  product?: string; // PIS / VRP payment product — keys the session leg payment:{product}:{consentId} / vrp:{consentId}
}

const CONSENT_RATE = { period: 60, limit: 20 } as const;
const AIS_CONSENT = "/v4.0/aisp/account-access-consents";
const AIS = "/v4.0/aisp";
const SIGNED_BODY = 32_768;

/** A session-gated AIS GET served by the generic aisRead handler. */
const aisRead = (id: string, path: string, hsbcPath: string, handler = "aisRead"): Route => ({
  id, method: "GET", path, hsbcOp: { method: "GET", path: hsbcPath }, token: "psu:ais", signJws: false, maxBodyBytes: 0, attest: false, session: true, handler,
});

// prettier-ignore
export const ROUTES: readonly Route[] = [
  { id: "health", method: "GET", path: "/v1/health", hsbcOp: { method: "GET", path: "/__none__" }, token: "none", signJws: false, maxBodyBytes: 0, attest: false, session: false },

  // Attestation (phase 06): a single-use challenge, and one-off App Attest key registration.
  { id: "attestChallenge", method: "POST", path: "/v1/attest/challenge", hsbcOp: { method: "POST", path: "/__none__" }, token: "none", signJws: false, maxBodyBytes: 256, attest: false, session: false, rateLimit: CONSENT_RATE },
  { id: "attestIosRegister", method: "POST", path: "/v1/attest/ios/register", hsbcOp: { method: "POST", path: "/__none__" }, token: "none", signJws: false, maxBodyBytes: 16_384, attest: false, session: false, rateLimit: CONSENT_RATE },

  // AIS consent + sign-in (the app has no session yet, so these are rate-limited instead).
  { id: "consentAisCreate", method: "POST", path: "/v1/consents/ais", hsbcOp: { method: "POST", path: AIS_CONSENT }, token: "cc:accounts", signJws: false, maxBodyBytes: 8_192, attest: true, session: false, rateLimit: CONSENT_RATE },
  { id: "consentAisGet", method: "GET", path: "/v1/consents/ais/:consentId", hsbcOp: { method: "GET", path: `${AIS_CONSENT}/:consentId` }, token: "cc:accounts", signJws: false, maxBodyBytes: 0, attest: false, session: true, handler: "ccRead" },
  { id: "consentAisDelete", method: "DELETE", path: "/v1/consents/ais/:consentId", hsbcOp: { method: "DELETE", path: `${AIS_CONSENT}/:consentId` }, token: "cc:accounts", signJws: false, maxBodyBytes: 0, attest: false, session: true, handler: "ccRead" },
  { id: "authComplete", method: "POST", path: "/v1/auth/complete", hsbcOp: { method: "POST", path: "/v1.1/oauth2/token" }, token: "none", signJws: false, maxBodyBytes: 16_384, attest: true, session: false, rateLimit: CONSENT_RATE },
  { id: "sessionLogout", method: "POST", path: "/v1/session/logout", hsbcOp: { method: "DELETE", path: `${AIS_CONSENT}/:consentId` }, token: "cc:accounts", signJws: false, maxBodyBytes: 256, attest: false, session: true },

  // AIS reads — one row per Aisp.kt call.
  aisRead("aisAccounts", "/v1/ais/accounts", `${AIS}/accounts`),
  aisRead("aisAccount", "/v1/ais/accounts/:accountId", `${AIS}/accounts/:accountId`),
  aisRead("aisBalances", "/v1/ais/accounts/:accountId/balances", `${AIS}/accounts/:accountId/balances`),
  aisRead("aisBeneficiaries", "/v1/ais/accounts/:accountId/beneficiaries", `${AIS}/accounts/:accountId/beneficiaries`),
  aisRead("aisDirectDebits", "/v1/ais/accounts/:accountId/direct-debits", `${AIS}/accounts/:accountId/direct-debits`),
  aisRead("aisProduct", "/v1/ais/accounts/:accountId/product", `${AIS}/accounts/:accountId/product`),
  aisRead("aisParty", "/v1/ais/accounts/:accountId/party", `${AIS}/accounts/:accountId/party`),
  aisRead("aisParties", "/v1/ais/accounts/:accountId/parties", `${AIS}/accounts/:accountId/parties`),
  aisRead("aisTransactions", "/v1/ais/accounts/:accountId/transactions", `${AIS}/accounts/:accountId/transactions`),
  aisRead("aisStandingOrders", "/v1/ais/accounts/:accountId/standing-orders", `${AIS}/accounts/:accountId/standing-orders`),
  aisRead("aisScheduledPayments", "/v1/ais/accounts/:accountId/scheduled-payments", `${AIS}/accounts/:accountId/scheduled-payments`),
  aisRead("aisStatements", "/v1/ais/accounts/:accountId/statements", `${AIS}/accounts/:accountId/statements`),
  aisRead("aisStatement", "/v1/ais/accounts/:accountId/statements/:statementId", `${AIS}/accounts/:accountId/statements/:statementId`),
  aisRead("aisStatementTransactions", "/v1/ais/accounts/:accountId/statements/:statementId/transactions", `${AIS}/accounts/:accountId/statements/:statementId/transactions`),
  aisRead("aisStatementFile", "/v1/ais/accounts/:accountId/statements/:statementId/file", `${AIS}/accounts/:accountId/statements/:statementId/file`, "aisStatementFile"),
  aisRead("aisPage", "/v1/ais/page", `${AIS}/*`, "aisPage"), // opaque cursor → a stored HSBC Links URL

  // PIS — per product (api/Pisp.kt): consent create (signed, cc), consent read, payment create (signed, PSU),
  // payment read; a funds check only on the immediate rails (OBIE / HSBC define none for the others).
  { id: "pisDomesticConsentCreate", method: "POST", path: "/v1/consents/payment/domestic", hsbcOp: { method: "POST", path: "/v4.0/pisp/domestic-payment-consents" }, token: "cc:payments", signJws: true, maxBodyBytes: SIGNED_BODY, attest: true, session: false, rateLimit: CONSENT_RATE, product: "domestic", handler: "paymentConsentCreate" },
  { id: "pisDomesticConsentGet", method: "GET", path: "/v1/consents/payment/domestic/:consentId", hsbcOp: { method: "GET", path: "/v4.0/pisp/domestic-payment-consents/:consentId" }, token: "cc:payments", signJws: false, maxBodyBytes: 0, attest: false, session: true, product: "domestic", handler: "ccRead" },
  { id: "pisDomesticFunds", method: "GET", path: "/v1/consents/payment/domestic/:consentId/funds-confirmation", hsbcOp: { method: "GET", path: "/v4.0/pisp/domestic-payment-consents/:consentId/funds-confirmation" }, token: "psu:payment", signJws: false, maxBodyBytes: 0, attest: false, session: true, product: "domestic", handler: "fundsRead" },
  { id: "pisDomesticPaymentCreate", method: "POST", path: "/v1/payments/domestic", hsbcOp: { method: "POST", path: "/v4.0/pisp/domestic-payments" }, token: "psu:payment", signJws: true, maxBodyBytes: SIGNED_BODY, attest: false, session: true, product: "domestic", handler: "signedPsuPost" },
  { id: "pisDomesticPaymentGet", method: "GET", path: "/v1/payments/domestic/:paymentId", hsbcOp: { method: "GET", path: "/v4.0/pisp/domestic-payments/:paymentId" }, token: "cc:payments", signJws: false, maxBodyBytes: 0, attest: false, session: true, product: "domestic", handler: "ccRead" },
  { id: "pisInternationalConsentCreate", method: "POST", path: "/v1/consents/payment/international", hsbcOp: { method: "POST", path: "/v4.0/pisp/international-payment-consents" }, token: "cc:payments", signJws: true, maxBodyBytes: SIGNED_BODY, attest: true, session: false, rateLimit: CONSENT_RATE, product: "international", handler: "paymentConsentCreate" },
  { id: "pisInternationalConsentGet", method: "GET", path: "/v1/consents/payment/international/:consentId", hsbcOp: { method: "GET", path: "/v4.0/pisp/international-payment-consents/:consentId" }, token: "cc:payments", signJws: false, maxBodyBytes: 0, attest: false, session: true, product: "international", handler: "ccRead" },
  { id: "pisInternationalFunds", method: "GET", path: "/v1/consents/payment/international/:consentId/funds-confirmation", hsbcOp: { method: "GET", path: "/v4.0/pisp/international-payment-consents/:consentId/funds-confirmation" }, token: "psu:payment", signJws: false, maxBodyBytes: 0, attest: false, session: true, product: "international", handler: "fundsRead" },
  { id: "pisInternationalPaymentCreate", method: "POST", path: "/v1/payments/international", hsbcOp: { method: "POST", path: "/v4.0/pisp/international-payments" }, token: "psu:payment", signJws: true, maxBodyBytes: SIGNED_BODY, attest: false, session: true, product: "international", handler: "signedPsuPost" },
  { id: "pisInternationalPaymentGet", method: "GET", path: "/v1/payments/international/:paymentId", hsbcOp: { method: "GET", path: "/v4.0/pisp/international-payments/:paymentId" }, token: "cc:payments", signJws: false, maxBodyBytes: 0, attest: false, session: true, product: "international", handler: "ccRead" },
  { id: "pisDomesticScheduledConsentCreate", method: "POST", path: "/v1/consents/payment/domestic-scheduled", hsbcOp: { method: "POST", path: "/v4.0/pisp/domestic-scheduled-payment-consents" }, token: "cc:payments", signJws: true, maxBodyBytes: SIGNED_BODY, attest: true, session: false, rateLimit: CONSENT_RATE, product: "domestic-scheduled", handler: "paymentConsentCreate" },
  { id: "pisDomesticScheduledConsentGet", method: "GET", path: "/v1/consents/payment/domestic-scheduled/:consentId", hsbcOp: { method: "GET", path: "/v4.0/pisp/domestic-scheduled-payment-consents/:consentId" }, token: "cc:payments", signJws: false, maxBodyBytes: 0, attest: false, session: true, product: "domestic-scheduled", handler: "ccRead" },
  { id: "pisDomesticScheduledPaymentCreate", method: "POST", path: "/v1/payments/domestic-scheduled", hsbcOp: { method: "POST", path: "/v4.0/pisp/domestic-scheduled-payments" }, token: "psu:payment", signJws: true, maxBodyBytes: SIGNED_BODY, attest: false, session: true, product: "domestic-scheduled", handler: "signedPsuPost" },
  { id: "pisDomesticScheduledPaymentGet", method: "GET", path: "/v1/payments/domestic-scheduled/:paymentId", hsbcOp: { method: "GET", path: "/v4.0/pisp/domestic-scheduled-payments/:paymentId" }, token: "cc:payments", signJws: false, maxBodyBytes: 0, attest: false, session: true, product: "domestic-scheduled", handler: "ccRead" },
  { id: "pisInternationalScheduledConsentCreate", method: "POST", path: "/v1/consents/payment/international-scheduled", hsbcOp: { method: "POST", path: "/v4.0/pisp/international-scheduled-payment-consents" }, token: "cc:payments", signJws: true, maxBodyBytes: SIGNED_BODY, attest: true, session: false, rateLimit: CONSENT_RATE, product: "international-scheduled", handler: "paymentConsentCreate" },
  { id: "pisInternationalScheduledConsentGet", method: "GET", path: "/v1/consents/payment/international-scheduled/:consentId", hsbcOp: { method: "GET", path: "/v4.0/pisp/international-scheduled-payment-consents/:consentId" }, token: "cc:payments", signJws: false, maxBodyBytes: 0, attest: false, session: true, product: "international-scheduled", handler: "ccRead" },
  { id: "pisInternationalScheduledPaymentCreate", method: "POST", path: "/v1/payments/international-scheduled", hsbcOp: { method: "POST", path: "/v4.0/pisp/international-scheduled-payments" }, token: "psu:payment", signJws: true, maxBodyBytes: SIGNED_BODY, attest: false, session: true, product: "international-scheduled", handler: "signedPsuPost" },
  { id: "pisInternationalScheduledPaymentGet", method: "GET", path: "/v1/payments/international-scheduled/:paymentId", hsbcOp: { method: "GET", path: "/v4.0/pisp/international-scheduled-payments/:paymentId" }, token: "cc:payments", signJws: false, maxBodyBytes: 0, attest: false, session: true, product: "international-scheduled", handler: "ccRead" },
  { id: "pisDomesticStandingOrderConsentCreate", method: "POST", path: "/v1/consents/payment/domestic-standing-order", hsbcOp: { method: "POST", path: "/v4.0/pisp/domestic-standing-order-consents" }, token: "cc:payments", signJws: true, maxBodyBytes: SIGNED_BODY, attest: true, session: false, rateLimit: CONSENT_RATE, product: "domestic-standing-order", handler: "paymentConsentCreate" },
  { id: "pisDomesticStandingOrderConsentGet", method: "GET", path: "/v1/consents/payment/domestic-standing-order/:consentId", hsbcOp: { method: "GET", path: "/v4.0/pisp/domestic-standing-order-consents/:consentId" }, token: "cc:payments", signJws: false, maxBodyBytes: 0, attest: false, session: true, product: "domestic-standing-order", handler: "ccRead" },
  { id: "pisDomesticStandingOrderPaymentCreate", method: "POST", path: "/v1/payments/domestic-standing-order", hsbcOp: { method: "POST", path: "/v4.0/pisp/domestic-standing-orders" }, token: "psu:payment", signJws: true, maxBodyBytes: SIGNED_BODY, attest: false, session: true, product: "domestic-standing-order", handler: "signedPsuPost" },
  { id: "pisDomesticStandingOrderPaymentGet", method: "GET", path: "/v1/payments/domestic-standing-order/:paymentId", hsbcOp: { method: "GET", path: "/v4.0/pisp/domestic-standing-orders/:paymentId" }, token: "cc:payments", signJws: false, maxBodyBytes: 0, attest: false, session: true, product: "domestic-standing-order", handler: "ccRead" },
  { id: "pisInternationalStandingOrderConsentCreate", method: "POST", path: "/v1/consents/payment/international-standing-order", hsbcOp: { method: "POST", path: "/v4.0/pisp/international-standing-order-consents" }, token: "cc:payments", signJws: true, maxBodyBytes: SIGNED_BODY, attest: true, session: false, rateLimit: CONSENT_RATE, product: "international-standing-order", handler: "paymentConsentCreate" },
  { id: "pisInternationalStandingOrderConsentGet", method: "GET", path: "/v1/consents/payment/international-standing-order/:consentId", hsbcOp: { method: "GET", path: "/v4.0/pisp/international-standing-order-consents/:consentId" }, token: "cc:payments", signJws: false, maxBodyBytes: 0, attest: false, session: true, product: "international-standing-order", handler: "ccRead" },
  { id: "pisInternationalStandingOrderPaymentCreate", method: "POST", path: "/v1/payments/international-standing-order", hsbcOp: { method: "POST", path: "/v4.0/pisp/international-standing-orders" }, token: "psu:payment", signJws: true, maxBodyBytes: SIGNED_BODY, attest: false, session: true, product: "international-standing-order", handler: "signedPsuPost" },
  { id: "pisInternationalStandingOrderPaymentGet", method: "GET", path: "/v1/payments/international-standing-order/:paymentId", hsbcOp: { method: "GET", path: "/v4.0/pisp/international-standing-orders/:paymentId" }, token: "cc:payments", signJws: false, maxBodyBytes: 0, attest: false, session: true, product: "international-standing-order", handler: "ccRead" },

  // VRP (api/Vrp.kt) — unlike PIS, the funds check is a signed POST on the PSU token.
  { id: "vrpConsentCreate", method: "POST", path: "/v1/consents/vrp", hsbcOp: { method: "POST", path: "/v4.0/pisp/domestic-vrp-consents" }, token: "cc:payments", signJws: true, maxBodyBytes: SIGNED_BODY, attest: true, session: false, rateLimit: CONSENT_RATE, product: "vrp", handler: "paymentConsentCreate" },
  { id: "vrpConsentGet", method: "GET", path: "/v1/consents/vrp/:consentId", hsbcOp: { method: "GET", path: "/v4.0/pisp/domestic-vrp-consents/:consentId" }, token: "cc:payments", signJws: false, maxBodyBytes: 0, attest: false, session: true, product: "vrp", handler: "ccRead" },
  { id: "vrpConsentDelete", method: "DELETE", path: "/v1/consents/vrp/:consentId", hsbcOp: { method: "DELETE", path: "/v4.0/pisp/domestic-vrp-consents/:consentId" }, token: "cc:payments", signJws: false, maxBodyBytes: 0, attest: false, session: true, product: "vrp", handler: "ccRead" },
  { id: "vrpFunds", method: "POST", path: "/v1/consents/vrp/:consentId/funds-confirmation", hsbcOp: { method: "POST", path: "/v4.0/pisp/domestic-vrp-consents/:consentId/funds-confirmation" }, token: "psu:vrp", signJws: true, maxBodyBytes: 16_384, attest: true, session: true, product: "vrp", handler: "signedPsuPost" },
  { id: "vrpPaymentCreate", method: "POST", path: "/v1/vrp/payments", hsbcOp: { method: "POST", path: "/v4.0/pisp/domestic-vrps" }, token: "psu:vrp", signJws: true, maxBodyBytes: SIGNED_BODY, attest: false, session: true, product: "vrp", handler: "signedPsuPost" },
  { id: "vrpPaymentGet", method: "GET", path: "/v1/vrp/payments/:paymentId", hsbcOp: { method: "GET", path: "/v4.0/pisp/domestic-vrps/:paymentId" }, token: "cc:payments", signJws: false, maxBodyBytes: 0, attest: false, session: true, product: "vrp", handler: "ccRead" },
];

const PARAM = /^[\w.-]{1,128}$/;

/** The HSBC path for a route template with its captured params, or null if a param is not a plain id. */
export function bankPath(template: string, params: Record<string, string>): string | null {
  let bad = false;
  const path = template.replace(/:([A-Za-z]+)/g, (_, name: string) => {
    const v = params[name] ?? "";
    if (!PARAM.test(v)) bad = true;
    return v;
  });
  return bad ? null : path;
}

/** The session leg a payment product's consent and PSU token live under. */
export const payLeg = (product: string, consentId: string) => (product === "vrp" ? `vrp:${consentId}` : `payment:${product}:${consentId}`);
