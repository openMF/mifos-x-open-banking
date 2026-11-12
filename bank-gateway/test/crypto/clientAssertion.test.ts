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
import { buildClientAssertion } from "../../src/crypto/clientAssertion";
import { initSigner } from "../../src/crypto/signer";
import { decodeJson, generateTestKeyPairPem, verifyPs256, type TestKeyPair } from "../fixtures/keys";

describe("buildClientAssertion (port of BuildClientAssertion.kt)", () => {
  let kp: TestKeyPair;
  beforeAll(async () => {
    kp = await generateTestKeyPairPem();
    initSigner(kp.privatePkcs8Pem);
  });

  it("carries iss == sub == clientId, aud == token URL, jti, iat and exp == iat + 60", async () => {
    const tokenUrl = "https://secure.sandbox.ob.hsbc.co.uk/obie/open-banking/v1.1/oauth2/token";
    const jwt = await buildClientAssertion({ clientId: "client-1", kid: "kid-1", tokenUrl, nowEpochSeconds: 1_700_000_000, jti: "jti-1" });
    const [h, p, s] = jwt.split(".");
    // Header is {alg, kid} only — the shape HSBC accepted from the deployed Worker.
    expect(decodeJson(h!)).toEqual({ alg: "PS256", kid: "kid-1" });
    expect(decodeJson(p!)).toEqual({
      iss: "client-1",
      sub: "client-1",
      aud: tokenUrl,
      jti: "jti-1",
      iat: 1_700_000_000,
      exp: 1_700_000_060,
    });
    expect(await verifyPs256(kp.publicKey, h!, p!, s!)).toBe(true);
  });
});
