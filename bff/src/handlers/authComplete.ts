/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

import { loadHsbcOidc, verifyHsbcIdToken } from "../crypto/idToken";
import { ccAccessToken, exchangeAuthorizationCode, HsbcError, hsbcFetch } from "../hsbc/client";
import { json, parseJson } from "../http";
import type { HandlerContext } from "../index";
import { requireSession } from "../gate";
import { bankPath, ROUTES } from "../routes";
import { newSession, sessionStub } from "../session/SessionDO";
import { nowSec } from "./aisRead";

const FIELDS = ["flowId", "code", "idToken", "state"] as const;
const MAX_FIELD = 8_192;
const FALLBACK_SESSION_MS = 90 * 86_400_000;

/**
 * POST /v1/auth/complete {flowId, code, idToken, state}. Order matters (D9, DEF-001): take the single-use
 * flow → state → full id_token verification → only then exchange the code → read the consent status →
 * Rejected discards the tokens (409) → otherwise store them on the flow's leg and return only the session id.
 *
 * The ais leg always mints a new session. A payment / vrp leg is added to the session presented in
 * `Authorization: Session <id>` (the signed-in app), or to a new one when none is presented.
 */
export async function authComplete(c: HandlerContext): Promise<Response> {
  const b = parseJson(c.body);
  if (!b || !FIELDS.every((k) => typeof b[k] === "string" && b[k].length > 0 && b[k].length <= MAX_FIELD)) return json({ error: "invalid_request" }, 400);
  const { flowId, code, idToken, state } = b as Record<(typeof FIELDS)[number], string>;
  const e = c.env;
  const now = nowSec();

  const presented = c.req.headers.has("authorization") ? await requireSession(c.req, e) : null;

  const flow = await e.SESSIONS.get(e.SESSIONS.idFromName(`flow:${flowId}`)).takePendingFlow(flowId, now);
  if (!flow) return json({ error: "unknown_flow" }, 400);
  const [leg, product] = flow.leg.split(":") as [string, string | undefined];
  let consentPath: string | undefined;
  switch (leg) {
    case "ais":
      consentPath = "/v4.0/aisp/account-access-consents/:consentId";
      break;
    case "payment":
    case "vrp":
      consentPath = ROUTES.find((r) => r.product === (leg === "vrp" ? "vrp" : product) && r.method === "GET" && r.path.endsWith("/:consentId"))?.hsbcOp.path;
      break;
  }
  if (!consentPath) return json({ error: "unknown_flow" }, 400);
  // ais re-sign-in ignores a stale session header; a payment / vrp leg must land on a live session or none.
  if (leg !== "ais" && presented instanceof Response) return presented;
  if (flow.state !== state) return json({ error: "state_mismatch" }, 400);

  const oidc = await loadHsbcOidc(e, now);
  const verdict = await verifyHsbcIdToken({ token: idToken, code, state, nonce: flow.nonce, clientId: e.HSBC_CLIENT_ID, nowEpochSeconds: now, jwks: oidc.jwks, issuer: oidc.issuer });
  if (!verdict.ok) return json({ error: "invalid_id_token" }, 400);

  let tokens;
  try {
    tokens = await exchangeAuthorizationCode(e, code);
  } catch (err) {
    if (err instanceof HsbcError) return json({ error: "token_exchange_failed", bankStatus: err.status }, 502);
    throw err;
  }
  // VRP payments run for the consent's lifetime, so its leg needs the refresh token; payments never refresh.
  if (leg === "vrp" && !tokens.refresh_token) return json({ error: "token_exchange_failed", bankStatus: 200 }, 502);

  // A failed status read is not a rejection: the session is still minted (consentStatus "Unknown").
  const res = await hsbcFetch(e, { method: "GET", path: bankPath(consentPath, { consentId: flow.consentId })! }, { bearer: await ccAccessToken(e, leg === "ais" ? "accounts" : "payments", now) });
  const consent = res.ok ? ((await res.json()) as { Data?: { Status?: string; ExpirationDateTime?: string; ControlParameters?: { ValidToDateTime?: string } } }).Data : undefined;
  if (consent?.Status === "Rejected") return json({ error: "consent_rejected" }, 409);

  const reuse = leg !== "ais" && presented && !(presented instanceof Response) ? presented : null;
  const session = reuse ? c.req.headers.get("authorization")!.slice("Session ".length) : await newSession(e.SESSIONS, now);
  const stub = reuse ?? (await sessionStub(e.SESSIONS, session));
  const tokenExpiry = now + (tokens.expires_in ?? 0);
  await stub.putTokens(flow.leg, { access: tokens.access_token, refresh: leg === "payment" ? undefined : tokens.refresh_token, expiresAt: tokenExpiry });
  await stub.putConsent(flow.leg, flow.consentId);

  const consentExpiresAt = consent?.ExpirationDateTime ?? consent?.ControlParameters?.ValidToDateTime ?? null;
  // ponytail: expiresAt is advisory — the consent's expiry (payment leg: its 5-minute PSU token); the Worker
  // ends a session when refresh fails (401).
  const expiresAt = leg === "payment" ? tokenExpiry * 1000 : Date.parse(consentExpiresAt ?? "") || Date.now() + FALLBACK_SESSION_MS;
  return json({ session, expiresAt, leg, consentId: flow.consentId, consentStatus: consent?.Status ?? "Unknown", consentExpiresAt });
}
