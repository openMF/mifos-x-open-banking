/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

/*
 * Bridge logic for the HSBC OBIE consent redirect, kept apart from index.html so it can be tested.
 *
 * HSBC uses the OIDC hybrid flow (response_type = "code id_token"), so it returns the result in the
 * URL *fragment*: #code=...&id_token=...&state=... A fragment is client-side only and is never sent
 * to a server, which is the entire reason this page exists: only JavaScript running here can read it
 * and hand it onward.
 *
 * Keep in lock-step with:
 *   - CONSENT_REDIRECT_SCHEME  (cmp-android ConsentRedirectIntent.kt, cmp-shared ConsentRedirectBridge.kt)
 *   - ConsentRedirectListener.PORT  (cmp-desktop)
 *   - WEB_RETURN_ORIGINS  (bank-gateway/wrangler.toml — same entries, same order; the relay test pins it)
 */

var SCHEME = 'org.mifosx.openbanking';
var LOOPBACK_PORT = 8765;

/*
 * Web origins a web sign-in may return to. The Worker prefixes `state` with `w<index>.` into this list;
 * the relay only ever navigates to an entry here, never to an origin carried in the URL. Append, never
 * reorder — an index already in flight would point somewhere else.
 */
var WEB_RETURN_ORIGINS = ['http://localhost:8080'];

/**
 * Returns the raw parameter string the bank sent back, or '' when there is nothing to relay.
 *
 * Prefers the fragment, because that is what the hybrid flow actually returns; the query is a
 * defensive fallback in case a future response_mode puts them there instead.
 */
function readParams(hash, search) {
  var fromHash = (hash || '').replace(/^#/, '');
  var fromQuery = (search || '').replace(/^\?/, '');
  return fromHash || fromQuery;
}

/** The mobile hand-off: Android intent-filter / iOS CFBundleURLSchemes. */
function appUrl(raw) {
  return SCHEME + '://callback?' + raw;
}

/**
 * The desktop hand-off. The fragment is rewritten to a query deliberately — a server cannot see a
 * fragment, so the loopback listener would receive nothing without this.
 */
function loopbackUrl(raw) {
  return 'http://127.0.0.1:' + LOOPBACK_PORT + '/callback?' + raw;
}

/**
 * The web hand-off, or null. Returns only for a state of the form `w<index>.…` whose index is on
 * WEB_RETURN_ORIGINS. The parameters stay in the fragment, so the web origin's server never sees them.
 */
function webReturnUrl(raw) {
  var m = /^w(0|[1-9][0-9]*)\./.exec(new URLSearchParams(raw).get('state') || '');
  var origin = m && WEB_RETURN_ORIGINS[Number(m[1])];
  return origin ? origin + '/#mifos-callback?' + raw : null;
}

if (typeof module !== 'undefined' && module.exports) {
  module.exports = {
    readParams: readParams, appUrl: appUrl, loopbackUrl: loopbackUrl, webReturnUrl: webReturnUrl,
    SCHEME: SCHEME, LOOPBACK_PORT: LOOPBACK_PORT, WEB_RETURN_ORIGINS: WEB_RETURN_ORIGINS,
  };
}
