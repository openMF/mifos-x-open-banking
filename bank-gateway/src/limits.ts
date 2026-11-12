/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

import type { Env } from "./env";
import { json } from "./http";
import type { Route } from "./routes";

/**
 * 429 { error, retryAfter } once the route's budget is spent for this install + IP, else null. Uses the
 * Rate Limiting binding when present, otherwise a Durable Object counter per key.
 * ponytail: X-Client-Instance is client-chosen, so rotating it resets the count — attestation (phase 06)
 * is the real anti-automation gate; add a per-IP-only bucket if abuse shows up before then.
 */
export async function enforceRateLimit(req: Request, env: Pick<Env, "CONSENT_LIMIT" | "RATE_LIMIT_COUNTER">, route: Route): Promise<Response | null> {
  const rl = route.rateLimit;
  if (!rl) return null;
  const instance = (req.headers.get("x-client-instance") ?? "anon").slice(0, 64);
  const ip = req.headers.get("cf-connecting-ip") ?? "unknown";
  const key = `${route.id}:${instance}:${ip}`;
  const ok = env.CONSENT_LIMIT
    ? (await env.CONSENT_LIMIT.limit({ key })).success
    : await env.RATE_LIMIT_COUNTER.get(env.RATE_LIMIT_COUNTER.idFromName(key)).hit(rl.limit, rl.period, Date.now());
  return ok ? null : json({ error: "rate_limited", retryAfter: rl.period }, 429, { "retry-after": String(rl.period) });
}
