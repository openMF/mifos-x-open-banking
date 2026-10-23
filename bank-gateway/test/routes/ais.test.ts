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
import { sessionStub } from "../../src/session/SessionDO";
import {
  ALL_TOKENS,
  BANK,
  call,
  CODE,
  completeAuth,
  CONSENT_ID,
  exposed,
  fakeBank,
  idTokenFor,
  sessionHeader,
  signIn,
  startFlow,
  testEnv,
  TOKENS,
} from "../fixtures/bank";
import { makeHsbcJwks } from "../fixtures/hsbcJwks";
import { decodeJson } from "../fixtures/keys";

const exchanges = (grants: string[]) => grants.filter((g) => g === "authorization_code").length;

describe("POST /v1/consents/ais", () => {
  it("returns {flowId, consentId, authorizeUrl} on the HSBC authorize host with a signed request object", async () => {
    const bank = fakeBank();
    const e = await testEnv(bank);
    const res = await call(e, "POST", "/v1/consents/ais", { body: { permissions: ["ReadAccountsDetail", "ReadBalances"] } });
    expect(res.status).toBe(200);
    const body = (await res.json()) as { flowId: string; consentId: string; authorizeUrl: string };
    expect(Object.keys(body).sort()).toEqual(["authorizeUrl", "consentId", "flowId"]);
    expect(body.consentId).toBe(CONSENT_ID);
    const u = new URL(body.authorizeUrl);
    expect(u.host).toBe(env.HSBC_AUTHORIZE_HOST);
    expect(u.pathname).toBe("/obie/open-banking/v1.1/oauth2/authorize");
    const jwt = u.searchParams.get("request")!;
    expect(jwt).toMatch(/^[\w-]+\.[\w-]+\.[\w-]+$/);
    const claims = decodeJson(jwt.split(".")[1]!);
    expect(claims.claims.id_token.openbanking_intent_id).toEqual({ value: CONSENT_ID, essential: true });
    expect(claims.aud).toBe(env.HSBC_ISSUER);
    expect(claims.scope).toBe("openid accounts");
    expect(claims.state).toBe(u.searchParams.get("state"));
    expect(claims.nonce).toBe(u.searchParams.get("nonce"));

    // The bank saw an OBIE consent body built from the camelCase request, with the client-credentials token.
    const create = bank.calls.find((c) => c.url.endsWith("/v4.0/aisp/account-access-consents"))!;
    expect(create.headers.get("authorization")).toBe(`Bearer ${TOKENS.cc}`);
    const sent = JSON.parse(create.body);
    expect(sent.Data.Permissions).toEqual(["ReadAccountsDetail", "ReadBalances"]);
    expect(typeof sent.Data.ExpirationDateTime).toBe("string");
    expect(sent.Risk).toEqual({});
  });

  it("the second consent signs only the request object — the client-credentials token comes from TokenCacheDO", async () => {
    const bank = fakeBank();
    const e = await testEnv(bank);
    await startFlow(e);
    const before = bank.grants.length;
    await startFlow(e);
    expect(bank.grants.length).toBe(before);
  });

  it("400 invalid_request for a body without permissions or with a non-Read permission", async () => {
    const e = await testEnv(fakeBank());
    for (const body of [{}, { permissions: [] }, { permissions: ["WriteAnything"] }, "not json", { permissions: ["ReadAccountsDetail"], expirationDateTime: "soon" }]) {
      const res = await call(e, "POST", "/v1/consents/ais", { body });
      expect(res.status, JSON.stringify(body)).toBe(400);
      expect(((await res.json()) as { error: string }).error).toBe("invalid_request");
    }
  });
});

describe("POST /v1/auth/complete", () => {
  it("happy path: opaque session + consent fields, PSU tokens stored server-side, never returned", async () => {
    const bank = fakeBank();
    const e = await testEnv(bank);
    const flow = await startFlow(e);
    const res = await completeAuth(e, flow);
    expect(res.status).toBe(200);
    const out = await exposed(res);
    for (const t of ALL_TOKENS) expect(out).not.toContain(t);
    const body = (await res.json()) as Record<string, unknown>;
    expect(Object.keys(body).sort()).toEqual(["consentExpiresAt", "consentId", "consentStatus", "expiresAt", "leg", "session"]);
    expect(body).toMatchObject({ leg: "ais", consentId: CONSENT_ID, consentStatus: "Authorised", consentExpiresAt: "2027-01-20T00:00:00Z" });
    expect(body.session).toMatch(/^[\w-]{43}$/);
    expect(body.expiresAt).toBe(Date.parse("2027-01-20T00:00:00Z"));
    expect(exchanges(bank.grants)).toBe(1);
    const exchange = bank.calls.find((c) => new URLSearchParams(c.body).get("grant_type") === "authorization_code")!;
    expect(new URLSearchParams(exchange.body).get("scope")).toBeNull();

    const stub = await sessionStub(env.SESSIONS, body.session as string);
    expect((await stub.getTokens("ais"))?.access).toBe(TOKENS.access);
    expect(await stub.getConsent("ais")).toBe(CONSENT_ID);
  });

  it("a stale session header on an AIS re-sign-in is ignored: a fresh session is minted", async () => {
    const e = await testEnv(fakeBank());
    const res = await completeAuth(e, await startFlow(e), {}, sessionHeader("A".repeat(43)));
    expect(res.status).toBe(200);
    expect(((await res.json()) as { session: string }).session).not.toBe("A".repeat(43));
  });

  it("409 consent_rejected discards the tokens when the consent is Rejected", async () => {
    const e = await testEnv(fakeBank({ consentStatus: "Rejected" }));
    const res = await completeAuth(e, await startFlow(e));
    expect(res.status).toBe(409);
    expect(await res.json()).toEqual({ error: "consent_rejected" });
  });

  it("400 state_mismatch without exchanging the code", async () => {
    const bank = fakeBank();
    const e = await testEnv(bank);
    const res = await completeAuth(e, await startFlow(e), { state: "other-state" });
    expect(res.status).toBe(400);
    expect(await res.json()).toEqual({ error: "state_mismatch" });
    expect(exchanges(bank.grants)).toBe(0);
  });

  it("a flow is single-use: replaying auth/complete is refused", async () => {
    const e = await testEnv(fakeBank());
    const flow = await startFlow(e);
    expect((await completeAuth(e, flow)).status).toBe(200);
    const again = await completeAuth(e, flow);
    expect(again.status).toBe(400);
    expect(await again.json()).toEqual({ error: "unknown_flow" });
  });

  const rejections: Array<[string, (f: Awaited<ReturnType<typeof startFlow>>) => Promise<string>]> = [
    ["bad signature", async (f) => idTokenFor(f, { signer: await makeHsbcJwks() })],
    ["wrong iss", (f) => idTokenFor(f, { claims: { iss: "https://evil.example" } })],
    ["wrong aud", (f) => idTokenFor(f, { claims: { aud: "someone-else" } })],
    ["expired", (f) => idTokenFor(f, { claims: { exp: Math.floor(Date.now() / 1000) - 3600, iat: Math.floor(Date.now() / 1000) - 3900 } })],
    ["nonce mismatch", (f) => idTokenFor(f, { claims: { nonce: "other-nonce" } })],
    ["c_hash mismatch", (f) => idTokenFor(f, { claims: { c_hash: "AAAAAAAAAAAAAAAAAAAAAA" } })],
    ["s_hash mismatch", (f) => idTokenFor(f, { claims: { s_hash: "AAAAAAAAAAAAAAAAAAAAAA" } })],
    // Malformed input is a client error, never a 500 (found live: the web return leg with a fake id_token).
    ["malformed (not base64url JSON)", async () => "fake.id.token"],
    ["not a JWT", async () => "garbage"],
  ];
  it.each(rejections)("400 invalid_id_token for %s and the code is never exchanged", async (_, mint) => {
    const bank = fakeBank();
    const e = await testEnv(bank);
    const flow = await startFlow(e);
    const res = await completeAuth(e, flow, { idToken: await mint(flow) });
    expect(res.status).toBe(400);
    expect(await res.json()).toEqual({ error: "invalid_id_token" });
    expect(exchanges(bank.grants)).toBe(0);
  });

  it("400 invalid_request for a malformed body", async () => {
    const e = await testEnv(fakeBank());
    const res = await call(e, "POST", "/v1/auth/complete", { body: { flowId: "f", code: CODE } });
    expect(res.status).toBe(400);
    expect(await res.json()).toEqual({ error: "invalid_request" });
  });
});

describe("AIS reads", () => {
  it("call HSBC with the session's psu:ais token and return the bank body", async () => {
    const bank = fakeBank({ accountsBody: () => ({ Data: { Balance: [{ Amount: { Amount: "10.00" } }] } }) });
    const e = await testEnv(bank);
    const s = await signIn(e);
    const res = await call(e, "GET", "/v1/ais/accounts/acc-1/balances", { headers: sessionHeader(s) });
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({ Data: { Balance: [{ Amount: { Amount: "10.00" } }] } });
    const read = bank.calls.at(-1)!;
    expect(new URL(read.url).pathname).toBe("/obie/open-banking/v4.0/aisp/accounts/acc-1/balances");
    expect(read.headers.get("authorization")).toBe(`Bearer ${TOKENS.access}`);
  });

  it("forward only fromBookingDateTime / toBookingDateTime on transactions", async () => {
    const bank = fakeBank();
    const e = await testEnv(bank);
    const s = await signIn(e);
    await call(e, "GET", "/v1/ais/accounts/acc-1/transactions?fromBookingDateTime=2026-01-01T00:00:00Z&toBookingDateTime=2026-02-01T00:00:00Z&evil=1", { headers: sessionHeader(s) });
    const q = new URL(bank.calls.at(-1)!.url).searchParams;
    expect([...q.keys()].sort()).toEqual(["fromBookingDateTime", "toBookingDateTime"]);
    await call(e, "GET", "/v1/ais/accounts/acc-1/balances?fromBookingDateTime=2026-01-01T00:00:00Z", { headers: sessionHeader(s) });
    expect(new URL(bank.calls.at(-1)!.url).search).toBe("");
  });

  it("refresh once on 401, retry, and never expose a token", async () => {
    const bank = fakeBank({ rejectFirstAccess: true });
    const e = await testEnv(bank);
    const s = await signIn(e);
    const res = await call(e, "GET", "/v1/ais/accounts", { headers: sessionHeader(s) });
    expect(res.status).toBe(200);
    expect(bank.grants.filter((g) => g === "refresh_token")).toHaveLength(1);
    const out = await exposed(res);
    for (const t of ALL_TOKENS) expect(out).not.toContain(t);
  });

  it("a bank error is reported as {error: bank_error, bankStatus} with the bank's status, no bank body", async () => {
    const e = await testEnv(fakeBank({ readError: 403 }));
    const s = await signIn(e);
    const res = await call(e, "GET", "/v1/ais/accounts/acc-1", { headers: sessionHeader(s) });
    expect(res.status).toBe(403);
    expect(await res.json()).toEqual({ error: "bank_error", bankStatus: 403 });
  });

  it("statement file streams the bytes with the negotiated type; anything but pdf/csv is 406", async () => {
    const bytes = new Uint8Array([0x25, 0x50, 0x44, 0x46, 0x2d, 0x31]);
    const bank = fakeBank({ statementFile: { type: "application/pdf", bytes } });
    const e = await testEnv(bank);
    const s = await signIn(e);
    const path = "/v1/ais/accounts/acc-1/statements/st-1/file";
    const res = await call(e, "GET", path, { headers: { ...sessionHeader(s), Accept: "application/pdf, text/csv" } });
    expect(res.status).toBe(200);
    expect(res.headers.get("content-type")).toBe("application/pdf");
    expect(new Uint8Array(await res.arrayBuffer())).toEqual(bytes);
    expect(bank.calls.at(-1)!.headers.get("accept")).toBe("application/pdf");

    const refused = await call(e, "GET", path, { headers: { ...sessionHeader(s), Accept: "application/json" } });
    expect(refused.status).toBe(406);
    expect(await refused.json()).toEqual({ error: "not_acceptable" });
  });
});

describe("consent read / delete and logout", () => {
  it("GET and DELETE /v1/consents/ais/{id} use the client-credentials token, only for the session's own consent", async () => {
    const bank = fakeBank();
    const e = await testEnv(bank);
    const s = await signIn(e);
    const got = await call(e, "GET", `/v1/consents/ais/${CONSENT_ID}`, { headers: sessionHeader(s) });
    expect(got.status).toBe(200);
    expect(((await got.json()) as { Data: { Status: string } }).Data.Status).toBe("Authorised");
    expect(bank.calls.at(-1)!.headers.get("authorization")).toBe(`Bearer ${TOKENS.cc}`);

    const foreign = await call(e, "GET", "/v1/consents/ais/aac-someone-else", { headers: sessionHeader(s) });
    expect(foreign.status).toBe(403);
    expect(await foreign.json()).toEqual({ error: "consent_not_in_session" });

    const del = await call(e, "DELETE", `/v1/consents/ais/${CONSENT_ID}`, { headers: sessionHeader(s) });
    expect(del.status).toBe(204);
    expect(bank.calls.at(-1)!.method).toBe("DELETE");
  });

  it("logout wipes the session; ?revoke=true also deletes the consent at HSBC", async () => {
    const bank = fakeBank();
    const e = await testEnv(bank);
    const s = await signIn(e);
    const out = await call(e, "POST", "/v1/session/logout?revoke=true", { headers: sessionHeader(s) });
    expect(out.status).toBe(204);
    const del = bank.calls.at(-1)!;
    expect(del.method).toBe("DELETE");
    expect(new URL(del.url).pathname).toBe(`/obie/open-banking/v4.0/aisp/account-access-consents/${CONSENT_ID}`);
    const after = await call(e, "GET", "/v1/ais/accounts", { headers: sessionHeader(s) });
    expect(after.status).toBe(401);
    expect(await after.json()).toEqual({ error: "unknown_session" });
  });
});

describe("bank host", () => {
  it("every HSBC call goes to the bank host", async () => {
    const bank = fakeBank();
    const e = await testEnv(bank);
    const s = await signIn(e);
    await call(e, "GET", "/v1/ais/accounts", { headers: sessionHeader(s) });
    for (const c of bank.calls) expect(new URL(c.url).host).toBe(BANK);
  });
});
