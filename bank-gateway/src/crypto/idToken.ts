/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

import type { Env } from "../env";
import { tokenCache } from "../session/TokenCacheDO";
import { base64url, fromBase64url, parseBase64urlJson, utf8 } from "./base64url";

export interface Jwk {
  kid: string;
  kty?: string;
  n: string;
  e: string;
  alg?: string;
}
export interface Jwks {
  keys: Jwk[];
}

export interface IdTokenInput {
  token: string;
  code: string; // for c_hash
  state: string; // for s_hash
  nonce: string;
  clientId: string; // aud
  nowEpochSeconds: number;
  skewSeconds?: number; // default 120
  jwks: Jwks;
  issuer: string; // the discovery document's issuer
}

export type IdTokenFailure = "malformed" | "bad_signature" | "wrong_iss" | "wrong_aud" | "expired" | "nonce_mismatch" | "wrong_c_hash" | "wrong_s_hash";
export type IdTokenVerdict = { ok: true; claims: Record<string, unknown> } | { ok: false; reason: IdTokenFailure };

const DEFAULT_SKEW_SECONDS = 120;

/** Full FAPI id_token verification (D9) — the app only ever checked nonce. */
export async function verifyHsbcIdToken(i: IdTokenInput): Promise<IdTokenVerdict> {
  const fail = (reason: IdTokenFailure): IdTokenVerdict => ({ ok: false, reason });
  const parts = i.token.split(".");
  if (parts.length !== 3) return fail("malformed");
  const [h64, p64, s64] = parts as [string, string, string];
  let header: { alg?: unknown; kid?: unknown };
  let claims: Record<string, any>;
  let sig: Uint8Array;
  try {
    header = parseBase64urlJson(h64);
    claims = parseBase64urlJson(p64);
    sig = fromBase64url(s64);
  } catch {
    return fail("malformed");
  }

  const jwk = i.jwks.keys.find((k) => k.kid === header.kid);
  if (header.alg !== "PS256" || !jwk) return fail("bad_signature");
  const key = await crypto.subtle.importKey("jwk", { kty: "RSA", n: jwk.n, e: jwk.e, alg: "PS256" }, { name: "RSA-PSS", hash: "SHA-256" }, false, ["verify"]);
  if (!(await crypto.subtle.verify({ name: "RSA-PSS", saltLength: 32 }, key, sig, utf8(`${h64}.${p64}`)))) return fail("bad_signature");

  if (claims.iss !== i.issuer) return fail("wrong_iss");
  if (claims.aud !== i.clientId && !(Array.isArray(claims.aud) && claims.aud.includes(i.clientId))) return fail("wrong_aud");
  const skew = i.skewSeconds ?? DEFAULT_SKEW_SECONDS;
  if (typeof claims.exp !== "number" || typeof claims.iat !== "number") return fail("expired");
  if (claims.exp + skew < i.nowEpochSeconds || claims.iat - skew > i.nowEpochSeconds) return fail("expired");
  if (claims.nonce !== i.nonce) return fail("nonce_mismatch");
  if (claims.c_hash !== (await halfHash(i.code))) return fail("wrong_c_hash");
  if (claims.s_hash !== (await halfHash(i.state))) return fail("wrong_s_hash");
  return { ok: true, claims };
}

/** OIDC c_hash / s_hash: base64url of the left 128 bits of SHA-256. */
async function halfHash(value: string): Promise<string> {
  const digest = new Uint8Array(await crypto.subtle.digest("SHA-256", utf8(value)));
  return base64url(digest.slice(0, digest.length / 2));
}

const OIDC_CACHE_KEY = "hsbc:oidc";
const DEFAULT_JWKS_TTL_SECONDS = 3600;

/**
 * HSBC discovery issuer + JWKS, cached in TokenCacheDO for the JWKS response's Cache-Control max-age
 * (3600 s without one). Fetched over the mTLS binding, which also serves hosts that don't ask for a cert.
 */
export async function loadHsbcOidc(
  env: Pick<Env, "HSBC_MTLS" | "HSBC_ISSUER" | "HSBC_AUTHORIZE_HOST" | "TOKEN_CACHE">,
  nowSec: number,
): Promise<{ issuer: string; jwks: Jwks }> {
  const cache = tokenCache(env);
  const hit = await cache.get(OIDC_CACHE_KEY, nowSec);
  if (hit) return JSON.parse(hit);

  // Discovery lives on the authorize host; the issuer host is the mTLS API front and serves none.
  const discovery = await getJson(env, `https://${env.HSBC_AUTHORIZE_HOST}/.well-known/openid-configuration`);
  const { issuer, jwks_uri } = discovery.body as { issuer?: string; jwks_uri?: string };
  if (!issuer || !jwks_uri) throw new Error("HSBC discovery document lacks issuer or jwks_uri");
  if (issuer !== env.HSBC_ISSUER) throw new Error("HSBC discovery issuer does not match HSBC_ISSUER");
  const jwksRes = await getJson(env, jwks_uri);
  const jwks = jwksRes.body as Jwks;
  if (!Array.isArray(jwks.keys)) throw new Error("HSBC JWKS has no keys");

  const maxAge = Number(/max-age=(\d+)/.exec(jwksRes.cacheControl)?.[1] ?? DEFAULT_JWKS_TTL_SECONDS);
  const value = { issuer, jwks };
  await cache.put(OIDC_CACHE_KEY, JSON.stringify(value), nowSec + maxAge);
  return value;
}

async function getJson(env: Pick<Env, "HSBC_MTLS">, url: string): Promise<{ body: unknown; cacheControl: string }> {
  const res = await env.HSBC_MTLS.fetch(url, { headers: { accept: "application/json" } });
  if (!res.ok) throw new Error(`HSBC ${new URL(url).pathname} returned ${res.status}`);
  return { body: await res.json(), cacheControl: res.headers.get("cache-control") ?? "" };
}
