/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

import { ROUTES, type Route } from "./routes";

export interface MatchHit {
  route: Route;
  params: Record<string, string>;
}

export type Resolved =
  | { ok: true; route: Route; params: Record<string, string>; body: Uint8Array | null }
  | { ok: false; status: 404 | 413 | 415; error: string };

/** Exact method + segment-by-segment template match; `:name` captures one non-empty segment. */
export function matchRoute(method: string, path: string, routes: readonly Route[] = ROUTES): MatchHit | null {
  const got = path.split("/");
  for (const route of routes) {
    if (route.method !== method) continue;
    const want = route.path.split("/");
    if (want.length !== got.length) continue;
    const params: Record<string, string> = {};
    const ok = want.every((w, i) => {
      const g = got[i]!;
      if (!w.startsWith(":")) return w === g;
      params[w.slice(1)] = g;
      return g.length > 0;
    });
    if (ok) return { route, params };
  }
  return null;
}

/** Matches the request and enforces the route's body rules: 404 unlisted, 415 non-JSON, 413 oversize. */
export async function resolveRoute(req: Request, routes: readonly Route[] = ROUTES): Promise<Resolved> {
  const hit = matchRoute(req.method, new URL(req.url).pathname, routes);
  if (!hit) return { ok: false, status: 404, error: "route_not_found" };
  // The runtime gives an empty POST a non-null body stream with Content-Length: 0 — that is no body.
  if (!req.body || req.headers.get("content-length") === "0") return { ...hit, ok: true, body: null };

  const tooLarge = { ok: false, status: 413, error: "payload_too_large" } as const;
  const max = hit.route.maxBodyBytes;
  if (max === 0 || Number(req.headers.get("content-length") ?? 0) > max) return tooLarge;
  const type = req.headers.get("content-type")?.split(";")[0]?.trim().toLowerCase();
  if (type !== "application/json") return { ok: false, status: 415, error: "unsupported_media_type" };

  // Content-Length can be absent (chunked) or lie, so count while reading and stop at the limit.
  const chunks: Uint8Array[] = [];
  let size = 0;
  const reader = req.body.getReader();
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    size += value.byteLength;
    if (size > max) {
      await reader.cancel();
      return tooLarge;
    }
    chunks.push(value);
  }
  const body = new Uint8Array(size);
  let offset = 0;
  for (const c of chunks) {
    body.set(c, offset);
    offset += c.byteLength;
  }
  return { ...hit, ok: true, body };
}
