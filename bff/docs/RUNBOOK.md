# Runbook

Operating the Worker `mifos-x-open-banking-bff`. Commands run from the app repository root unless they
say otherwise. Every credential comes from the claude-product-cycle framework vault; never paste a
secret value into a terminal, chat or file.

| | |
|---|---|
| Worker URL | `https://mifos-x-open-banking-bff.mifos-x-open-banking-bff.workers.dev` |
| Cloudflare account | Mifos (vault alias `mifos-x-cloudflare-account-id`) |
| Bank | HSBC UK Open Banking sandbox (`secure.sandbox.ob.hsbc.co.uk`, `sandbox.ob.hsbc.co.uk`) |
| Relay | `https://mifos-x-open-banking-relay.pages.dev` (Cloudflare Pages, see `relay/README.md`) |

## 1. First-time setup

1. **Tools:** Node 22.12 or newer (vitest 5; CI uses Node 24), `npm`, `openssl`, `curl`. Wrangler comes
   from `npx wrangler@4`; nothing global is needed.
2. **Dependencies:** `cd bff && npm ci`.
3. **Framework checkout.** `scripts/deploy.sh` and `scripts/cpu-report.sh` walk up from `bff/` until they
   find `core/scripts/secrets-get.sh`, so the app repository must sit inside a claude-product-cycle
   checkout (`workspaces/mifos-x/mifos-x-open-banking/source/mifos-x-open-banking`) whose user has vault
   access.
4. **Vault aliases** the deploy reads:

   | Alias | Used for |
   |---|---|
   | `mifos-x-open-banking-cloudflare-bff-token` | Cloudflare API token for the Worker |
   | `mifos-x-cloudflare-account-id` | Cloudflare account |
   | `mifos-x-open-banking-hsbc-private-key` | `HSBC_SIGNING_KEY_PEM` (must be PKCS#8, `BEGIN PRIVATE KEY`) |
   | `mifos-x-open-banking-hsbc-transport-p12`, `…-transport-p12-password` | the mTLS certificate |
   | `mifos-x-open-banking-hsbc-client-id`, `…-hsbc-kid`, `…-hsbc-software-statement` | `HSBC_CLIENT_ID`, `HSBC_KID`, `HSBC_SOFTWARE_STATEMENT` |
   | `mifos-x-open-banking-hsbc-signing-issuer` | `HSBC_SIGNING_ISSUER` — the signing certificate's subject DN (RFC 2253) |
   | `mifos-x-open-banking-play-integrity-sa`, `mifos-x-open-banking-apple-team-id` | optional until hand-off (§7) |

5. **Cloudflare token scopes.** The Worker token (`mifos-x-open-banking-cloudflare-bff-token`) needs, on
   the Mifos account only: **Workers Scripts: Edit**, **SSL and Certificates: Edit** (mTLS certificate
   upload), **Account Analytics: Read** (`cpu-report.sh --since`) and **Account Settings: Read**. It has
   no Pages scope. The relay deploy uses a different token, `mifos-x-cloudflare-api-token`, with
   **Cloudflare Pages: Edit** (§6).
6. **Dry run:** `bash bff/scripts/deploy.sh --dry-run` resolves every alias, checks the signing key is
   PKCS#8 and that the PKCS#12 opens with its password, then stops before touching Cloudflare.

To run any other `wrangler` command by hand, give it the vaulted credentials for that one command:

```bash
# from the framework root
TF=$(bash core/scripts/secrets-get.sh mifos-x-open-banking-cloudflare-bff-token --secure-temp)
AF=$(bash core/scripts/secrets-get.sh mifos-x-cloudflare-account-id --secure-temp)
cd workspaces/mifos-x/mifos-x-open-banking/source/mifos-x-open-banking/bff
CLOUDFLARE_API_TOKEN="$(cat "$TF")" CLOUDFLARE_ACCOUNT_ID="$(cat "$AF")" npx wrangler@4 deployments list
rm -f "$TF" "$AF"
```

## 2. Deploy

```bash
cd bff && npx tsc --noEmit && npx vitest run    # must be green first
bash scripts/deploy.sh                          # [--dry-run] [--no-smoke]
```

`deploy.sh` (`--env` is accepted and ignored; there is one environment):

1. Resolves the nine required aliases into a `0700` temp directory (shredded on exit), plus the two
   attestation aliases if present. A missing attestation alias is skipped and named:
   `⚠ attestation credentials not in the vault, skipped: PLAY_INTEGRITY_SA_JSON APPLE_TEAM_ID`.
2. Checks the signing key is PKCS#8.
3. Splits the PKCS#12 into a certificate and a key, re-encoded through `openssl x509` / `openssl pkey`
   (retrying with `-legacy`). The password is read from a file, never from the command line.
4. Looks up the Cloudflare mTLS certificate named `mifos-x-open-banking-hsbc-transport`; uploads it if
   absent; writes its id into `wrangler.toml` (`certificate_id`).
5. `wrangler deploy`, then reads the `workers.dev` URL from the output.
6. Sets all Worker secrets in one `wrangler secret bulk` call from a `0600` JSON file.
7. Runs `scripts/smoke.sh` against the URL (skip with `--no-smoke`).

Nothing secret is printed or passed on a command line. If `wrangler.toml` shows a changed
`certificate_id` afterwards, commit it (it is a public id).

## 3. Verify

```bash
BFF_WORKER_URL=https://mifos-x-open-banking-bff.mifos-x-open-banking-bff.workers.dev bash bff/scripts/smoke.sh
curl -sS -D - https://mifos-x-open-banking-bff.mifos-x-open-banking-bff.workers.dev/v1/health
```

- `smoke.sh` exits 0 when `GET /v1/health` is `200` (body `{"status":"ok"}`, header `x-request-id`).
- Live logs: `npx wrangler@4 tail mifos-x-open-banking-bff --format pretty` (credentials as in §1). Each
  request is one JSON line with `requestId`, `route`, `status`, `ms` and, on attested routes,
  `attestation`.
- A consent from the app (or `POST /v1/consents/ais` with a permissions body) should answer `200` with
  an `authorizeUrl` on `sandbox.ob.hsbc.co.uk`. That proves the mTLS binding, the client-credentials
  grant and the signer in one call.
- CPU: `bash bff/scripts/cpu-report.sh --since 24h` (§9).
- Bundle size: `bash bff/scripts/bundle-size.sh` (fails at 950 KB).

## 4. Rollback

- **Worker:** list versions, then roll back (credentials as in §1):
  ```bash
  npx wrangler@4 deployments list
  npx wrangler@4 rollback            # previous version, or: npx wrangler@4 rollback <version-id>
  ```
  Then run §3. Check `npx wrangler@4 secret list` still shows the expected secret names.
- **App:** revert the commit that cut the app over to the BFF (`git revert <sha>`) and ship the previous
  build. Be aware that the pre-cutover app carried the HSBC identity on the device; prefer fixing forward
  on the Worker.
- **Relay:** `relay/README.md` (Pages deployment rollback).

## 5. Rotate a secret or certificate

Procedure only; not performed so far.

**Signing key** (`HSBC_SIGNING_KEY_PEM`, with `HSBC_KID` and possibly `HSBC_SIGNING_ISSUER`):

1. Create the new key and certificate and register them with HSBC DevHub, so HSBC publishes the new
   `kid` alongside the old one.
2. Push the new PKCS#8 key, kid and the new certificate's subject DN to the vault with `/secrets handoff`
   (macOS: Keychain path first). Never paste them into chat.
3. `bash bff/scripts/deploy.sh` — the secret bulk call replaces the values.
4. §3, plus one consent creation and one payment consent (both sign).
5. Revoke the old key at HSBC DevHub (§10).

**Transport certificate** (`mifos-x-open-banking-hsbc-transport-p12` + password):

1. Issue the new transport certificate at HSBC DevHub; push the PKCS#12 and its password to the vault.
2. `deploy.sh` **reuses** a Cloudflare certificate by name, so it will not pick up a new PKCS#12 on its
   own. Remove the old entry first (`npx wrangler@4 mtls-certificate list`, then
   `npx wrangler@4 mtls-certificate delete --name mifos-x-open-banking-hsbc-transport`). If Cloudflare
   refuses because the certificate is still bound, change `CERT_NAME` in `scripts/deploy.sh` to a new
   name instead.
3. `bash bff/scripts/deploy.sh` uploads the new certificate and writes its id into `wrangler.toml`;
   commit that line.
4. §3, then revoke the old certificate at HSBC DevHub.

**Software statement, client id:** push to the vault, deploy, verify.

**Cloudflare token:** create a new token with the scopes in §1, push it to the vault under the same
alias, run `deploy.sh --dry-run`, then revoke the old token in the Cloudflare dashboard.

After any suspected exposure, rotate with a reason
(`/secrets rotate <alias> --reason "exposed-in-chat-<date>"`) and treat the session as compromised.

## 6. Deploy the relay

The relay is a separate Cloudflare Pages project (`mifos-x-open-banking-relay`, production branch `dev`).
Its deploy uses `mifos-x-cloudflare-api-token` (Pages: Edit); the Worker token is refused there with
`Authentication error [code: 10000]`. Steps, tests and rollback are in `relay/README.md`.

## 7. Hand off the attestation credentials and enforce

The Worker ships `ATTESTATION_MODE = "report-only"` because two credentials are pending. To enforce:

1. **Hand off** both values through the vault (never chat): `/secrets handoff` for
   `mifos-x-open-banking-play-integrity-sa` (the Google service account JSON with Play Integrity access)
   and `mifos-x-open-banking-apple-team-id`.
2. **Deploy** with `bash bff/scripts/deploy.sh`. It must print
   `✓ resolved attestation credentials (PLAY_INTEGRITY_SA_JSON, APPLE_TEAM_ID)`.
3. **Ship app builds that attest.** Android sends no Play Integrity token until
   `PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER` is set for the build (`local.properties`, env var or Gradle
   property; generated into `BffConfig` by `:core:network:generateBffConfig`). iOS registers its App
   Attest key at `POST /v1/attest/ios/register` on first use. Builds without these would be refused once
   enforcing.
4. **Check report-only first.** In Workers Logs or `wrangler tail`, Android and iOS requests to consent
   routes should show `attestation: {status: "verified"}`. Investigate every `unverified` (the `reason`
   field says why).
5. **Enforce:** set `ATTESTATION_MODE = "enforce"` in `bff/wrangler.toml`, update the test
   `wrangler.toml ships report-only while the credentials are pending` in `test/gate.test.ts` to expect
   the new value, run the tests, deploy.
6. Verify: an Android consent without `X-Attestation` now gets `401 attestation_missing`.

## 8. Add a web origin

A web app served from a new origin (for example a production host) needs three lists updated:

1. `bff/wrangler.toml` `WEB_RETURN_ORIGINS`: **append** the origin (comma-separated). Never reorder or
   remove an entry while a sign-in may be in flight; the index is baked into `state`.
2. `relay/site/callback/bridge.js` `WEB_RETURN_ORIGINS`: append the same origin at the same position.
   `relay/test/bridge.test.js` fails if the two lists differ.
3. `bff/wrangler.toml` `CORS_ORIGINS`: add the origin so the browser may call the Worker.

Then run both test suites (`cd bff && npx vitest run`, `node --test "relay/test/*.test.js"`), deploy the
Worker (§2), then the relay (§6).

## 9. Workers Free CPU limit and the Paid decision

Workers Free allows 10 ms CPU per request. Measure with:

```bash
bash bff/scripts/cpu-report.sh --since 24h            # Worker-wide p50/p99 (also 1h, 7d)
bash bff/scripts/cpu-report.sh --per-route --routes consents -- <command that generates traffic>
```

Both exit 5 when a p99 reaches 10 ms (`--limit-ms` changes the threshold).

**Decision recorded 2026-10-22: stay on Workers Free.** Warm signed requests fit (payment consent p99
7 ms, VRP consent 4 ms). The first signed consent on a fresh isolate pays for the key import plus two
signatures and measured 10 ms; the Worker-wide p99 has been 10–12 ms with 0 errors. Readings are in
[ARCHITECTURE.md § CPU budget](ARCHITECTURE.md#cpu-budget).

**Trigger for Workers Paid:** Cloudflare error **1102** (Worker exceeded CPU time limit) in Workers Logs,
or failed requests that the CPU report ties to a route. Then either:

- move the account to Workers Paid (dashboard: Workers & Pages → Plans). No code change; or
- split signed consent creation so each request signs once (return `{flowId, consentId}` first and the
  `authorizeUrl` from a second call). This needs a route change (§11) and an app change.

Record the readings and the choice in this section when it happens.

## 10. Revoke at HSBC DevHub

- **A retired signing key or transport certificate:** sign in to HSBC Open Banking DevHub, open the
  application, and revoke the old certificate. Do this only after the new one is deployed and verified
  (§5).
- **A single consent:** the app's sign-out revokes the AIS consent (`POST /v1/session/logout?revoke=true`),
  and `DELETE /v1/consents/ais/:consentId` / `DELETE /v1/consents/vrp/:consentId` revoke one consent.
  The customer can also revoke in HSBC's own channels.
- **Everything (compromise of the TPP identity):** revoke the certificates at DevHub first; every HSBC call
  from the Worker then fails, which is the intended stop. Rotate (§5) before redeploying.

## 11. Add a new route

1. Add the row to `ROUTES` in `src/routes.ts`: `method`, `path`, `hsbcOp`, `token`, `signJws`,
   `maxBodyBytes`, `attest`, `session`, `rateLimit` if it can be called without a session, `handler`,
   `product` for payments.
2. Reuse a handler in `src/handlers/` or add one and register it in `HANDLERS` (`src/index.ts`). Check
   ownership before any signature or HSBC call.
3. Tests: update the route-table pin in `test/router.test.ts` and the gating lists in `test/gate.test.ts`;
   add a happy path, at least one 4xx and the gate under `test/routes/`.
4. Add a `### METHOD /path` section to `docs/API.md` with **Auth**, **Request**, **Response**,
   **Errors** and **HSBC** lines. `test/docs.test.ts` fails until the section exists and its Auth line
   matches the row's flags.
5. Add the client call in `core/network` (the app).
6. Deploy (§2), then measure the route: `cpu-report.sh --per-route --routes <path> -- <traffic>`.

## 12. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Every HSBC call fails with an **empty 500** from the mTLS binding | The certificate/key were uploaded straight from `openssl pkcs12` output, which carries `Bag Attributes` text. Cloudflare accepts the upload but cannot present the certificate. | Re-encode through `openssl x509` / `openssl pkey` (what `deploy.sh` does) and upload again (§5 transport steps). |
| HSBC `UK.OBIE.Signature.InvalidClaim` on `http://openbanking.org.uk/iss` | `HSBC_SIGNING_ISSUER` is the OBIE `org_id/software_id` form. HSBC wants the signing certificate's subject DN (RFC 2253). | Put the DN in the vault alias, redeploy. |
| HSBC `U015 'x-jws-signature' header signature could not be verified` | The signed bytes were not compact JSON (pretty-printed, trailing newline). | Send compact JSON. The Worker refuses such bodies itself with `400 body_not_compact`; a U015 means the bytes changed after signing. |
| HSBC `U004` on a domestic payment consent | Missing `RemittanceInformation.Structured[0].CreditorReferenceInformation.Reference`. | Add the structured creditor reference to the consent and payment bodies. |
| HSBC `U002 … Unstructured \| Invalid value` | `RemittanceInformation.Unstructured` sent as a string. | Send an array of strings (OBIE v4). |
| `POST /v1/auth/complete` returns `500 internal` for every sign-in | OIDC discovery fetched from the wrong host. HSBC publishes discovery on the authorize host (`HSBC_AUTHORIZE_HOST`, `sandbox.ob.hsbc.co.uk`), not on the mTLS API host; its `issuer` must equal `HSBC_ISSUER`. | Check both vars in `wrangler.toml`; `loadHsbcOidc` in `src/crypto/idToken.ts` throws on a mismatch. |
| `deploy.sh`: `signing key is not PKCS#8` | The vaulted key is PKCS#1 (`BEGIN RSA PRIVATE KEY`). | Convert with `openssl pkcs8 -topk8 -nocrypt` and push the result to the vault. |
| `deploy.sh`: `cannot open transport.p12 with the vaulted password` | Wrong password alias, or a PKCS#12 that even `-legacy` cannot read. | Re-hand-off the pair. |
| `503 attestation_unconfigured` | Enforce mode without the platform's credential. | §7, or keep `report-only`. |
| Web sign-in returns `400 web_origin_not_allowed` | The page's origin is not in `WEB_RETURN_ORIGINS`. | §8. |
| Browser preflight gets `404` | Origin not in `CORS_ORIGINS`, or the route is not in the table. | §8 / §11. |
| `401 session_expired` on AIS reads | HSBC refused the refresh (consent expired or revoked). | The user signs in again. |
| `401 psu_token_expired` on a payment | The 5-minute payment token ran out. | Authorise the payment consent again. |
| `cpu-report.sh` exits 5 | A p99 reached 10 ms. | §9. |
