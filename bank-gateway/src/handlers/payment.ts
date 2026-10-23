/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

import { detachedJwsSignature } from "../crypto/detachedJws";
import { initSigner } from "../crypto/signer";
import type { Env } from "../env";
import { bankError, ccAccessToken, HsbcError, hsbcFetch, psuFetch } from "../hsbc/client";
import { json } from "../http";
import type { HandlerContext } from "../index";
import { bankPath, payLeg } from "../routes";
import { nowSec } from "./aisRead";
import { beginAuthorisation, webStatePrefix } from "./consentAis";

/*
 * PIS + VRP (api/Pisp.kt, api/Vrp.kt). Every signed POST forwards the client's body bytes unchanged
 * with a detached JWS over exactly those bytes — the body is parsed only to check it, never re-serialised,
 * which is the TextContent invariant of the app's signedPost. Every refusal below runs before the RSA work.
 */

const IDEMPOTENCY_KEY = /^[A-Za-z0-9._~-]{1,40}$/; // OBIE caps x-idempotency-key at 40; RFC 7230 token chars
const NOT_OWNED = "consent_not_owned";
const MISSING_INITIATION = "missing_initiation";

interface Signed {
  raw: Uint8Array;
  data: Record<string, unknown>;
  idempotencyKey: string;
}

/**
 * The mandatory idempotency key and a compact OBIE `{Data: {...}}` body, or the 400 refusal. HSBC verifies
 * the JWS over the compact JSON form (a trailing newline or pretty-printing → U015, measured live), so a
 * body that is not already compact is refused rather than silently re-serialised — the app's
 * JsonObject.toString() is compact.
 */
function signedRequest(c: HandlerContext): Signed | Response {
  const idempotencyKey = c.req.headers.get("x-idempotency-key") ?? "";
  if (!IDEMPOTENCY_KEY.test(idempotencyKey)) return json({ error: "idempotency_key" }, 400);
  let text: string;
  let body: unknown;
  try {
    text = new TextDecoder("utf-8", { fatal: true, ignoreBOM: true }).decode(c.body ?? new Uint8Array());
    body = JSON.parse(text);
  } catch {
    return json({ error: "invalid_request" }, 400);
  }
  const data = (body as { Data?: unknown } | null)?.Data;
  if (data === null || typeof data !== "object" || Array.isArray(data)) return json({ error: "invalid_request" }, 400);
  if (JSON.stringify(body) !== text) return json({ error: "body_not_compact" }, 400);
  return { raw: c.body!, data: data as Record<string, unknown>, idempotencyKey };
}

/** One RSA signature: the detached JWS over the exact bytes that will be forwarded. */
function sign(e: Env, raw: Uint8Array): Promise<string> {
  initSigner(e.HSBC_SIGNING_KEY_PEM);
  return detachedJwsSignature({ payload: raw, kid: e.HSBC_KID, issuer: e.HSBC_SIGNING_ISSUER, issuedAtEpochSeconds: nowSec() });
}

/** The bank's error codes / paths / messages (never tokens); a 4xx keeps its status, anything else is 502. */
async function bankFailure(res: Response): Promise<Response> {
  const status = res.status >= 400 && res.status < 500 ? res.status : 502;
  return json({ error: "bank_error", bankStatus: res.status, bankError: await bankError(res) }, status);
}

/**
 * POST /v1/consents/payment/{product} and /v1/consents/vrp — the client's OBIE consent body, signed and
 * forwarded on the cached cc:payments token → {flowId, consentId, authorizeUrl}, as for AIS.
 */
export async function paymentConsentCreate(c: HandlerContext): Promise<Response> {
  const req = signedRequest(c);
  if (req instanceof Response) return req;
  if (!req.data.Initiation) return json({ error: MISSING_INITIATION }, 400);
  const prefix = webStatePrefix(c.req, c.env);
  if (prefix === null) return json({ error: "web_origin_not_allowed" }, 400);
  const e = c.env;
  const res = await hsbcFetch(e, c.route.hsbcOp, {
    bearer: await ccAccessToken(e, "payments", nowSec()),
    body: req.raw,
    idempotencyKey: req.idempotencyKey,
    jwsSignature: await sign(e, req.raw),
  });
  if (!res.ok) return bankFailure(res);
  const consentId = ((await res.json()) as { Data?: { ConsentId?: unknown } }).Data?.ConsentId;
  if (typeof consentId !== "string" || !bankPath(":id", { id: consentId })) return json({ error: "bank_error", bankStatus: res.status }, 502);
  return beginAuthorisation(e, payLeg(c.route.product!, consentId), consentId, "payments", prefix);
}

/** GET …/{consentId}/funds-confirmation (domestic + international) on the consent's PSU payment token. */
export async function fundsRead(c: HandlerContext): Promise<Response> {
  const path = bankPath(c.route.hsbcOp.path, c.params);
  if (!path) return json({ error: "invalid_param" }, 400);
  const leg = payLeg(c.route.product!, c.params.consentId!);
  if ((await c.session!.getConsent(leg)) !== c.params.consentId) return json({ error: NOT_OWNED }, 403);
  return psuForward(c, leg, { method: "GET", path }, {});
}

/**
 * POST a payment (six PIS products, VRP) or a VRP funds-confirmation on the PSU token of the consent the
 * body names. The consent must be one this session authorised — otherwise the Worker's signing key could
 * be aimed at someone else's consent — and a VRP funds path must name the same consent as its body.
 */
export async function signedPsuPost(c: HandlerContext): Promise<Response> {
  const req = signedRequest(c);
  if (req instanceof Response) return req;
  const consentId = req.data.ConsentId;
  if (typeof consentId !== "string" || !bankPath(":id", { id: consentId })) return json({ error: "invalid_request" }, 400);
  const isFunds = c.params.consentId !== undefined;
  if (isFunds ? consentId !== c.params.consentId : !req.data.Initiation) return isFunds ? json({ error: NOT_OWNED }, 403) : json({ error: MISSING_INITIATION }, 400);
  const leg = payLeg(c.route.product!, consentId);
  if ((await c.session!.getConsent(leg)) !== consentId) return json({ error: NOT_OWNED }, 403);
  const path = bankPath(c.route.hsbcOp.path, c.params)!;

  const res = await psuForward(c, leg, { method: "POST", path }, { body: req.raw, idempotencyKey: req.idempotencyKey, jwsSignature: await sign(c.env, req.raw) });
  if (!isFunds && res.ok) {
    const out = (await res.clone().json()) as { Data?: Record<string, unknown> };
    // Data.<Product>Id (DomesticPaymentId, DomesticVRPId, …) — recorded so only this session can read it back.
    const id = Object.entries(out.Data ?? {}).find(([k, v]) => k !== "ConsentId" && k.endsWith("Id") && typeof v === "string")?.[1] as string | undefined;
    if (id) await c.session!.putPayment(c.route.product!, id);
  }
  return res;
}

/** psuFetch with the bank body passed through; an unrefreshable 401 means the PSU must authorise again. */
async function psuForward(c: HandlerContext, leg: string, op: { method: "GET" | "POST"; path: string }, call: Parameters<typeof psuFetch>[4]): Promise<Response> {
  let res: Response;
  try {
    res = await psuFetch(c.env, c.session!, leg, op, call);
  } catch (e) {
    if (e instanceof HsbcError) return json({ error: "session_expired" }, 401); // the VRP refresh was refused
    throw e;
  }
  if (res.status === 401) return json({ error: "psu_token_expired" }, 401); // single payments: 5 min, no refresh
  if (!res.ok) return bankFailure(res);
  return json(await res.json(), res.status);
}
