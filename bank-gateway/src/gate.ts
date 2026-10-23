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
import { sessionStub, type SessionDO } from "./session/SessionDO";

export const SESSION_HEADER = /^Session ([A-Za-z0-9_-]{43})$/; // 32 random bytes, base64url (newSession)

/** The SessionDO for `Authorization: Session <id>`, or a 401 JSON response. The id is hashed before lookup. */
export async function requireSession(req: Request, env: Pick<Env, "SESSIONS">): Promise<DurableObjectStub<SessionDO> | Response> {
  const m = SESSION_HEADER.exec(req.headers.get("authorization") ?? "");
  if (!m) return json({ error: "missing_session" }, 401);
  const stub = await sessionStub(env.SESSIONS, m[1]!);
  if (!(await stub.exists())) return json({ error: "unknown_session" }, 401);
  return stub;
}
