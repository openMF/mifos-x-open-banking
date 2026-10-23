# Security

What the Worker protects, from whom, and what is left over. Procedures (rotation, revocation, rollback)
are in [RUNBOOK.md](RUNBOOK.md).

## 1. Threat model

| Threat | What an attacker could do | Mitigation (where) | Residual risk |
|---|---|---|---|
| **Stolen or unpacked app** (APK, IPA, desktop jar, web bundle) | Read every string and file in the binary | The app contains no HSBC key, certificate, client id, kid, software statement or signing issuer. The `no-tpp-identity` workflow (`.github/workflows/no-tpp-identity.yml`) greps app source for the identity symbols and fails on any `.pem`/`.p12`/`certs/` entry in a built APK or jar. The app's only credential is its own session id. | The Worker URL and route list are public. Anyone can start a consent, which is why consent creation is rate-limited and (android/ios) attested. |
| **Malicious session holder** (a stolen session id, or a user scripting their own) | Call every session route as that user | The session reaches only data the PSU already authorised at HSBC. Consent reads and deletes are limited to the session's own consents (`403 consent_not_in_session`), payment reads to payments the session submitted (`403 payment_not_in_session`), payments and funds checks to consents the session authorised for that product (`403 consent_not_owned`). No HSBC token is ever returned, so a stolen session cannot be turned into direct bank access. Logout wipes the session. | The session lives as long as HSBC keeps refreshing the AIS token (the consent's lifetime, 90 days by default); there is no Worker-side idle timeout. A session id taken from a device works from anywhere until logout. |
| **Replay** | Re-send a captured `/v1/auth/complete`, payment or attestation | Pending flows are single use and expire after 600 s (a replay gets `400 unknown_flow`); `state` must match; the `id_token` must carry the flow's `nonce` and matching `c_hash`/`s_hash`. Payment POSTs need an `X-Idempotency-Key`, which HSBC enforces per consent. Attestation challenges are single use (300 s); App Attest counters must strictly increase. | A captured payment body with its idempotency key replays to the same HSBC result, not a second payment. |
| **Open-proxy abuse** | Use the Worker's mTLS identity to reach arbitrary HSBC endpoints or other hosts | Closed route table: unlisted method + path → `404` before any HSBC call (`test/router.test.ts`). Path parameters are limited to `[\w.-]{1,128}`. HSBC `Links` URLs on any other host are refused, and pagination uses opaque per-session cursors instead of URLs. Body size and type limits per route. Consent creation and sign-in are rate-limited per install + IP. | `X-Client-Instance` is chosen by the client, so rotating it resets that client's count (the IP stays in the key). |
| **Foreign consent ids** (IDOR) | Pay against, read, or revoke someone else's consent; aim the Worker's signing key at it | Ownership is bound at `/v1/auth/complete` and checked before any signing or HSBC call (see above). Payment legs are product-scoped, so a domestic consent cannot be paid through the international route. The VRP funds path must name the same consent as its body. | None known. |
| **Log leakage** | Read Workers Logs, `wrangler tail` or CI output | One JSON line per request: request id, method, path, route id, status, duration, attestation verdict — passed through `redact()` (`src/index.ts`), which masks `authorization`, `access_token`, `refresh_token`, `id_token`, `request`, `code`, `x-jws-signature` and `body` at any depth. Exceptions log their type only, never their message. Query strings are not logged. Bank errors are reduced to codes, paths and messages (`bankError()`). `test/custody.test.ts` scans every response and log line for the fixture tokens. | The path is logged, so consent, account, statement and payment ids appear in logs. |
| **Web origin spoofing** | Make a web sign-in return to an attacker's page, or call the Worker from another site | `WEB_RETURN_ORIGINS`: a web consent from an `Origin` not on the list gets `400 web_origin_not_allowed`. `state` carries only an index into the list, and the relay navigates only to an entry of its own copy, never to a URL taken from the request. CORS is answered only for origins on `CORS_ORIGINS`. | `Origin` is enforced by browsers, not by scripts; a non-browser client can claim any origin, but then the return lands on a listed origin it does not control. |
| **Mobile custom-scheme hijack** | Another app registers `org.mifosx.openbanking://` and receives the code | Out of the Worker's reach; see `relay/README.md`. A stolen code alone is useless without the matching `flowId`, which only the app that started the flow holds. | Android App Links / iOS Universal Links are the planned fix (relay plan FR-052). |

## 2. Key custody

- **Transport certificate (mTLS).** Uploaded to Cloudflare as the mTLS certificate
  `mifos-x-open-banking-hsbc-transport` and bound to the Worker as `HSBC_MTLS` (`[[mtls_certificates]]`
  in `wrangler.toml`, which holds only the certificate's public id). Every HSBC call goes through
  `env.HSBC_MTLS.fetch`; Worker code never sees the private key. `scripts/deploy.sh` splits the vaulted
  PKCS#12 into PEM files in a `0700` temp directory and shreds them on exit.
- **Signing key.** The Worker secret `HSBC_SIGNING_KEY_PEM` is imported once per isolate into a
  **non-extractable** RSA-PSS `CryptoKey` (`src/crypto/signer.ts`). No code path exports it; the module
  exposes only `signJwt` and `signDetached`.
- **The vault is the source of truth** (RULE-SECRETS-VAULT-001). Every credential lives in the
  framework vault under a `mifos-x-open-banking-*` alias. `deploy.sh` resolves aliases with
  `secrets-get.sh --secure-temp`, sets the Worker secrets in one `wrangler secret bulk` call from a
  `0600` file, and prints nothing secret. Nothing secret is in `wrangler.toml`, in this repository, or in
  any app build; `bff/.gitignore` excludes `.dev.vars`, `*.pem` and `*.p12`.
- **Cloudflare API tokens** are vaulted too: `mifos-x-open-banking-cloudflare-bff-token` for the Worker
  and `mifos-x-cloudflare-api-token` for the relay's Pages project. The account id comes from
  `mifos-x-cloudflare-account-id` at run time.

## 3. Session model

- `/v1/auth/complete` mints a session only after the flow, `state` and the full `id_token` check pass,
  the code is exchanged, and HSBC does not report the consent `Rejected`.
- The session id is 32 random bytes, base64url (43 characters). It is returned once. The Worker stores
  only its SHA-256, which also names the `SessionDO`.
- The `SessionDO` holds the HSBC tokens per leg (`ais`, `payment:<product>:<consentId>`,
  `vrp:<consentId>`), the consent id per leg, payment ids the session submitted, and pagination
  cursors. The app holds only the session id (`bff_session` in its settings).
- AIS and VRP tokens are refreshed server-side on an HSBC `401`, one refresh per leg at a time. A
  refused refresh answers `401 session_expired`; the app signs in again. Payment tokens are not
  refreshable (HSBC gives 5 minutes).
- `POST /v1/session/logout` wipes the session; `?revoke=true` also deletes the AIS consent at HSBC.
- The response's `expiresAt` is advisory. The session ends when the bank stops refreshing, or at logout.

## 4. Attestation

| Platform (`X-Client-Platform`) | Check | Where |
|---|---|---|
| `android` | Play Integrity standard request. The Worker consumes a single-use challenge, has Google decode the token, and requires package `org.mifosx.openbanking`, `requestHash` = SHA-256 of challenge + `clientData`, `PLAY_RECOGNIZED` and `MEETS_DEVICE_INTEGRITY`. A pass is cached for the session for 10 minutes. | `src/attest/playIntegrity.ts` |
| `ios` | App Attest. A key is registered once (certificate chain to the Apple App Attestation Root CA embedded in `src/attest/appleRootCA.ts`, nonce, key id, app id hash for `APPLE_TEAM_ID.org.mifosx.openbanking`, counter 0). Every attested call carries an assertion over `clientData`: one ECDSA verify and a strictly increasing counter. | `src/attest/appAttest.ts` |
| `desktop`, `web`, absent | None. Session and rate limits only. | `src/attest/gate.ts` |

`clientData` is `"METHOD /path\n"` followed by the exact body, so an attestation covers that one request.
Attested routes: every consent creation (AIS, six payment products, VRP), the VRP funds check, and
`/v1/auth/complete`.

**Current status: report-only.** `wrangler.toml` sets `ATTESTATION_MODE = "report-only"` because the
Play Integrity service account and the Apple Team ID have not been handed off. In this mode nothing is
refused; responses carry `x-attestation: verified` or `unverified`, so the gate gives visibility, not
protection. Switching to enforce is in [RUNBOOK.md §7](RUNBOOK.md#7-hand-off-the-attestation-credentials-and-enforce).

**Residual risk.** `X-Client-Platform` is asserted by the client. Desktop and web have no platform
attestation, so a script can claim `desktop` or `web` and skip the check even in enforce mode. What
holds then is the rate limit, the closed route table, and the fact that any data access still needs a
real PSU login and SCA at HSBC. This trade-off is accepted (GOAL D3).

## 5. Never returned, never logged

- HSBC access, refresh and id tokens; client-credentials tokens; the Google access token.
- Client assertions, request objects, `x-jws-signature` values.
- The signing key, the transport certificate and key, the PKCS#12 password, the software statement.
- Authorisation codes and request bodies (bodies are never logged; signed bodies are forwarded, not
  stored).
- The raw session id is returned once by `/v1/auth/complete` and never stored or logged.

`test/custody.test.ts` checks every route's response body and headers, and the log output, against the
fixture token values.

## 6. Incident response

| Event | Action ([RUNBOOK.md](RUNBOOK.md)) |
|---|---|
| Signing key, transport certificate or software statement exposed | Rotate the material at HSBC DevHub and in the vault, redeploy, then revoke the old material (§5, §10). Treat any exposure in chat or logs as a leak (RULE-SECRETS-NO-VALUE-EGRESS-001). |
| Cloudflare token exposed | Revoke it in the Cloudflare dashboard, issue a new one with the same scopes, push it to the vault (§1). |
| Bad deploy | `wrangler rollback` (§4). |
| Suspected session theft | The user logs out (wipes the session) and revokes the consent; HSBC revocation ends every token for that consent. |
| Abuse of consent creation | Check the request ids in Workers Logs; enforce attestation (§7); tighten the rate limit in `routes.ts` / `wrangler.toml`. |
