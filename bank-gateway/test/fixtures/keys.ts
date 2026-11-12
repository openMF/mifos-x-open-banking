/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

// Ephemeral RSA-PSS key pairs generated per test run — never real credentials, never committed.

export interface TestKeyPair {
  privatePkcs8Pem: string;
  publicSpkiPem: string;
  publicJwk: JsonWebKey;
  publicKey: CryptoKey;
  privateKey: CryptoKey;
}

export async function generateTestKeyPairPem(): Promise<TestKeyPair> {
  const kp = (await crypto.subtle.generateKey(
    { name: "RSA-PSS", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
    true,
    ["sign", "verify"],
  )) as CryptoKeyPair;
  const priv = new Uint8Array((await crypto.subtle.exportKey("pkcs8", kp.privateKey)) as ArrayBuffer);
  const pub = new Uint8Array((await crypto.subtle.exportKey("spki", kp.publicKey)) as ArrayBuffer);
  return {
    privatePkcs8Pem: wrapPem("PRIVATE KEY", priv),
    publicSpkiPem: wrapPem("PUBLIC KEY", pub),
    publicJwk: (await crypto.subtle.exportKey("jwk", kp.publicKey)) as JsonWebKey,
    publicKey: kp.publicKey,
    privateKey: kp.privateKey,
  };
}

function wrapPem(label: string, bytes: Uint8Array): string {
  let bin = "";
  for (const b of bytes) bin += String.fromCharCode(b);
  const body = btoa(bin).replace(/(.{64})/g, "$1\n");
  return `-----BEGIN ${label}-----\n${body}\n-----END ${label}-----\n`;
}

// Test-side JOSE helpers, written independently of src/ so the tests are not tautological.
export const b64urlEncode = (bytes: Uint8Array): string => {
  let bin = "";
  for (const b of bytes) bin += String.fromCharCode(b);
  return btoa(bin).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");
};

export const b64urlDecode = (s: string): Uint8Array =>
  Uint8Array.from(atob(s.replaceAll("-", "+").replaceAll("_", "/")), (c) => c.charCodeAt(0));

export const decodeJson = (segment: string): Record<string, any> =>
  JSON.parse(new TextDecoder().decode(b64urlDecode(segment)));

/** Verifies a compact (or detached, given the payload) PS256 JWS with the paired public key. */
export async function verifyPs256(publicKey: CryptoKey, headerB64: string, payloadB64: string, sigB64: string): Promise<boolean> {
  return crypto.subtle.verify(
    { name: "RSA-PSS", saltLength: 32 },
    publicKey,
    b64urlDecode(sigB64),
    new TextEncoder().encode(`${headerB64}.${payloadB64}`),
  );
}
