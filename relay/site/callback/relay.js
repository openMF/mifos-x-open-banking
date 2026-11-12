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
 * The relay flow for the HSBC OBIE consent redirect. Lives in its own file (not inline in index.html) so
 * the page can run under `script-src 'self'`, and takes its browser objects as arguments so every branch
 * is testable under `node --test` with a fake window.
 *
 * A web sign-in is told apart by its state (`w<index>.`, set by the Worker) and goes straight back to that
 * allowlisted web origin. Otherwise two delivery paths exist, because the page cannot tell up front which
 * platform opened it:
 *   - loopback      -> desktop HttpServer on 127.0.0.1:8765
 *   - custom scheme -> Android intent-filter / iOS CFBundleURLSchemes
 *
 * They are mutually exclusive, and the loopback probe is what tells them apart: it resolves only when a
 * desktop listener answers, and rejects with connection-refused when there is none. Running both
 * unconditionally made desktop deliver over loopback and *also* open the custom scheme, surfacing "no
 * application found" on a consent journey that had already succeeded.
 */

/* global readParams, appUrl, loopbackUrl, webReturnUrl */

function run(env) {
  var bridge = typeof module !== 'undefined' && module.exports && typeof readParams === 'undefined'
    ? require('./bridge.js')
    : { readParams: readParams, appUrl: appUrl, loopbackUrl: loopbackUrl, webReturnUrl: webReturnUrl };

  var doc = env.document;
  var loc = env.location;

  var raw = bridge.readParams(loc.hash, loc.search);

  if (!raw) {
    doc.getElementById('heading').textContent = 'Nothing to return';
    doc.getElementById('detail').textContent =
      'This page had no authorisation response to pass back to the app.';
    return;
  }

  /*
   * The authorisation code must not outlive this page load. replaceState removes the fragment from the
   * address bar and from the history entry, so Back, history sync and "reopen closed tab" cannot replay
   * or leak it. The value we need is already in `raw`.
   */
  try {
    env.history.replaceState(null, '', loc.pathname);
  } catch (e) { /* very old browsers — the relay still works, the code just stays in history */ }

  /* Web: same tab, replace (not push) so Back does not land on the relay again. Origin is from the allowlist only. */
  var web = bridge.webReturnUrl(raw);
  if (web) {
    loc.replace(web);
    return;
  }

  var target = bridge.appUrl(raw);
  doc.getElementById('retry').onclick = function () { loc.href = target; };

  function handOffToMobileApp() {
    loc.href = target;

    /*
     * If the hand-off worked we are backgrounded by now and this never runs. If the browser refused
     * it — iOS Safari blocks custom-scheme navigation that no user gesture initiated — offer a tap
     * rather than leave the customer on a page that looks stalled.
     */
    env.setTimeout(function () {
      doc.getElementById('heading').textContent = 'Open the app to continue';
      doc.getElementById('detail').textContent =
        'Your bank has authorised the request. Reopen the app to finish connecting.';
      doc.getElementById('manual').style.display = 'block';
    }, 1500);
  }

  /*
   * `no-cors` because only whether something answered matters, not what it said.
   * `targetAddressSpace: 'loopback'` declares the request to Chrome's Local Network Access check, which
   * asks the user once per site; denied → the probe rejects and the mobile/custom-scheme path runs.
   */
  var probe;
  try {
    probe = env.fetch(bridge.loopbackUrl(raw), { mode: 'no-cors', cache: 'no-store', targetAddressSpace: 'loopback' });
  } catch (e) {
    probe = Promise.reject(e); // fetch unavailable or threw synchronously — treat as "no desktop listener"
  }

  probe.then(
    function () {
      doc.getElementById('heading').textContent = 'Consent received';
      doc.getElementById('detail').textContent = 'You can close this tab and return to the app.';
    },
    function () {
      handOffToMobileApp();
    },
  );
}

if (typeof module !== 'undefined' && module.exports) {
  module.exports = { run: run };
} else {
  run({
    document: document,
    location: window.location,
    history: window.history,
    fetch: window.fetch ? window.fetch.bind(window) : undefined,
    setTimeout: window.setTimeout.bind(window),
  });
}
