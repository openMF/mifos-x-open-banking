# Consent relay

The HTTPS page HSBC redirects to after the customer authorises a consent. HSBC uses the OIDC hybrid
flow, so the response (`code`, `id_token`, `state`) arrives in the URL **fragment**, which only browser
JavaScript can read. This page reads it and hands it to the app:

- **Desktop** — `http://127.0.0.1:8765/callback?…` (the desktop app's `ConsentRedirectListener`)
- **Mobile** — `org.mifosx.openbanking://callback?…` (Android intent-filter / iOS URL scheme), used only
  when no desktop listener answers

It holds **no secrets** and stores nothing.

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
│   ├── site.css              ← root page styles (external — CSP has no 'unsafe-inline')
│   ├── _headers              ← security headers for every response
│   └── callback/
│       ├── index.html        ← the redirect target — no inline script
│       ├── bridge.js         ← pure helpers: readParams / appUrl / loopbackUrl + constants
│       ├── relay.js          ← the flow: history scrub → loopback probe → mobile hand-off
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
(`HSBC_REDIRECT_URI` in `local.properties` must match it byte-for-byte).

## Changing the scheme or loopback port

`bridge.js` `SCHEME` and `LOOPBACK_PORT` must match the app exactly. See the table in the root
[README](../README.md#5-handle-the-oauth-redirect-consent-callback). The test pins both values.

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
- **No secrets here:** the TPP private keys must eventually move into a Cloudflare Worker on this same
  domain (plan: task `cloudflare-worker-bff`), not into this site.
