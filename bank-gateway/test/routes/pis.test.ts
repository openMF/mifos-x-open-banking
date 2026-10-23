/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

import { env } from "cloudflare:test";
import { describe, expect, it } from "vitest";
import { CLAIM_ISS } from "../../src/crypto/detachedJws";
import { matchRoute } from "../../src/router";
import { sessionStub } from "../../src/session/SessionDO";
import {
  ALL_TOKENS,
  authorisePay,
  call,
  consentBody,
  exposed,
  fakeBank,
  idemHeader,
  INITIATION,
  PAYMENT_ID,
  payConsentId,
  paymentBody,
  sessionHeader,
  signIn,
  SIGNING_ISSUER,
  testEnv,
  TOKENS,
  verifyDetachedJws,
} from "../fixtures/bank";
import { decodeJson } from "../fixtures/keys";

// [product, HSBC consents segment, HSBC payments segment, has funds-confirmation] — from api/Pisp.kt.
const PRODUCTS = [
  ["domestic", "domestic-payment-consents", "domestic-payments", true],
  ["international", "international-payment-consents", "international-payments", true],
  ["domestic-scheduled", "domestic-scheduled-payment-consents", "domestic-scheduled-payments", false],
  ["international-scheduled", "international-scheduled-payment-consents", "international-scheduled-payments", false],
  ["domestic-standing-order", "domestic-standing-order-consents", "domestic-standing-orders", false],
  ["international-standing-order", "international-standing-order-consents", "international-standing-orders", false],
] as const;

const PIS = "/obie/open-banking/v4.0/pisp";
const row = (method: string, path: string) => matchRoute(method, path)?.route;

describe.each(PRODUCTS)("PIS %s", (product, consents, payments, hasFunds) => {
  const consentPath = `/v1/consents/payment/${product}`;
  const paymentPath = `/v1/payments/${product}`;
  const consentId = payConsentId(consents);

  it("route rows map to the OBIE operations with the right token, signing and gating", () => {
    expect(row("POST", consentPath)).toMatchObject({ hsbcOp: { method: "POST", path: `/v4.0/pisp/${consents}` }, token: "cc:payments", signJws: true, session: false, rateLimit: { period: 60, limit: 20 } });
    expect(row("GET", `${consentPath}/c1`)).toMatchObject({ hsbcOp: { method: "GET", path: `/v4.0/pisp/${consents}/:consentId` }, token: "cc:payments", signJws: false, session: true });
    expect(row("POST", paymentPath)).toMatchObject({ hsbcOp: { method: "POST", path: `/v4.0/pisp/${payments}` }, token: "psu:payment", signJws: true, session: true });
    expect(row("GET", `${paymentPath}/p1`)).toMatchObject({ hsbcOp: { method: "GET", path: `/v4.0/pisp/${payments}/:paymentId` }, token: "cc:payments", signJws: false, session: true });
    const funds = row("GET", `${consentPath}/c1/funds-confirmation`);
    if (hasFunds) expect(funds).toMatchObject({ hsbcOp: { method: "GET", path: `/v4.0/pisp/${consents}/:consentId/funds-confirmation` }, token: "psu:payment", signJws: false, session: true });
    else expect(funds).toBeUndefined();
  });

  it("consent create forwards and signs the exact client bytes with a cc:payments token, returns {flowId, consentId, authorizeUrl}", async () => {
    const bank = fakeBank();
    const e = await testEnv(bank);
    const body = consentBody();
    const res = await call(e, "POST", consentPath, { body, headers: { "X-Idempotency-Key": "consent-key-1" } });
    expect(res.status).toBe(200);
    const out = (await res.json()) as { flowId: string; consentId: string; authorizeUrl: string };
    expect(Object.keys(out).sort()).toEqual(["authorizeUrl", "consentId", "flowId"]);
    expect(out.consentId).toBe(consentId);
    const u = new URL(out.authorizeUrl);
    expect(u.host).toBe(env.HSBC_AUTHORIZE_HOST);
    expect(decodeJson(u.searchParams.get("request")!.split(".")[1]!)).toMatchObject({ scope: "openid payments", claims: { id_token: { openbanking_intent_id: { value: consentId } } } });

    const sent = bank.calls.find((c) => new URL(c.url).pathname === `${PIS}/${consents}`)!;
    expect(sent.body).toBe(body); // byte-for-byte — no re-serialisation
    expect(sent.headers.get("authorization")).toBe(`Bearer ${TOKENS.cc}`);
    expect(sent.headers.get("x-idempotency-key")).toBe("consent-key-1");
    expect(sent.headers.get("x-fapi-financial-id")).toBeNull(); // the app leaves it blank
    expect(sent.headers.get("x-fapi-interaction-id")).toMatch(/^[0-9a-f-]{36}$/);
    const jws = sent.headers.get("x-jws-signature")!;
    expect(await verifyDetachedJws(jws, sent.body)).toBe(true);
    expect(await verifyDetachedJws(jws, `${body} `)).toBe(false); // the signature covers these bytes and no others
    expect(decodeJson(jws.split(".")[0]!)[CLAIM_ISS]).toBe(SIGNING_ISSUER);
  });

  it("auth/complete (leg payment) → consent read, payment create (signed, PSU token), payment read", async () => {
    const bank = fakeBank();
    const e = await testEnv(bank);
    const s = await signIn(e);
    const { out } = await authorisePay(e, consentPath, consentBody(), s);
    expect(out).toMatchObject({ session: s, leg: "payment", consentId, consentStatus: "Authorised", consentExpiresAt: null });

    const got = await call(e, "GET", `${consentPath}/${consentId}`, { headers: sessionHeader(s) });
    expect(got.status).toBe(200);
    expect(bank.calls.at(-1)!.headers.get("authorization")).toBe(`Bearer ${TOKENS.cc}`);
    expect(new URL(bank.calls.at(-1)!.url).pathname).toBe(`${PIS}/${consents}/${consentId}`);

    if (hasFunds) {
      const funds = await call(e, "GET", `${consentPath}/${consentId}/funds-confirmation`, { headers: sessionHeader(s) });
      expect(funds.status).toBe(200);
      expect(await funds.json()).toEqual({ Data: { FundsAvailableResult: { FundsAvailable: true } } });
      expect(bank.calls.at(-1)!.headers.get("authorization")).toBe(`Bearer ${TOKENS.access}`);
    }

    const body = paymentBody(consentId);
    const paid = await call(e, "POST", paymentPath, { body, headers: { ...sessionHeader(s), "X-Idempotency-Key": "pay-key-1" } });
    expect(paid.status).toBe(201);
    expect(((await paid.json()) as { Data: Record<string, string> }).Data.Status).toBe("AcceptedSettlementInProcess");
    const sent = bank.calls.at(-1)!;
    expect(new URL(sent.url).pathname).toBe(`${PIS}/${payments}`);
    expect(sent.body).toBe(body);
    expect(sent.headers.get("authorization")).toBe(`Bearer ${TOKENS.access}`);
    expect(sent.headers.get("x-idempotency-key")).toBe("pay-key-1");
    expect(sent.headers.get("x-fapi-financial-id")).toBeNull();
    expect(await verifyDetachedJws(sent.headers.get("x-jws-signature")!, sent.body)).toBe(true);

    const read = await call(e, "GET", `${paymentPath}/${PAYMENT_ID}`, { headers: sessionHeader(s) });
    expect(read.status).toBe(200);
    expect(bank.calls.at(-1)!.headers.get("authorization")).toBe(`Bearer ${TOKENS.cc}`);
    const foreign = await call(e, "GET", `${paymentPath}/pmt-someone-else`, { headers: sessionHeader(s) });
    expect(foreign.status).toBe(403);
    expect(await foreign.json()).toEqual({ error: "payment_not_in_session" });
  });

  it("rejects a Data.ConsentId not owned by this session with 403 consent_not_owned — no signature, no bank call", async () => {
    const bank = fakeBank();
    const e = await testEnv(bank);
    const s = await signIn(e);
    const before = bank.calls.length;
    const res = await call(e, "POST", paymentPath, { body: paymentBody("pc-someone-elses"), headers: { ...sessionHeader(s), ...idemHeader() } });
    expect(res.status).toBe(403);
    expect(await res.json()).toEqual({ error: "consent_not_owned" });
    expect(bank.calls.length).toBe(before);

    const foreignRead = await call(e, "GET", `${consentPath}/pc-someone-elses`, { headers: sessionHeader(s) });
    expect(foreignRead.status).toBe(403);
    expect(await foreignRead.json()).toEqual({ error: "consent_not_in_session" });
  });
});

describe("PIS consent ownership is per product and per session", () => {
  it("a domestic consent cannot be paid through the international route, nor from another session", async () => {
    const e = await testEnv(fakeBank());
    const s = await signIn(e);
    const { out } = await authorisePay(e, "/v1/consents/payment/domestic", consentBody(), s);
    const cross = await call(e, "POST", "/v1/payments/international", { body: paymentBody(out.consentId), headers: { ...sessionHeader(s), ...idemHeader() } });
    expect(cross.status).toBe(403);
    expect(await cross.json()).toEqual({ error: "consent_not_owned" });

    const other = await signIn(e);
    const stolen = await call(e, "POST", "/v1/payments/domestic", { body: paymentBody(out.consentId), headers: { ...sessionHeader(other), ...idemHeader() } });
    expect(stolen.status).toBe(403);
    expect(await stolen.json()).toEqual({ error: "consent_not_owned" });
  });

  it("auth/complete without a session mints one carrying the payment leg; with an unknown session it is 401", async () => {
    const e = await testEnv(fakeBank());
    const { out } = await authorisePay(e, "/v1/consents/payment/domestic", consentBody());
    expect(out.session).toMatch(/^[\w-]{43}$/);
    const stub = await sessionStub(env.SESSIONS, out.session);
    const tokens = await stub.getTokens(`payment:domestic:${out.consentId}`);
    expect(tokens?.access).toBe(TOKENS.access);
    expect(tokens?.refresh).toBeUndefined(); // single payments: no refresh counterpart, even though the bank sent one
    expect(out.expiresAt).toBeGreaterThan(Date.now());
    expect(out.expiresAt).toBeLessThanOrEqual(Date.now() + 300_000);

    await expect(authorisePay(e, "/v1/consents/payment/domestic", consentBody(), "A".repeat(43))).rejects.toThrow(/401/);
  });

  it("409 consent_rejected discards the payment tokens", async () => {
    const e = await testEnv(fakeBank({ consentStatus: "Rejected" }));
    await expect(authorisePay(e, "/v1/consents/payment/domestic", consentBody())).rejects.toThrow(/409/);
  });
});

describe("signed POST guard — refusals before any signing work", () => {
  it("X-Idempotency-Key is mandatory, ≤ 40 chars, token characters only", async () => {
    const bank = fakeBank();
    const e = await testEnv(bank);
    const cases: Record<string, string>[] = [{}, { "X-Idempotency-Key": "x".repeat(41) }, { "X-Idempotency-Key": "has space" }, { "X-Idempotency-Key": "" }];
    for (const headers of cases) {
      const res = await call(e, "POST", "/v1/consents/payment/domestic", { body: consentBody(), headers });
      expect(res.status, JSON.stringify(headers)).toBe(400);
      expect(await res.json()).toEqual({ error: "idempotency_key" });
    }
    expect(bank.calls).toHaveLength(0);
    const ok = await call(e, "POST", "/v1/consents/payment/domestic", { body: consentBody(), headers: { "X-Idempotency-Key": "k".repeat(40) } });
    expect(ok.status).toBe(200);
  });

  it("400 for a body that is not an OBIE object or lacks Data.Initiation; 415 non-JSON; 413 oversize", async () => {
    const bank = fakeBank();
    const e = await testEnv(bank);
    const post = (body: string, headers: Record<string, string> = {}) => call(e, "POST", "/v1/consents/payment/domestic", { body, headers: { ...idemHeader(), ...headers } });
    expect(await (await post("not json")).json()).toEqual({ error: "invalid_request" });
    expect(await (await post("[]")).json()).toEqual({ error: "invalid_request" });
    const noInit = await post(consentBody({ Permission: "Create" }));
    expect(noInit.status).toBe(400);
    expect(await noInit.json()).toEqual({ error: "missing_initiation" });
    expect((await post(consentBody(), { "content-type": "text/plain" })).status).toBe(415);
    expect((await post(consentBody({ Initiation: { ...INITIATION, pad: "x".repeat(40_000) } }))).status).toBe(413);
    expect(bank.calls).toHaveLength(0);
  });

  it("400 body_not_compact for whitespace-formatted JSON — HSBC verifies the JWS over compact JSON (U015 otherwise)", async () => {
    const bank = fakeBank();
    const e = await testEnv(bank);
    for (const body of [`${consentBody()}\n`, JSON.stringify(JSON.parse(consentBody()), null, 2), ` ${consentBody()}`]) {
      const res = await call(e, "POST", "/v1/consents/payment/domestic", { body, headers: idemHeader() });
      expect(res.status).toBe(400);
      expect(await res.json()).toEqual({ error: "body_not_compact" });
    }
    expect(bank.calls).toHaveLength(0);
  });

  it("a payment body without Data.ConsentId or Data.Initiation is refused", async () => {
    const e = await testEnv(fakeBank());
    const s = await signIn(e);
    const noConsent = await call(e, "POST", "/v1/payments/domestic", { body: `{"Data":{"Initiation":{}},"Risk":{}}`, headers: { ...sessionHeader(s), ...idemHeader() } });
    expect(noConsent.status).toBe(400);
    expect(await noConsent.json()).toEqual({ error: "invalid_request" });
    const noInit = await call(e, "POST", "/v1/payments/domestic", { body: paymentBody("pc-x", {}), headers: { ...sessionHeader(s), ...idemHeader() } });
    expect(noInit.status).toBe(400);
    expect(await noInit.json()).toEqual({ error: "missing_initiation" });
  });

  it("a bank refusal returns its error codes and status, never a token", async () => {
    const bank = fakeBank();
    const e = await testEnv({ ...bank, fetcher: { fetch: async (u: RequestInfo | URL, i?: RequestInit) => (String(u).endsWith("-consents") ? new Response(JSON.stringify({ Code: "400", Errors: [{ ErrorCode: "U004", Path: "Data.Initiation.RemittanceInformation", Message: "Field missing" }] }), { status: 400, headers: { "content-type": "application/json" } }) : bank.fetcher.fetch(u, i)) } as unknown as Fetcher });
    const res = await call(e, "POST", "/v1/consents/payment/domestic", { body: consentBody(), headers: idemHeader() });
    expect(res.status).toBe(400);
    const out = await exposed(res);
    for (const t of ALL_TOKENS) expect(out).not.toContain(t);
    expect(await res.json()).toEqual({ error: "bank_error", bankStatus: 400, bankError: "400 ; U004 | Data.Initiation.RemittanceInformation | Field missing" });
  });
});
