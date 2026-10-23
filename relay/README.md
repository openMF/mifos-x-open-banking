# Consent relay

The HTTPS page HSBC redirects to after the customer authorises a consent. HSBC uses the OIDC hybrid
flow, so the response (`code`, `id_token`, `state`) arrives in the URL **fragment**, which only browser
JavaScript can read. This page reads it and hands it to the app:

- **Desktop** — `http://127.0.0.1:8765/callback?…` (the desktop app's `ConsentRedirectListener`)
- **Mobile** — `org.mifosx.openbanking://callback?…` (Android intent-filter / iOS URL scheme), used only
  when no desktop listener answers
- **Web** — `<origin>/#mifos-callback?…` (cmp-web), used only when `state` starts with `w<index>.` and
  `<index>` is an entry of `WEB_RETURN_ORIGINS` (see below). No loopback probe, no custom scheme.

It holds **no secrets** and stores nothing.

The relay only carries `code`, `id_token` and `state` back to the app. The app sends them to the
Mifos X Open Banking Worker at `POST /v1/auth/complete` ([`bff/`](../bff/README.md)), which verifies the
`id_token` and exchanges the code with HSBC's token endpoint over mTLS. The relay never talks to HSBC or
to the Worker.

| | |
|---|---|
| Live URL (production) | https://mifos-x-open-banking-relay.pages.dev |
| Redirect URI to register with HSBC | `https://mifos-x-open-banking-relay.pages.dev/callback/` |
| Hosting | Cloudflare Pages, project `mifos-x-open-banking-relay`, production branch `dev` |
| Cloudflare account | Mifos (`Mobileapptech@mifos.org`'s account) |
| License | MPL-2.0 |

## Layout

```
relay/
├── site/                     ← exactly what is deployed
│   ├── index.html            ← public root page: what the relay does, data handling, source, licence, security contact
│   ├── privacy/index.html    ← privacy policy (dated; update the date + text if the relay's data handling changes)
│   ├── terms/index.html      ← terms of use (dated)
│   ├── site.css              ← shared styles for the public pages (external — CSP has no 'unsafe-inline')
│   ├── assets/               ← Mifos logo (light + dark) and the X mark used as favicon — self-hosted, img-src 'self'
│   ├── _headers              ← security headers for every response
│   └── callback/
│       ├── index.html        ← the redirect target — no inline script
│       ├── bridge.js         ← pure helpers: readParams / appUrl / loopbackUrl / webReturnUrl + constants
│       ├── relay.js          ← the flow: history scrub → web return | loopback probe → mobile hand-off
│       └── relay.css
├── test/bridge.test.js       ← node:test, no dependencies
└── wrangler.toml             ← Pages project name + output dir (no account ID, no token)
```

## Test

```bash
node --test "relay/test/*.test.js"
```

Covers parameter parsing, both hand-offs, history scrubbing, the no-inline-script rule (needed for the
CSP) and the `_headers` policy. Run it before every deploy.

## Deploy

Credentials come from the vault, never from this repo. The token needs **Account → Cloudflare Pages → Edit**.

```bash
# from the claude-product-cycle framework root (vault access)
TF=$(bash core/scripts/secrets-get.sh mifos-x-cloudflare-api-token --secure-temp)

# from relay/ — run here, not the framework root: a .wrangler/ cache elsewhere can pin a different account
cd <app-repo>/relay
CLOUDFLARE_API_TOKEN="$(cat "$TF")" \
CLOUDFLARE_ACCOUNT_ID=ff801fbaae66bbf57ebc0595a5b8b85d \
  wrangler pages deploy site --project-name mifos-x-open-banking-relay --branch dev
rm -f "$TF"
```

- `--branch dev` publishes to production. Any other branch name makes a preview URL.
- Rollback: `wrangler pages deployment list --project-name mifos-x-open-banking-relay`, then
  `wrangler pages deployment rollback <id> --project-name mifos-x-open-banking-relay`.

Forking this project? Create your own Pages project, deploy `site/`, and register **your** URL with HSBC
(the Worker's `HSBC_REDIRECT_URI` must match it byte-for-byte).

## Web return

A browser tab has no loopback listener and no custom scheme, so the web app is identified by `state`:

1. The web app starts a consent with `X-Client-Platform: web`; the browser adds its `Origin`.
2. The Worker accepts it only if that origin is in its `WEB_RETURN_ORIGINS` var (ordered, comma-separated,
   `bff/wrangler.toml`), and prefixes the `state` it signs with `w<index>.` — the origin's position in the
   list. Any other web origin gets `400 {"error":"web_origin_not_allowed"}`. Other platforms are unchanged.
3. HSBC echoes `state`. The relay scrubs history as usual, then `location.replace(origin + "/#mifos-callback?" + params)`
   with `origin = WEB_RETURN_ORIGINS[index]` from **its own copy** in `bridge.js`. An index outside the list
   is ignored and the desktop/mobile flow runs. The URL never names the origin, so the relay can never be
   pointed at a host that is not listed.
4. The web app reads `#mifos-callback?…` on load, hands it to the same `ConsentRedirectBus` as the other
   platforms, and clears the hash. The parameters stay in the fragment, so the web server never sees them.
   The app then completes the sign-in at the Worker's `POST /v1/auth/complete`, as on every platform.

The Worker also has to allow the web origin for CORS (`CORS_ORIGINS` in `bff/wrangler.toml`); see
[`bff/docs/RUNBOOK.md` §8](../bff/docs/RUNBOOK.md#8-add-a-web-origin).

The two lists must match entry for entry and in order (the relay test reads `bff/wrangler.toml` and fails
otherwise). Adding a web host: append it to both, deploy the Worker, deploy the relay. Never reorder or
remove an entry while a sign-in may be in flight. CSP is unchanged: a top-level navigation is not governed
by `connect-src`.

## Changing the scheme or loopback port

`bridge.js` `SCHEME` and `LOOPBACK_PORT` must match the app exactly. See the table in the root
[README](../README.md#2-handle-the-oauth-redirect-consent-callback). The test pins both values.

## Security

- **Headers** (`site/_headers`):
  - strict CSP (`default-src 'none'`, `script-src 'self'`, `connect-src` only the loopback)
  - `frame-ancestors 'none'`, `Referrer-Policy: no-referrer`, `Cache-Control: no-store`
  - HSTS, `nosniff`, `noindex`
- **History scrub:** the code is removed from browser history (`history.replaceState`) as soon as it is
  read.
- **Chrome Local Network Access:** browsers now ask the user before an https site may call
  `127.0.0.1`. On desktop the first sign-in shows a one-time prompt to allow the site to access devices
  on the local network. If the user denies it, the page falls back to the custom scheme.
- **Custom-scheme hijacking:** any installed app can claim `org.mifosx.openbanking://`. The planned fix is
  Android App Links / iOS Universal Links on this domain (plan: FR-052). That needs the release
  signing-certificate fingerprint and the Apple team ID, and the app's intent-filter changes with it.
- **No secrets here:** the HSBC TPP identity (signing key, transport certificate, identifiers) lives in
  the Cloudflare Worker `mifos-x-open-banking-bff` (source and docs in [`bff/`](../bff/README.md)). The
  relay has no credentials and no server code, and cannot talk to HSBC.
