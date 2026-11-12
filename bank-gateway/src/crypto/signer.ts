/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

import { base64url, base64urlJson, utf8 } from "./base64url";

/*
 * The one signing key of the TPP. Imported once per isolate into a non-extractable RSA-PSS SHA-256
 * CryptoKey at module scope — re-importing per request would blow the 10 ms CPU budget on the
 * Free plan (D2). The vault key must be PKCS#8 ("BEGIN PRIVATE KEY"); a legacy PKCS#1 key is
 * converted before deploy with `openssl pkcs8 -topk8 -nocrypt`.
 */
let signingKey: Promise<CryptoKey> | undefined;
let signingPem: string | undefined;

/** Cheap after the first call: the env secret is the same string for the life of the isolate. */
export function initSigner(pem: string): void {
  if (signingKey && pem === signingPem) return;
  if (!pem.includes("-----BEGIN PRIVATE KEY-----")) throw new Error("HSBC_SIGNING_KEY_PEM must be PKCS#8 (BEGIN PRIVATE KEY)");
  const der = Uint8Array.from(atob(pem.replace(/-----(BEGIN|END) PRIVATE KEY-----|\s+/g, "")), (c) => c.charCodeAt(0));
  signingKey = crypto.subtle.importKey("pkcs8", der, { name: "RSA-PSS", hash: "SHA-256" }, false, ["sign"]);
  signingPem = pem;
}

/** PS256 over `header.payload` (both already base64url), saltLength = hash length. */
async function ps256(headerB64: string, payloadB64: string): Promise<string> {
  if (!signingKey) throw new Error("signer not initialized — call initSigner(env.HSBC_SIGNING_KEY_PEM) first");
  const sig = await crypto.subtle.sign({ name: "RSA-PSS", saltLength: 32 }, await signingKey, utf8(`${headerB64}.${payloadB64}`));
  return base64url(sig);
}

/** Compact JWS `header.payload.signature`. */
export async function signJwt(header: object, claims: object): Promise<string> {
  const h = base64urlJson(header);
  const p = base64urlJson(claims);
  return `${h}.${p}.${await ps256(h, p)}`;
}

/** Detached JWS `header..signature`: the signature covers base64url(body), the payload segment is dropped. */
export async function signDetached(header: object, bodyBytes: Uint8Array): Promise<string> {
  const h = base64urlJson(header);
  return `${h}..${await ps256(h, base64url(bodyBytes))}`;
}
