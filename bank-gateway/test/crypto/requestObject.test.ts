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
import { buildAuthorizeUrl, buildRequestObjectJwt } from "../../src/crypto/requestObject";
import { initSigner } from "../../src/crypto/signer";
import { decodeJson, generateTestKeyPairPem, verifyPs256, type TestKeyPair } from "../fixtures/keys";

const input = {
  clientId: "client-1",
  kid: "kid-1",
  issuer: "https://secure.sandbox.ob.hsbc.co.uk",
  redirectUri: "https://relay.example/callback/",
  scope: "accounts",
  consentId: "consent-123",
  state: "11111111-1111-4111-8111-111111111111",
  nonce: "22222222-2222-4222-8222-222222222222",
  nowEpochSeconds: 1_700_000_000,
};

describe("buildRequestObjectJwt (port of ConsentAuthorization.kt)", () => {
  let kp: TestKeyPair;
  beforeAll(async () => {
    kp = await generateTestKeyPairPem();
    initSigner(kp.privatePkcs8Pem);
  });

  it("is a PS256 JWS with the structural claims HSBC requires", async () => {
    const jwt = await buildRequestObjectJwt(input);
    const [h, p, s] = jwt.split(".");
    expect(decodeJson(h!)).toEqual({ alg: "PS256", kid: "kid-1" });
    expect(await verifyPs256(kp.publicKey, h!, p!, s!)).toBe(true);

    const c = decodeJson(p!);
    expect(c.claims.userinfo.openbanking_intent_id).toEqual({ value: "consent-123", essential: true });
    expect(c.claims.id_token.openbanking_intent_id).toEqual({ value: "consent-123", essential: true });
    expect(c.claims.id_token.acr).toEqual({ essential: false, values: ["urn:openbanking:psd2:sca", "urn:openbanking:psd2:ca"] });
    expect(c.exp - input.nowEpochSeconds).toBe(1200);
    expect(c.exp - input.nowEpochSeconds).toBeLessThanOrEqual(30 * 60);
    expect(c.nbf).toBe(input.nowEpochSeconds - 1);
    expect(c.aud).toBe(input.issuer);
    expect(c.iss).toBe("client-1");
    expect(c.client_id).toBe("client-1");
    expect(c.response_type).toBe("code id_token");
    expect(c.scope).toBe("openid accounts");
    expect(c.state).toBe(input.state);
    expect(c.nonce).toBe(input.nonce);
    expect(c.redirect_uri).toBe(input.redirectUri);
  });

  it("buildAuthorizeUrl appends the signed request plus the front-channel params", async () => {
    const jwt = await buildRequestObjectJwt(input);
    const url = new URL(
      buildAuthorizeUrl("https://sandbox.ob.hsbc.co.uk/obie/open-banking/v1.1/oauth2/authorize", {
        clientId: input.clientId,
        scope: input.scope,
        redirectUri: input.redirectUri,
        state: input.state,
        nonce: input.nonce,
        requestJwt: jwt,
      }),
    );
    expect(url.host).toBe("sandbox.ob.hsbc.co.uk");
    expect(url.pathname).toBe("/obie/open-banking/v1.1/oauth2/authorize");
    expect(Object.fromEntries(url.searchParams)).toEqual({
      response_type: "code id_token",
      client_id: "client-1",
      scope: "openid accounts",
      redirect_uri: input.redirectUri,
      state: input.state,
      nonce: input.nonce,
      request: jwt,
    });
  });
});
