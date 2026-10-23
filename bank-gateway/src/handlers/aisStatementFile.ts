/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

import { json } from "../http";
import type { HandlerContext } from "../index";
import { bankPath } from "../routes";
import { psuGet } from "./aisRead";

const FILE_TYPES = ["application/pdf", "text/csv"];

/** Statement file bytes piped straight through; the client may only ask for application/pdf or text/csv. */
export async function aisStatementFile(c: HandlerContext): Promise<Response> {
  const path = bankPath(c.route.hsbcOp.path, c.params);
  if (!path) return json({ error: "invalid_param" }, 400);
  const wanted = (c.req.headers.get("accept") ?? "application/pdf").split(",").map((t) => t.split(";")[0]!.trim().toLowerCase());
  const type = wanted.find((t) => FILE_TYPES.includes(t));
  if (!type) return json({ error: "not_acceptable" }, 406);
  const res = await psuGet(c, path, type);
  if (!(res instanceof Response)) return res.denied;
  if (!res.ok) return json({ error: "bank_error", bankStatus: res.status }, res.status);
  return new Response(res.body, { status: 200, headers: { "content-type": type, "cache-control": "no-store" } });
}
