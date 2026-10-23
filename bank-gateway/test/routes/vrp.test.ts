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
  paymentBody,
  sessionHeader,
  signIn,
  testEnv,
  TOKENS,
  verifyDetachedJws,
  VRP_CONSENT_ID,
} from "../fixtures/bank";

const VRP = "/obie/open-banking/v4.0/pisp";
const CONTROL = { ValidToDateTime: "2027-03-01T00:00:00Z", MaximumIndividualAmount: { Amount: "50.00", Currency: "GBP" } };
const vrpConsentBody = () => consentBody({ ControlParameters: CONTROL, Initiation: INITIATION });
const fundsBody = (consentId: string) => `{"Data":{"ConsentId":"${consentId}","Reference":"sweep-1","InstructedAmount":{"Amount":"10.00","Currency":"GBP"}}}`;
const row = (method: string, path: string) => matchRoute(method, path)?.route;

describe("VRP route rows (from api/Vrp.kt)", () => {
  it("map to the OBIE VRP operations; funds-confirmation is a signed POST on the PSU vrp token", () => {
    expect(row("POST", "/v1/consents/vrp")).toMatchObject({ hsbcOp: { method: "POST", path: "/v4.0/pisp/domestic-vrp-consents" }, token: "cc:payments", signJws: true, session: false, rateLimit: { period: 60, limit: 20 } });
    expect(row("GET", "/v1/consents/vrp/c1")).toMatchObject({ hsbcOp: { method: "GET", path: "/v4.0/pisp/domestic-vrp-consents/:consentId" }, token: "cc:payments", signJws: false, session: true });
    expect(row("DELETE", "/v1/consents/vrp/c1")).toMatchObject({ hsbcOp: { method: "DELETE", path: "/v4.0/pisp/domestic-vrp-consents/:consentId" }, token: "cc:payments", signJws: false, session: true });
    expect(row("POST", "/v1/consents/vrp/c1/funds-confirmation")).toMatchObject({ hsbcOp: { method: "POST", path: "/v4.0/pisp/domestic-vrp-consents/:consentId/funds-confirmation" }, token: "psu:vrp", signJws: true, session: true });
    expect(row("GET", "/v1/consents/vrp/c1/funds-confirmation")).toBeUndefined();
    expect(row("POST", "/v1/vrp/payments")).toMatchObject({ hsbcOp: { method: "POST", path: "/v4.0/pisp/domestic-vrps" }, token: "psu:vrp", signJws: true, session: true });
    expect(row("GET", "/v1/vrp/payments/p1")).toMatchObject({ hsbcOp: { method: "GET", path: "/v4.0/pisp/domestic-vrps/:paymentId" }, token: "cc:payments", signJws: false, session: true });
  });
});

describe("VRP end to end", () => {
  it("consent (signed exact bytes) → auth/complete (leg vrp, refresh stored) → funds → payment → reads → delete", async () => {
    const bank = fakeBank();
    const e = await testEnv(bank);
    const s = await signIn(e);
    const body = vrpConsentBody();
    const { out } = await authorisePay(e, "/v1/consents/vrp", body, s);
    expect(out).toMatchObject({ session: s, leg: "vrp", consentId: VRP_CONSENT_ID, consentStatus: "Authorised", consentExpiresAt: "2027-03-01T00:00:00Z" });
    expect(out.expiresAt).toBe(Date.parse("2027-03-01T00:00:00Z"));
    const create = bank.calls.find((c) => new URL(c.url).pathname === `${VRP}/domestic-vrp-consents` && c.method === "POST")!;
    expect(create.body).toBe(body);
    expect(await verifyDetachedJws(create.headers.get("x-jws-signature")!, create.body)).toBe(true);

    const stub = await sessionStub(env.SESSIONS, s);
    expect(await stub.getTokens(`vrp:${VRP_CONSENT_ID}`)).toMatchObject({ access: TOKENS.access, refresh: TOKENS.refresh });

    const fb = fundsBody(VRP_CONSENT_ID);
    const funds = await call(e, "POST", `/v1/consents/vrp/${VRP_CONSENT_ID}/funds-confirmation`, { body: fb, headers: { ...sessionHeader(s), ...idemHeader() } });
    expect(funds.status).toBe(200);
    const fundsCall = bank.calls.at(-1)!;
    expect(new URL(fundsCall.url).pathname).toBe(`${VRP}/domestic-vrp-consents/${VRP_CONSENT_ID}/funds-confirmation`);
    expect(fundsCall.method).toBe("POST");
    expect(fundsCall.body).toBe(fb);
    expect(fundsCall.headers.get("authorization")).toBe(`Bearer ${TOKENS.access}`);
    expect(await verifyDetachedJws(fundsCall.headers.get("x-jws-signature")!, fb)).toBe(true);

    const pb = paymentBody(VRP_CONSENT_ID);
    const paid = await call(e, "POST", "/v1/vrp/payments", { body: pb, headers: { ...sessionHeader(s), ...idemHeader() } });
    expect(paid.status).toBe(201);
    expect(((await paid.json()) as { Data: Record<string, string> }).Data.DomesticVRPId).toBe(PAYMENT_ID);
    const payCall = bank.calls.at(-1)!;
    expect(payCall.body).toBe(pb);
    expect(payCall.headers.get("x-fapi-financial-id")).toBeNull();
    expect(await verifyDetachedJws(payCall.headers.get("x-jws-signature")!, pb)).toBe(true);

    expect((await call(e, "GET", `/v1/vrp/payments/${PAYMENT_ID}`, { headers: sessionHeader(s) })).status).toBe(200);
    expect((await call(e, "GET", `/v1/consents/vrp/${VRP_CONSENT_ID}`, { headers: sessionHeader(s) })).status).toBe(200);
    expect(bank.calls.at(-1)!.headers.get("authorization")).toBe(`Bearer ${TOKENS.cc}`);
    const del = await call(e, "DELETE", `/v1/consents/vrp/${VRP_CONSENT_ID}`, { headers: sessionHeader(s) });
    expect(del.status).toBe(204);
    expect(bank.calls.at(-1)!.method).toBe("DELETE");
  });

  it("rejects a foreign Data.ConsentId on payment and funds-confirmation with 403 — not owned, no signature, no bank call", async () => {
    const bank = fakeBank();
    const e = await testEnv(bank);
    const s = await signIn(e);
    await authorisePay(e, "/v1/consents/vrp", vrpConsentBody(), s);
    const before = bank.calls.length;
    const pay = await call(e, "POST", "/v1/vrp/payments", { body: paymentBody("vrp-not-mine"), headers: { ...sessionHeader(s), ...idemHeader() } });
    expect(pay.status).toBe(403);
    expect(await pay.json()).toEqual({ error: "consent_not_owned" });
    // The path names the owned consent but the signed body points elsewhere — the signer is not aimed at it.
    const funds = await call(e, "POST", `/v1/consents/vrp/${VRP_CONSENT_ID}/funds-confirmation`, { body: fundsBody("vrp-not-mine"), headers: { ...sessionHeader(s), ...idemHeader() } });
    expect(funds.status).toBe(403);
    expect(await funds.json()).toEqual({ error: "consent_not_owned" });
    const other = await call(e, "POST", "/v1/consents/vrp/vrp-not-mine/funds-confirmation", { body: fundsBody("vrp-not-mine"), headers: { ...sessionHeader(s), ...idemHeader() } });
    expect(other.status).toBe(403);
    expect(bank.calls.length).toBe(before);
    expect((await call(e, "GET", "/v1/consents/vrp/vrp-not-mine", { headers: sessionHeader(s) })).status).toBe(403);
  });

  it("a 401 on a VRP payment refreshes once and retries with the same signature (one RSA signature per request)", async () => {
    const bank = fakeBank({ rejectFirstAccess: true });
    const e = await testEnv(bank);
    const { out } = await authorisePay(e, "/v1/consents/vrp", vrpConsentBody());
    const res = await call(e, "POST", "/v1/vrp/payments", { body: paymentBody(VRP_CONSENT_ID), headers: { ...sessionHeader(out.session), ...idemHeader() } });
    expect(res.status).toBe(201);
    expect(bank.grants.filter((g) => g === "refresh_token")).toHaveLength(1);
    const tries = bank.calls.filter((c) => c.url.endsWith("/domestic-vrps"));
    expect(tries.map((c) => c.headers.get("authorization"))).toEqual([`Bearer ${TOKENS.access}`, `Bearer ${TOKENS.newAccess}`]);
    expect(tries[0]!.headers.get("x-jws-signature")).toBe(tries[1]!.headers.get("x-jws-signature"));
    expect(tries[0]!.headers.get("x-idempotency-key")).toBe(tries[1]!.headers.get("x-idempotency-key"));
    const exposedOut = await exposed(res);
    for (const t of ALL_TOKENS) expect(exposedOut).not.toContain(t);
  });

  it("502 when the bank's VRP authorisation returns no refresh token (VRP needs one per consent)", async () => {
    const e = await testEnv(fakeBank({ noRefreshToken: true }));
    await expect(authorisePay(e, "/v1/consents/vrp", vrpConsentBody())).rejects.toThrow(/502/);
  });
});
