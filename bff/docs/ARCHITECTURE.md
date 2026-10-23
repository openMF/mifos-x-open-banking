# Architecture

The Worker sits between the Mifos X Open Banking apps and the HSBC UK Open Banking sandbox. It is the
only component that holds the TPP identity (signing key, transport certificate, client id, kid,
software statement, signing issuer) and the only one that holds HSBC tokens. The app holds one opaque
session id. Route details are in [API.md](API.md); the security reasoning is in [SECURITY.md](SECURITY.md).

```
 app (Android / iOS / desktop / web)                    Cloudflare                          HSBC sandbox
 ┌──────────────────────────────┐   HTTPS + Session   ┌──────────────────────────┐  mTLS   ┌───────────────────────────┐
 │ core/network BffHttpClient   │ ──────────────────▶ │ Worker mifos-x-open-     │ ──────▶ │ secure.sandbox.ob.hsbc.   │
 │ holds: bff_session only      │ ◀────────────────── │ banking-bff (src/)       │ ◀────── │ co.uk  (API + token)      │
 └──────────────────────────────┘                     │  Durable Objects:        │         ├───────────────────────────┤
        ▲  code / id_token / state                    │  SessionDO TokenCacheDO  │ ──────▶ │ sandbox.ob.hsbc.co.uk     │
        │                                             │  AttestationDO           │         │ (authorize, discovery)    │
 ┌──────┴───────────────────────┐   browser redirect  │  RateLimitCounterDO      │         └───────────────────────────┘
 │ relay (Cloudflare Pages)     │ ◀─────────────────  └──────────────────────────┘
 │ relay/site/callback/         │       from HSBC              │ Play Integrity decode (Google)
 └──────────────────────────────┘                              ▼
```

## 1. Components

| Component | File(s) | Responsibility |
|---|---|---|
| Fetch entry | `src/index.ts` | CORS preflight, route resolution, the three gates (rate limit → session → attestation), handler dispatch, `x-request-id` on every response, the redacted log line, `500 internal` on an exception. Exports the four Durable Object classes. |
| Route table | `src/routes.ts` | The closed allowlist: 56 rows of `{id, method, path, hsbcOp, token, signJws, maxBodyBytes, attest, session, rateLimit?, handler?, product?}`. `bankPath()` fills an HSBC path from route params and refuses any param outside `^[\w.-]{1,128}$`. `payLeg()` names a payment's session leg. |
| Router | `src/router.ts` | Exact method + segment-by-segment template match (`:name` = one non-empty segment). `404 route_not_found` for anything unlisted, `413` over the row's `maxBodyBytes` (counted while streaming), `415` for a non-JSON body. |
| Handlers | `src/handlers/*.ts` | `consentAis.ts` (AIS consent, `beginAuthorisation`, `webStatePrefix`, `ccRead` for consent/payment reads and deletes) · `authComplete.ts` · `aisRead.ts`, `aisPage.ts`, `aisStatementFile.ts` · `payment.ts` (signed consent creation, funds check, signed PSU POST) · `sessionLogout.ts` · `attest.ts`. |
| Session gate | `src/gate.ts` | `Authorization: Session <43 base64url chars>` → the `SessionDO` addressed by SHA-256 of the id; `401` when missing or unknown. |
| Rate limit | `src/limits.ts`, `src/limits/RateLimitCounterDO.ts` | 20 requests / 60 s per route + `X-Client-Instance` + IP. Uses the `CONSENT_LIMIT` Rate Limiting binding when present, else a fixed-window Durable Object counter. |
| Attestation gate | `src/attest/gate.ts`, `playIntegrity.ts`, `appAttest.ts`, `AttestationDO.ts`, `appleRootCA.ts` | Play Integrity (Android) and App Attest (iOS) checks over `clientData`; `ATTESTATION_MODE` decides refuse vs report. |
| Signer | `src/crypto/signer.ts` | Imports `HSBC_SIGNING_KEY_PEM` (PKCS#8) once per isolate into a non-extractable RSA-PSS SHA-256 `CryptoKey` held at module scope. `signJwt` (compact) and `signDetached` (detached JWS). |
| Client assertion | `src/crypto/clientAssertion.ts` | `private_key_jwt`: PS256, `iss` = `sub` = client id, `aud` = token URL, `jti`, `exp` = `iat` + 60 s. |
| Request object | `src/crypto/requestObject.ts` | OIDC request object (`response_type` `code id_token`, `openbanking_intent_id` = consent id, 1,200 s lifetime) and the authorize URL. |
| Detached JWS | `src/crypto/detachedJws.ts` | OBIE v4 `x-jws-signature`: `typ: JOSE`, `cty: application/json`, `crit` = the three OB claims (`iat`, `iss`, `tan`), **no** `b64` header. `iss` is `HSBC_SIGNING_ISSUER`, the signing certificate's subject DN. |
| id_token check | `src/crypto/idToken.ts` | Discovery + JWKS from the authorize host, cached; PS256 signature, `iss`, `aud`, `exp`/`iat` (±120 s), `nonce`, `c_hash`, `s_hash`. |
| HSBC client | `src/hsbc/client.ts` | `hsbcFetch` over `env.HSBC_MTLS.fetch` with FAPI headers (fresh `x-fapi-interaction-id`), bearer, idempotency key, JWS. Token grants (`client_credentials`, `authorization_code`, `refresh_token`) with a fresh client assertion. `ccAccessToken` caches client-credentials tokens. `psuFetch` refreshes once on 401. `bankError` extracts codes/paths/messages only. |
| Pagination | `src/hsbc/pagination.ts` | Checks every HSBC `Links` URL is https on the bank host, then maps them to Worker cursor URLs. |

The Worker is configured in `wrangler.toml`: name `mifos-x-open-banking-bff`, `workers_dev = true`,
`nodejs_compat`, observability on, the `HSBC_MTLS` mTLS certificate binding, the plain vars, the four
Durable Object bindings with their SQLite migrations, and the `CONSENT_LIMIT` rate limit. Secrets are not
in that file; `scripts/deploy.sh` sets them (see [RUNBOOK.md](RUNBOOK.md)).

| Var (`wrangler.toml`) | Value on the deployed Worker |
|---|---|
| `HSBC_BANK_HOST` | `secure.sandbox.ob.hsbc.co.uk` — API and token endpoint (mTLS) |
| `HSBC_AUTHORIZE_HOST` | `sandbox.ob.hsbc.co.uk` — authorize endpoint and OIDC discovery |
| `HSBC_ISSUER` | `https://secure.sandbox.ob.hsbc.co.uk` — expected `iss`; also the request object's `aud` |
| `HSBC_REDIRECT_URI` | `https://mifos-x-open-banking-relay.pages.dev/callback/` |
| `ATTESTATION_MODE` | `report-only` |
| `CORS_ORIGINS` | `http://localhost:8080` |
| `WEB_RETURN_ORIGINS` | `http://localhost:8080` |

| Secret (set by `deploy.sh`) | Vault alias |
|---|---|
| `HSBC_SIGNING_KEY_PEM` | `mifos-x-open-banking-hsbc-private-key` |
| `HSBC_CLIENT_ID` | `mifos-x-open-banking-hsbc-client-id` |
| `HSBC_KID` | `mifos-x-open-banking-hsbc-kid` |
| `HSBC_SOFTWARE_STATEMENT` | `mifos-x-open-banking-hsbc-software-statement` |
| `HSBC_SIGNING_ISSUER` | `mifos-x-open-banking-hsbc-signing-issuer` |
| `PLAY_INTEGRITY_SA_JSON` (pending hand-off) | `mifos-x-open-banking-play-integrity-sa` |
| `APPLE_TEAM_ID` (pending hand-off) | `mifos-x-open-banking-apple-team-id` |
| mTLS certificate `mifos-x-open-banking-hsbc-transport` (not a secret var; bound as `HSBC_MTLS`) | `mifos-x-open-banking-hsbc-transport-p12` + `…-transport-p12-password` |

## 2. Data flows

### 2.1 Consent leg

`POST /v1/consents/ais`, `/v1/consents/payment/<product>`, `/v1/consents/vrp`. No session exists yet,
so these are rate-limited and (android/ios) attested.

```
app                               Worker                                   HSBC
 │ POST /v1/consents/ais           │                                        │
 │ {permissions,…}  X-Client-*     │                                        │
 │────────────────────────────────▶│ rate limit → attestation               │
 │                                 │ validate body; web: Origin → w<i>.     │
 │                                 │ cc token (TokenCacheDO, or grant ─────▶│ POST /v1.1/oauth2/token
 │                                 │   with a signed client assertion)  ◀───│  client_credentials
 │                                 │ POST account-access-consents ─────────▶│
 │                                 │                                   ◀────│ {Data.ConsentId}
 │                                 │ SessionDO "flow:<flowId>":             │
 │                                 │   {state, nonce, leg, consentId} 600 s │
 │                                 │ sign request object (PS256)            │
 │◀────────────────────────────────│ {flowId, consentId, authorizeUrl}      │
 │ open authorizeUrl in browser ──────────────────────────────────────────▶│ sandbox.ob.hsbc.co.uk
```

Payment and VRP consents differ in two ways: the body is the client's OBIE consent, forwarded byte for
byte with a detached JWS (see 2.4), and the flow's leg is `payment:<product>:<consentId>` or
`vrp:<consentId>`.

### 2.2 Auth completion

HSBC redirects the browser to the relay with `code`, `id_token` and `state` in the URL fragment. The
relay hands them to the app (loopback on desktop, custom scheme on mobile, `#mifos-callback?` on web), and
the app calls the Worker:

```
app                               Worker                                   HSBC
 │ POST /v1/auth/complete          │                                        │
 │ {flowId, code, idToken, state}  │ rate limit → attestation               │
 │ [Session <id> for payment/vrp]  │ take flow:<flowId> (single use) ─ none → 400 unknown_flow
 │────────────────────────────────▶│ payment/vrp + bad Session → 401        │
 │                                 │ state == flow.state ─ no → 400 state_mismatch
 │                                 │ discovery + JWKS (cached) ────────────▶│ sandbox.ob.hsbc.co.uk/.well-known/…
 │                                 │ verify id_token: PS256 sig, iss, aud,  │
 │                                 │  exp/iat, nonce, c_hash, s_hash        │
 │                                 │   ─ fail → 400 invalid_id_token        │
 │                                 │ code exchange (client assertion) ─────▶│ POST /v1.1/oauth2/token
 │                                 │                                   ◀────│ access [+ refresh]
 │                                 │ GET consent (cc token) ───────────────▶│
 │                                 │   Status == Rejected → 409 consent_rejected (tokens dropped)
 │                                 │ SessionDO(SHA-256(session)):           │
 │                                 │   tokens[leg], consents[leg]           │
 │◀────────────────────────────────│ {session, expiresAt, leg, consentId,   │
 │                                 │  consentStatus, consentExpiresAt}      │
```

The order is deliberate: the flow is consumed first (a replay gets `unknown_flow`), the `id_token` is
fully verified before the code is spent, and the consent status is read before any token is stored. The
AIS leg always creates a new session. A payment or VRP leg is added to the session the app presents, or
to a new session when it presents none. A payment leg keeps only the access token (HSBC gives 5
minutes, no refresh); a VRP leg must come with a refresh token.

### 2.3 AIS read with server-side refresh

```
app                               Worker                                   HSBC
 │ GET /v1/ais/accounts/:id/       │                                        │
 │     transactions  Session <id>  │ session gate                           │
 │────────────────────────────────▶│ tokens[ais] from SessionDO             │
 │                                 │ GET …/transactions (PSU token) ───────▶│
 │                                 │                         401 ◀──────────│
 │                                 │ SessionDO.refresh(ais): one in-flight  │
 │                                 │  refresh per leg, shared by callers ──▶│ POST token (refresh_token)
 │                                 │ retry once with the new token ────────▶│
 │                                 │                         200 ◀──────────│ {Data, Links}
 │                                 │ check every Links URL is on the bank   │
 │                                 │  host; store each as a cursor (1 h)    │
 │◀────────────────────────────────│ {Data, Links: /v1/ais/page?cursor=…}   │
```

A refused refresh, or no AIS tokens, is `401 session_expired`. The next page is
`GET /v1/ais/page?cursor=…`; the cursor resolves only in the session that received it.

### 2.4 Payment with a detached JWS

```
app                               Worker                                   HSBC
 │ POST /v1/payments/domestic      │                                        │
 │ compact JSON, X-Idempotency-Key │ session gate                           │
 │────────────────────────────────▶│ key format; UTF-8; JSON; Data object;  │
 │                                 │ bytes == compact form ─ no → 400       │
 │                                 │ Data.ConsentId owned by this session   │
 │                                 │  for this product ─ no → 403           │
 │                                 │ one PS256 over the exact bytes         │
 │                                 │  (x-jws-signature, detached)           │
 │                                 │ POST domestic-payments (PSU token) ───▶│
 │                                 │                                   ◀────│ 201 {Data.DomesticPaymentId}
 │                                 │ record payment id in SessionDO         │
 │◀────────────────────────────────│ 201 HSBC body                          │
```

Every refusal runs before the RSA work. The Worker never re-serialises a body: the bytes it signs are
the bytes it forwards. A `401` from HSBC on a payment leg is returned as `401 psu_token_expired` (there is
nothing to refresh with).

### 2.5 VRP

Consent creation and authorisation follow 2.1 and 2.2 with the leg `vrp:<consentId>`. Each VRP payment
(`POST /v1/vrp/payments`) and funds check (`POST /v1/consents/vrp/:consentId/funds-confirmation`) is a
signed POST as in 2.4, on the VRP PSU token. On an HSBC `401` the token is refreshed as in 2.3 and the
request is retried with the same signature, so a VRP request makes exactly one signature.

### 2.6 Attestation

```
Android                                    Worker                         Google / AttestationDO
 │ POST /v1/attest/challenge ─────────────▶│ 32 random bytes ─────────────▶ challenge:<c> (300 s)
 │◀──────────── {challenge, expiresIn} ────│
 │ Play Integrity standard request:        │
 │  requestHash = b64url(SHA-256(c ‖ clientData)),  clientData = "POST /v1/consents/ais\n" + body
 │ POST /v1/consents/ais                   │
 │  X-Attestation: play <c>.<token> ──────▶│ verdict cached for this session? → pass
 │                                         │ take challenge:<c> (single use)
 │                                         │ Google token (TokenCacheDO, RS256 on a miss) ─▶ decodeIntegrityToken
 │                                         │ package, requestHash, PLAY_RECOGNIZED,
 │                                         │  MEETS_DEVICE_INTEGRITY → cache verdict 10 min
iOS
 │ once: challenge → attestKey → POST /v1/attest/ios/register {keyId, attestationObject, challenge}
 │        Worker: x5c chain to the embedded Apple root, nonce, keyId, rpIdHash, counter 0, aaguid
 │        → key:<keyId> (public key, counter 0)
 │ each attested call: X-Attestation: ios <keyId>.<assertion over SHA-256(clientData)>
 │        Worker → key:<keyId>: one ECDSA P-256 verify + counter must strictly increase
```

Attested routes: every `POST /v1/consents/*` (including the VRP funds check) and `POST /v1/auth/complete`.
Desktop and web are not attested. With `ATTESTATION_MODE = "report-only"` (the deployed setting) a
failure is reported in `x-attestation: unverified` instead of refused.

### 2.7 Web return through the relay

A browser tab has no loopback listener and no custom scheme, so the return target travels in `state`:

```
web app (Origin O)          Worker                         HSBC            relay (bridge.js)
 │ POST /v1/consents/ais     │                              │                │
 │ X-Client-Platform: web ──▶│ i = WEB_RETURN_ORIGINS.indexOf(O)             │
 │                           │  i < 0 → 400 web_origin_not_allowed           │
 │                           │ state = "w<i>." + uuid       │                │
 │◀── authorizeUrl ──────────│                              │                │
 │ navigate ───────────────────────────────────────────────▶│ login + SCA    │
 │                                                          │ redirect ─────▶│ #code&id_token&state=w<i>.…
 │                                                          │                │ scrub history
 │◀──────────────────────── location.replace(WEB_RETURN_ORIGINS[i] + "/#mifos-callback?" + params)
 │ ConsentRedirectBus → POST /v1/auth/complete (as 2.2)
```

The relay only ever navigates to an entry of its own list; the URL carries an index, never an origin.
`WEB_RETURN_ORIGINS` in `wrangler.toml` and in `relay/site/callback/bridge.js` must match entry for
entry and in order (`relay/test/bridge.test.js` reads `wrangler.toml` and fails otherwise).

## 3. Durable Objects

All four are SQLite-backed (available on Workers Free), declared in `wrangler.toml` migrations v1–v3.

| Binding / class | Instance name | Stores |
|---|---|---|
| `SESSIONS` / `SessionDO` (`src/session/SessionDO.ts`) | `SHA-256(session id)` (base64url) for a session; `flow:<flowId>` for a pending flow | Tables: `session` (id hash, created at) · `tokens` (per leg: access, refresh, expiry) · `consents` (per leg: consent id) · `flows` (state, nonce, leg, consent id, 600 s expiry; an alarm deletes expired ones) · `cursors` (opaque cursor → HSBC Links URL, 1 h) · `payments` (product + payment id this session submitted). Legs: `ais`, `payment:<product>:<consentId>`, `vrp:<consentId>`. An in-memory map makes refresh single-writer per leg. `wipe()` clears every table on logout. The raw session id is never stored. |
| `TOKEN_CACHE` / `TokenCacheDO` (`src/session/TokenCacheDO.ts`) | `global` | `cache` table: `cc:accounts` and `cc:payments` client-credentials tokens (until 60 s before expiry) · `hsbc:oidc` discovery issuer + JWKS (JWKS `max-age`, default 3,600 s) · `google:playintegrity` Google access token (until 60 s before expiry). |
| `ATTESTATION` / `AttestationDO` (`src/attest/AttestationDO.ts`) | `challenge:<challenge>`, `key:<keyId>`, `verdict:<SHA-256(session id)>` | One fact per object: a single-use challenge (300 s) · an App Attest public key and its sign counter · a passed Play Integrity verdict for one session (10 min). Every check-and-set is one SQL statement, so two concurrent requests cannot both win. Challenge and verdict objects delete themselves by alarm. |
| `RATE_LIMIT_COUNTER` / `RateLimitCounterDO` (`src/limits/RateLimitCounterDO.ts`) | `<route id>:<X-Client-Instance>:<IP>` | A fixed window `{start, count}`; an alarm drops it when the window ends. Used only when the `CONSENT_LIMIT` binding is absent. |

## 4. Route table and gating

`src/routes.ts` is the single allowlist. Each row declares:

| Field | Meaning |
|---|---|
| `method`, `path` | What the app calls. `:name` segments are parameters. |
| `hsbcOp` | The HSBC method and path (under `/obie/open-banking`) the row maps to. |
| `token` | `cc:accounts`, `cc:payments` (client-credentials) · `psu:ais`, `psu:payment`, `psu:vrp` (the session's PSU token) · `none`. |
| `signJws` | The row forwards a client body with a detached JWS. |
| `maxBodyBytes` | 0 = no body allowed. |
| `session`, `attest`, `rateLimit` | The gates. |
| `handler`, `product` | Handler key (defaults to `id`) and the payment product. |

Request processing in `handle()` (`src/index.ts`):

1. `OPTIONS` from an origin on `CORS_ORIGINS` for a listed method + path → `204` preflight.
2. `resolveRoute`: match or `404`; body size (`413`) and type (`415`).
3. Gates, always in this order, before any handler: **rate limit** → **session** → **attestation**.
   A handler cannot skip one, because it never sees a request that failed one.
4. The handler. Ownership checks (the consent or payment belongs to this session) and body checks run
   before any signature or HSBC call.
5. `x-request-id` (and `x-attestation`, CORS headers) on the response; one JSON log line through
   `redact()`.

Anything not in the table is a `404` with no HSBC call; `test/router.test.ts` pins the table and
checks that unlisted methods and paths never reach the `HSBC_MTLS` binding.

## 5. Workers Free CPU rules

Workers Free allows 10 ms of CPU per request (wall time spent waiting on HSBC does not count). The code
keeps hot paths inside it:

- **Key import once per isolate.** `initSigner` imports the PKCS#8 key into a module-scope `CryptoKey`
  and skips the import while the secret string is unchanged. The first signed request on a fresh
  isolate pays for the import.
- **Signatures per request.** Client-credentials tokens come from `TokenCacheDO`, so a warm request
  signs only what it must: AIS consent 1 (request object) · payment / VRP consent 2 (detached JWS +
  request object) · payment, VRP payment and VRP funds check 1 (detached JWS; a refresh retry reuses it)
  · `/v1/auth/complete` 1 (client assertion for the code exchange) · reads 0 (a refresh adds 1).
- **No per-request signature for attestation.** The Google token is minted with one RS256 signature on a
  cache miss (about hourly); a Play Integrity check is a hash plus two subrequests; an App Attest
  assertion is one ECDSA verify. App Attest registration (chain validation) is a one-off per key, and the
  `@peculiar/x509` code is loaded by dynamic import so only registration evaluates it.
- **Bundle size.** `scripts/bundle-size.sh` fails at 950 KB (the bundle is parsed on every cold isolate);
  it measured 786 KB (132 KB gzipped) after the attestation libraries were added.
- **Measuring.** `scripts/cpu-report.sh --since 1h|24h|7d` reads Worker-wide `cpuTimeP50`/`cpuTimeP99`
  from the Cloudflare GraphQL analytics (no per-path dimension exists there).
  `scripts/cpu-report.sh --per-route [--routes a,b] -- <traffic command>` runs `wrangler tail` while the
  command generates traffic and prints p99 per `METHOD /path`. Both exit 5 when a p99 reaches
  `--limit-ms` (default 10).
- **Decision (2026-10-22): stay on Workers Free.** Warm signed paths fit; a cold isolate's first signed
  consent measured 10 ms and the Worker-wide p99 is 10–12 ms with 0 errors. The trigger for Workers
  Paid is Cloudflare error 1102 (CPU limit exceeded) in Workers Logs; see
  [RUNBOOK.md](RUNBOOK.md#9-workers-free-cpu-limit-and-the-paid-decision).

## CPU budget

Readings so far (sandbox traffic, from `IMPLEMENTATION_STATUS.md`). Per-route rows come from
`cpu-report.sh --per-route`; Worker-wide rows from `cpu-report.sh --since`.

| Route | Measured | Requests | p99 CPU |
|---|---|---:|---:|
| `GET /v1/health` | Phase 2, warm | 20 | 0 ms |
| `POST /v1/consents/payment/domestic` (2 signatures) | Phase 5, first traffic after deploy (cold isolate) | 10 | 10 ms |
| `POST /v1/consents/payment/domestic` | Phase 5, warm | 10 | 7 ms |
| `POST /v1/consents/vrp` (2 signatures) | Phase 5 | 3 + 3 | 4 ms |
| `POST /v1/attest/challenge` | Phase 6 | 20 + 20 | 1–3 ms |
| `POST /v1/attest/ios/register` (unconfigured path) | Phase 6 | 3 | 0 ms |
| `POST /v1/consents/ais` from android, report-only | Phase 6 | 5 | 6 ms |
| Worker-wide, 1 h | Phase 5 (2 deploys, cold isolates) | 88, 0 errors | 12.29 ms (p50 3.12 ms) |
| Worker-wide, 1 h / 24 h | Phase 6 | 82 / 121, 0 errors | 10.25 ms |

PENDING-T9: final 24 h report
