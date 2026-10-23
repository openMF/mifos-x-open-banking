/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

import { env } from "cloudflare:test";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { verifyPlayIntegrity } from "../../src/attest/playIntegrity";
import { tokenCache } from "../../src/session/TokenCacheDO";
import { BUNDLE_ID, clientData, fakeGoogle, makeServiceAccount, requestHash, type Verdict } from "../fixtures/attest";
import { call, exposed, fakeBank, testEnv } from "../fixtures/bank";

const BODY = JSON.stringify({ permissions: ["ReadAccountsDetail"] });
const DATA = clientData("POST", "/v1/consents/ais", BODY);

let sa: Awaited<ReturnType<typeof makeServiceAccount>>;
beforeEach(async () => {
  sa ??= await makeServiceAccount();
  await tokenCache(env).put("google:playintegrity", "", 0); // no Google token carried over between tests
});
afterEach(() => vi.restoreAllMocks());

async function challenge(e: Awaited<ReturnType<typeof testEnv>>): Promise<string> {
  return ((await (await call(e, "POST", "/v1/attest/challenge")).json()) as { challenge: string }).challenge;
}

/** POST /v1/consents/ais from Android in enforce mode; Google answers `verdict(challenge)`. */
async function attempt(verdict: (ch: string) => Promise<Verdict> | Verdict, o: { reuse?: string; body?: string } = {}) {
  let v: Verdict = {};
  const g = fakeGoogle(sa.publicKey, () => v);
  vi.spyOn(globalThis, "fetch").mockImplementation(g.fake);
  const e = await testEnv(fakeBank(), { PLAY_INTEGRITY_SA_JSON: sa.json, ATTESTATION_MODE: "enforce" });
  const ch = o.reuse ?? (await challenge(e));
  v = await verdict(ch);
  const res = await call(e, "POST", "/v1/consents/ais", { body: o.body ?? BODY, headers: { "X-Client-Platform": "android", "X-Attestation": `play ${ch}.TOKEN-${ch}` } });
  return { res, g, e, ch };
}
const good = async (ch: string): Promise<Verdict> => ({ requestHash: await requestHash(ch, DATA) });

describe("Play Integrity (standard request)", () => {
  it("accepts a MEETS_DEVICE_INTEGRITY verdict with matching package and nonce", async () => {
    const { res, g } = await attempt(good);
    expect(res.status).toBe(200);
    expect(res.headers.get("x-attestation")).toBe("verified");
    expect(g.calls.decodeUrls).toEqual([`/v1/${BUNDLE_ID}:decodeIntegrityToken`]);
    expect(g.calls.badAssertion).toBe(0);
  });

  it("mints the Google token once and serves it from TokenCacheDO afterwards", async () => {
    let expected = "";
    const g = fakeGoogle(sa.publicKey, () => ({ requestHash: expected }));
    vi.spyOn(globalThis, "fetch").mockImplementation(g.fake);
    const e = await testEnv(fakeBank(), { PLAY_INTEGRITY_SA_JSON: sa.json, ATTESTATION_MODE: "enforce" });
    for (let i = 0; i < 3; i++) {
      const ch = await challenge(e);
      expected = await requestHash(ch, DATA);
      const res = await call(e, "POST", "/v1/consents/ais", { body: BODY, headers: { "X-Client-Platform": "android", "X-Attestation": `play ${ch}.T` } });
      expect(res.status).toBe(200);
    }
    expect(g.calls.token).toBe(1);
    expect(g.calls.decode).toBe(3);
  });

  it("rejects wrong package", async () => {
    const { res } = await attempt(async (ch) => ({ ...(await good(ch)), requestPackageName: "com.evil.clone" }));
    expect(res.status).toBe(403);
    expect(await res.json()).toMatchObject({ error: "attestation_invalid", reason: "package" });
  });

  it("rejects nonce mismatch (requestHash over a different body)", async () => {
    const { res } = await attempt(good, { body: JSON.stringify({ permissions: ["ReadBalances"] }) });
    expect(res.status).toBe(403);
    expect(await res.json()).toMatchObject({ reason: "request_hash" });
  });

  it("rejects missing MEETS_DEVICE_INTEGRITY", async () => {
    const { res } = await attempt(async (ch) => ({ ...(await good(ch)), deviceRecognitionVerdict: ["MEETS_BASIC_INTEGRITY"] }));
    expect(res.status).toBe(403);
    expect(await res.json()).toMatchObject({ reason: "device_verdict" });
  });

  it("rejects an app Play does not recognise", async () => {
    const { res } = await attempt(async (ch) => ({ ...(await good(ch)), appRecognitionVerdict: "UNRECOGNIZED_VERSION" }));
    expect(res.status).toBe(403);
    expect(await res.json()).toMatchObject({ reason: "app_verdict" });
  });

  it("a challenge is single-use", async () => {
    const { res, e, ch } = await attempt(good);
    expect(res.status).toBe(200);
    const again = await call(e, "POST", "/v1/consents/ais", { body: BODY, headers: { "X-Client-Platform": "android", "X-Attestation": `play ${ch}.TOKEN-${ch}` } });
    expect(again.status).toBe(403);
    expect(await again.json()).toMatchObject({ reason: "challenge" });
  });

  it("an unknown challenge is refused before any Google call", async () => {
    const { res, g } = await attempt(good, { reuse: "A".repeat(43) });
    expect(res.status).toBe(403);
    expect(await res.json()).toMatchObject({ reason: "challenge" });
    expect(g.calls.decode).toBe(0);
  });

  it("a malformed header is 403 malformed", async () => {
    vi.spyOn(globalThis, "fetch").mockImplementation(fakeGoogle(sa.publicKey, () => ({})).fake);
    const e = await testEnv(fakeBank(), { PLAY_INTEGRITY_SA_JSON: sa.json, ATTESTATION_MODE: "enforce" });
    for (const h of ["ios abc", "play nodot", "play .x", "bearer x"]) {
      const res = await call(e, "POST", "/v1/consents/ais", { body: BODY, headers: { "X-Client-Platform": "android", "X-Attestation": h } });
      expect(res.status, h).toBe(403);
      expect(await res.json(), h).toMatchObject({ reason: "malformed" });
    }
  });

  it("never echoes the Google token or the integrity token", async () => {
    const { res } = await attempt(good);
    const text = await exposed(res);
    expect(text).not.toContain("fixture-google-access-5a1c");
    expect(text).not.toContain("TOKEN-");
  });

  it("caches a verdict per session for 10 minutes", async () => {
    const e = await testEnv(fakeBank(), { PLAY_INTEGRITY_SA_JSON: sa.json });
    const sessionId = "S".repeat(43);
    const ch = await challenge(e);
    const v = await requestHash(ch, DATA);
    const fake2 = fakeGoogle(sa.publicKey, () => ({ requestHash: v }));
    vi.spyOn(globalThis, "fetch").mockImplementation(fake2.fake);
    expect(await verifyPlayIntegrity(e, `${ch}.T`, DATA, sessionId)).toEqual({ ok: true });
    // Same session, no challenge left, no Google call: the cached verdict answers.
    expect(await verifyPlayIntegrity(e, `${ch}.T`, DATA, sessionId)).toEqual({ ok: true });
    expect(fake2.calls.decode).toBe(1);
    // Another session gets no free pass.
    expect(await verifyPlayIntegrity(e, `${ch}.T`, DATA, "Z".repeat(43))).toEqual({ ok: false, reason: "challenge" });
  });
});
