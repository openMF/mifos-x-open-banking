/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

import { decode } from "cbor-x";
import { base64url, utf8 } from "../crypto/base64url";
import type { Env } from "../env";
import { attestationStub } from "./AttestationDO";
import { APPLE_APP_ATTEST_ROOT_CA_PEM } from "./appleRootCA";
import { CHALLENGE, concat, equal, fail, fromBase64, OK, sha256, type Verdict } from "./bytes";
import type { X509Certificate } from "./x509";

/*
 * App Attest (iOS), per Apple's "Validating apps that connect to your server".
 * Registration (once per key): POST /v1/attest/ios/register {keyId, attestationObject, challenge}, the app
 *   having called attestKey(keyId, clientDataHash = SHA-256(UTF-8 challenge)). One-off cost: CBOR decode,
 *   two ECDSA chain verifies against the embedded root, three hashes.
 * Assertion (every attest request): `X-Attestation: ios <keyId>.<base64 CBOR assertion>`, generated with
 *   generateAssertion(keyId, SHA-256(clientData)). One ECDSA P-256 verify, run inside the key's
 *   AttestationDO together with the strictly-increasing counter update.
 */

const APP_ID_SUFFIX = ".org.mifosx.openbanking";
const NONCE_EXT_PREFIX = [0x30, 0x24, 0xa1, 0x22, 0x04, 0x20]; // SEQUENCE { [1] { OCTET STRING (32) } }
const AAGUIDS = ["appattestdevelop", "appattest\0\0\0\0\0\0\0"];

let root: { pem: string; cert: X509Certificate } | undefined;

const u32 = (b: Uint8Array, at: number) => ((b[at]! << 24) | (b[at + 1]! << 16) | (b[at + 2]! << 8) | b[at + 3]!) >>> 0;
const bytesOf = (v: unknown) => (v instanceof Uint8Array ? new Uint8Array(v) : null);
const appIdHash = (env: Pick<Env, "APPLE_TEAM_ID">) => sha256(utf8(`${env.APPLE_TEAM_ID}${APP_ID_SUFFIX}`));

/** `rootPem` is overridable for tests only; production always passes nothing and gets Apple's root. */
export async function registerAppAttest(env: Env, input: unknown, rootPem = APPLE_APP_ATTEST_ROOT_CA_PEM): Promise<Verdict> {
  const b = input as Record<string, unknown> | null;
  if (typeof b?.keyId !== "string" || typeof b.attestationObject !== "string" || typeof b.challenge !== "string" || !CHALLENGE.test(b.challenge)) return fail("malformed");
  const keyId = fromBase64(b.keyId);
  const raw = fromBase64(b.attestationObject);
  if (keyId?.length !== 32 || !raw) return fail("malformed");
  let att: { fmt?: unknown; attStmt?: { x5c?: unknown[] }; authData?: unknown };
  try {
    att = decode(raw);
  } catch {
    return fail("malformed");
  }
  const authData = bytesOf(att?.authData);
  const x5c = Array.isArray(att?.attStmt?.x5c) ? att.attStmt.x5c.map(bytesOf) : [];
  if (att.fmt !== "apple-appattest" || !authData || authData.length < 87 || x5c.length < 2 || !x5c[0] || !x5c[1]) return fail("malformed");

  if (!(await attestationStub(env, `challenge:${b.challenge}`).takeChallenge(Date.now()))) return fail("challenge");

  // 1. credCert ← intermediate ← the embedded root (x5c never supplies the trust anchor).
  const { X509Certificate } = await import("./x509");
  if (root?.pem !== rootPem) root = { pem: rootPem, cert: new X509Certificate(rootPem) };
  let leaf: X509Certificate;
  try {
    leaf = new X509Certificate(x5c[0]);
    const intermediate = new X509Certificate(x5c[1]);
    if (!(await intermediate.verify({ publicKey: root.cert })) || !(await leaf.verify({ publicKey: intermediate }))) return fail("chain");
  } catch {
    return fail("chain");
  }
  // 2. Apple nonce extension == SHA-256(authData ‖ SHA-256(challenge)).
  const ext = leaf.getExtension("1.2.840.113635.100.8.2");
  const nonce = await sha256(concat(authData, await sha256(utf8(b.challenge))));
  if (!ext || !equal(new Uint8Array(ext.value), concat(new Uint8Array(NONCE_EXT_PREFIX), nonce))) return fail("nonce");
  // 3. keyId == SHA-256(credCert public key point) == credentialId.
  const spki = new Uint8Array(leaf.publicKey.rawData);
  const credIdLen = (authData[53]! << 8) | authData[54]!;
  if (!equal(await sha256(spki.slice(-65)), keyId) || !equal(authData.slice(55, 55 + credIdLen), keyId)) return fail("keyId");
  // 4. rpIdHash, counter 0, aaguid.
  if (!equal(authData.slice(0, 32), await appIdHash(env))) return fail("rpIdHash");
  if (u32(authData, 33) !== 0) return fail("counter");
  if (!AAGUIDS.includes(String.fromCharCode(...authData.slice(37, 53)))) return fail("aaguid");

  return (await attestationStub(env, `key:${base64url(keyId)}`).registerKey(spki)) ? OK : fail("key_exists");
}

/** `value` is the header after "ios ": `<keyId>.<base64 CBOR {signature, authenticatorData}>`. */
export async function verifyAppAttestAssertion(env: Env, value: string, clientData: Uint8Array): Promise<Verdict> {
  const dot = value.indexOf(".");
  const keyId = fromBase64(value.slice(0, dot));
  const raw = fromBase64(value.slice(dot + 1));
  if (dot < 1 || keyId?.length !== 32 || !raw) return fail("malformed");
  let a: { signature?: unknown; authenticatorData?: unknown };
  try {
    a = decode(raw);
  } catch {
    return fail("malformed");
  }
  const authData = bytesOf(a?.authenticatorData);
  const signature = bytesOf(a?.signature) && derToP1363(bytesOf(a.signature)!);
  if (!authData || authData.length < 37 || !signature) return fail("malformed");
  if (!equal(authData.slice(0, 32), await appIdHash(env))) return fail("rpIdHash");

  const nonce = await sha256(concat(authData, await sha256(clientData)));
  const r = await attestationStub(env, `key:${base64url(keyId)}`).assert(nonce, signature, u32(authData, 33));
  return r === "ok" ? OK : fail(r);
}

/** DER ECDSA-Sig-Value → the 64-byte r‖s WebCrypto verifies; null when it is not one. */
function derToP1363(der: Uint8Array): Uint8Array | null {
  if (der[0] !== 0x30 || der[1] !== der.length - 2) return null;
  const out = new Uint8Array(64);
  let at = 2;
  for (const half of [0, 32]) {
    if (der[at] !== 0x02) return null;
    const len = der[at + 1]!;
    let v = der.slice(at + 2, at + 2 + len);
    at += 2 + len;
    while (v.length > 32 && v[0] === 0) v = v.slice(1);
    if (v.length > 32 || v.length === 0) return null;
    out.set(v, half + 32 - v.length);
  }
  return at === der.length ? out : null;
}
