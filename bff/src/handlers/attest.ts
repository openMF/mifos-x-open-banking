/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

import { registerAppAttest } from "../attest/appAttest";
import { attestationStub } from "../attest/AttestationDO";
import { base64url } from "../crypto/base64url";
import { json, parseJson } from "../http";
import type { HandlerContext } from "../index";

const CHALLENGE_TTL_S = 300;

/** POST /v1/attest/challenge — a single-use 32-byte challenge, valid for 5 minutes. */
export async function attestChallenge(c: HandlerContext): Promise<Response> {
  const challenge = base64url(crypto.getRandomValues(new Uint8Array(32)));
  await attestationStub(c.env, `challenge:${challenge}`).issueChallenge(Date.now() + CHALLENGE_TTL_S * 1000);
  return json({ challenge, expiresIn: CHALLENGE_TTL_S });
}

/** POST /v1/attest/ios/register {keyId, attestationObject, challenge} — App Attest key registration. */
export async function attestIosRegister(c: HandlerContext): Promise<Response> {
  if (!c.env.APPLE_TEAM_ID) return json({ error: "attestation_unconfigured" }, 503); // nothing to check rpIdHash against
  const b = parseJson(c.body);
  if (typeof b?.keyId !== "string" || typeof b.attestationObject !== "string" || typeof b.challenge !== "string") return json({ error: "invalid_request" }, 400);
  const v = await registerAppAttest(c.env, b);
  return v.ok ? json({ keyId: b.keyId }) : json({ error: "attestation_invalid", reason: v.reason }, 403);
}
