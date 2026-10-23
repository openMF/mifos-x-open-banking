/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

import { afterEach, describe, expect, it, vi } from "vitest";
import { ROUTES } from "../src/routes";
import { clientData, fakeGoogle, makeServiceAccount, requestHash } from "./fixtures/attest";
import { call, consentBody, fakeBank, idemHeader, testEnv } from "./fixtures/bank";

const concrete = (path: string) => path.replace(/:[A-Za-z]+/g, "x1");
const SESSION_ROUTES = ROUTES.filter((r) => r.session);
const LIMITED_ROUTES = ROUTES.filter((r) => r.rateLimit);

describe("route table gating shape", () => {
  it("every PSU-token route and every consent read/delete + logout is session-gated", () => {
    for (const r of ROUTES) if (r.token.startsWith("psu:")) expect(r.session, r.id).toBe(true);
    const ids = SESSION_ROUTES.map((r) => r.id);
    for (const id of ["consentAisGet", "consentAisDelete", "sessionLogout", "aisPage", "aisStatementFile", "aisAccounts", "aisTransactions"]) expect(ids).toContain(id);
    // Every PIS / VRP row but consent creation needs a session: reads are owner-only, writes need the PSU leg.
    for (const r of ROUTES.filter((r) => r.product)) expect(r.session, r.id).toBe(!r.rateLimit);
  });

  it("consent creation and auth-complete are rate-limited at 20 per 60 s", () => {
    expect(LIMITED_ROUTES.map((r) => `${r.method} ${r.path}`).sort()).toEqual([
      "POST /v1/attest/challenge",
      "POST /v1/attest/ios/register",
      "POST /v1/auth/complete",
      "POST /v1/consents/ais",
      "POST /v1/consents/payment/domestic",
      "POST /v1/consents/payment/domestic-scheduled",
      "POST /v1/consents/payment/domestic-standing-order",
      "POST /v1/consents/payment/international",
      "POST /v1/consents/payment/international-scheduled",
      "POST /v1/consents/payment/international-standing-order",
      "POST /v1/consents/vrp",
    ]);
    for (const r of LIMITED_ROUTES) expect(r.rateLimit).toEqual({ period: 60, limit: 20 });
  });
});

describe("session gating — 401 on every gated row", () => {
  const headerCases: Array<[string, Record<string, string>, string]> = [
    ["no Authorization", {}, "missing_session"],
    ["Bearer instead of Session", { Authorization: "Bearer abc" }, "missing_session"],
    ["malformed session id", { Authorization: "Session short" }, "missing_session"],
    ["unknown session id", { Authorization: `Session ${"A".repeat(43)}` }, "unknown_session"],
  ];
  it.each(SESSION_ROUTES.map((r) => [`${r.method} ${r.path}`, r] as const))("%s", async (_, r) => {
    const bank = fakeBank();
    const e = await testEnv(bank);
    for (const [label, headers, code] of headerCases) {
      const res = await call(e, r.method, concrete(r.path), { headers });
      expect(res.status, label).toBe(401);
      expect(await res.json(), label).toMatchObject({ error: code });
    }
    expect(bank.calls).toHaveLength(0);
  });
});

describe("rate limits — 429 past the threshold", () => {
  const bodies: Record<string, unknown> = {
    "/v1/consents/ais": { permissions: ["ReadAccountsDetail"] },
    "/v1/auth/complete": { flowId: "f", code: "c", idToken: "i", state: "s" },
    "/v1/attest/challenge": {},
    "/v1/attest/ios/register": {},
  };
  const bodyFor = (path: string) => bodies[path] ?? consentBody();

  it.each(LIMITED_ROUTES.map((r) => [r.path] as const))("%s via the Rate Limiting binding", async (path) => {
    const seen: string[] = [];
    let n = 0;
    const CONSENT_LIMIT = { limit: async ({ key }: { key: string }) => (seen.push(key), { success: ++n <= 20 }) } as RateLimit;
    const e = await testEnv(fakeBank(), { CONSENT_LIMIT });
    const statuses: number[] = [];
    for (let i = 0; i < 22; i++) {
      const res = await call(e, "POST", path, { body: bodyFor(path), headers: { ...idemHeader(), "X-Client-Instance": "install-rl", "CF-Connecting-IP": "203.0.113.7" } });
      statuses.push(res.status);
      if (res.status === 429) expect(await res.json()).toEqual({ error: "rate_limited", retryAfter: 60 });
    }
    expect(statuses.slice(0, 20)).not.toContain(429);
    expect(statuses.slice(20)).toEqual([429, 429]);
    expect(new Set(seen).size).toBe(1);
    expect(seen[0]).toContain("install-rl");
    expect(seen[0]).toContain("203.0.113.7");
  });

  it("the wrangler.toml CONSENT_LIMIT binding itself cuts off at 20 per install", async () => {
    const e = await testEnv(fakeBank());
    expect(e.CONSENT_LIMIT).toBeDefined();
    const instance = crypto.randomUUID();
    const statuses: number[] = [];
    for (let i = 0; i < 21; i++) statuses.push((await call(e, "POST", "/v1/auth/complete", { body: bodies["/v1/auth/complete"], headers: { "X-Client-Instance": instance } })).status);
    expect(statuses.slice(0, 20)).not.toContain(429);
    expect(statuses[20]).toBe(429);
  });

  it.each(LIMITED_ROUTES.map((r) => [r.path] as const))("%s via the Durable Object counter when the binding is absent", async (path) => {
    const e = await testEnv(fakeBank(), { CONSENT_LIMIT: undefined });
    const instance = crypto.randomUUID();
    const statuses: number[] = [];
    for (let i = 0; i < 22; i++) {
      const res = await call(e, "POST", path, { body: bodyFor(path), headers: { ...idemHeader(), "X-Client-Instance": instance } });
      statuses.push(res.status);
    }
    expect(statuses.slice(0, 20)).not.toContain(429);
    expect(statuses.slice(20)).toEqual([429, 429]);
    // A different install is counted separately.
    const other = await call(e, "POST", path, { body: bodyFor(path), headers: { ...idemHeader(), "X-Client-Instance": crypto.randomUUID() } });
    expect(other.status).not.toBe(429);
  });
});

describe("attestation gate (attest: true rows)", () => {
  afterEach(() => vi.restoreAllMocks());
  const AIS = { permissions: ["ReadAccountsDetail"] };
  const unconfigured = { PLAY_INTEGRITY_SA_JSON: undefined, APPLE_TEAM_ID: undefined };

  it("attest is set on exactly the consent POSTs and auth/complete", () => {
    expect(ROUTES.filter((r) => r.attest).map((r) => `${r.method} ${r.path}`).sort()).toEqual([
      "POST /v1/auth/complete",
      "POST /v1/consents/ais",
      "POST /v1/consents/payment/domestic",
      "POST /v1/consents/payment/domestic-scheduled",
      "POST /v1/consents/payment/domestic-standing-order",
      "POST /v1/consents/payment/international",
      "POST /v1/consents/payment/international-scheduled",
      "POST /v1/consents/payment/international-standing-order",
      "POST /v1/consents/vrp",
      "POST /v1/consents/vrp/:consentId/funds-confirmation",
    ]);
  });

  it.each(["android", "ios"])("enforce + unconfigured: %s fails closed with 503 attestation_unconfigured", async (platform) => {
    const bank = fakeBank();
    const e = await testEnv(bank, { ...unconfigured, ATTESTATION_MODE: "enforce" });
    const res = await call(e, "POST", "/v1/consents/ais", { body: AIS, headers: { "X-Client-Platform": platform } });
    expect(res.status).toBe(503);
    expect(await res.json()).toMatchObject({ error: "attestation_unconfigured" });
    expect(bank.calls).toHaveLength(0);
  });

  it("an unknown ATTESTATION_MODE value enforces", async () => {
    const e = await testEnv(fakeBank(), { ...unconfigured, ATTESTATION_MODE: "Report-Only " });
    expect((await call(e, "POST", "/v1/consents/ais", { body: AIS, headers: { "X-Client-Platform": "android" } })).status).toBe(503);
  });

  it.each(["android", "ios"])("report-only + unconfigured: %s proceeds with x-attestation: unverified", async (platform) => {
    const e = await testEnv(fakeBank(), { ...unconfigured, ATTESTATION_MODE: "report-only" });
    const res = await call(e, "POST", "/v1/consents/ais", { body: AIS, headers: { "X-Client-Platform": platform } });
    expect(res.status).toBe(200);
    expect(res.headers.get("x-attestation")).toBe("unverified");
  });

  it("wrangler.toml ships report-only while the credentials are pending", async () => {
    const e = await testEnv(fakeBank(), unconfigured);
    expect(e.ATTESTATION_MODE).toBe("report-only");
  });

  it("attest route: android with no X-Attestation returns 401 attestation_missing (enforce, configured)", async () => {
    const sa = await makeServiceAccount();
    const e = await testEnv(fakeBank(), { PLAY_INTEGRITY_SA_JSON: sa.json, ATTESTATION_MODE: "enforce" });
    const res = await call(e, "POST", "/v1/consents/ais", { body: AIS, headers: { "X-Client-Platform": "android" } });
    expect(res.status).toBe(401);
    expect(await res.json()).toMatchObject({ error: "attestation_missing" });
  });

  it("attest route: android with bad token returns 403 attestation_invalid (enforce, configured)", async () => {
    const sa = await makeServiceAccount();
    const g = fakeGoogle(sa.publicKey, () => 400);
    vi.spyOn(globalThis, "fetch").mockImplementation(g.fake);
    const e = await testEnv(fakeBank(), { PLAY_INTEGRITY_SA_JSON: sa.json, ATTESTATION_MODE: "enforce" });
    const ch = await challenge(e);
    const res = await call(e, "POST", "/v1/consents/ais", { body: AIS, headers: { "X-Client-Platform": "android", "X-Attestation": `play ${ch}.BAD` } });
    expect(res.status).toBe(403);
    const body = (await res.json()) as Record<string, unknown>;
    expect(body).toMatchObject({ error: "attestation_invalid", reason: "google_error" });
    expect(JSON.stringify(body)).not.toContain("fixture google error detail");
  });

  it("report-only + configured: an invalid attestation still proceeds, marked unverified", async () => {
    const sa = await makeServiceAccount();
    vi.spyOn(globalThis, "fetch").mockImplementation(fakeGoogle(sa.publicKey, () => ({ deviceRecognitionVerdict: [] })).fake);
    const e = await testEnv(fakeBank(), { PLAY_INTEGRITY_SA_JSON: sa.json, ATTESTATION_MODE: "report-only" });
    const ch = await challenge(e);
    const res = await call(e, "POST", "/v1/consents/ais", { body: AIS, headers: { "X-Client-Platform": "android", "X-Attestation": `play ${ch}.TOKEN` } });
    expect(res.status).toBe(200);
    expect(res.headers.get("x-attestation")).toBe("unverified");
  });

  it("enforce + configured: a valid Play Integrity verdict proceeds, marked verified", async () => {
    const sa = await makeServiceAccount();
    const body = JSON.stringify(AIS);
    let expected = "";
    vi.spyOn(globalThis, "fetch").mockImplementation(fakeGoogle(sa.publicKey, () => ({ requestHash: expected })).fake);
    const e = await testEnv(fakeBank(), { PLAY_INTEGRITY_SA_JSON: sa.json, ATTESTATION_MODE: "enforce" });
    const ch = await challenge(e);
    expected = await requestHash(ch, clientData("POST", "/v1/consents/ais", body));
    const res = await call(e, "POST", "/v1/consents/ais", { body, headers: { "X-Client-Platform": "android", "X-Attestation": `play ${ch}.TOKEN` } });
    expect(res.status).toBe(200);
    expect(res.headers.get("x-attestation")).toBe("verified");
  });

  it.each(["desktop", "web", undefined])("attest route: desktop skips attestation and falls through to session gate (platform %s)", async (platform) => {
    const e = await testEnv(fakeBank(), { ...unconfigured, ATTESTATION_MODE: "enforce" });
    // A browser always sends Origin on this cross-origin POST; web must come from a WEB_RETURN_ORIGINS entry (webReturn.test.ts).
    const headers: Record<string, string> = platform ? { "X-Client-Platform": platform, ...(platform === "web" ? { Origin: "http://localhost:8080" } : {}) } : {};
    const res = await call(e, "POST", "/v1/consents/ais", { body: AIS, headers });
    expect(res.status).toBe(200);
    expect(res.headers.get("x-attestation")).toBeNull();
    // A session-gated attest row still answers 401 for the session first.
    const funds = await call(e, "POST", "/v1/consents/vrp/x1/funds-confirmation", { body: {}, headers: { ...headers, ...idemHeader() } });
    expect(funds.status).toBe(401);
    expect(await funds.json()).toMatchObject({ error: "missing_session" });
  });
});

async function challenge(e: Awaited<ReturnType<typeof testEnv>>): Promise<string> {
  const res = await call(e, "POST", "/v1/attest/challenge");
  expect(res.status).toBe(200);
  const b = (await res.json()) as { challenge: string; expiresIn: number };
  expect(b.expiresIn).toBe(300);
  expect(b.challenge).toMatch(/^[A-Za-z0-9_-]{43}$/);
  return b.challenge;
}
