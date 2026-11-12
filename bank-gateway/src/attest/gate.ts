/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

import { utf8 } from "../crypto/base64url";
import type { Env } from "../env";
import { SESSION_HEADER } from "../gate";
import { json } from "../http";
import { verifyAppAttestAssertion } from "./appAttest";
import { concat, fail, type Verdict } from "./bytes";
import { verifyPlayIntegrity } from "./playIntegrity";

/*
 * The attestation gate for `attest: true` rows (run by index.ts after rate limit + session).
 * X-Client-Platform android → Play Integrity, ios → App Attest assertion. desktop / web / absent → no
 * attestation, session + rate limits only (GOAL D3; the header is client-asserted — residual risk, see plan).
 *
 *   enforce (default)  unconfigured → 503 attestation_unconfigured · no X-Attestation → 401 attestation_missing
 *                      · failed check → 403 attestation_invalid {reason}
 *   report-only        never refuses; the response carries x-attestation: unverified (or verified)
 *
 * Both platforms sign over the same clientData: UTF-8 "METHOD /path\n" followed by the exact body bytes.
 */

export type Attestation = { status: "verified" | "unverified"; reason?: string };
type Check = { ok: true } | { ok: false; status: 401 | 403 | 503; error: string; reason?: string };

export const clientData = (req: Request, body: Uint8Array | null) =>
  concat(utf8(`${req.method} ${new URL(req.url).pathname}\n`), body ?? new Uint8Array());

/** null when the platform needs no attestation; a refusal Response (enforce); else the verdict for x-attestation. */
export async function requireAttestation(req: Request, env: Env, body: Uint8Array | null): Promise<Response | Attestation | null> {
  const platform = req.headers.get("x-client-platform")?.trim().toLowerCase();
  if (platform !== "android" && platform !== "ios") return null;
  const c = await check(req, env, platform, body);
  if (c.ok) return { status: "verified" };
  if (env.ATTESTATION_MODE === "report-only") return { status: "unverified", reason: c.reason ?? c.error };
  return json({ error: c.error, ...(c.reason ? { reason: c.reason } : {}) }, c.status);
}

async function check(req: Request, env: Env, platform: "android" | "ios", body: Uint8Array | null): Promise<Check> {
  if (!(platform === "android" ? env.PLAY_INTEGRITY_SA_JSON : env.APPLE_TEAM_ID)) return { ok: false, status: 503, error: "attestation_unconfigured" };
  const header = req.headers.get("x-attestation");
  if (!header) return { ok: false, status: 401, error: "attestation_missing" };
  const prefix = platform === "android" ? "play " : "ios ";
  let v: Verdict;
  try {
    const value = header.slice(prefix.length);
    if (!header.startsWith(prefix)) v = fail("malformed");
    else if (platform === "android") v = await verifyPlayIntegrity(env, value, clientData(req, body), SESSION_HEADER.exec(req.headers.get("authorization") ?? "")?.[1]);
    else v = await verifyAppAttestAssertion(env, value, clientData(req, body));
  } catch {
    v = fail("error"); // e.g. an unparseable service account — never a 500, never a pass in enforce
  }
  return v.ok ? v : { ok: false, status: 403, error: "attestation_invalid", reason: v.reason };
}
