/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

import { parseBase64urlJson, utf8 } from "./base64url";
import { signDetached } from "./signer";

export const CLAIM_IAT = "http://openbanking.org.uk/iat";
export const CLAIM_ISS = "http://openbanking.org.uk/iss";
export const CLAIM_TAN = "http://openbanking.org.uk/tan";
export const OPEN_BANKING_TRUST_ANCHOR = "openbanking.org.uk";

export interface DetachedJwsInput {
  payload: string | Uint8Array; // the exact JSON body being POSTed (bytes: signed as-is, never re-encoded)
  kid: string;
  issuer: string; // the signing certificate's subject DN (RFC 2253) — HSBC refuses org_id/software_id
  issuedAtEpochSeconds: number;
}

/**
 * x-jws-signature value, OBIE v4 — port of DetachedJws.kt. typ is JOSE ("JWT" is refused), crit lists
 * exactly the three OB claims, and the unencoded-payload flag is deliberately absent (v4 removed it).
 */
export function detachedJwsSignature(i: DetachedJwsInput): Promise<string> {
  const header = {
    alg: "PS256",
    kid: i.kid,
    typ: "JOSE",
    cty: "application/json",
    [CLAIM_IAT]: i.issuedAtEpochSeconds,
    [CLAIM_ISS]: i.issuer,
    [CLAIM_TAN]: OPEN_BANKING_TRUST_ANCHOR,
    crit: [CLAIM_IAT, CLAIM_ISS, CLAIM_TAN],
  };
  return signDetached(header, typeof i.payload === "string" ? utf8(i.payload) : i.payload);
}

export function decodeDetachedJwsHeader(sig: string): Record<string, unknown> {
  return parseBase64urlJson(sig.split(".")[0] ?? "");
}
