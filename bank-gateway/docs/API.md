# API

Every route the Worker answers. The list mirrors the closed route table in
[`src/routes.ts`](../src/routes.ts) row for row; [`test/docs.test.ts`](../test/docs.test.ts) fails if a
route is missing here, if this file documents a route the table does not have, or if a section's
**Auth** line disagrees with the row's `session`, `attest` and `rateLimit` flags.

- Reference deployment: `https://mifos-x-open-banking-gateway.mifos-x-open-banking.workers.dev`
- Bank host (sandbox): `secure.sandbox.ob.hsbc.co.uk` (`HSBC_BANK_HOST`). Every **HSBC** line below is a
  path under `https://<bank host>/obie/open-banking`.
- Path parameters (`:consentId`, `:accountId`, …) match one non-empty path segment. Before a parameter
  is put into an HSBC path it must match `^[\w.-]{1,128}$`, otherwise the route answers
  `400 invalid_param` (`bankPath` in `src/routes.ts`).

## Contents

- [Conventions](#conventions): headers, error envelope, signed bodies, CORS
- [Health and attestation](#health-and-attestation)
- [Sign-in and session](#sign-in-and-session)
- [Account information (AIS)](#account-information-ais)
- [Payment initiation (PIS)](#payment-initiation-pis)
- [Variable recurring payments (VRP)](#variable-recurring-payments-vrp)

## Conventions

### Request headers

| Header | Sent on | Meaning |
|---|---|---|
| `Authorization: Session <id>` | every route whose Auth line says **session**; optional on `POST /v1/auth/complete` | The opaque session id from `/v1/auth/complete`: exactly 43 base64url characters (32 random bytes). The Worker hashes it (SHA-256) before looking up the `SessionDO`, so the raw id is never stored. Missing or malformed → `401 missing_session`; unknown → `401 unknown_session` (`src/gate.ts`). |
| `X-Client-Platform` | every request from the app | `android`, `ios`, `desktop` or `web`. Selects the attestation check on attestation routes (`android` → Play Integrity, `ios` → App Attest, anything else → none) and, with `web`, the web return prefix on consent routes. Client-asserted, see [SECURITY.md](SECURITY.md#4-attestation). |
| `X-Client-Instance` | every request from the app | A random id the app creates on first launch (`gateway_client_instance`). Not a secret. Rate-limit key, together with the IP (`src/limits.ts`). Only the first 64 characters are used; absent → `anon`. |
| `X-Attestation` | attestation routes, from android / ios | `play <challenge>.<integrity token>` (Android) or `ios <keyId>.<base64 CBOR assertion>` (iOS). Both sign over `clientData` = UTF-8 `"METHOD /path\n"` followed by the exact request body bytes. Android: Play Integrity standard request with `requestHash = base64url(SHA-256(challenge ‖ clientData))`, the challenge from `POST /v1/attest/challenge`. iOS: `generateAssertion(keyId, SHA-256(clientData))` with a key registered at `POST /v1/attest/ios/register`. See `src/attest/gate.ts`. |
| `X-Idempotency-Key` | every signed POST (payment and VRP consents, payments, VRP funds check) | Required. 1–40 characters from `A–Z a–z 0–9 . _ ~ -`, otherwise `400 idempotency_key`. Forwarded to HSBC as `x-idempotency-key`. Reuse the same key when retrying the same payment. |
| `Content-Type: application/json` | every request with a body | Any other type → `415 unsupported_media_type`. |
| `Origin` | added by the browser | Checked against `CORS_ORIGINS` (CORS) and, for `X-Client-Platform: web` consent routes, against `WEB_RETURN_ORIGINS`. |

### Response headers

| Header | When |
|---|---|
| `x-request-id` | Every response. A fresh UUID; it is also the `requestId` in the Worker's log line. Quote it when reporting a problem. |
| `x-attestation: verified \| unverified` | Attestation routes called with `X-Client-Platform: android` or `ios`. `unverified` appears only in report-only mode (see [Attestation mode](#attestation-mode)). |
| `cache-control: no-store` | Every JSON response and every statement file. |
| `access-control-allow-origin`, `access-control-expose-headers: x-request-id, x-attestation`, `vary: Origin` | Requests from an origin on `CORS_ORIGINS`. |
| `retry-after` | `429 rate_limited`. |

### Error envelope

Every error is JSON with a snake_case `error` code. Some codes carry extra fields:

```json
{ "error": "bank_error", "bankStatus": 400, "bankError": "UK.OBIE.Field.Invalid | Data.Initiation | …" }
```

| Field | Present on |
|---|---|
| `error` | always |
| `requestId` | the router and entry-point errors: `404 route_not_found`, `413 payload_too_large`, `415 unsupported_media_type`, `500 internal`. Every other error carries the same id in the `x-request-id` header only. |
| `bankStatus` | `bank_error`, `token_exchange_failed`: the HTTP status HSBC returned. |
| `bankError` | `bank_error` on signed writes: HSBC's error codes, paths and messages only, at most 400 characters (`bankError()` in `src/hsbc/client.ts`). Never tokens, headers or request data. |
| `reason` | `attestation_invalid`: which check failed (for example `challenge`, `request_hash`, `device_verdict`, `counter`). |
| `retryAfter` | `rate_limited`: seconds until the window resets. |

Errors every route can return:

| Status | `error` | Cause |
|---|---|---|
| 404 | `route_not_found` | Method + path not in the route table. Nothing is sent to HSBC (`src/router.ts`, pinned by `test/router.test.ts`). |
| 413 | `payload_too_large` | A body on a route that takes none, or a body over the route's limit (counted while reading, so a missing or false `Content-Length` does not help). |
| 415 | `unsupported_media_type` | A body that is not `application/json`. |
| 500 | `internal` | An unexpected exception. Only the exception type is logged. |

Gate errors, in the order the gates run (`gate()` in `src/index.ts`):

| Gate | Status | `error` |
|---|---|---|
| Rate limit (Auth line says **rate limit**) | 429 | `rate_limited` — 20 requests per 60 s per route, `X-Client-Instance` and IP |
| Session (Auth line says **session**) | 401 | `missing_session`, `unknown_session` |
| Attestation (Auth line says **attestation**), enforce mode only | 503 / 401 / 403 | `attestation_unconfigured` (the Worker lacks the platform's credential) · `attestation_missing` (no `X-Attestation`) · `attestation_invalid` + `reason` |

#### Attestation mode

`ATTESTATION_MODE` in `wrangler.toml`. The deployed Worker runs `report-only`: a missing, failed or
unconfigured attestation is let through and marked `x-attestation: unverified`. Any other value
enforces the errors above. It is `report-only` only until the Play Integrity service account and the
Apple Team ID are handed off (see [RUNBOOK.md](RUNBOOK.md#7-hand-off-the-attestation-credentials-and-enforce)).
Desktop, web and requests without `X-Client-Platform` are never attested.

### Signed bodies: compact JSON

The routes that carry a detached JWS (payment and VRP consent creation, payment creation, VRP funds
check and VRP payment) forward the client's body bytes to HSBC **unchanged** and sign exactly those
bytes. HSBC verifies `x-jws-signature` over the compact JSON form (a trailing newline or pretty-printed
JSON gets HSBC error `U015`), so the Worker refuses, before any signing:

| Status | `error` | Cause |
|---|---|---|
| 400 | `idempotency_key` | `X-Idempotency-Key` missing or not 1–40 allowed characters |
| 400 | `invalid_request` | Not valid UTF-8, not JSON, or no `Data` object |
| 400 | `body_not_compact` | The bytes differ from `JSON.stringify(JSON.parse(body))`: send compact JSON, no trailing newline, no extra whitespace |

The body is the OBIE v4.0 request body (`{"Data": {…}, "Risk": {…}}`). Two HSBC sandbox rules found
live: `RemittanceInformation.Unstructured` is an **array** of strings (a plain string gets `U002`), and
domestic payment consents need `RemittanceInformation.Structured[0].CreditorReferenceInformation.Reference`
(`U004` otherwise). The Worker does not check either; HSBC does.

Failures from HSBC on these routes return `{"error":"bank_error","bankStatus":…,"bankError":…}`. A 4xx
from HSBC keeps its status; anything else becomes 502.

### CORS

An `OPTIONS` preflight is answered `204` only when the `Origin` is on `CORS_ORIGINS` and the requested
method + path is in the route table. It allows `GET, POST, DELETE` and the headers `authorization,
content-type, x-client-platform, x-client-instance, x-attestation, x-idempotency-key`, with
`access-control-max-age: 600`. Any other preflight falls through to the route table (`404`).

## Health and attestation

### GET /v1/health

- **Auth:** none
- **Request:** no body.
- **Response:** `200 {"status":"ok"}`
- **Errors:** only the common errors.
- **HSBC:** none. Used by `scripts/smoke.sh`.

### POST /v1/attest/challenge

- **Auth:** rate limit (20 / 60 s)
- **Request:** no body needed (at most 256 bytes accepted, ignored).
- **Response:** `200 {"challenge":"<43 base64url chars>","expiresIn":300}`. The challenge is 32 random
  bytes, single use, valid for 300 s, held in an `AttestationDO` named `challenge:<challenge>`.
- **Errors:** `429 rate_limited`.
- **HSBC:** none.

### POST /v1/attest/ios/register

- **Auth:** rate limit (20 / 60 s)
- **Request:** `{"keyId":"<base64>","attestationObject":"<base64>","challenge":"<challenge>"}` (≤ 16 KB).
  The app calls `attestKey(keyId, SHA-256(UTF-8 challenge))` with a challenge from
  `POST /v1/attest/challenge`.
- **Response:** `200 {"keyId":"…"}`. The key's public key is stored in the `AttestationDO` named
  `key:<keyId>` with sign counter 0.
- **Errors:** `503 attestation_unconfigured` (no `APPLE_TEAM_ID`, in either mode) · `400 invalid_request`
  (a field missing) · `403 attestation_invalid` with `reason` one of `malformed`, `challenge` (unknown,
  used or expired), `chain` (not issued under the Apple App Attestation Root CA embedded in
  `src/attest/appleRootCA.ts`), `nonce`, `keyId`, `rpIdHash`, `counter`, `aaguid`, `key_exists`.
- **HSBC:** none.

## Sign-in and session

### POST /v1/consents/ais

- **Auth:** rate limit (20 / 60 s) · attestation (android / ios)
- **Request:** `{"permissions":["ReadAccountsDetail", …],"expirationDateTime"?,"transactionFromDateTime"?,"transactionToDateTime"?}`
  (≤ 8 KB). 1–40 permissions, each `Read` + letters. Dates are ISO-8601; absent ones default to +90
  days, −90 days and now. With `X-Client-Platform: web` the `Origin` must be on `WEB_RETURN_ORIGINS`.
- **Response:** `200 {"flowId","consentId","authorizeUrl"}`. `authorizeUrl` is the HSBC authorize
  endpoint on the authorize host (`HSBC_AUTHORIZE_HOST`) carrying the Worker-signed request object.
  The Worker parks `{state, nonce, leg: "ais", consentId}` as a single-use pending flow for 600 s.
  For web, `state` starts with `w<index>.` (see [ARCHITECTURE.md](ARCHITECTURE.md#27-web-return-through-the-relay)).
- **Errors:** `400 invalid_request` · `400 web_origin_not_allowed` · `502 bank_error` + `bankStatus` ·
  the gate errors.
- **HSBC:** `POST /v4.0/aisp/account-access-consents` on the client-credentials token (scope `accounts`).

### GET /v1/consents/ais/:consentId

- **Auth:** session
- **Request:** no body.
- **Response:** `200` HSBC's consent JSON (`Data.Status`, `Data.Permissions`, …), unchanged.
- **Errors:** `403 consent_not_in_session` (not this session's AIS consent) · `400 invalid_param` ·
  `bank_error` + `bankStatus` with HSBC's status.
- **HSBC:** `GET /v4.0/aisp/account-access-consents/{consentId}` on the client-credentials token (`accounts`).

### DELETE /v1/consents/ais/:consentId

- **Auth:** session
- **Request:** no body.
- **Response:** `204`. The consent is revoked at HSBC; the session itself is untouched (use
  `POST /v1/session/logout`).
- **Errors:** as `GET /v1/consents/ais/:consentId`.
- **HSBC:** `DELETE /v4.0/aisp/account-access-consents/{consentId}` on the client-credentials token (`accounts`).

### POST /v1/auth/complete

- **Auth:** rate limit (20 / 60 s) · attestation (android / ios)
- **Request:** `{"flowId","code","idToken","state"}` (≤ 16 KB), each a non-empty string of at most
  8,192 characters, exactly as the relay handed them to the app. For a payment or VRP leg, send the
  app's current `Authorization: Session <id>` so the new leg is added to that session; without it a new
  session is created. On the AIS leg the header is ignored and a new session is always created.
- **Response:**
  ```json
  { "session": "<43 chars>", "expiresAt": 1767225600000, "leg": "ais", "consentId": "…",
    "consentStatus": "Authorised", "consentExpiresAt": "2027-01-20T10:00:00+00:00" }
  ```
  `leg` is `ais`, `payment` or `vrp`. `expiresAt` (epoch ms) is advisory: the consent's expiry, or +90
  days when HSBC gives none; for a payment leg, the expiry of the 5-minute payment token. For VRP,
  `consentExpiresAt` is `ControlParameters.ValidToDateTime`. If the consent-status read fails the
  session is still created with `consentStatus: "Unknown"`. **No HSBC token is ever in the response.**
- **Errors, in check order:** `400 invalid_request` · `400 unknown_flow` (unknown, already used or
  older than 600 s; replaying a completed call gets this) · `401 missing_session` / `401 unknown_session`
  (payment or VRP leg with a header that is not a live session) · `400 state_mismatch` ·
  `400 invalid_id_token` · `502 token_exchange_failed` + `bankStatus` (HSBC refused the code, or a VRP
  leg got no refresh token) · `409 consent_rejected` (HSBC reports the consent `Rejected`; the tokens are
  discarded) · the gate errors.
- **HSBC:** `POST /v1.1/oauth2/token` (`authorization_code`, `private_key_jwt`); before it, OIDC discovery
  and JWKS (cached, see [ARCHITECTURE.md](ARCHITECTURE.md#22-auth-completion)); after it, a `GET` of the
  consent (`/v4.0/aisp/account-access-consents/{id}`, or the payment / VRP consent) on the
  client-credentials token.

### POST /v1/session/logout

- **Auth:** session
- **Request:** no body. Query `?revoke=true` also deletes the session's AIS consent at HSBC.
- **Response:** `204`. The session, its tokens, consents, pending flows, cursors and payment records are
  wiped; the id is unknown afterwards. The HSBC revoke is best effort: the session is wiped whatever
  HSBC answers.
- **Errors:** only the gate errors.
- **HSBC:** with `?revoke=true`, `DELETE /v4.0/aisp/account-access-consents/{consentId}` on the
  client-credentials token (`accounts`).

## Account information (AIS)

Every AIS read uses the session's `ais` PSU token. On an HSBC `401` the Worker refreshes the token once
(one refresh per session at a time, shared by concurrent requests) and retries. Common to all of them:

- `401 session_expired`: no AIS tokens in this session, or the refresh was refused. Sign in again.
- `bank_error` + `bankStatus`, with HSBC's status (for example `403` for an account outside the
  consent, `404` for an unknown id).
- `502 bank_error`: HSBC returned a `Links` URL on another host (refused, never followed).
- `400 invalid_param`.
- In every JSON response, HSBC's `Links` (`Self`, `First`, `Prev`, `Next`, `Last`) are replaced with
  `https://<worker>/v1/ais/page?cursor=<opaque>`. A cursor works for one hour and only for the session it
  was issued to.

### GET /v1/ais/accounts

- **Auth:** session
- **Request:** no body.
- **Response:** `200` HSBC's accounts JSON, `Links` rewritten.
- **Errors:** the AIS errors above.
- **HSBC:** `GET /v4.0/aisp/accounts`

### GET /v1/ais/accounts/:accountId

- **Auth:** session
- **Request:** no body.
- **Response:** `200` HSBC's account JSON, `Links` rewritten.
- **Errors:** the AIS errors above.
- **HSBC:** `GET /v4.0/aisp/accounts/{accountId}`

### GET /v1/ais/accounts/:accountId/balances

- **Auth:** session
- **Request:** no body.
- **Response:** `200` HSBC's balances JSON, `Links` rewritten.
- **Errors:** the AIS errors above.
- **HSBC:** `GET /v4.0/aisp/accounts/{accountId}/balances`

### GET /v1/ais/accounts/:accountId/beneficiaries

- **Auth:** session
- **Request:** no body.
- **Response:** `200` HSBC's beneficiaries JSON, `Links` rewritten.
- **Errors:** the AIS errors above.
- **HSBC:** `GET /v4.0/aisp/accounts/{accountId}/beneficiaries`

### GET /v1/ais/accounts/:accountId/direct-debits

- **Auth:** session
- **Request:** no body.
- **Response:** `200` HSBC's direct-debits JSON, `Links` rewritten.
- **Errors:** the AIS errors above.
- **HSBC:** `GET /v4.0/aisp/accounts/{accountId}/direct-debits`

### GET /v1/ais/accounts/:accountId/product

- **Auth:** session
- **Request:** no body.
- **Response:** `200` HSBC's product JSON, `Links` rewritten.
- **Errors:** the AIS errors above.
- **HSBC:** `GET /v4.0/aisp/accounts/{accountId}/product`

### GET /v1/ais/accounts/:accountId/party

- **Auth:** session
- **Request:** no body.
- **Response:** `200` HSBC's party JSON, `Links` rewritten.
- **Errors:** the AIS errors above.
- **HSBC:** `GET /v4.0/aisp/accounts/{accountId}/party`

### GET /v1/ais/accounts/:accountId/parties

- **Auth:** session
- **Request:** no body.
- **Response:** `200` HSBC's parties JSON, `Links` rewritten.
- **Errors:** the AIS errors above.
- **HSBC:** `GET /v4.0/aisp/accounts/{accountId}/parties`

### GET /v1/ais/accounts/:accountId/transactions

- **Auth:** session
- **Request:** optional query `fromBookingDateTime`, `toBookingDateTime` (passed to HSBC as is). Any other
  query parameter is dropped. Later pages: follow `Links.Next` (a `/v1/ais/page` URL).
- **Response:** `200` HSBC's transactions JSON, `Links` rewritten.
- **Errors:** the AIS errors above.
- **HSBC:** `GET /v4.0/aisp/accounts/{accountId}/transactions`

### GET /v1/ais/accounts/:accountId/standing-orders

- **Auth:** session
- **Request:** no body.
- **Response:** `200` HSBC's standing-orders JSON, `Links` rewritten.
- **Errors:** the AIS errors above.
- **HSBC:** `GET /v4.0/aisp/accounts/{accountId}/standing-orders`

### GET /v1/ais/accounts/:accountId/scheduled-payments

- **Auth:** session
- **Request:** no body.
- **Response:** `200` HSBC's scheduled-payments JSON, `Links` rewritten.
- **Errors:** the AIS errors above.
- **HSBC:** `GET /v4.0/aisp/accounts/{accountId}/scheduled-payments`

### GET /v1/ais/accounts/:accountId/statements

- **Auth:** session
- **Request:** no body.
- **Response:** `200` HSBC's statements JSON, `Links` rewritten.
- **Errors:** the AIS errors above.
- **HSBC:** `GET /v4.0/aisp/accounts/{accountId}/statements`

### GET /v1/ais/accounts/:accountId/statements/:statementId

- **Auth:** session
- **Request:** no body.
- **Response:** `200` HSBC's statement JSON, `Links` rewritten.
- **Errors:** the AIS errors above.
- **HSBC:** `GET /v4.0/aisp/accounts/{accountId}/statements/{statementId}`

### GET /v1/ais/accounts/:accountId/statements/:statementId/transactions

- **Auth:** session
- **Request:** optional query `fromBookingDateTime`, `toBookingDateTime`, as for account transactions
  (every path ending in `/transactions` passes them through).
- **Response:** `200` HSBC's statement transactions JSON, `Links` rewritten.
- **Errors:** the AIS errors above.
- **HSBC:** `GET /v4.0/aisp/accounts/{accountId}/statements/{statementId}/transactions`

### GET /v1/ais/accounts/:accountId/statements/:statementId/file

- **Auth:** session
- **Request:** `Accept: application/pdf` (the default when absent) or `text/csv`.
- **Response:** `200` the file bytes streamed from HSBC, with `content-type` set to the type asked for
  and `cache-control: no-store`. Not JSON.
- **Errors:** `406 not_acceptable` (neither type accepted) · `401 session_expired` · `bank_error` +
  `bankStatus` · `400 invalid_param`.
- **HSBC:** `GET /v4.0/aisp/accounts/{accountId}/statements/{statementId}/file` with the same `Accept`.

### GET /v1/ais/page

- **Auth:** session
- **Request:** query `cursor=<opaque>`, taken from a rewritten `Links` URL.
- **Response:** `200` the HSBC page the cursor stands for, `Links` rewritten again.
- **Errors:** `400 unknown_cursor` (missing, expired, or issued to another session) · `400 host_refused`
  (the stored URL is not https on the bank host under `/obie/open-banking/v4.0/aisp/`; cannot happen
  for a cursor the Worker issued) · the AIS errors above.
- **HSBC:** `GET` of the stored `Links` URL, always under `/v4.0/aisp/`.

## Payment initiation (PIS)

Six products, each with the same four routes, plus a funds check on the two immediate products.

| Product (`:product` below) | HSBC consent resource | HSBC payment resource |
|---|---|---|
| `domestic` | `domestic-payment-consents` | `domestic-payments` |
| `international` | `international-payment-consents` | `international-payments` |
| `domestic-scheduled` | `domestic-scheduled-payment-consents` | `domestic-scheduled-payments` |
| `international-scheduled` | `international-scheduled-payment-consents` | `international-scheduled-payments` |
| `domestic-standing-order` | `domestic-standing-order-consents` | `domestic-standing-orders` |
| `international-standing-order` | `international-standing-order-consents` | `international-standing-orders` |

A payment runs in three steps:

1. **Create the consent** (`POST /v1/consents/payment/<product>`). No session needed. The body must carry
   `Data.Initiation`. The response is `{flowId, consentId, authorizeUrl}`, like AIS.
2. **Authorise it** at HSBC, then `POST /v1/auth/complete` with the session header. The consent and its
   PSU token are stored on that session under the leg `payment:<product>:<consentId>`. HSBC's payment
   token lasts 5 minutes and has no refresh token.
3. **Submit the payment** (`POST /v1/payments/<product>`). `Data.ConsentId` must name a consent this
   session authorised **for this product**, otherwise `403 consent_not_owned` before any signing. The
   Worker records the returned payment id (`Data.*Id`) so only this session can read it back.

Errors common to the payment and funds routes:

- `401 psu_token_expired`: HSBC refused the 5-minute payment token. Authorise the consent again.
- `401 session_expired`: this session holds no token for that consent.
- `bank_error` + `bankStatus` + `bankError` (4xx keeps HSBC's status, anything else is 502).

### POST /v1/consents/payment/domestic

- **Auth:** rate limit (20 / 60 s) · attestation (android / ios)
- **Request:** OBIE v4.0 domestic payment consent body, compact JSON, ≤ 32 KB, `X-Idempotency-Key`
  required. Must include `Data.Initiation` and, on the HSBC sandbox, `RemittanceInformation.Structured`
  with a creditor reference.
- **Response:** `200 {"flowId","consentId","authorizeUrl"}`.
- **Errors:** the signed-body errors · `400 missing_initiation` · `400 web_origin_not_allowed` ·
  `bank_error` · the gate errors.
- **HSBC:** `POST /v4.0/pisp/domestic-payment-consents` on the client-credentials token (`payments`),
  with `x-jws-signature`.

### GET /v1/consents/payment/domestic/:consentId

- **Auth:** session
- **Request:** no body.
- **Response:** `200` HSBC's consent JSON.
- **Errors:** `403 consent_not_in_session` · `400 invalid_param` · `bank_error` + `bankStatus`.
- **HSBC:** `GET /v4.0/pisp/domestic-payment-consents/{consentId}` on the client-credentials token (`payments`).

### GET /v1/consents/payment/domestic/:consentId/funds-confirmation

- **Auth:** session
- **Request:** no body. Call after `/v1/auth/complete` and within the payment token's 5 minutes.
- **Response:** `200` HSBC's funds-confirmation JSON (`Data.FundsAvailableResult`).
- **Errors:** `403 consent_not_owned` · `400 invalid_param` · the payment errors above.
- **HSBC:** `GET /v4.0/pisp/domestic-payment-consents/{consentId}/funds-confirmation` on the PSU payment token.

### POST /v1/payments/domestic

- **Auth:** session
- **Request:** OBIE v4.0 domestic payment body (`Data.ConsentId` + `Data.Initiation`, identical to the
  consent's initiation), compact JSON, ≤ 32 KB, `X-Idempotency-Key` required.
- **Response:** HSBC's status (normally `201`) and JSON (`Data.DomesticPaymentId`, `Data.Status`).
- **Errors:** the signed-body errors · `400 invalid_request` (no valid `Data.ConsentId`) ·
  `400 missing_initiation` · `403 consent_not_owned` · the payment errors above.
- **HSBC:** `POST /v4.0/pisp/domestic-payments` on the PSU payment token, with `x-jws-signature`.

### GET /v1/payments/domestic/:paymentId

- **Auth:** session
- **Request:** no body.
- **Response:** `200` HSBC's payment JSON (status).
- **Errors:** `403 payment_not_in_session` (not submitted by this session) · `400 invalid_param` ·
  `bank_error` + `bankStatus`.
- **HSBC:** `GET /v4.0/pisp/domestic-payments/{paymentId}` on the client-credentials token (`payments`).

### POST /v1/consents/payment/international

- **Auth:** rate limit (20 / 60 s) · attestation (android / ios)
- **Request:** OBIE v4.0 international payment consent body, compact JSON, ≤ 32 KB, `X-Idempotency-Key`
  required, `Data.Initiation` required.
- **Response:** `200 {"flowId","consentId","authorizeUrl"}`.
- **Errors:** the signed-body errors · `400 missing_initiation` · `400 web_origin_not_allowed` ·
  `bank_error` · the gate errors.
- **HSBC:** `POST /v4.0/pisp/international-payment-consents` on the client-credentials token (`payments`),
  with `x-jws-signature`.

### GET /v1/consents/payment/international/:consentId

- **Auth:** session
- **Request:** no body.
- **Response:** `200` HSBC's consent JSON.
- **Errors:** `403 consent_not_in_session` · `400 invalid_param` · `bank_error` + `bankStatus`.
- **HSBC:** `GET /v4.0/pisp/international-payment-consents/{consentId}` on the client-credentials token (`payments`).

### GET /v1/consents/payment/international/:consentId/funds-confirmation

- **Auth:** session
- **Request:** no body. Call after `/v1/auth/complete` and within the payment token's 5 minutes.
- **Response:** `200` HSBC's funds-confirmation JSON.
- **Errors:** `403 consent_not_owned` · `400 invalid_param` · the payment errors above.
- **HSBC:** `GET /v4.0/pisp/international-payment-consents/{consentId}/funds-confirmation` on the PSU payment token.

### POST /v1/payments/international

- **Auth:** session
- **Request:** OBIE v4.0 international payment body (`Data.ConsentId` + `Data.Initiation`), compact JSON,
  ≤ 32 KB, `X-Idempotency-Key` required.
- **Response:** HSBC's status (normally `201`) and JSON (`Data.InternationalPaymentId`).
- **Errors:** the signed-body errors · `400 invalid_request` · `400 missing_initiation` ·
  `403 consent_not_owned` · the payment errors above.
- **HSBC:** `POST /v4.0/pisp/international-payments` on the PSU payment token, with `x-jws-signature`.

### GET /v1/payments/international/:paymentId

- **Auth:** session
- **Request:** no body.
- **Response:** `200` HSBC's payment JSON.
- **Errors:** `403 payment_not_in_session` · `400 invalid_param` · `bank_error` + `bankStatus`.
- **HSBC:** `GET /v4.0/pisp/international-payments/{paymentId}` on the client-credentials token (`payments`).

### POST /v1/consents/payment/domestic-scheduled

- **Auth:** rate limit (20 / 60 s) · attestation (android / ios)
- **Request:** OBIE v4.0 domestic scheduled payment consent body, compact JSON, ≤ 32 KB,
  `X-Idempotency-Key` required, `Data.Initiation` required.
- **Response:** `200 {"flowId","consentId","authorizeUrl"}`.
- **Errors:** the signed-body errors · `400 missing_initiation` · `400 web_origin_not_allowed` ·
  `bank_error` · the gate errors.
- **HSBC:** `POST /v4.0/pisp/domestic-scheduled-payment-consents` on the client-credentials token
  (`payments`), with `x-jws-signature`.

### GET /v1/consents/payment/domestic-scheduled/:consentId

- **Auth:** session
- **Request:** no body.
- **Response:** `200` HSBC's consent JSON.
- **Errors:** `403 consent_not_in_session` · `400 invalid_param` · `bank_error` + `bankStatus`.
- **HSBC:** `GET /v4.0/pisp/domestic-scheduled-payment-consents/{consentId}` on the client-credentials token (`payments`).

### POST /v1/payments/domestic-scheduled

- **Auth:** session
- **Request:** OBIE v4.0 domestic scheduled payment body (`Data.ConsentId` + `Data.Initiation`), compact
  JSON, ≤ 32 KB, `X-Idempotency-Key` required.
- **Response:** HSBC's status (normally `201`) and JSON (`Data.DomesticScheduledPaymentId`).
- **Errors:** the signed-body errors · `400 invalid_request` · `400 missing_initiation` ·
  `403 consent_not_owned` · the payment errors above.
- **HSBC:** `POST /v4.0/pisp/domestic-scheduled-payments` on the PSU payment token, with `x-jws-signature`.

### GET /v1/payments/domestic-scheduled/:paymentId

- **Auth:** session
- **Request:** no body.
- **Response:** `200` HSBC's payment JSON.
- **Errors:** `403 payment_not_in_session` · `400 invalid_param` · `bank_error` + `bankStatus`.
- **HSBC:** `GET /v4.0/pisp/domestic-scheduled-payments/{paymentId}` on the client-credentials token (`payments`).

### POST /v1/consents/payment/international-scheduled

- **Auth:** rate limit (20 / 60 s) · attestation (android / ios)
- **Request:** OBIE v4.0 international scheduled payment consent body, compact JSON, ≤ 32 KB,
  `X-Idempotency-Key` required, `Data.Initiation` required.
- **Response:** `200 {"flowId","consentId","authorizeUrl"}`.
- **Errors:** the signed-body errors · `400 missing_initiation` · `400 web_origin_not_allowed` ·
  `bank_error` · the gate errors.
- **HSBC:** `POST /v4.0/pisp/international-scheduled-payment-consents` on the client-credentials token
  (`payments`), with `x-jws-signature`.

### GET /v1/consents/payment/international-scheduled/:consentId

- **Auth:** session
- **Request:** no body.
- **Response:** `200` HSBC's consent JSON.
- **Errors:** `403 consent_not_in_session` · `400 invalid_param` · `bank_error` + `bankStatus`.
- **HSBC:** `GET /v4.0/pisp/international-scheduled-payment-consents/{consentId}` on the client-credentials token (`payments`).

### POST /v1/payments/international-scheduled

- **Auth:** session
- **Request:** OBIE v4.0 international scheduled payment body (`Data.ConsentId` + `Data.Initiation`),
  compact JSON, ≤ 32 KB, `X-Idempotency-Key` required.
- **Response:** HSBC's status (normally `201`) and JSON (`Data.InternationalScheduledPaymentId`).
- **Errors:** the signed-body errors · `400 invalid_request` · `400 missing_initiation` ·
  `403 consent_not_owned` · the payment errors above.
- **HSBC:** `POST /v4.0/pisp/international-scheduled-payments` on the PSU payment token, with `x-jws-signature`.

### GET /v1/payments/international-scheduled/:paymentId

- **Auth:** session
- **Request:** no body.
- **Response:** `200` HSBC's payment JSON.
- **Errors:** `403 payment_not_in_session` · `400 invalid_param` · `bank_error` + `bankStatus`.
- **HSBC:** `GET /v4.0/pisp/international-scheduled-payments/{paymentId}` on the client-credentials token (`payments`).

### POST /v1/consents/payment/domestic-standing-order

- **Auth:** rate limit (20 / 60 s) · attestation (android / ios)
- **Request:** OBIE v4.0 domestic standing order consent body, compact JSON, ≤ 32 KB,
  `X-Idempotency-Key` required, `Data.Initiation` required.
- **Response:** `200 {"flowId","consentId","authorizeUrl"}`.
- **Errors:** the signed-body errors · `400 missing_initiation` · `400 web_origin_not_allowed` ·
  `bank_error` · the gate errors.
- **HSBC:** `POST /v4.0/pisp/domestic-standing-order-consents` on the client-credentials token
  (`payments`), with `x-jws-signature`.

### GET /v1/consents/payment/domestic-standing-order/:consentId

- **Auth:** session
- **Request:** no body.
- **Response:** `200` HSBC's consent JSON.
- **Errors:** `403 consent_not_in_session` · `400 invalid_param` · `bank_error` + `bankStatus`.
- **HSBC:** `GET /v4.0/pisp/domestic-standing-order-consents/{consentId}` on the client-credentials token (`payments`).

### POST /v1/payments/domestic-standing-order

- **Auth:** session
- **Request:** OBIE v4.0 domestic standing order body (`Data.ConsentId` + `Data.Initiation`), compact JSON,
  ≤ 32 KB, `X-Idempotency-Key` required.
- **Response:** HSBC's status (normally `201`) and JSON (`Data.DomesticStandingOrderId`).
- **Errors:** the signed-body errors · `400 invalid_request` · `400 missing_initiation` ·
  `403 consent_not_owned` · the payment errors above.
- **HSBC:** `POST /v4.0/pisp/domestic-standing-orders` on the PSU payment token, with `x-jws-signature`.

### GET /v1/payments/domestic-standing-order/:paymentId

- **Auth:** session
- **Request:** no body.
- **Response:** `200` HSBC's standing order JSON.
- **Errors:** `403 payment_not_in_session` · `400 invalid_param` · `bank_error` + `bankStatus`.
- **HSBC:** `GET /v4.0/pisp/domestic-standing-orders/{paymentId}` on the client-credentials token (`payments`).

### POST /v1/consents/payment/international-standing-order

- **Auth:** rate limit (20 / 60 s) · attestation (android / ios)
- **Request:** OBIE v4.0 international standing order consent body, compact JSON, ≤ 32 KB,
  `X-Idempotency-Key` required, `Data.Initiation` required.
- **Response:** `200 {"flowId","consentId","authorizeUrl"}`.
- **Errors:** the signed-body errors · `400 missing_initiation` · `400 web_origin_not_allowed` ·
  `bank_error` · the gate errors.
- **HSBC:** `POST /v4.0/pisp/international-standing-order-consents` on the client-credentials token
  (`payments`), with `x-jws-signature`.

### GET /v1/consents/payment/international-standing-order/:consentId

- **Auth:** session
- **Request:** no body.
- **Response:** `200` HSBC's consent JSON.
- **Errors:** `403 consent_not_in_session` · `400 invalid_param` · `bank_error` + `bankStatus`.
- **HSBC:** `GET /v4.0/pisp/international-standing-order-consents/{consentId}` on the client-credentials token (`payments`).

### POST /v1/payments/international-standing-order

- **Auth:** session
- **Request:** OBIE v4.0 international standing order body (`Data.ConsentId` + `Data.Initiation`),
  compact JSON, ≤ 32 KB, `X-Idempotency-Key` required.
- **Response:** HSBC's status (normally `201`) and JSON (`Data.InternationalStandingOrderId`).
- **Errors:** the signed-body errors · `400 invalid_request` · `400 missing_initiation` ·
  `403 consent_not_owned` · the payment errors above.
- **HSBC:** `POST /v4.0/pisp/international-standing-orders` on the PSU payment token, with `x-jws-signature`.

### GET /v1/payments/international-standing-order/:paymentId

- **Auth:** session
- **Request:** no body.
- **Response:** `200` HSBC's standing order JSON.
- **Errors:** `403 payment_not_in_session` · `400 invalid_param` · `bank_error` + `bankStatus`.
- **HSBC:** `GET /v4.0/pisp/international-standing-orders/{paymentId}` on the client-credentials token (`payments`).

## Variable recurring payments (VRP)

The consent is created and authorised like a payment consent, and stored on the session under the leg
`vrp:<consentId>`. Unlike a single payment, `/v1/auth/complete` requires a refresh token for this leg
(`502 token_exchange_failed` otherwise), and the Worker refreshes the VRP token on an HSBC `401`, so
the consent can be used until it expires. The funds check is a signed POST on the PSU token (HSBC's
VRP API), not a GET.

### POST /v1/consents/vrp

- **Auth:** rate limit (20 / 60 s) · attestation (android / ios)
- **Request:** OBIE v4.0 domestic VRP consent body (`Data.ControlParameters`, `Data.Initiation`),
  compact JSON, ≤ 32 KB, `X-Idempotency-Key` required.
- **Response:** `200 {"flowId","consentId","authorizeUrl"}`.
- **Errors:** the signed-body errors · `400 missing_initiation` · `400 web_origin_not_allowed` ·
  `bank_error` · the gate errors.
- **HSBC:** `POST /v4.0/pisp/domestic-vrp-consents` on the client-credentials token (`payments`), with `x-jws-signature`.

### GET /v1/consents/vrp/:consentId

- **Auth:** session
- **Request:** no body.
- **Response:** `200` HSBC's VRP consent JSON.
- **Errors:** `403 consent_not_in_session` · `400 invalid_param` · `bank_error` + `bankStatus`.
- **HSBC:** `GET /v4.0/pisp/domestic-vrp-consents/{consentId}` on the client-credentials token (`payments`).

### DELETE /v1/consents/vrp/:consentId

- **Auth:** session
- **Request:** no body.
- **Response:** `204`. The VRP consent is revoked at HSBC. The session's stored VRP token is not removed;
  HSBC refuses it from then on.
- **Errors:** `403 consent_not_in_session` · `400 invalid_param` · `bank_error` + `bankStatus`.
- **HSBC:** `DELETE /v4.0/pisp/domestic-vrp-consents/{consentId}` on the client-credentials token (`payments`).

### POST /v1/consents/vrp/:consentId/funds-confirmation

- **Auth:** session · attestation (android / ios)
- **Request:** OBIE v4.0 VRP funds-confirmation body; `Data.ConsentId` must equal `:consentId`. Compact
  JSON, ≤ 16 KB, `X-Idempotency-Key` required.
- **Response:** HSBC's status and funds-confirmation JSON.
- **Errors:** the signed-body errors · `400 invalid_request` · `403 consent_not_owned` (body and path
  name different consents, or not this session's consent) · `401 session_expired` (no VRP token, or the
  refresh was refused) · `401 psu_token_expired` · `bank_error` · the gate errors.
- **HSBC:** `POST /v4.0/pisp/domestic-vrp-consents/{consentId}/funds-confirmation` on the PSU VRP token,
  with `x-jws-signature`.

### POST /v1/vrp/payments

- **Auth:** session
- **Request:** OBIE v4.0 domestic VRP body (`Data.ConsentId` + `Data.Initiation` + `Data.Instruction`),
  compact JSON, ≤ 32 KB, `X-Idempotency-Key` required (a new key per payment).
- **Response:** HSBC's status (normally `201`) and JSON (`Data.DomesticVRPId`).
- **Errors:** the signed-body errors · `400 invalid_request` · `400 missing_initiation` ·
  `403 consent_not_owned` · `401 session_expired` · `401 psu_token_expired` · `bank_error`.
- **HSBC:** `POST /v4.0/pisp/domestic-vrps` on the PSU VRP token, with `x-jws-signature`. A refresh
  retry reuses the same signature.

### GET /v1/vrp/payments/:paymentId

- **Auth:** session
- **Request:** no body.
- **Response:** `200` HSBC's VRP payment JSON.
- **Errors:** `403 payment_not_in_session` · `400 invalid_param` · `bank_error` + `bankStatus`.
- **HSBC:** `GET /v4.0/pisp/domestic-vrps/{paymentId}` on the client-credentials token (`payments`).
