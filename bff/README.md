# mifos-x-open-banking-bff

A Cloudflare Worker (TypeScript) that holds the HSBC TPP identity for Mifos X Open Banking.

The Android, iOS, desktop and web apps used to carry the HSBC signing key, the mTLS transport
certificate and the TPP identifiers inside the app, and kept HSBC tokens on the device. Anything shipped
in an app can be extracted from it, so this Worker now does that work instead:

- it alone presents the HSBC transport certificate (a Cloudflare mTLS certificate binding);
- it alone signs every PS256 artefact: client assertions, request objects, detached JWS on payments;
- it verifies the `id_token`, exchanges the authorisation code, refreshes tokens, and keeps every HSBC
  token in Durable Objects;
- the app holds one opaque session id and calls a closed list of routes over plain HTTPS.

Deployed at `https://mifos-x-open-banking-bff.mifos-x-open-banking-bff.workers.dev` against the HSBC UK
Open Banking sandbox.

## Layout

```
bff/
├── src/
│   ├── index.ts              fetch entry: CORS, gates (rate limit → session → attestation), request id, redacted log
│   ├── routes.ts             the closed route table (56 rows) + bankPath / payLeg
│   ├── router.ts             method + path match, 404 / 413 / 415
│   ├── gate.ts               Authorization: Session → SessionDO
│   ├── limits.ts             rate limits (binding, or limits/RateLimitCounterDO.ts)
│   ├── env.ts, http.ts       bindings / vars / secrets; JSON helpers
│   ├── handlers/             consentAis, authComplete, aisRead, aisPage, aisStatementFile, payment, sessionLogout, attest
│   ├── crypto/               signer, clientAssertion, requestObject, detachedJws, idToken, base64url
│   ├── hsbc/                 client.ts (mTLS fetch, token grants, refresh), pagination.ts
│   ├── session/              SessionDO, TokenCacheDO
│   └── attest/               gate, Play Integrity, App Attest, AttestationDO, Apple root CA
├── test/                     vitest suites (run inside workerd) + fixtures
├── scripts/                  deploy.sh, smoke.sh, cpu-report.sh, bundle-size.sh
├── docs/                     ARCHITECTURE, API, SECURITY, RUNBOOK
└── wrangler.toml             Worker, mTLS binding, vars, Durable Objects, rate limit — no secrets
```

## Prerequisites

- Node 22.12 or newer and `npm` (CI uses Node 24). Wrangler 4 is a dev dependency and is also run as
  `npx wrangler@4` by the scripts.
- For deploy and CPU reports only: this repository checked out inside the claude-product-cycle framework
  (`workspaces/mifos-x/mifos-x-open-banking/source/mifos-x-open-banking`) by a user with vault access,
  plus `openssl` and `curl`. Tests need neither.

## Test

```bash
cd bff
npm ci
npx tsc --noEmit
npx vitest run
```

`@cloudflare/vitest-plugin` runs the suites inside workerd with the real Durable Object bindings from
`wrangler.toml`. The HSBC mTLS binding is replaced by a fake fetcher (`test/fixtures/fakeMtls.ts`),
since a client certificate cannot be presented in local tests; the live mTLS path is proven by a deploy
and a consent call. `test/docs.test.ts` fails if `docs/API.md` and `src/routes.ts` disagree. CI runs the
same three commands (`.github/workflows/bff.yml`).

## Deploy

```bash
bash bff/scripts/deploy.sh --dry-run    # resolve and check every credential, change nothing
bash bff/scripts/deploy.sh              # deploy; --no-smoke skips the health check
```

`deploy.sh` reads every credential from the vault into a temp directory that is shredded on exit,
splits the transport PKCS#12 and uploads it as a Cloudflare mTLS certificate (reused by name if already
there, its public id written into `wrangler.toml`), runs `wrangler deploy`, sets all Worker secrets in
one `wrangler secret bulk` call, and runs `smoke.sh`. It never prints a secret or puts one on a command
line. Details: [docs/RUNBOOK.md §2](docs/RUNBOOK.md#2-deploy).

## Smoke

```bash
BFF_WORKER_URL=https://mifos-x-open-banking-bff.mifos-x-open-banking-bff.workers.dev bash bff/scripts/smoke.sh
```

Checks `GET /v1/health` returns `200`.

## CPU report

```bash
bash bff/scripts/cpu-report.sh --since 24h                         # Worker-wide p50 / p99 (1h, 24h, 7d)
bash bff/scripts/cpu-report.sh --per-route -- <traffic command>    # p99 per route via wrangler tail
bash bff/scripts/bundle-size.sh                                    # fails at 950 KB
```

`cpu-report.sh` exits 5 when a p99 reaches 10 ms, the Workers Free limit. The decision for that case
is in [docs/RUNBOOK.md §9](docs/RUNBOOK.md#9-workers-free-cpu-limit-and-the-paid-decision).

## Documentation

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md): components, data flows, Durable Objects, gating order,
  Workers Free CPU rules and readings.
- [docs/API.md](docs/API.md): every route, headers, error envelope, signed-body rules.
- [docs/SECURITY.md](docs/SECURITY.md): threat model, key custody, session model, attestation and its
  residual risk, what is never returned or logged.
- [docs/RUNBOOK.md](docs/RUNBOOK.md): setup, deploy, verify, rollback, rotation, attestation hand-off,
  web origins, CPU decision, revocation, adding a route, troubleshooting.

License: MPL-2.0, as the rest of the app.
