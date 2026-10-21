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

> ⚠️ **iOS is untested.** The iOS build is implemented but has not yet been verified on a Mac — treat iOS support as experimental.

## 📖 About

Mifos X Open Banking is a standalone Open Banking fintech application built with Kotlin Multiplatform. It is a reference implementation that demonstrates how a third-party provider (TPP) can use Open Banking standards — PSD2 and UK Open Banking — to deliver both **Account Information Services (AIS)** and **Payment Initiation Services (PIS)** to users of Mifos/Fineract-powered institutions and beyond.

From a single codebase it runs on Android, iOS, desktop and web, and currently integrates with the **HSBC UK Open Banking sandbox** as its reference bank.

## 📱 Using the app

You supply your own HSBC sandbox credentials and certificates — the app ships with none. A checkout without them still builds; it only fails when it tries to reach the bank. Set it up as follows.

### Prerequisites

- JDK 17+
- Android Studio / IntelliJ IDEA
- `openssl` (to prepare the certificates)
- An **HSBC Open Banking sandbox** TPP registration (see below)
- Xcode (for the iOS target)

### 1. Get HSBC sandbox credentials

Register as a TPP on HSBC's Open Banking DevHub / sandbox (via **Dynamic Client Registration**). From onboarding you obtain (or generate):

- a **client id** (`client_id`) and a signing **key id** (`kid`)
- a **Software Statement (SSA)**
- a **transport certificate** (`Transport.crt`, the QWAC used for mTLS) and a **signing certificate** (`Signing.crt`, the OBSEAL used to sign JWTs), plus the **private key** they were issued against
- a registered **redirect URI**

**Authentication model:** the client authenticates at the token endpoint with **`private_key_jwt`** — a PS256-signed JWT assertion, signed by your signing key and referenced by `kid` — and every call to the bank runs over **mTLS**, presenting your transport certificate + key on the TLS handshake. In the sandbox one key-pair backs both certificates.

> See HSBC's Open Banking sandbox documentation for registration and the OBIE Read/Write v4.0 AIS APIs.

### 2. Generate the two certificate files

The app loads exactly two files, with names fixed in `core/network/src/commonMain/kotlin/org/mifosx/openbanking/core/network/certs/CertPaths.kt`. Create them from the sandbox artifacts with `openssl`:

**`signing_key.pem`** — the private key that pairs with `Signing.crt` (the key, *not* the certificate), in PKCS#8 PEM. It signs the `private_key_jwt` client assertion:

```bash
openssl pkcs8 -topk8 -nocrypt -in <signing-private-key>.key -out signing_key.pem
```

**`transport.p12`** — a PKCS#12 bundle of `Transport.crt` + its private key, presented on the mTLS handshake. The export password **must match `HSBC_TRANSPORT_P12_PASSWORD`** (step 4) and must be **non-empty** (Android's BouncyCastle rejects an empty PKCS#12 password):

```bash
openssl pkcs12 -export -inkey <transport-private-key>.key -in Transport.crt -out transport.p12 -passout pass:<HSBC_TRANSPORT_P12_PASSWORD>
```

The same key backs both certificates in the sandbox, so `<signing-private-key>.key` and `<transport-private-key>.key` are the one key HSBC gave you.

> `Signing.crt` and its public key are **not** placed in the app. Their only role is the public half you registered with HSBC, so the bank can verify the JWTs you sign. Only `signing_key.pem` and `transport.p12` go into the app.

### 3. Place the certificate files

Drop **both** files into the platform's `certs/` directory. These directories are git-ignored — the files are supplied locally per developer and never committed.

| Platform | Directory (both files) |
|----------|------------------------|
| Android  | `core/network/src/androidMain/assets/certs/` |
| Desktop  | `core/network/src/desktopMain/resources/certs/` |
| iOS      | `cmp-ios/iosApp/certs/` (add the folder as a bundle reference in the iOS target) |
| Web (js / wasm) | not supported — a browser cannot present a client certificate, so the HSBC client is unavailable on web |

### 4. Configure `local.properties`

Add the following to the root `local.properties`. A generator task (`:core:network:generateHsbcConfig`) reads them at build time:

```properties
HSBC_CLIENT_ID=your-client-id
HSBC_KID=your-key-id
HSBC_SOFTWARE_STATEMENT=your-software-statement-jwt
HSBC_BANK_HOST=secure.sandbox.ob.hsbc.co.uk
HSBC_AUTHORIZE_HOST=sandbox.ob.hsbc.co.uk
HSBC_REDIRECT_URI=your-registered-redirect-uri
HSBC_TRANSPORT_P12_PASSWORD=your-p12-password
```

- `HSBC_BANK_HOST` is the mTLS API host (token, consent, resources); `HSBC_AUTHORIZE_HOST` is the front-channel host the browser is sent to for PSU login.
- `HSBC_REDIRECT_URI` must exactly match a redirect URI you registered with HSBC.
- `HSBC_TRANSPORT_P12_PASSWORD` must match the password used to export `transport.p12`, and must be non-empty.

After changing any value, regenerate the config: `./gradlew :core:network:generateHsbcConfig`.

### 5. Handle the OAuth redirect (consent callback)

After the customer authorises consent, HSBC redirects back to your **registered `redirect_uri`** — a single HTTPS page, the same for every platform. HSBC uses the OIDC hybrid flow, so the result comes back in the URL *fragment*, which only the browser can read. That page is therefore a small **relay** ([`relay/`](relay/README.md), live at `https://mifos-x-open-banking-relay.pages.dev/callback/` on Cloudflare Pages): its JavaScript reads the fragment and hands the result to the running app — through a **custom URL scheme** on Android/iOS, or a **localhost loopback** on desktop. The value registered with HSBC is always that HTTPS relay page; the per-platform scheme/port below only govern the relay's second hop back into the app.

**If you fork this project, you must:**

1. **Host your own relay page** — deploy `relay/site/` to an HTTPS host you control (the reference uses Cloudflare Pages so the security headers in `relay/site/_headers` apply — see [`relay/README.md`](relay/README.md)), set `HSBC_REDIRECT_URI` in `local.properties` to its `/callback/` URL, and register the identical URL with HSBC.
2. **Choose your own custom scheme** (the reference uses `org.mifosx.openbanking`) and set it — identically — in every file below.

| Platform | File | What to set |
|----------|------|-------------|
| Relay page | `relay/site/callback/bridge.js` | `SCHEME` (your scheme) and `LOOPBACK_PORT` (desktop, default `8765`) |
| Android | `cmp-android/src/main/AndroidManifest.xml` | the `<data android:scheme="…">` entries in the redirect `<intent-filter>` |
| Android | `cmp-android/src/main/kotlin/cmp/android/app/ConsentRedirectIntent.kt` | `CONSENT_REDIRECT_SCHEME` (must match the manifest) |
| iOS | `cmp-ios/iosApp/Info.plist` | the `CFBundleURLSchemes` entry |
| iOS / shared | `cmp-shared/src/nativeMain/kotlin/org/mifos/shared/ConsentRedirectBridge.kt` | `CONSENT_REDIRECT_SCHEME` (must match the plist) |
| Desktop | `cmp-desktop/src/jvmMain/kotlin/ConsentRedirectListener.kt` | `CALLBACK_SCHEME`, and `PORT` (must match `bridge.js` `LOOPBACK_PORT`) |

Android and iOS receive the custom scheme directly (the OS wakes the app and delivers the URL). **Desktop** has no scheme registration, so the relay `fetch`es a small loopback server the app runs at `http://127.0.0.1:8765/callback` — keep that port in sync between `ConsentRedirectListener` and `bridge.js`.

### 6. Run it

| Platform | Command                                                        |
|----------|----------------------------------------------------------------|
| Android  | `./gradlew :cmp-android:installDemoDebug`, then launch the app |
| Desktop  | `./gradlew :cmp-desktop:run`                                   |
| iOS      | open `cmp-ios/iosApp` in Xcode and run                         |


> **iOS is experimental.** The mTLS integration is implemented but has not been built or verified on a Mac yet, so iOS is not confirmed to work against the sandbox.

### Security

Never commit or log the private key, `transport.p12`, the PKCS#12 passphrase, the Software Statement, or any access / refresh / id tokens. The `certs/` directories and all `*.pem` / `*.p12` files are git-ignored. `client_id` and `kid` are non-secret identifiers.

## 🔐 Consent relay

**Live:** <https://mifos-x-open-banking-relay.pages.dev> · **Redirect URI:** `https://mifos-x-open-banking-relay.pages.dev/callback/` · **Source:** [`relay/`](relay/README.md)

The consent relay is the small web page your bank redirects to after you approve access or a payment. HSBC returns its answer in the URL fragment, which only the browser can read, so the page reads it and hands it to the app: over `127.0.0.1:8765` on desktop, or by opening the app on mobile.

- **Stores nothing.** It has no server code, database, cookies, analytics or third-party scripts.
- **Holds no keys.** It cannot sign in or make payments on its own.
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
