/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

import { API_PREFIX } from "../hsbc/client";
import { json } from "../http";
import type { HandlerContext } from "../index";
import { aisJson, nowSec } from "./aisRead";

const AIS_PREFIX = `${API_PREFIX}/v4.0/aisp/`;

/** Resolves an opaque cursor (issued to this session by aisJson) back to its HSBC Links URL and reads it. */
export async function aisPage(c: HandlerContext): Promise<Response> {
  const cursor = new URL(c.req.url).searchParams.get("cursor") ?? "";
  const stored = cursor ? await c.session!.getCursor(cursor, nowSec()) : null;
  if (!stored) return json({ error: "unknown_cursor" }, 400);
  const u = new URL(stored);
  // Cursors are only minted from checked bank links; this re-check keeps the page route a closed door.
  if (u.protocol !== "https:" || u.host !== c.env.HSBC_BANK_HOST || !u.pathname.startsWith(AIS_PREFIX)) {
    return json({ error: "host_refused" }, 400);
  }
  return aisJson(c, `${u.pathname.slice(API_PREFIX.length)}${u.search}`);
}
