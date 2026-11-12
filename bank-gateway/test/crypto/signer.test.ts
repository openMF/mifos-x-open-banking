/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

import { beforeAll, describe, expect, it, vi } from "vitest";
import { initSigner, signDetached, signJwt } from "../../src/crypto/signer";
import { b64urlEncode, decodeJson, generateTestKeyPairPem, verifyPs256, type TestKeyPair } from "../fixtures/keys";

describe("signer", () => {
  let kp: TestKeyPair;
  beforeAll(async () => {
    kp = await generateTestKeyPairPem();
  });

  it("refuses to sign before initSigner", async () => {
    vi.resetModules();
    const fresh = await import("../../src/crypto/signer");
    await expect(fresh.signJwt({ alg: "PS256" }, {})).rejects.toThrow(/not initialized/);
  });

  it("rejects a PKCS#1 PEM — the key must be converted to PKCS#8 before deploy", () => {
    expect(() => initSigner("-----BEGIN RSA PRIVATE KEY-----\nAAAA\n-----END RSA PRIVATE KEY-----")).toThrow(/PKCS#8/);
  });

  it("signJwt produces a compact PS256 JWS that verifies with the paired public key", async () => {
    initSigner(kp.privatePkcs8Pem);
    const jwt = await signJwt({ alg: "PS256", kid: "k1" }, { iss: "client", n: 1 });
    const [h, p, s] = jwt.split(".");
    expect(decodeJson(h!)).toEqual({ alg: "PS256", kid: "k1" });
    expect(decodeJson(p!)).toEqual({ iss: "client", n: 1 });
    expect(await verifyPs256(kp.publicKey, h!, p!, s!)).toBe(true);
  });

  it("signDetached drops the payload segment and the signature covers base64url(body)", async () => {
    initSigner(kp.privatePkcs8Pem);
    const body = new TextEncoder().encode('{"Data":{}}');
    const sig = await signDetached({ alg: "PS256", kid: "k1" }, body);
    const [h, middle, s] = sig.split(".");
    expect(middle).toBe("");
    expect(await verifyPs256(kp.publicKey, h!, b64urlEncode(body), s!)).toBe(true);
  });

  it("imports the CryptoKey once per isolate, not per signature", async () => {
    const other = await generateTestKeyPairPem();
    const spy = vi.spyOn(crypto.subtle, "importKey");
    try {
      initSigner(other.privatePkcs8Pem);
      initSigner(other.privatePkcs8Pem);
      await signJwt({ alg: "PS256" }, { a: 1 });
      await signJwt({ alg: "PS256" }, { a: 2 });
      initSigner(other.privatePkcs8Pem);
      expect(spy).toHaveBeenCalledTimes(1);
    } finally {
      spy.mockRestore();
    }
  });
});
