/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

import "reflect-metadata";
import { Extension, X509CertificateGenerator } from "@peculiar/x509";
import { encode } from "cbor-x";
import { b64urlEncode } from "./keys";

// Test-only attestation material, generated per run — a synthetic App Attest chain (P-384 root and
// intermediate, P-256 credential key) and a Play Integrity service account. Never real credentials.
// Written independently of src/ so the tests are not tautological.

const enc = new TextEncoder();
export const sha256 = async (...parts: Uint8Array[]) => new Uint8Array(await crypto.subtle.digest("SHA-256", concat(...parts)));
export function concat(...parts: Uint8Array[]): Uint8Array {
  const out = new Uint8Array(parts.reduce((n, p) => n + p.length, 0));
  let o = 0;
  for (const p of parts) (out.set(p, o), (o += p.length));
  return out;
}
const b64 = (bytes: Uint8Array) => btoa(String.fromCharCode(...bytes));

export const TEAM_ID = "TESTTEAM01";
export const BUNDLE_ID = "org.mifosx.openbanking";

/** What the app signs over: "METHOD /path\n" followed by the exact body bytes. */
export const clientData = (method: string, path: string, body = "") => concat(enc.encode(`${method} ${path}\n`), enc.encode(body));

const u32 = (n: number) => new Uint8Array([n >>> 24, (n >>> 16) & 255, (n >>> 8) & 255, n & 255]);

/** P1363 (r‖s) → DER SEQUENCE { INTEGER r, INTEGER s }, the form App Attest assertions carry. */
function derSig(p1363: Uint8Array): Uint8Array {
  const int = (b: Uint8Array) => {
    let i = 0;
    while (i < b.length - 1 && b[i] === 0) i++;
    const v = b[i]! & 0x80 ? concat(new Uint8Array([0]), b.slice(i)) : b.slice(i);
    return concat(new Uint8Array([0x02, v.length]), v);
  };
  const body = concat(int(p1363.slice(0, 32)), int(p1363.slice(32)));
  return concat(new Uint8Array([0x30, body.length]), body);
}

const ec = (namedCurve: string) => crypto.subtle.generateKey({ name: "ECDSA", namedCurve }, true, ["sign", "verify"]) as Promise<CryptoKeyPair>;

let chain: Promise<{ rootPem: string; intermediate: { keys: CryptoKeyPair; der: Uint8Array } }> | undefined;
/** One synthetic root + intermediate per test file. */
function testChain() {
  chain ??= (async () => {
    const rootKeys = await ec("P-384");
    const root = await X509CertificateGenerator.createSelfSigned({ name: "CN=Test App Attestation Root CA", keys: rootKeys, signingAlgorithm: { name: "ECDSA", hash: "SHA-384" } });
    const intKeys = await ec("P-384");
    const int = await X509CertificateGenerator.create({
      subject: "CN=Test App Attestation CA 1", issuer: root.subject, publicKey: intKeys.publicKey, signingKey: rootKeys.privateKey,
      signingAlgorithm: { name: "ECDSA", hash: "SHA-384" },
    });
    return { rootPem: root.toString("pem"), intermediate: { keys: intKeys, der: new Uint8Array(int.rawData) } };
  })();
  return chain;
}

export type AttestTamper = "nonce" | "rpIdHash" | "counter" | "aaguid" | "keyId";

/** A registration the Worker should accept for `challenge`, or one with a single field broken. */
export async function makeAttestation(challenge: string, tamper?: AttestTamper) {
  const { rootPem, intermediate } = await testChain();
  const keys = await ec("P-256");
  const rawPub = new Uint8Array((await crypto.subtle.exportKey("raw", keys.publicKey)) as ArrayBuffer);
  const keyIdBytes = await sha256(rawPub);
  const appId = tamper === "rpIdHash" ? `WRONGTEAM9.${BUNDLE_ID}` : `${TEAM_ID}.${BUNDLE_ID}`;
  const aaguid = enc.encode(tamper === "aaguid" ? "notappattest0000" : "appattestdevelop");
  const credId = tamper === "keyId" ? new Uint8Array(32).fill(7) : keyIdBytes;
  const authData = concat(await sha256(enc.encode(appId)), new Uint8Array([0x40]), u32(tamper === "counter" ? 1 : 0), aaguid, new Uint8Array([0, 32]), credId, encode({ 1: 2, 3: -7 }));
  let nonce = await sha256(authData, await sha256(enc.encode(challenge)));
  if (tamper === "nonce") nonce = nonce.map((b, i) => (i === 0 ? b ^ 1 : b));
  const leaf = await X509CertificateGenerator.create({
    subject: "CN=test-credential", issuer: "CN=Test App Attestation CA 1", publicKey: keys.publicKey, signingKey: intermediate.keys.privateKey,
    signingAlgorithm: { name: "ECDSA", hash: "SHA-256" },
    extensions: [new Extension("1.2.840.113635.100.8.2", false, concat(new Uint8Array([0x30, 0x24, 0xa1, 0x22, 0x04, 0x20]), nonce))],
  });
  const attestationObject = encode({ fmt: "apple-appattest", attStmt: { x5c: [new Uint8Array(leaf.rawData), intermediate.der], receipt: new Uint8Array([1]) }, authData });
  return { rootPem, keyId: b64(keyIdBytes), privateKey: keys.privateKey, body: { keyId: b64(keyIdBytes), attestationObject: b64(attestationObject), challenge } };
}

/** The X-Attestation header for one iOS request: `ios <keyId>.<base64 CBOR assertion>`. */
export async function assertionHeader(a: { keyId: string; privateKey: CryptoKey }, counter: number, data: Uint8Array, teamId = TEAM_ID): Promise<string> {
  const authData = concat(await sha256(enc.encode(`${teamId}.${BUNDLE_ID}`)), new Uint8Array([0]), u32(counter));
  const nonce = await sha256(authData, await sha256(data));
  const sig = new Uint8Array(await crypto.subtle.sign({ name: "ECDSA", hash: "SHA-256" }, a.privateKey, nonce));
  return `ios ${a.keyId}.${b64(encode({ signature: derSig(sig), authenticatorData: authData }))}`;
}

// ── Play Integrity ──

/** A Google service account JSON with a fresh RS256 key, plus its public key for checking the minted JWT. */
export async function makeServiceAccount() {
  const kp = (await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"])) as CryptoKeyPair;
  const pkcs8 = new Uint8Array((await crypto.subtle.exportKey("pkcs8", kp.privateKey)) as ArrayBuffer);
  const pem = `-----BEGIN PRIVATE KEY-----\n${b64(pkcs8).replace(/(.{64})/g, "$1\n")}\n-----END PRIVATE KEY-----\n`;
  const json = JSON.stringify({ type: "service_account", client_email: "pi@test-project.iam.gserviceaccount.com", private_key: pem, token_uri: "https://oauth2.googleapis.com/token" });
  return { json, publicKey: kp.publicKey };
}

/** requestHash the app puts in a standard Play Integrity request: base64url(SHA-256(challenge ‖ clientData)). */
export const requestHash = async (challenge: string, data: Uint8Array) => b64urlEncode(await sha256(enc.encode(challenge), data));

export interface Verdict {
  requestPackageName?: string;
  requestHash?: string;
  appRecognitionVerdict?: string;
  deviceRecognitionVerdict?: string[];
}

export const verdictPayload = (v: Verdict) => ({
  tokenPayloadExternal: {
    requestDetails: { requestPackageName: v.requestPackageName ?? BUNDLE_ID, requestHash: v.requestHash, timestampMillis: String(Date.now()) },
    appIntegrity: { appRecognitionVerdict: v.appRecognitionVerdict ?? "PLAY_RECOGNIZED", packageName: BUNDLE_ID },
    deviceIntegrity: { deviceRecognitionVerdict: v.deviceRecognitionVerdict ?? ["MEETS_DEVICE_INTEGRITY"] },
    accountDetails: { appLicensingVerdict: "LICENSED" },
  },
});

/**
 * Replaces global fetch with a fake Google: the OAuth token endpoint (checks the RS256 assertion
 * against the service account's public key) and decodeIntegrityToken (answers `verdictFor(token)`).
 */
export function fakeGoogle(publicKey: CryptoKey, verdictFor: (token: string) => Verdict | number) {
  const calls = { token: 0, decode: 0, badAssertion: 0, decodeUrls: [] as string[] };
  const fake = async (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
    const req = new Request(input, init);
    const url = new URL(req.url);
    if (url.href === "https://oauth2.googleapis.com/token") {
      calls.token++;
      const form = new URLSearchParams(await req.text());
      const [h, p, s] = (form.get("assertion") ?? "").split(".");
      const dec = (x: string) => Uint8Array.from(atob(x.replaceAll("-", "+").replaceAll("_", "/")), (c) => c.charCodeAt(0));
      const ok = form.get("grant_type") === "urn:ietf:params:oauth:grant-type:jwt-bearer" && (await crypto.subtle.verify("RSASSA-PKCS1-v1_5", publicKey, dec(s!), enc.encode(`${h}.${p}`)));
      const claims = JSON.parse(new TextDecoder().decode(dec(p!)));
      if (!ok || claims.scope !== "https://www.googleapis.com/auth/playintegrity" || claims.aud !== "https://oauth2.googleapis.com/token") {
        calls.badAssertion++;
        return Response.json({ error: "invalid_grant" }, { status: 400 });
      }
      return Response.json({ access_token: "fixture-google-access-5a1c", expires_in: 3599, token_type: "Bearer" });
    }
    if (url.hostname === "playintegrity.googleapis.com") {
      calls.decode++;
      calls.decodeUrls.push(url.pathname);
      if (req.headers.get("authorization") !== "Bearer fixture-google-access-5a1c") return Response.json({ error: { code: 401 } }, { status: 401 });
      const v = verdictFor(((await req.json()) as { integrity_token: string }).integrity_token);
      return typeof v === "number" ? Response.json({ error: { code: v, message: "fixture google error detail" } }, { status: v }) : Response.json(verdictPayload(v));
    }
    return new Response("unexpected fetch", { status: 599 });
  };
  return { fake, calls };
}
