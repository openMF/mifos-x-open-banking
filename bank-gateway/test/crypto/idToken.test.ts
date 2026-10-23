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
import { beforeAll, describe, expect, it } from "vitest";
import { loadHsbcOidc, verifyHsbcIdToken, type IdTokenInput } from "../../src/crypto/idToken";
import { fakeMtls, jsonResponse } from "../fixtures/fakeMtls";
import { halfHash, makeHsbcJwks, signTestIdToken, type TestJwks } from "../fixtures/hsbcJwks";

const ISSUER = "https://secure.sandbox.ob.hsbc.co.uk";
const NOW = 1_700_000_000;
const CODE = "auth-code-xyz";
const STATE = "33333333-3333-4333-8333-333333333333";
const NONCE = "44444444-4444-4444-8444-444444444444";
const CLIENT_ID = "client-1";

describe("verifyHsbcIdToken (D9 / AC5)", () => {
  let hsbc: TestJwks;
  let goodClaims: Record<string, unknown>;

  beforeAll(async () => {
    hsbc = await makeHsbcJwks();
    goodClaims = {
      iss: ISSUER,
      aud: CLIENT_ID,
      sub: "consent-123",
      iat: NOW - 5,
      exp: NOW + 300,
      nonce: NONCE,
      c_hash: await halfHash(CODE),
      s_hash: await halfHash(STATE),
      openbanking_intent_id: "consent-123",
    };
  });

  const verify = (token: string, over: Partial<IdTokenInput> = {}) =>
    verifyHsbcIdToken({ token, code: CODE, state: STATE, nonce: NONCE, clientId: CLIENT_ID, nowEpochSeconds: NOW, jwks: hsbc.jwks, issuer: ISSUER, ...over });
  const mint = (over: Record<string, unknown> = {}, header: Record<string, unknown> = {}) =>
    signTestIdToken({ ...goodClaims, ...over }, hsbc, header);

  it("accepts a valid token signed by the HSBC JWKS", async () => {
    const v = await verify(await mint());
    expect(v).toEqual({ ok: true, claims: goodClaims });
  });

  it("accepts aud as an array containing the client id", async () => {
    expect((await verify(await mint({ aud: ["other", CLIENT_ID] }))).ok).toBe(true);
  });

  it("rejects a bad signature", async () => {
    const other = await makeHsbcJwks(hsbc.kid); // same kid, different key
    const forged = await signTestIdToken(goodClaims, other);
    expect(await verify(forged)).toEqual({ ok: false, reason: "bad_signature" });
  });

  it("rejects a tampered payload", async () => {
    const [h, , s] = (await mint()).split(".");
    const p = btoa(JSON.stringify({ ...goodClaims, sub: "attacker" })).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");
    expect(await verify(`${h}.${p}.${s}`)).toEqual({ ok: false, reason: "bad_signature" });
  });

  it("rejects an unknown kid and a non-PS256 alg", async () => {
    expect(await verify(await mint({}, { kid: "unknown" }))).toEqual({ ok: false, reason: "bad_signature" });
    expect(await verify(await mint({}, { alg: "none" }))).toEqual({ ok: false, reason: "bad_signature" });
  });

  it("rejects a malformed token", async () => {
    expect(await verify("not-a-jwt")).toEqual({ ok: false, reason: "malformed" });
    expect(await verify("a.b.c")).toEqual({ ok: false, reason: "malformed" });
  });

  it("rejects the wrong iss", async () => {
    expect(await verify(await mint({ iss: "https://evil.example" }))).toEqual({ ok: false, reason: "wrong_iss" });
  });

  it("rejects the wrong aud", async () => {
    expect(await verify(await mint({ aud: "someone-else" }))).toEqual({ ok: false, reason: "wrong_aud" });
  });

  it("rejects an expired token, a future iat and a missing exp (±120 s skew)", async () => {
    expect(await verify(await mint({ exp: NOW - 121 }))).toEqual({ ok: false, reason: "expired" });
    expect((await verify(await mint({ exp: NOW - 119 }))).ok).toBe(true);
    expect(await verify(await mint({ iat: NOW + 121 }))).toEqual({ ok: false, reason: "expired" });
    expect(await verify(await mint({ exp: undefined }))).toEqual({ ok: false, reason: "expired" });
  });

  it("rejects a nonce mismatch", async () => {
    expect(await verify(await mint({ nonce: "other-nonce" }))).toEqual({ ok: false, reason: "nonce_mismatch" });
  });

  it("rejects the wrong c_hash (and a missing one)", async () => {
    expect(await verify(await mint({ c_hash: await halfHash("other-code") }))).toEqual({ ok: false, reason: "wrong_c_hash" });
    expect(await verify(await mint({ c_hash: undefined }))).toEqual({ ok: false, reason: "wrong_c_hash" });
  });

  it("rejects the wrong s_hash (and a missing one)", async () => {
    expect(await verify(await mint({ s_hash: await halfHash("other-state") }))).toEqual({ ok: false, reason: "wrong_s_hash" });
    expect(await verify(await mint({ s_hash: undefined }))).toEqual({ ok: false, reason: "wrong_s_hash" });
  });
});

describe("loadHsbcOidc — discovery + JWKS cached in TokenCacheDO", () => {
  it("fetches discovery then jwks_uri over the mTLS binding, caches for max-age, refetches after", async () => {
    const hsbc = await makeHsbcJwks("cache-kid");
    const mtls = fakeMtls((call) =>
      call.url.endsWith("/.well-known/openid-configuration")
        ? jsonResponse({ issuer: ISSUER, jwks_uri: "https://keys.hsbc.example/jwks" })
        : jsonResponse(hsbc.jwks, 200, { "cache-control": "public, max-age=600" }),
    );
    const e = { ...env, HSBC_MTLS: mtls.fetcher, HSBC_ISSUER: ISSUER };

    const first = await loadHsbcOidc(e, NOW);
    expect(first).toEqual({ issuer: ISSUER, jwks: hsbc.jwks });
    // HSBC publishes discovery on the authorize host (sandbox.ob.hsbc.co.uk); the issuer host is the mTLS API
    // front and has none — fetching it there 500'd every live /v1/auth/complete.
    expect(mtls.calls.map((c) => c.url)).toEqual([`https://${env.HSBC_AUTHORIZE_HOST}/.well-known/openid-configuration`, "https://keys.hsbc.example/jwks"]);

    expect(await loadHsbcOidc(e, NOW + 599)).toEqual(first);
    expect(mtls.calls).toHaveLength(2); // served from TokenCacheDO

    await loadHsbcOidc(e, NOW + 601);
    expect(mtls.calls).toHaveLength(4); // max-age elapsed → refetched
  });

  it("refuses a discovery document whose issuer is not HSBC_ISSUER", async () => {
    const mtls = fakeMtls(() => jsonResponse({ issuer: "https://evil.example", jwks_uri: "https://keys.hsbc.example/jwks" }));
    const e = { ...env, HSBC_MTLS: mtls.fetcher, HSBC_ISSUER: ISSUER, TOKEN_CACHE: env.TOKEN_CACHE };
    await expect(loadHsbcOidc(e, NOW + 5_000_000)).rejects.toThrow(/issuer/);
  });

  it("falls back to a 3600 s TTL without Cache-Control", async () => {
    const hsbc = await makeHsbcJwks("ttl-kid");
    const mtls = fakeMtls((call) =>
      call.url.endsWith("/.well-known/openid-configuration") ? jsonResponse({ issuer: ISSUER, jwks_uri: "https://keys.hsbc.example/jwks" }) : jsonResponse(hsbc.jwks),
    );
    const e = { ...env, HSBC_MTLS: mtls.fetcher, HSBC_ISSUER: ISSUER };
    const t = NOW + 1_000_000; // clear of the previous test's cache window if storage is shared
    await loadHsbcOidc(e, t);
    await loadHsbcOidc(e, t + 3599);
    expect(mtls.calls).toHaveLength(2);
    await loadHsbcOidc(e, t + 3601);
    expect(mtls.calls).toHaveLength(4);
  });
});
