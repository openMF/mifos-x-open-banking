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

export interface ClientAssertionInput {
  clientId: string;
  kid: string;
  tokenUrl: string;
  nowEpochSeconds: number;
  jti: string;
}

/** private_key_jwt — the shape of BuildClientAssertion.kt that HSBC accepted from the deployed Worker. */
export function buildClientAssertion(i: ClientAssertionInput): Promise<string> {
  const iat = i.nowEpochSeconds;
  return signJwt(
    { alg: "PS256", kid: i.kid },
    { iss: i.clientId, sub: i.clientId, aud: i.tokenUrl, jti: i.jti, iat, exp: iat + 60 },
  );
}
