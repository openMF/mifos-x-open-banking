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
import { beforeAll, describe, expect, it, vi } from "vitest";
import {
  ccAccessToken,
  clientCredentialsToken,
  exchangeAuthorizationCode,
  fapiHeaders,
  HsbcError,
  hsbcFetch,
  refreshAccessToken,
  requireIdempotencyKey,
  withRefreshOn401,
} from "../../src/hsbc/client";
import { fakeMtls, jsonResponse, type RecordedCall } from "../fixtures/fakeMtls";
import { decodeJson, generateTestKeyPairPem, verifyPs256, type TestKeyPair } from "../fixtures/keys";

const BANK = "secure.sandbox.ob.hsbc.co.uk";
const TOKEN_URL = `https://${BANK}/obie/open-banking/v1.1/oauth2/token`;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

let kp: TestKeyPair;
beforeAll(async () => {
  kp = await generateTestKeyPairPem();
});

function makeEnv(responder: Parameters<typeof fakeMtls>[0]) {
  const mtls = fakeMtls(responder);
  const e = {
    ...env,
    HSBC_MTLS: mtls.fetcher,
    HSBC_SIGNING_KEY_PEM: kp.privatePkcs8Pem,
    HSBC_CLIENT_ID: "client-1",
    HSBC_KID: "kid-1",
  };
  return { e, calls: mtls.calls };
}

const form = (c: RecordedCall) => Object.fromEntries(new URLSearchParams(c.body));

describe("FAPI headers", () => {
  it("sets a fresh uuid x-fapi-interaction-id per call", () => {
    const a = fapiHeaders()["x-fapi-interaction-id"];
    const b = fapiHeaders()["x-fapi-interaction-id"];
    expect(a).toMatch(UUID);
    expect(b).toMatch(UUID);
    expect(a).not.toBe(b);
  });

  it("sets x-fapi-financial-id only when non-blank", () => {
    expect(fapiHeaders()).not.toHaveProperty("x-fapi-financial-id");
    expect(fapiHeaders("  ")).not.toHaveProperty("x-fapi-financial-id");
    expect(fapiHeaders("fin-1")["x-fapi-financial-id"]).toBe("fin-1");
  });

  it("x-idempotency-key must be 1..40 chars", () => {
    expect(() => requireIdempotencyKey("a".repeat(40))).not.toThrow();
    expect(() => requireIdempotencyKey("a".repeat(41))).toThrow(/1\.\.40/);
    expect(() => requireIdempotencyKey("")).toThrow(/1\.\.40/);
  });
});

describe("hsbcFetch", () => {
  it("calls the bank over HSBC_MTLS with bearer, FAPI, idempotency and JWS headers", async () => {
    const { e, calls } = makeEnv(() => jsonResponse({ Data: {} }, 201));
    const res = await hsbcFetch(e, { method: "POST", path: "/v4.0/pisp/domestic-payment-consents" }, {
      bearer: "cc-token",
      body: '{"Data":{}}',
      idempotencyKey: "idem-1",
      jwsSignature: "h..s",
    });
    expect(res.status).toBe(201);
    expect(calls).toHaveLength(1);
    const c = calls[0]!;
    expect(c.url).toBe(`https://${BANK}/obie/open-banking/v4.0/pisp/domestic-payment-consents`);
    expect(c.method).toBe("POST");
    expect(c.headers.get("authorization")).toBe("Bearer cc-token");
    expect(c.headers.get("x-fapi-interaction-id")).toMatch(UUID);
    expect(c.headers.has("x-fapi-financial-id")).toBe(false);
    expect(c.headers.get("x-idempotency-key")).toBe("idem-1");
    expect(c.headers.get("x-jws-signature")).toBe("h..s");
    expect(c.headers.get("content-type")).toBe("application/json");
    expect(c.body).toBe('{"Data":{}}');
  });

  it("refuses an over-long idempotency key before calling the bank", async () => {
    const { e, calls } = makeEnv(() => jsonResponse({}));
    await expect(hsbcFetch(e, { method: "POST", path: "/v4.0/x" }, { body: "{}", idempotencyKey: "k".repeat(41) })).rejects.toThrow(/1\.\.40/);
    expect(calls).toHaveLength(0);
  });
});

describe("token grants", () => {
  it("client_credentials carries scope and a private_key_jwt for the token URL", async () => {
    const { e, calls } = makeEnv(() => jsonResponse({ access_token: "cc-a", token_type: "Bearer", expires_in: 3600 }));
    const t = await clientCredentialsToken(e, "accounts");
    expect(t.access_token).toBe("cc-a");
    const c = calls[0]!;
    expect(c.url).toBe(TOKEN_URL);
    expect(c.headers.get("content-type")).toBe("application/x-www-form-urlencoded");
    const f = form(c);
    expect(f.grant_type).toBe("client_credentials");
    expect(f.scope).toBe("accounts");
    expect(f.client_assertion_type).toBe("urn:ietf:params:oauth:client-assertion-type:jwt-bearer");
    const [h, p, s] = f.client_assertion!.split(".");
    expect(decodeJson(p!)).toMatchObject({ iss: "client-1", sub: "client-1", aud: TOKEN_URL });
    expect(await verifyPs256(kp.publicKey, h!, p!, s!)).toBe(true);
  });

  it("authorization_code carries code + redirect_uri and NO scope", async () => {
    const { e, calls } = makeEnv(() => jsonResponse({ access_token: "psu-a", refresh_token: "psu-r", id_token: "x", expires_in: 300 }));
    await exchangeAuthorizationCode(e, "code-1");
    const f = form(calls[0]!);
    expect(f.grant_type).toBe("authorization_code");
    expect(f.code).toBe("code-1");
    expect(f.redirect_uri).toBe(env.HSBC_REDIRECT_URI);
    expect(f).not.toHaveProperty("scope");
    expect(f.client_assertion).toBeTruthy();
  });

  it("refresh_token carries the refresh token and NO scope", async () => {
    const { e, calls } = makeEnv(() => jsonResponse({ access_token: "psu-a2", expires_in: 300 }));
    await refreshAccessToken(e, "psu-r");
    const f = form(calls[0]!);
    expect(f.grant_type).toBe("refresh_token");
    expect(f.refresh_token).toBe("psu-r");
    expect(f).not.toHaveProperty("scope");
  });

  it("a refused grant throws HsbcError with the bank's error code only", async () => {
    const { e } = makeEnv(() => jsonResponse({ error: "invalid_client", error_description: "bad assertion" }, 401));
    const err = await clientCredentialsToken(e, "payments").catch((x) => x);
    expect(err).toBeInstanceOf(HsbcError);
    expect(err.status).toBe(401);
    expect(err.message).toContain("invalid_client");
  });

  it("ccAccessToken caches the client-credentials token per scope in TokenCacheDO", async () => {
    const { e, calls } = makeEnv(() => jsonResponse({ access_token: `cc-${crypto.randomUUID()}`, expires_in: 3600 }));
    const now = 1_800_000_000;
    const a = await ccAccessToken(e, "payments", now);
    expect(await ccAccessToken(e, "payments", now + 100)).toBe(a);
    expect(calls).toHaveLength(1);
    const b = await ccAccessToken(e, "accounts", now);
    expect(b).not.toBe(a);
    expect(calls).toHaveLength(2);
    expect(await ccAccessToken(e, "payments", now + 3600)).not.toBe(a); // expired (minus safety margin)
    expect(calls).toHaveLength(3);
  });
});

describe("withRefreshOn401", () => {
  it("does not refresh on success", async () => {
    const refresher = vi.fn(async () => {});
    const doFetch = vi.fn(async () => new Response("ok"));
    expect((await withRefreshOn401(doFetch, refresher)).status).toBe(200);
    expect(refresher).not.toHaveBeenCalled();
    expect(doFetch).toHaveBeenCalledTimes(1);
  });

  it("refreshes once and retries once on a 401", async () => {
    const refresher = vi.fn(async () => {});
    const statuses = [401, 200];
    const doFetch = vi.fn(async () => new Response(null, { status: statuses.shift() }));
    expect((await withRefreshOn401(doFetch, refresher)).status).toBe(200);
    expect(refresher).toHaveBeenCalledTimes(1);
    expect(doFetch).toHaveBeenCalledTimes(2);
  });

  it("returns the second 401 instead of looping", async () => {
    const refresher = vi.fn(async () => {});
    const doFetch = vi.fn(async () => new Response(null, { status: 401 }));
    expect((await withRefreshOn401(doFetch, refresher)).status).toBe(401);
    expect(refresher).toHaveBeenCalledTimes(1);
    expect(doFetch).toHaveBeenCalledTimes(2);
  });
});
