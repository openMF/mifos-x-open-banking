/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

import { describe, expect, it } from "vitest";
import { registerAppAttest, verifyAppAttestAssertion } from "../../src/attest/appAttest";
import { assertionHeader, clientData, makeAttestation, TEAM_ID, type AttestTamper } from "../fixtures/attest";
import { call, fakeBank, testEnv } from "../fixtures/bank";

const BODY = JSON.stringify({ permissions: ["ReadAccountsDetail"] });
const DATA = clientData("POST", "/v1/consents/ais", BODY);

async function setup(extra: Record<string, unknown> = {}) {
  const e = await testEnv(fakeBank(), { APPLE_TEAM_ID: TEAM_ID, ATTESTATION_MODE: "enforce", ...extra });
  const ch = ((await (await call(e, "POST", "/v1/attest/challenge")).json()) as { challenge: string }).challenge;
  return { e, ch };
}

/** A registered key plus an iOS request helper. */
async function registered() {
  const { e, ch } = await setup();
  const a = await makeAttestation(ch);
  expect(await registerAppAttest(e, a.body, a.rootPem)).toEqual({ ok: true });
  const send = async (counter: number, body = BODY, data = DATA) =>
    call(e, "POST", "/v1/consents/ais", { body, headers: { "X-Client-Platform": "ios", "X-Attestation": await assertionHeader(a, counter, data) } });
  return { e, a, send };
}

describe("App Attest registration", () => {
  it("accepts a well-formed attestation", async () => {
    const { e, ch } = await setup();
    const a = await makeAttestation(ch);
    await expect(registerAppAttest(e, a.body, a.rootPem)).resolves.toEqual({ ok: true });
  });

  it.each<[AttestTamper, string]>([
    ["counter", "counter"],
    ["aaguid", "aaguid"],
    ["keyId", "keyId"],
  ])("rejects a tampered %s", async (tamper, reason) => {
    const { e, ch } = await setup();
    const a = await makeAttestation(ch, tamper);
    await expect(registerAppAttest(e, a.body, a.rootPem)).resolves.toEqual({ ok: false, reason });
  });

  it("rejects tampered nonce", async () => {
    const { e, ch } = await setup();
    const a = await makeAttestation(ch, "nonce");
    await expect(registerAppAttest(e, a.body, a.rootPem)).resolves.toEqual({ ok: false, reason: "nonce" });
  });

  it("rejects wrong rpIdHash", async () => {
    const { e, ch } = await setup({ APPLE_TEAM_ID: "OTHERTEAM1" });
    const a = await makeAttestation(ch);
    await expect(registerAppAttest(e, a.body, a.rootPem)).resolves.toEqual({ ok: false, reason: "rpIdHash" });
  });

  it("rejects a chain that does not end at the embedded Apple root", async () => {
    const { e, ch } = await setup();
    const a = await makeAttestation(ch);
    await expect(registerAppAttest(e, a.body)).resolves.toEqual({ ok: false, reason: "chain" });
  });

  it("rejects a reused or unknown challenge", async () => {
    const { e, ch } = await setup();
    const a = await makeAttestation(ch);
    expect(await registerAppAttest(e, a.body, a.rootPem)).toEqual({ ok: true });
    const b = await makeAttestation(ch);
    await expect(registerAppAttest(e, b.body, b.rootPem)).resolves.toEqual({ ok: false, reason: "challenge" });
  });

  it("rejects malformed input", async () => {
    const { e, ch } = await setup();
    for (const body of [null, {}, { keyId: "k", attestationObject: "!!", challenge: ch }, { keyId: "k", attestationObject: btoa("not cbor"), challenge: ch }]) {
      expect(await registerAppAttest(e, body)).toMatchObject({ ok: false });
    }
  });

  it("POST /v1/attest/ios/register: 503 when APPLE_TEAM_ID is absent, whatever the mode", async () => {
    const e = await testEnv(fakeBank(), { APPLE_TEAM_ID: undefined, ATTESTATION_MODE: "report-only" });
    const res = await call(e, "POST", "/v1/attest/ios/register", { body: { keyId: "k", attestationObject: "a", challenge: "c" } });
    expect(res.status).toBe(503);
    expect(await res.json()).toMatchObject({ error: "attestation_unconfigured" });
  });

  it("POST /v1/attest/ios/register: 400 on a malformed body, 403 on a failed check", async () => {
    const { e, ch } = await setup();
    expect((await call(e, "POST", "/v1/attest/ios/register", { body: { keyId: 1 } })).status).toBe(400);
    const a = await makeAttestation(ch); // signed by the test root, so the real Apple root refuses it
    const res = await call(e, "POST", "/v1/attest/ios/register", { body: a.body });
    expect(res.status).toBe(403);
    expect(await res.json()).toMatchObject({ error: "attestation_invalid", reason: "chain" });
  });
});

describe("App Attest assertion", () => {
  it("accepts a strictly increasing counter, marked verified", async () => {
    const { send } = await registered();
    for (const n of [1, 2, 7]) {
      const res = await send(n);
      expect(res.status, `counter ${n}`).toBe(200);
      expect(res.headers.get("x-attestation")).toBe("verified");
    }
  });

  it("rejects non-increasing counter", async () => {
    const { send } = await registered();
    expect((await send(5)).status).toBe(200);
    for (const n of [5, 4]) {
      const res = await send(n);
      expect(res.status).toBe(403);
      expect(await res.json()).toMatchObject({ error: "attestation_invalid", reason: "counter" });
    }
  });

  it("rejects an assertion signed over a different request", async () => {
    const { send } = await registered();
    const res = await send(1, BODY, clientData("POST", "/v1/consents/ais", "{}"));
    expect(res.status).toBe(403);
    expect(await res.json()).toMatchObject({ reason: "signature" });
  });

  it("rejects an unknown key and a malformed header", async () => {
    const { e, a } = await registered();
    const other = { ...a, keyId: btoa("x".repeat(32)) };
    const unknown = await verifyAppAttestAssertion(e, (await assertionHeader(other, 1, DATA)).slice(4), DATA);
    expect(unknown).toEqual({ ok: false, reason: "unknown_key" });
    expect(await verifyAppAttestAssertion(e, "nodot", DATA)).toEqual({ ok: false, reason: "malformed" });
    expect(await verifyAppAttestAssertion(e, `${a.keyId}.!!`, DATA)).toEqual({ ok: false, reason: "malformed" });
  });

  it("rejects an assertion for another app id", async () => {
    const { e, a } = await registered();
    const h = await assertionHeader(a, 1, DATA, "OTHERTEAM1");
    expect(await verifyAppAttestAssertion(e, h.slice(4), DATA)).toEqual({ ok: false, reason: "rpIdHash" });
  });
});
