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
import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import { psuFetch } from "../src/hsbc/client";
import worker, { handle, redact } from "../src/index";
import { ROUTES, type Route } from "../src/routes";
import { newSession, sessionStub } from "../src/session/SessionDO";
import {
  ALL_TOKENS,
  authorisePay,
  call,
  CONSENT_ID,
  consentBody,
  exposed as exposedAll,
  fakeBank,
  idemHeader,
  INITIATION,
  PAYMENT_ID,
  payConsentId,
  paymentBody,
  sessionHeader,
  signIn,
  testEnv,
  VRP_CONSENT_ID,
} from "./fixtures/bank";
import { fakeMtls, jsonResponse } from "./fixtures/fakeMtls";
import { generateTestKeyPairPem } from "./fixtures/keys";

// Known token values: custody (D8 / AC8) means none of them ever leaves the Worker.
const ACCESS = "fixture-access-token-6f1d2c";
const REFRESH = "fixture-refresh-token-9a8b7c";
const ID_TOKEN = "fixture-id-token-eyJ0ZXN0";
const NEW_ACCESS = "fixture-new-access-token-55aa";
const SECRETS = [ACCESS, REFRESH, ID_TOKEN, NEW_ACCESS];

const tokenBody = { access_token: ACCESS, refresh_token: REFRESH, id_token: ID_TOKEN, token_type: "Bearer", expires_in: 300 };
const nowSec = () => Math.floor(Date.now() / 1000);

let pem = "";
beforeAll(async () => {
  pem = (await generateTestKeyPairPem()).privatePkcs8Pem;
});
afterEach(() => vi.restoreAllMocks());

function captureLogs() {
  const lines: string[] = [];
  for (const m of ["log", "info", "warn", "error"] as const) {
    vi.spyOn(console, m).mockImplementation((...a: unknown[]) => void lines.push(a.map(String).join(" ")));
  }
  return lines;
}

async function exposed(res: Response): Promise<string> {
  return `${[...res.headers].map(([k, v]) => `${k}: ${v}`).join("\n")}\n${await res.text()}`;
}

describe("token custody (D8 / AC8)", () => {
  it("no registered route returns a token in its body or headers", async () => {
    const mtls = fakeMtls(() => jsonResponse(tokenBody));
    const logs = captureLogs();
    for (const r of ROUTES) {
      const path = r.path.replace(/:[A-Za-z]+/g, "x");
      const init: RequestInit = { method: r.method };
      if (r.maxBodyBytes > 0) Object.assign(init, { body: "{}", headers: { "content-type": "application/json" } });
      const res = await worker.fetch(new Request(`https://gateway.test${path}`, init), { ...env, HSBC_MTLS: mtls.fetcher });
      const out = await exposed(res);
      for (const s of SECRETS) expect(out, r.id).not.toContain(s);
    }
    for (const s of SECRETS) expect(logs.join("\n")).not.toContain(s);
  });

  it("the AIS + auth routes on their happy paths return and log no PSU or client-credentials token", async () => {
    const logs = captureLogs();
    const e = await testEnv(fakeBank({ rejectFirstAccess: true }));
    const s = await signIn(e);
    const outs = [
      await call(e, "GET", "/v1/ais/accounts", { headers: sessionHeader(s) }),
      await call(e, "GET", "/v1/ais/accounts/acc-1/transactions", { headers: sessionHeader(s) }),
      await call(e, "GET", `/v1/consents/ais/${CONSENT_ID}`, { headers: sessionHeader(s) }),
      await call(e, "POST", "/v1/session/logout?revoke=true", { headers: sessionHeader(s) }),
    ];
    for (const res of outs) {
      const out = await exposedAll(res);
      for (const t of ALL_TOKENS) expect(out).not.toContain(t);
    }
    for (const t of ALL_TOKENS) expect(logs.join("\n")).not.toContain(t);
  });

  it("the payment and VRP legs on their happy paths return and log no PSU or client-credentials token", async () => {
    const logs = captureLogs();
    const e = await testEnv(fakeBank());
    const s = await signIn(e);
    const dom = payConsentId("domestic-payment-consents");
    const outs = [(await authorisePay(e, "/v1/consents/payment/domestic", consentBody(), s)).out, (await authorisePay(e, "/v1/consents/vrp", consentBody({ ControlParameters: {}, Initiation: INITIATION }), s)).out].map((o) => JSON.stringify(o));
    const res = [
      await call(e, "GET", `/v1/consents/payment/domestic/${dom}/funds-confirmation`, { headers: sessionHeader(s) }),
      await call(e, "POST", "/v1/payments/domestic", { body: paymentBody(dom), headers: { ...sessionHeader(s), ...idemHeader() } }),
      await call(e, "GET", `/v1/payments/domestic/${PAYMENT_ID}`, { headers: sessionHeader(s) }),
      await call(e, "POST", "/v1/vrp/payments", { body: paymentBody(VRP_CONSENT_ID), headers: { ...sessionHeader(s), ...idemHeader() } }),
      await call(e, "GET", `/v1/consents/vrp/${VRP_CONSENT_ID}`, { headers: sessionHeader(s) }),
    ];
    for (const r of res) expect(r.status).toBeLessThan(300);
    for (const r of res) outs.push(await exposedAll(r));
    for (const t of ALL_TOKENS) for (const o of outs) expect(o).not.toContain(t);
    for (const t of ALL_TOKENS) expect(logs.join("\n")).not.toContain(t);
  });

  it("a handler failure carrying a token returns only the error envelope and logs nothing secret", async () => {
    const logs = captureLogs();
    const route: Route = { ...ROUTES[0]!, id: "boom", path: "/v1/boom" };
    const res = await handle(new Request("https://gateway.test/v1/boom"), env, [route], {
      boom: async () => {
        throw new Error(`bank said no for Bearer ${ACCESS} / ${REFRESH} / ${ID_TOKEN}`);
      },
    });
    expect(res.status).toBe(500);
    const body = (await res.json()) as Record<string, unknown>;
    expect(body).toEqual({ error: "internal", requestId: res.headers.get("x-request-id") });
    for (const s of SECRETS) expect(logs.join("\n")).not.toContain(s);
  });

  it("redact() masks the hard-coded sensitive keys at any depth", () => {
    const out = JSON.stringify(
      redact({ requestId: "r", headers: { Authorization: `Bearer ${ACCESS}`, "x-jws-signature": "jws-value-7c1e" }, grant: { access_token: ACCESS, refresh_token: REFRESH, id_token: ID_TOKEN, code: "c", request: "jwt" }, body: "raw" }),
    );
    for (const s of [...SECRETS, "jws-value-7c1e", '"c"', '"jwt"', '"raw"']) expect(out).not.toContain(s);
    expect(out).toContain('"requestId":"r"');
  });
});

describe("refresh-on-401 through SessionDO (AC8)", () => {
  function bank() {
    let tokenCalls = 0;
    let dataCalls = 0;
    const mtls = fakeMtls(async (call) => {
      if (call.url.endsWith("/oauth2/token")) {
        tokenCalls++;
        await new Promise((r) => setTimeout(r, 20)); // keep the refresh in flight while the other caller arrives
        return jsonResponse({ access_token: NEW_ACCESS, refresh_token: "fixture-rotated-refresh", expires_in: 300 });
      }
      dataCalls++;
      return call.headers.get("authorization") === `Bearer ${NEW_ACCESS}` ? jsonResponse({ Data: { Account: [] } }) : jsonResponse({}, 401);
    });
    const e = { ...env, HSBC_MTLS: mtls.fetcher, HSBC_SIGNING_KEY_PEM: pem, HSBC_CLIENT_ID: "client-1", HSBC_KID: "kid-1" };
    return { e, counts: () => ({ tokenCalls, dataCalls }) };
  }
  const op = { method: "GET" as const, path: "/v4.0/aisp/accounts" };

  it("refreshes exactly once per 401 and retries once", async () => {
    const { e, counts } = bank();
    const session = await sessionStub(env.SESSIONS, await newSession(env.SESSIONS, nowSec()));
    await session.putTokens("ais", { access: ACCESS, refresh: REFRESH, expiresAt: nowSec() + 300 });
    const res = await psuFetch(e, session, "ais", op);
    expect(res.status).toBe(200);
    expect(counts()).toEqual({ tokenCalls: 1, dataCalls: 2 });
    expect((await session.getTokens("ais"))?.access).toBe(NEW_ACCESS);
  });

  it("two concurrent requests on one session serialise into a single refresh", async () => {
    const { e, counts } = bank();
    const session = await sessionStub(env.SESSIONS, await newSession(env.SESSIONS, nowSec()));
    await session.putTokens("ais", { access: ACCESS, refresh: REFRESH, expiresAt: nowSec() + 300 });
    const [a, b] = await Promise.all([psuFetch(e, session, "ais", op), psuFetch(e, session, "ais", op)]);
    expect([a.status, b.status]).toEqual([200, 200]);
    expect(counts()).toEqual({ tokenCalls: 1, dataCalls: 4 });
  });
});

describe("payment + VRP legs (AC8)", () => {
  it("the single-payment leg has no refresh counterpart: a 401 is surfaced, never refreshed", async () => {
    const bank = fakeBank({ rejectFirstAccess: true });
    const e = await testEnv(bank);
    const { out } = await authorisePay(e, "/v1/consents/payment/domestic", consentBody());
    expect((await sessionStub(env.SESSIONS, out.session).then((s) => s.getTokens(`payment:domestic:${out.consentId}`)))?.refresh).toBeUndefined();
    const res = await call(e, "POST", "/v1/payments/domestic", { body: paymentBody(out.consentId), headers: { ...sessionHeader(out.session), ...idemHeader() } });
    expect(res.status).toBe(401);
    expect(await res.json()).toEqual({ error: "psu_token_expired" });
    expect(bank.grants.filter((g) => g === "refresh_token")).toHaveLength(0);
  });

  it("two concurrent VRP 401s on one session serialise into a single refresh", async () => {
    let tokenCalls = 0;
    const mtls = fakeMtls(async (c) => {
      if (c.url.endsWith("/oauth2/token")) {
        tokenCalls++;
        await new Promise((r) => setTimeout(r, 20));
        return jsonResponse({ access_token: NEW_ACCESS, refresh_token: "fixture-rotated-refresh", expires_in: 300 });
      }
      return c.headers.get("authorization") === `Bearer ${NEW_ACCESS}` ? jsonResponse({ Data: {} }, 201) : jsonResponse({}, 401);
    });
    const e = { ...env, HSBC_MTLS: mtls.fetcher, HSBC_SIGNING_KEY_PEM: pem, HSBC_CLIENT_ID: "client-1", HSBC_KID: "kid-1" };
    const session = await sessionStub(env.SESSIONS, await newSession(env.SESSIONS, nowSec()));
    await session.putTokens("vrp:c-1", { access: ACCESS, refresh: REFRESH, expiresAt: nowSec() + 300 });
    const op = { method: "POST" as const, path: "/v4.0/pisp/domestic-vrps" };
    const [a, b] = await Promise.all([psuFetch(e, session, "vrp:c-1", op, { body: "{}" }), psuFetch(e, session, "vrp:c-1", op, { body: "{}" })]);
    expect([a.status, b.status]).toEqual([201, 201]);
    expect(tokenCalls).toBe(1);
    expect((await session.getTokens("vrp:c-1"))?.access).toBe(NEW_ACCESS);
  });
});
