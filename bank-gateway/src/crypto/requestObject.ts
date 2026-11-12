/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

import { signJwt } from "./signer";

const REQUEST_OBJECT_TTL_SECONDS = 1200; // ≤ 30 min per the HSBC profile
const NBF_SKEW_SECONDS = 1;
const RESPONSE_TYPE = "code id_token";

export interface RequestObjectInput {
  clientId: string;
  kid: string;
  issuer: string; // aud — the mTLS secure.* host
  redirectUri: string;
  scope: string; // "accounts" | "payments" | …
  consentId: string;
  state: string; // uuidv4
  nonce: string; // uuidv4
  nowEpochSeconds: number;
}

/** OIDC request object — port of ConsentAuthorization.kt generateConsentAuthorizationUrl. */
export function buildRequestObjectJwt(i: RequestObjectInput): Promise<string> {
  const intent = { value: i.consentId, essential: true };
  return signJwt(
    { alg: "PS256", kid: i.kid },
    {
      claims: {
        userinfo: { openbanking_intent_id: intent },
        id_token: {
          openbanking_intent_id: intent,
          acr: { essential: false, values: ["urn:openbanking:psd2:sca", "urn:openbanking:psd2:ca"] },
        },
      },
      iss: i.clientId,
      aud: i.issuer,
      response_type: RESPONSE_TYPE,
      client_id: i.clientId,
      state: i.state,
      exp: i.nowEpochSeconds + REQUEST_OBJECT_TTL_SECONDS,
      nbf: i.nowEpochSeconds - NBF_SKEW_SECONDS,
      redirect_uri: i.redirectUri,
      nonce: i.nonce,
      scope: `openid ${i.scope}`,
    },
  );
}

/** Front-channel authorize URL (on the authorize host, distinct from the issuer) carrying the signed request. */
export function buildAuthorizeUrl(
  authorizeUrl: string,
  p: { clientId: string; scope: string; redirectUri: string; state: string; nonce: string; requestJwt: string },
): string {
  const u = new URL(authorizeUrl);
  u.searchParams.append("response_type", RESPONSE_TYPE);
  u.searchParams.append("client_id", p.clientId);
  u.searchParams.append("scope", `openid ${p.scope}`);
  u.searchParams.append("redirect_uri", p.redirectUri);
  u.searchParams.append("state", p.state);
  u.searchParams.append("nonce", p.nonce);
  u.searchParams.append("request", p.requestJwt);
  return u.toString();
}
