/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

import { b64urlEncode, generateTestKeyPairPem } from "./keys";

// A fake HSBC JWKS and an id_token minter, so idToken tests can produce valid and tampered tokens.

export interface TestJwks {
  kid: string;
  jwks: { keys: Array<{ kty: string; kid: string; n: string; e: string; alg: string; use: string }> };
  privateKey: CryptoKey;
}

export async function makeHsbcJwks(kid = "hsbc-test-kid"): Promise<TestJwks> {
  const kp = await generateTestKeyPairPem();
  const jwk = kp.publicJwk;
  return {
    kid,
    jwks: { keys: [{ kty: "RSA", kid, n: jwk.n!, e: jwk.e!, alg: "PS256", use: "sig" }] },
    privateKey: kp.privateKey,
  };
}

export async function signTestIdToken(
  claims: Record<string, unknown>,
  signer: { kid: string; privateKey: CryptoKey },
  header: Record<string, unknown> = {},
): Promise<string> {
  const enc = (o: unknown) => b64urlEncode(new TextEncoder().encode(JSON.stringify(o)));
  const input = `${enc({ alg: "PS256", kid: signer.kid, ...header })}.${enc(claims)}`;
  const sig = await crypto.subtle.sign({ name: "RSA-PSS", saltLength: 32 }, signer.privateKey, new TextEncoder().encode(input));
  return `${input}.${b64urlEncode(new Uint8Array(sig))}`;
}

/** OIDC c_hash / s_hash: base64url of the left half of SHA-256(value). */
export async function halfHash(value: string): Promise<string> {
  const d = new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value)));
  return b64urlEncode(d.slice(0, 16));
}
