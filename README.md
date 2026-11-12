<div align="center">

<img src="https://github.com/user-attachments/assets/ab2f5bf9-5b88-4fee-90e9-741e3b3f7a26" alt="Mifos X Open Banking" width="150" style="margin-right: 20px;" />

<h1>Mifos X Open Banking</h1>

<p>A Kotlin Multiplatform + Compose Open Banking client for the HSBC UK Open Banking sandbox — built for Account Information (AIS) and Payment Initiation (PIS) — running on Android, iOS, desktop and web from one codebase.</p>

![Kotlin](https://img.shields.io/badge/Kotlin-7f52ff?style=flat-square&logo=kotlin&logoColor=white)
![Kotlin Multiplatform](https://img.shields.io/badge/Kotlin%20Multiplatform-4c8d3f?style=flat-square&logo=kotlin&logoColor=white)
![Compose Multiplatform](https://img.shields.io/badge/Jetpack%20Compose%20Multiplatform-000000?style=flat-square&logo=android&logoColor=white)

![badge-android](http://img.shields.io/badge/platform-android-6EDB8D.svg?style=flat)
![badge-ios](http://img.shields.io/badge/platform-ios-CDCDCD.svg?style=flat)
![badge-desktop](http://img.shields.io/badge/platform-desktop-DB413D.svg?style=flat)
![badge-js](http://img.shields.io/badge/platform-web-FDD835.svg?style=flat)

[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg?style=flat-square)](http://makeapullrequest.com)
[![GitHub license](https://img.shields.io/github/license/openMF/mifos-x-open-banking?style=flat-square)](https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE)
[![PR Checks](https://github.com/openMF/mifos-x-open-banking/actions/workflows/pr-check.yml/badge.svg)](https://github.com/openMF/mifos-x-open-banking/actions/workflows/pr-check.yml)
[![Slack](https://img.shields.io/badge/Slack-4A154B?style=flat-square&logo=slack&logoColor=white)](https://join.slack.com/t/mifos/shared_invite/zt-2wvi9t82t-DuSBdqdQVOY9fsqsLjkKPA)

</div>

> ⚠️ **iOS is untested.** The iOS build is implemented but has not yet been verified on a device against the sandbox — treat iOS support as experimental.

## 📖 About

Mifos X Open Banking is a standalone Open Banking fintech application built with Kotlin Multiplatform. It is a reference implementation that demonstrates how a third-party provider (TPP) can use Open Banking standards — PSD2 and UK Open Banking — to deliver both **Account Information Services (AIS)** and **Payment Initiation Services (PIS)** to users of Mifos/Fineract-powered institutions and beyond.

From a single codebase it runs on Android, iOS, desktop and web, and currently integrates with the **HSBC UK Open Banking sandbox** as its reference bank.

## 📱 Using the app

The app holds **no** bank credential, certificate or key on any platform. It is a plain HTTPS client of a Cloudflare Worker (`bank-gateway/`) that holds the HSBC TPP identity and every HSBC token; the app only carries an opaque session.

### Prerequisites

- JDK 17+
- Android Studio / IntelliJ IDEA
- Xcode (for the iOS target)

### 1. Point the app at a bank gateway

The HSBC TPP identity (signing key, transport certificate, client id, kid, software statement, signing issuer) lives in the Cloudflare Worker in [`bank-gateway/`](bank-gateway/README.md), never in the app. The app only needs to know the Worker's URL:

- **Reference Worker (default):** `https://mifos-x-open-banking-gateway.mifos-x-open-banking.workers.dev`, connected to the HSBC sandbox. A checkout with no configuration builds against it.
- **Your own Worker:** deploy `bank-gateway/` with your own HSBC sandbox registration and Cloudflare account (see [`bank-gateway/README.md`](bank-gateway/README.md) and [`bank-gateway/docs/RUNBOOK.md`](bank-gateway/docs/RUNBOOK.md)), then set its URL in the root `local.properties`:

```properties
GATEWAY_BASE_URL=https://mifos-x-open-banking-gateway.<your-subdomain>.workers.dev
# optional, Android attestation (Play Integrity) — the Google Cloud project number
PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER=
```

Both values are public configuration, not secrets. The `:core:network:generateGatewayConfig` task reads each key from an environment variable, then `local.properties`, then a Gradle property, and otherwise uses the default.

### 2. Handle the OAuth redirect (consent callback)

After the customer authorises consent, HSBC redirects back to your **registered `redirect_uri`** — a single HTTPS page, the same for every platform. HSBC uses the OIDC hybrid flow, so the result comes back in the URL *fragment*, which only the browser can read. That page is therefore a small **relay** ([`relay/`](relay/README.md), live at `https://mifos-x-open-banking-relay.pages.dev/callback/` on Cloudflare Pages): its JavaScript reads the fragment and hands the result to the running app — through a **custom URL scheme** on Android/iOS, a **localhost loopback** on desktop, or a `#mifos-callback?` fragment on the web app's own origin. The app then sends it to the Worker's `POST /v1/auth/complete`, which verifies the `id_token`, exchanges the code with HSBC and returns the app's session. The value registered with HSBC is always that HTTPS relay page; the per-platform scheme/port below only govern the relay's second hop back into the app.

**If you fork this project, you must:**

1. **Host your own relay page** — deploy `relay/site/` to an HTTPS host you control (the reference uses Cloudflare Pages so the security headers in `relay/site/_headers` apply — see [`relay/README.md`](relay/README.md)), set the Worker's `HSBC_REDIRECT_URI` to its `/callback/` URL, and register the identical URL with HSBC.
2. **Choose your own custom scheme** (the reference uses `org.mifosx.openbanking`) and set it — identically — in every file below.

| Platform | File | What to set |
|----------|------|-------------|
| Relay page | `relay/site/callback/bridge.js` | `SCHEME` (your scheme) and `LOOPBACK_PORT` (desktop, default `8765`) |
| Android | `cmp-android/src/main/AndroidManifest.xml` | the `<data android:scheme="…">` entries in the redirect `<intent-filter>` |
| Android | `cmp-android/src/main/kotlin/cmp/android/app/ConsentRedirectIntent.kt` | `CONSENT_REDIRECT_SCHEME` (must match the manifest) |
| iOS | `cmp-ios/iosApp/Info.plist` | the `CFBundleURLSchemes` entry |
| iOS / shared | `cmp-shared/src/nativeMain/kotlin/org/mifos/shared/ConsentRedirectBridge.kt` | `CONSENT_REDIRECT_SCHEME` (must match the plist) |
| Desktop | `cmp-desktop/src/jvmMain/kotlin/ConsentRedirectListener.kt` | `CALLBACK_SCHEME`, and `PORT` (must match `bridge.js` `LOOPBACK_PORT`) |

Android and iOS receive the custom scheme directly (the OS wakes the app and delivers the URL). **Desktop** has no scheme registration, so the relay `fetch`es a small loopback server the app runs at `http://127.0.0.1:8765/callback` — keep that port in sync between `ConsentRedirectListener` and `bridge.js`. **Web** has neither, so the Worker marks a web sign-in's `state` with an index into its `WEB_RETURN_ORIGINS` list and the relay returns to that origin; a new web host must be added to the Worker's list and to `bridge.js` in the same order ([`relay/README.md`](relay/README.md#web-return), [`bank-gateway/docs/RUNBOOK.md`](bank-gateway/docs/RUNBOOK.md#8-add-a-web-origin)).

### 3. Run it

| Platform | Command                                                        |
|----------|----------------------------------------------------------------|
| Android  | `./gradlew :cmp-android:installDemoDebug`, then launch the app |
| Desktop  | `./gradlew :cmp-desktop:run`                                   |
| iOS      | open `cmp-ios/iosApp` in Xcode and run                         |
| Web      | `./gradlew :cmp-web:jsBrowserDistribution`, then serve `cmp-web/build/dist/js/productionExecutable/` on `http://localhost:8080` (the origin the reference Worker allows) |

### 4. Sign in with the sandbox test customer

The HSBC UK sandbox uses a shared test customer, not real banking credentials. When the bank's page opens (inside the app — a Custom Tab on Android, an `ASWebAuthenticationSession` sheet on iOS), sign in with:

| Field | Value |
|-------|-------|
| Username | `customer01` |
| Security code | `123456` |

Then tick the accounts to share and tap **Finish**. You come back to the app automatically; if Chrome asks for a tap first, the relay page shows **Reopen the app**. The sandbox consent page is laid out for desktop widths, so on a phone you may need to scroll sideways to reach the fields — that is HSBC's sandbox page, not the app.

> These are HSBC's public sandbox test values. Real HSBC credentials must never be typed into a sandbox build.

> **iOS has not been verified on a device yet.** It no longer contains any certificate or signing code (all of that moved to the Worker), but it still needs a Mac to build and has not been run against the sandbox.
>
> **Web works** since the move to the Worker: a browser cannot present an mTLS client certificate, and it no longer has to. The bank login itself still needs a person (sandbox credentials and SCA).

### Security

No HSBC private key, transport certificate, PKCS#12 password, software statement, kid or client id is in this repository or in any app build; the Worker holds them, with the framework vault as the source of truth (see [`bank-gateway/docs/SECURITY.md`](bank-gateway/docs/SECURITY.md)). HSBC access, refresh and id tokens never leave the Worker. The app stores one opaque session id. The `Bank gateway` CI workflow fails any change that references the identity in app source or ships a certificate or key file in a built artefact.

Never commit or log the Worker session, an authorisation code or an `id_token`.

## 🔐 Consent relay

**Live:** <https://mifos-x-open-banking-relay.pages.dev> · **Redirect URI:** `https://mifos-x-open-banking-relay.pages.dev/callback/` · **Source:** [`relay/`](relay/README.md)

The consent relay is the small web page your bank redirects to after you approve access or a payment. HSBC returns its answer in the URL fragment, which only the browser can read, so the page reads it and hands it to the app: over `127.0.0.1:8765` on desktop, by opening the app on mobile, or by returning to the web app's origin.

- **Stores nothing.** It has no server code, database, cookies, analytics or third-party scripts.
- **Holds no keys.** It cannot sign in or make payments on its own; the code exchange happens at the Worker (`bank-gateway/`).
- **Scrubs the code from browser history** as soon as it has been read.
- **Locked down by headers** in [`relay/site/_headers`](relay/site/_headers):
  - a strict CSP with no inline code
  - no framing and no caching
  - no referrer, plus HSTS
- **Verify the headers yourself:** `curl -I https://mifos-x-open-banking-relay.pages.dev/callback/`

It's hosted on Cloudflare Pages and released under MPL-2.0 like the rest of the app. Tests, deploy and rollback steps are in [`relay/README.md`](relay/README.md).

**Forking the app?** Deploy your own copy of `relay/site/` and register your own URL with your bank. Never point your build at this one.

## 📁 Project Structure

The project follows a modular architecture:

- **Bank gateway**: `bank-gateway/`, the Cloudflare Worker that holds the HSBC identity and tokens (see [`bank-gateway/README.md`](bank-gateway/README.md))
- **Consent relay**: `relay/`, the OAuth redirect page on Cloudflare Pages (see [Consent relay](#-consent-relay))
- **Platform Modules**: `cmp-android`, `cmp-ios`, `cmp-desktop`, `cmp-web`, `cmp-navigation`
- **Core Modules**: common, reusable components shared across all features (`core/*`, `core-base/*`)
- **Feature Modules**: self-contained feature implementations (`feature/*`)
- **Build Logic**: custom Gradle plugins and build configuration

## 🤝 Contributing

We welcome contributions to Mifos X Open Banking! Here's how you can help:

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/amazing-feature`
3. Commit changes: `git commit -m 'Add amazing feature'`
4. Push to the branch: `git push origin feature/amazing-feature`
5. Open a pull request

Please follow our [Contributing Guidelines](CONTRIBUTING.md) for detailed information.

## 📫 Support

- Join the conversation in our [Slack channel](https://mifos.slack.com/archives/C05V139DVT9)
- Report issues on [GitHub](https://github.com/openMF/mifos-x-open-banking/issues)
- Track progress on [Jira](https://mifosforge.jira.com/jira/software/c/projects/MXOBA/boards/430)

## 📄 License

This project is licensed under the [Mozilla Public License 2.0](LICENSE)
