/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

/*
 * Bank gateway Worker entry. Every request is matched against the closed route table (routes.ts); anything
 * unlisted is a 404 that never reaches HSBC. Every response carries X-Request-Id, every error is the
 * envelope { error, requestId }, and logs pass through redact() — no token, code, signature or body
 * is ever written out. Gates run here, before any handler: a route's `rateLimit` (limits.ts), then its
 * `session` (requireSession, gate.ts), then its `attest` (requireAttestation, attest/gate.ts) — a handler
 * can't forget one.
 */

import { requireAttestation, type Attestation } from "./attest/gate";
import type { Env } from "./env";
import { requireSession } from "./gate";
import { aisPage } from "./handlers/aisPage";
import { attestChallenge, attestIosRegister } from "./handlers/attest";
import { aisRead } from "./handlers/aisRead";
import { aisStatementFile } from "./handlers/aisStatementFile";
import { authComplete } from "./handlers/authComplete";
import { ccRead, consentAisCreate } from "./handlers/consentAis";
import { fundsRead, paymentConsentCreate, signedPsuPost } from "./handlers/payment";
import { sessionLogout } from "./handlers/sessionLogout";
import { json } from "./http";
import { enforceRateLimit } from "./limits";
import { matchRoute, resolveRoute } from "./router";
import { ROUTES, type Route } from "./routes";
import type { SessionDO } from "./session/SessionDO";

export { AttestationDO } from "./attest/AttestationDO";
export type { Env } from "./env";
export { RateLimitCounterDO } from "./limits/RateLimitCounterDO";
export { SessionDO } from "./session/SessionDO";
export { TokenCacheDO } from "./session/TokenCacheDO";

export interface HandlerContext {
  req: Request;
  env: Env;
  route: Route;
  params: Record<string, string>;
  body: Uint8Array | null;
  requestId: string;
  session?: DurableObjectStub<SessionDO>; // set for every `session: true` route
}
export type Handler = (c: HandlerContext) => Promise<Response>;

export const HANDLERS: Record<string, Handler> = {
  health: async () => json({ status: "ok" }),
  consentAisCreate,
  ccRead,
  authComplete,
  sessionLogout,
  aisRead,
  aisStatementFile,
  aisPage,
  paymentConsentCreate,
  fundsRead,
  signedPsuPost,
  attestChallenge,
  attestIosRegister,
};

interface Passed {
  session?: DurableObjectStub<SessionDO>;
  attestation?: Attestation;
}

/** Rate limit, then session, then attestation — or the refusal response. */
async function gate(req: Request, env: Env, route: Route, body: Uint8Array | null): Promise<Response | Passed> {
  const limited = await enforceRateLimit(req, env, route);
  if (limited) return limited;
  const session = route.session ? await requireSession(req, env) : undefined;
  if (session instanceof Response) return session;
  const attestation = route.attest ? await requireAttestation(req, env, body) : null;
  if (attestation instanceof Response) return attestation;
  return { session, attestation: attestation ?? undefined };
}

const REDACT_KEYS = new Set(["authorization", "access_token", "refresh_token", "id_token", "request", "code", "x-jws-signature", "body"]);

/** Masks REDACT_KEYS at any depth (keys compared case-insensitively). */
export function redact(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(redact);
  if (value === null || typeof value !== "object") return value;
  return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, REDACT_KEYS.has(k.toLowerCase()) ? "[redacted]" : redact(v)]));
}

function log(level: "info" | "error", entry: Record<string, unknown>): void {
  const line = JSON.stringify(redact({ level, ...entry }));
  if (level === "error") console.error(line);
  else console.log(line);
}

const CORS_ALLOW_HEADERS = "authorization, content-type, x-client-platform, x-client-instance, x-attestation, x-idempotency-key";

/** The request's Origin when it is on the CORS_ORIGINS allowlist (the web client), else null. */
function allowedOrigin(req: Request, env: Env): string | null {
  const origin = req.headers.get("origin");
  if (!origin) return null;
  return (env.CORS_ORIGINS ?? "").split(",").map((o) => o.trim()).includes(origin) ? origin : null;
}

/** A preflight is answered only for an allowlisted origin asking for a method + path the route table lists. */
function preflight(req: Request, origin: string | null, routes: readonly Route[]): Response | null {
  const want = req.headers.get("access-control-request-method")?.toUpperCase();
  if (req.method !== "OPTIONS" || !origin || !want || !matchRoute(want, new URL(req.url).pathname, routes)) return null;
  return new Response(null, {
    status: 204,
    headers: {
      "access-control-allow-origin": origin,
      "access-control-allow-methods": "GET, POST, DELETE",
      "access-control-allow-headers": CORS_ALLOW_HEADERS,
      "access-control-max-age": "600",
      vary: "Origin",
    },
  });
}

export async function handle(req: Request, env: Env, routes: readonly Route[] = ROUTES, handlers: Record<string, Handler> = HANDLERS): Promise<Response> {
  const origin = allowedOrigin(req, env);
  const pre = preflight(req, origin, routes);
  if (pre) return pre;
  const requestId = crypto.randomUUID();
  const started = Date.now();
  let routeId = "unmatched";
  let attestation: Attestation | undefined;
  let res: Response;
  try {
    const r = await resolveRoute(req, routes);
    const handler = r.ok ? handlers[r.route.handler ?? r.route.id] : undefined;
    if (!r.ok) res = json({ error: r.error, requestId }, r.status);
    else if (!handler) res = json({ error: "route_not_found", requestId }, 404);
    else {
      routeId = r.route.id;
      const g = await gate(req, env, r.route, r.body);
      if (!(g instanceof Response)) attestation = g.attestation;
      res = g instanceof Response ? g : await handler({ req, env, route: r.route, params: r.params, body: r.body, requestId, session: g.session });
    }
  } catch (e) {
    // The error itself may carry bank text; only its type is logged, never its message.
    log("error", { requestId, route: routeId, error: e instanceof Error ? e.name : "unknown" });
    res = json({ error: "internal", requestId }, 500);
  }
  const headers = new Headers(res.headers);
  headers.set("x-request-id", requestId);
  if (attestation) headers.set("x-attestation", attestation.status);
  if (origin && req.method !== "OPTIONS") { // a refused preflight stays unreadable
    headers.set("access-control-allow-origin", origin);
    headers.set("access-control-expose-headers", "x-request-id, x-attestation");
    headers.append("vary", "Origin");
  }
  log("info", { requestId, method: req.method, path: new URL(req.url).pathname, route: routeId, status: res.status, ms: Date.now() - started, attestation });
  return new Response(res.body, { status: res.status, headers });
}

export default {
  fetch: (req: Request, env: Env) => handle(req, env),
} satisfies ExportedHandler<Env>;
