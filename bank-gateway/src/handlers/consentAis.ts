/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

import { buildAuthorizeUrl, buildRequestObjectJwt } from "../crypto/requestObject";
import { initSigner } from "../crypto/signer";
import { API_PREFIX, ccAccessToken, hsbcFetch } from "../hsbc/client";
import { json, parseJson } from "../http";
import type { HandlerContext } from "../index";
import type { Env } from "../env";
import { bankPath, payLeg } from "../routes";
import { nowSec } from "./aisRead";

const PERMISSION = /^Read[A-Za-z]{1,64}$/;
const MAX_PERMISSIONS = 40;
const DAY_MS = 86_400_000;
const DEFAULT_WINDOW_DAYS = 90; // as LoginRepositoryImpl: consent expires in 90 days, transactions from 90 days back
const DATE_FIELDS = ["expirationDateTime", "transactionFromDateTime", "transactionToDateTime"] as const;

/**
 * POST /v1/consents/ais {permissions, expirationDateTime?, transactionFromDateTime?, transactionToDateTime?}
 * → {flowId, consentId, authorizeUrl}. The client-credentials token comes from TokenCacheDO, so the only
 * signature on a warm path is the request object.
 */
export async function consentAisCreate(c: HandlerContext): Promise<Response> {
  const b = parseJson(c.body);
  const perms = b?.permissions;
  const valid =
    Array.isArray(perms) &&
    perms.length > 0 &&
    perms.length <= MAX_PERMISSIONS &&
    perms.every((p) => typeof p === "string" && PERMISSION.test(p)) &&
    DATE_FIELDS.every((k) => b![k] === undefined || (typeof b![k] === "string" && !Number.isNaN(Date.parse(b![k] as string))));
  if (!valid) return json({ error: "invalid_request" }, 400);
  const prefix = webStatePrefix(c.req, c.env);
  if (prefix === null) return json({ error: "web_origin_not_allowed" }, 400);

  const e = c.env;
  const now = Date.now();
  const iso = (ms: number) => new Date(ms).toISOString();
  const res = await hsbcFetch(e, c.route.hsbcOp, {
    bearer: await ccAccessToken(e, "accounts", nowSec()),
    body: JSON.stringify({
      Data: {
        Permissions: perms,
        ExpirationDateTime: b!.expirationDateTime ?? iso(now + DEFAULT_WINDOW_DAYS * DAY_MS),
        TransactionFromDateTime: b!.transactionFromDateTime ?? iso(now - DEFAULT_WINDOW_DAYS * DAY_MS),
        TransactionToDateTime: b!.transactionToDateTime ?? iso(now),
      },
      Risk: {},
    }),
  });
  if (!res.ok) return json({ error: "bank_error", bankStatus: res.status }, 502);
  const consentId = ((await res.json()) as { Data?: { ConsentId?: unknown } }).Data?.ConsentId;
  if (typeof consentId !== "string" || !bankPath(":id", { id: consentId })) return json({ error: "bank_error", bankStatus: res.status }, 502);

  return beginAuthorisation(e, "ais", consentId, "accounts", prefix);
}

/**
 * The `state` prefix that tells the relay page where a web sign-in returns: `w<index>.` into the ordered
 * WEB_RETURN_ORIGINS list (the relay holds the same list), for X-Client-Platform: web with an allowlisted
 * Origin. "" for every other platform; null for web from an origin that is not on the list.
 */
export function webStatePrefix(req: Request, e: Env): string | null {
  if (req.headers.get("x-client-platform")?.trim().toLowerCase() !== "web") return "";
  const i = (e.WEB_RETURN_ORIGINS ?? "").split(",").map((o) => o.trim()).filter(Boolean).indexOf(req.headers.get("origin") ?? "");
  return i < 0 ? null : `w${i}.`;
}

/**
 * Parks {state, nonce, leg, consentId} as a single-use pending flow and signs the request object
 * → {flowId, consentId, authorizeUrl}. Shared by the AIS, PIS and VRP consent routes.
 */
export async function beginAuthorisation(e: Env, leg: string, consentId: string, scope: "accounts" | "payments", statePrefix = ""): Promise<Response> {
  const flowId = crypto.randomUUID();
  const state = statePrefix + crypto.randomUUID();
  const nonce = crypto.randomUUID();
  await e.SESSIONS.get(e.SESSIONS.idFromName(`flow:${flowId}`)).createPendingFlow({ flowId, state, nonce, leg, consentId, nowSec: nowSec() });

  initSigner(e.HSBC_SIGNING_KEY_PEM);
  const requestJwt = await buildRequestObjectJwt({
    clientId: e.HSBC_CLIENT_ID,
    kid: e.HSBC_KID,
    issuer: e.HSBC_ISSUER,
    redirectUri: e.HSBC_REDIRECT_URI,
    scope,
    consentId,
    state,
    nonce,
    nowEpochSeconds: nowSec(),
  });
  const authorizeUrl = buildAuthorizeUrl(`https://${e.HSBC_AUTHORIZE_HOST}${API_PREFIX}/v1.1/oauth2/authorize`, {
    clientId: e.HSBC_CLIENT_ID,
    scope,
    redirectUri: e.HSBC_REDIRECT_URI,
    state,
    nonce,
    requestJwt,
  });
  return json({ flowId, consentId, authorizeUrl });
}

/**
 * GET / DELETE a consent (AIS, PIS, VRP) or GET a payment on the client-credentials token — only what
 * this session owns: its sign-in consent, a payment consent it authorised, a payment it submitted.
 */
export async function ccRead(c: HandlerContext): Promise<Response> {
  const path = bankPath(c.route.hsbcOp.path, c.params);
  if (!path) return json({ error: "invalid_param" }, 400);
  const { product } = c.route;
  const s = c.session!;
  if (c.params.paymentId !== undefined) {
    if (!(await s.hasPayment(product!, c.params.paymentId))) return json({ error: "payment_not_in_session" }, 403);
  } else if ((await s.getConsent(product ? payLeg(product, c.params.consentId!) : "ais")) !== c.params.consentId) {
    return json({ error: "consent_not_in_session" }, 403);
  }
  const scope = c.route.token === "cc:payments" ? "payments" : "accounts";
  const res = await hsbcFetch(c.env, { method: c.route.hsbcOp.method, path }, { bearer: await ccAccessToken(c.env, scope, nowSec()) });
  if (!res.ok) return json({ error: "bank_error", bankStatus: res.status }, res.status);
  return c.route.method === "DELETE" ? new Response(null, { status: 204 }) : json(await res.json());
}
