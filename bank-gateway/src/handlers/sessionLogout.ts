/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

import { ccAccessToken, hsbcFetch } from "../hsbc/client";
import type { HandlerContext } from "../index";
import { bankPath } from "../routes";
import { nowSec } from "./aisRead";

/** POST /v1/session/logout[?revoke=true] — optionally deletes the AIS consent at HSBC, then wipes the session. */
export async function sessionLogout(c: HandlerContext): Promise<Response> {
  const session = c.session!;
  if (new URL(c.req.url).searchParams.get("revoke") === "true") {
    const consentId = await session.getConsent("ais");
    const path = consentId && bankPath(c.route.hsbcOp.path, { consentId });
    // Best effort: the session is wiped whatever the bank answers, so the app is logged out either way.
    if (path) await hsbcFetch(c.env, { method: "DELETE", path }, { bearer: await ccAccessToken(c.env, "accounts", nowSec()) });
  }
  await session.wipe();
  return new Response(null, { status: 204 });
}
