/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

import { buildClientAssertion } from "../crypto/clientAssertion";
import { initSigner } from "../crypto/signer";
import type { Env } from "../env";
import type { Method } from "../routes";
import type { SessionDO, TokenSet } from "../session/SessionDO";
import { tokenCache } from "../session/TokenCacheDO";

export const API_PREFIX = "/obie/open-banking";
const TOKEN_PATH = `${API_PREFIX}/v1.1/oauth2/token`;
const CLIENT_ASSERTION_TYPE = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";
const IDEMPOTENCY_KEY_MAX_LENGTH = 40;
const CC_TOKEN_SAFETY_SECONDS = 60; // drop a cached client-credentials token this long before it expires

type HsbcEnv = Pick<Env, "HSBC_MTLS" | "HSBC_BANK_HOST" | "HSBC_CLIENT_ID" | "HSBC_KID" | "HSBC_SIGNING_KEY_PEM" | "HSBC_REDIRECT_URI">;

export interface TokenResponse {
  access_token: string;
  refresh_token?: string;
  id_token?: string;
  expires_in?: number;
  scope?: string;
}

/** A refused bank call. The message holds the bank's error codes only — never request data or tokens. */
export class HsbcError extends Error {
  constructor(readonly status: number, detail: string) {
    super(`HSBC ${status}: ${detail}`);
  }
}

/** x-fapi-interaction-id fresh per call; x-fapi-financial-id only when non-blank (the app leaves it blank). */
export function fapiHeaders(financialId?: string): Record<string, string> {
  const h: Record<string, string> = { "x-fapi-interaction-id": crypto.randomUUID() };
  if (financialId?.trim()) h["x-fapi-financial-id"] = financialId;
  return h;
}

export function requireIdempotencyKey(k: string): void {
  if (!k || k.length > IDEMPOTENCY_KEY_MAX_LENGTH) {
    throw new Error(`x-idempotency-key must be 1..${IDEMPOTENCY_KEY_MAX_LENGTH} characters, was ${k.length}`);
  }
}

export interface HsbcCall {
  bearer?: string;
  body?: string | Uint8Array; // forwarded as-is: a signed body's bytes are the bytes its JWS covers
  idempotencyKey?: string;
  jwsSignature?: string;
  financialId?: string;
  accept?: string; // default application/json
}

/** One bank API call over the mTLS binding. `op.path` is relative to /obie/open-banking. */
export async function hsbcFetch(env: Pick<Env, "HSBC_MTLS" | "HSBC_BANK_HOST">, op: { method: Method; path: string }, c: HsbcCall = {}): Promise<Response> {
  const headers: Record<string, string> = { accept: c.accept ?? "application/json", ...fapiHeaders(c.financialId) };
  if (c.bearer) headers.authorization = `Bearer ${c.bearer}`;
  if (c.body !== undefined) headers["content-type"] = "application/json";
  if (c.idempotencyKey !== undefined) {
    requireIdempotencyKey(c.idempotencyKey);
    headers["x-idempotency-key"] = c.idempotencyKey;
  }
  if (c.jwsSignature) headers["x-jws-signature"] = c.jwsSignature;
  return env.HSBC_MTLS.fetch(`https://${env.HSBC_BANK_HOST}${API_PREFIX}${op.path}`, { method: op.method, headers, body: c.body });
}

/** POSTs a grant to the token endpoint, authenticated with a fresh private_key_jwt. */
async function tokenGrant(env: HsbcEnv, params: Record<string, string>): Promise<TokenResponse> {
  initSigner(env.HSBC_SIGNING_KEY_PEM);
  const tokenUrl = `https://${env.HSBC_BANK_HOST}${TOKEN_PATH}`;
  const assertion = await buildClientAssertion({
    clientId: env.HSBC_CLIENT_ID,
    kid: env.HSBC_KID,
    tokenUrl,
    nowEpochSeconds: Math.floor(Date.now() / 1000),
    jti: crypto.randomUUID(),
  });
  const res = await env.HSBC_MTLS.fetch(tokenUrl, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded", accept: "application/json" },
    body: new URLSearchParams({ ...params, client_assertion_type: CLIENT_ASSERTION_TYPE, client_assertion: assertion }),
  });
  if (!res.ok) throw new HsbcError(res.status, await bankError(res));
  return res.json();
}

/** client_credentials carries scope; authorization_code and refresh_token do not. */
export const clientCredentialsToken = (env: HsbcEnv, scope: "accounts" | "payments") => tokenGrant(env, { grant_type: "client_credentials", scope });

export const exchangeAuthorizationCode = (env: HsbcEnv, code: string) =>
  tokenGrant(env, { grant_type: "authorization_code", code, redirect_uri: env.HSBC_REDIRECT_URI });

// redirect_uri as in HSBCSandboxHttpClient.refreshAccessToken, the refresh path the app runs today.
export const refreshAccessToken = (env: HsbcEnv, refreshToken: string) =>
  tokenGrant(env, { grant_type: "refresh_token", refresh_token: refreshToken, redirect_uri: env.HSBC_REDIRECT_URI });

/** Client-credentials access token per scope, cached in TokenCacheDO so hot paths sign at most once (D2). */
export async function ccAccessToken(env: HsbcEnv & Pick<Env, "TOKEN_CACHE">, scope: "accounts" | "payments", nowSec: number): Promise<string> {
  const cache = tokenCache(env);
  const key = `cc:${scope}`;
  const hit = await cache.get(key, nowSec);
  if (hit) return hit;
  const t = await clientCredentialsToken(env, scope);
  await cache.put(key, t.access_token, nowSec + (t.expires_in ?? 0) - CC_TOKEN_SAFETY_SECONDS);
  return t.access_token;
}

/** Retries exactly once after a 401: `doFetch` must read the token the refresher installs. */
export async function withRefreshOn401(doFetch: () => Promise<Response>, refresher: () => Promise<void>): Promise<Response> {
  const first = await doFetch();
  if (first.status !== 401) return first;
  await refresher();
  return doFetch();
}

/** A PSU-token call for one session leg; a 401 refreshes through SessionDO's single-writer lock. */
export async function psuFetch(
  env: HsbcEnv,
  session: DurableObjectStub<SessionDO>,
  leg: string,
  op: { method: Method; path: string },
  c: Omit<HsbcCall, "bearer"> = {},
): Promise<Response> {
  const tokens = await session.getTokens(leg);
  if (!tokens) throw new HsbcError(401, "no PSU tokens for this leg");
  let access = tokens.access;
  // A leg without a refresh token (single payments) has nothing to refresh with: its 401 is the answer.
  if (!tokens.refresh) return hsbcFetch(env, op, { ...c, bearer: access });
  return withRefreshOn401(
    () => hsbcFetch(env, op, { ...c, bearer: access }),
    async () => {
      access = await session.refresh(leg, access, async (refreshToken: string): Promise<TokenSet> => {
        const t = await refreshAccessToken(env, refreshToken);
        return { access: t.access_token, refresh: t.refresh_token, expiresAt: Math.floor(Date.now() / 1000) + (t.expires_in ?? 0) };
      });
    },
  );
}

/** Bank error codes, paths and messages only — never request data, tokens or headers. */
export async function bankError(res: Response): Promise<string> {
  try {
    const b = (await res.json()) as { error?: string; error_description?: string; Code?: string; Errors?: { ErrorCode?: string; Message?: string; Path?: string }[] };
    const parts = [b.error, b.error_description, b.Code, ...(b.Errors ?? []).map((e) => [e.ErrorCode, e.Path, e.Message].filter(Boolean).join(" | "))];
    return parts.filter(Boolean).join(" ; ").slice(0, 400) || "unparsed";
  } catch {
    return "non-json";
  }
}
