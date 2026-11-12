/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

import { beforeAll, describe, expect, it } from "vitest";
import { CLAIM_IAT, CLAIM_ISS, CLAIM_TAN, decodeDetachedJwsHeader, detachedJwsSignature } from "../../src/crypto/detachedJws";
import { initSigner } from "../../src/crypto/signer";
import { b64urlEncode, generateTestKeyPairPem, verifyPs256, type TestKeyPair } from "../fixtures/keys";

const ISSUER = "CN=test,OU=test,O=Test Org,C=GB"; // the signing certificate's subject DN (RFC 2253)

describe("detachedJwsSignature (port of DetachedJws.kt, OBIE v4)", () => {
  let kp: TestKeyPair;
  beforeAll(async () => {
    kp = await generateTestKeyPairPem();
    initSigner(kp.privatePkcs8Pem);
  });

  const sign = (payload: string) => detachedJwsSignature({ payload, kid: "kid-1", issuer: ISSUER, issuedAtEpochSeconds: 1_700_000_000 });

  it("has an empty middle segment", async () => {
    const parts = (await sign('{"Data":{}}')).split(".");
    expect(parts).toHaveLength(3);
    expect(parts[1]).toBe("");
  });

  it("header is typ JOSE, cty application/json, OB claims, crit == exactly the three OB claims, no b64", async () => {
    const header = decodeDetachedJwsHeader(await sign('{"Data":{}}'));
    expect(header).toEqual({
      alg: "PS256",
      kid: "kid-1",
      typ: "JOSE",
      cty: "application/json",
      [CLAIM_IAT]: 1_700_000_000,
      [CLAIM_ISS]: ISSUER,
      [CLAIM_TAN]: "openbanking.org.uk",
      crit: ["http://openbanking.org.uk/iat", "http://openbanking.org.uk/iss", "http://openbanking.org.uk/tan"],
    });
    expect("b64" in header).toBe(false);
    expect(header.crit as string[]).not.toContain("b64");
  });

  it("verifies against base64url(payload) with the public key, and only for that payload", async () => {
    const a = '{"Data":{"Amount":"1.00"}}';
    const b = '{"Data":{"Amount":"2.00"}}';
    const [h, , s] = (await sign(a)).split(".");
    const enc = (x: string) => b64urlEncode(new TextEncoder().encode(x));
    expect(await verifyPs256(kp.publicKey, h!, enc(a), s!)).toBe(true);
    expect(await verifyPs256(kp.publicKey, h!, enc(b), s!)).toBe(false);
    expect(await sign(a)).not.toBe(await sign(b));
  });
});
