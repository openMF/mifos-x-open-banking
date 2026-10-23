/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

import { base64url, base64urlJson, utf8 } from "../crypto/base64url";
import type { Env } from "../env";
import { tokenCache } from "../session/TokenCacheDO";
import { attestationStub } from "./AttestationDO";
import { CHALLENGE, concat, fail, OK, sha256, type Verdict } from "./bytes";

/*
 * Play Integrity, standard request (Android). The app asks Play for a token with
 *   requestHash = base64url(SHA-256(challenge ‖ clientData))   (clientData: see attest/gate.ts)
 * and sends `X-Attestation: play <challenge>.<token>`. The Worker consumes the challenge (single use),
 * has Google decode the token and checks package, requestHash, app and device verdicts.
 * CPU: the Google access token is minted with one RS256 signature on a TokenCacheDO miss (~hourly),
 * never per request; a request costs two subrequests (DO + Google) and a hash.
 */

const PACKAGE = "org.mifosx.openbanking";
const TOKEN_URI = "https://oauth2.googleapis.com/token";
const CACHE_KEY = "google:playintegrity";
const VERDICT_TTL_MS = 10 * 60_000;

interface DecodeResponse {
  tokenPayloadExternal?: {
    requestDetails?: { requestPackageName?: string; requestHash?: string };
    appIntegrity?: { appRecognitionVerdict?: string };
    deviceIntegrity?: { deviceRecognitionVerdict?: string[] };
  };
}

let sa: { json: string; email: string; key: Promise<CryptoKey> } | undefined;
/** The service-account key, imported once per isolate (the secret is the same string for its life). */
function serviceAccount(json: string) {
  if (sa?.json !== json) {
    const parsed = JSON.parse(json) as { client_email: string; private_key: string };
    const der = Uint8Array.from(atob(parsed.private_key.replace(/-----(BEGIN|END) PRIVATE KEY-----|\s+/g, "")), (c) => c.charCodeAt(0));
    sa = { json, email: parsed.client_email, key: crypto.subtle.importKey("pkcs8", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]) };
  }
  return sa;
}

/** A Google OAuth access token for the Play Integrity scope, from TokenCacheDO or freshly minted. */
async function googleToken(env: Pick<Env, "TOKEN_CACHE" | "PLAY_INTEGRITY_SA_JSON">): Promise<string | null> {
  const now = Math.floor(Date.now() / 1000);
  const cache = tokenCache(env);
  const hit = await cache.get(CACHE_KEY, now);
  if (hit) return hit;
  const { email, key } = serviceAccount(env.PLAY_INTEGRITY_SA_JSON!);
  const h = base64urlJson({ alg: "RS256", typ: "JWT" });
  const p = base64urlJson({ iss: email, scope: "https://www.googleapis.com/auth/playintegrity", aud: TOKEN_URI, iat: now, exp: now + 3600 });
  const sig = base64url(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", await key, utf8(`${h}.${p}`)));
  const res = await fetch(TOKEN_URI, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: `${h}.${p}.${sig}` }),
  });
  if (!res.ok) return null;
  const t = await res.json<{ access_token: string; expires_in: number }>();
  await cache.put(CACHE_KEY, t.access_token, now + t.expires_in - 60);
  return t.access_token;
}

/** `value` is the header after "play ". A passed verdict is cached for `sessionId` (if any) for 10 minutes. */
export async function verifyPlayIntegrity(env: Env, value: string, clientData: Uint8Array, sessionId?: string): Promise<Verdict> {
  const cached = sessionId ? attestationStub(env, `verdict:${base64url(await sha256(utf8(sessionId)))}`) : undefined;
  if (cached && (await cached.hasVerdict(Date.now()))) return OK;

  const dot = value.indexOf(".");
  const challenge = value.slice(0, dot);
  const token = value.slice(dot + 1);
  if (!CHALLENGE.test(challenge) || !token) return fail("malformed");
  if (!(await attestationStub(env, `challenge:${challenge}`).takeChallenge(Date.now()))) return fail("challenge");

  const access = await googleToken(env);
  if (!access) return fail("google_error");
  const res = await fetch(`https://playintegrity.googleapis.com/v1/${PACKAGE}:decodeIntegrityToken`, {
    method: "POST",
    headers: { authorization: `Bearer ${access}`, "content-type": "application/json" },
    body: JSON.stringify({ integrity_token: token }),
  });
  if (!res.ok) return fail("google_error"); // Google's error body stays out of the response and the log
  const p = (await res.json<DecodeResponse>()).tokenPayloadExternal;

  if (p?.requestDetails?.requestPackageName !== PACKAGE) return fail("package");
  if (p.requestDetails.requestHash !== base64url(await sha256(concat(utf8(challenge), clientData)))) return fail("request_hash");
  if (p.appIntegrity?.appRecognitionVerdict !== "PLAY_RECOGNIZED") return fail("app_verdict");
  if (!p.deviceIntegrity?.deviceRecognitionVerdict?.includes("MEETS_DEVICE_INTEGRITY")) return fail("device_verdict");
  await cached?.cacheVerdict(Date.now() + VERDICT_TTL_MS);
  return OK;
}
