/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

import type { AttestationDO } from "./attest/AttestationDO";
import type { RateLimitCounterDO } from "./limits/RateLimitCounterDO";
import type { SessionDO } from "./session/SessionDO";
import type { TokenCacheDO } from "./session/TokenCacheDO";

// Vars come from wrangler.toml; secrets are set from the vault by scripts/deploy.sh.
export interface Env {
  HSBC_MTLS: Fetcher;
  HSBC_BANK_HOST: string;
  HSBC_AUTHORIZE_HOST: string;
  HSBC_REDIRECT_URI: string;
  HSBC_ISSUER: string;
  HSBC_SIGNING_KEY_PEM: string;
  HSBC_CLIENT_ID: string;
  HSBC_KID: string;
  HSBC_SIGNING_ISSUER: string;
  HSBC_SOFTWARE_STATEMENT: string;
  SESSIONS: DurableObjectNamespace<SessionDO>;
  TOKEN_CACHE: DurableObjectNamespace<TokenCacheDO>;
  CONSENT_LIMIT?: RateLimit; // absent where the account has no Rate Limiting binding → RATE_LIMIT_COUNTER
  RATE_LIMIT_COUNTER: DurableObjectNamespace<RateLimitCounterDO>;
  ATTESTATION: DurableObjectNamespace<AttestationDO>;
  // Attestation (phase 06). Absent until handed off from the vault — see attest/gate.ts for what each mode does then.
  PLAY_INTEGRITY_SA_JSON?: string;
  APPLE_TEAM_ID?: string;
  ATTESTATION_MODE?: string; // "report-only" | anything else (incl. unset) = enforce
  CORS_ORIGINS?: string; // comma-separated web-client origins allowed to call cross-origin (phase 07); unset = none
  WEB_RETURN_ORIGINS?: string; // ordered, comma-separated web origins a sign-in may return to; state carries `w<index>.` (relay bridge.js holds the same list)
}
