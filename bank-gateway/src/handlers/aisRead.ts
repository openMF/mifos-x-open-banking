/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

import { HsbcError, psuFetch } from "../hsbc/client";
import { rewriteLinks } from "../hsbc/pagination";
import { json } from "../http";
import type { HandlerContext } from "../index";
import { bankPath } from "../routes";

// The session is required by the router before this runs (route.session → requireSession in index.ts).

const TX_PASSTHROUGH = ["fromBookingDateTime", "toBookingDateTime"];

/** Every AIS GET row: the session's psu:ais token, refresh-once-on-401, Links → opaque cursors. */
export async function aisRead(c: HandlerContext): Promise<Response> {
  const path = bankPath(c.route.hsbcOp.path, c.params);
  if (!path) return json({ error: "invalid_param" }, 400);
  const q = new URLSearchParams();
  if (path.endsWith("/transactions")) {
    const from = new URL(c.req.url).searchParams;
    for (const k of TX_PASSTHROUGH) {
      const v = from.get(k);
      if (v) q.set(k, v);
    }
  }
  return aisJson(c, q.size ? `${path}?${q}` : path);
}

/** GET an AIS path (relative to /obie/open-banking) and return its JSON with Links swapped for cursors. */
export async function aisJson(c: HandlerContext, opPath: string): Promise<Response> {
  const res = await psuGet(c, opPath);
  if (!(res instanceof Response)) return res.denied;
  if (!res.ok) return json({ error: "bank_error", bankStatus: res.status }, res.status);
  const body = (await res.json()) as Record<string, unknown>;
  const session = c.session!;
  const page = `${new URL(c.req.url).origin}/v1/ais/page?cursor=`;
  try {
    return json(await rewriteLinks(body, c.env.HSBC_BANK_HOST, async (urls) => (await session.putCursors(urls, nowSec())).map((id) => page + id)));
  } catch {
    return json({ error: "bank_error", bankStatus: 502 }, 502); // a Links URL off the bank host
  }
}

/** psuFetch for the ais leg; a failed refresh (or no tokens) means the bank session is gone → 401. */
export async function psuGet(c: HandlerContext, opPath: string, accept?: string): Promise<Response | { denied: Response }> {
  try {
    return await psuFetch(c.env, c.session!, "ais", { method: "GET", path: opPath }, accept ? { accept } : {});
  } catch (e) {
    if (e instanceof HsbcError) return { denied: json({ error: "session_expired" }, 401) };
    throw e;
  }
}

export const nowSec = () => Math.floor(Date.now() / 1000);
