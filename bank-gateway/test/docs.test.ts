/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
 */

import { describe, expect, it } from "vitest";
import { ROUTES } from "../src/routes";
// The tests run inside workerd, which cannot read the host file system; Vite's ?raw bundles the file.
import doc from "../docs/API.md?raw";

/*
 * Route / doc parity (GOAL D12, AC15): every row of the closed route table has a `### METHOD /path`
 * section in docs/API.md, using the exact path template from src/routes.ts, and no section documents a
 * route that is not in the table.
 */

const HEADING = /^### (GET|POST|PUT|PATCH|DELETE) (\/\S+)\s*$/gm;
const headings = Array.from(doc.matchAll(HEADING), (m) => `${m[1]} ${m[2]}`);
const routeKeys = ROUTES.map((r) => `${r.method} ${r.path}`);

/** The text of one `### …` section, up to the next `###` or `##` heading. */
function section(key: string): string {
  const start = doc.indexOf(`### ${key}\n`);
  if (start < 0) return "";
  const rest = doc.slice(start + 4);
  const end = rest.search(/^#{2,3} /m);
  return end < 0 ? rest : rest.slice(0, end);
}

describe("docs/API.md mirrors src/routes.ts", () => {
  it("documents every route", () => {
    const missing = routeKeys.filter((k) => !headings.includes(k));
    expect(missing, `undocumented routes: ${missing.join(", ")}`).toEqual([]);
  });

  it("documents no route that is not in the table", () => {
    const strays = headings.filter((h) => !routeKeys.includes(h));
    expect(strays, `documented but not routed: ${strays.join(", ")}`).toEqual([]);
  });

  it("documents each route once", () => {
    const dupes = headings.filter((h, i) => headings.indexOf(h) !== i);
    expect(dupes).toEqual([]);
  });

  it("gives every route an Auth line and an HSBC line", () => {
    const bad = routeKeys.filter((k) => !/^- \*\*Auth:\*\*/m.test(section(k)) || !/^- \*\*HSBC:\*\*/m.test(section(k)));
    expect(bad, `sections missing Auth or HSBC: ${bad.join(", ")}`).toEqual([]);
  });

  it("states the session, attestation and rate-limit gates the table declares", () => {
    const wrong = ROUTES.filter((r) => {
      const auth = /^- \*\*Auth:\*\*(.*)$/m.exec(section(`${r.method} ${r.path}`))?.[1] ?? "";
      return /\bsession\b/i.test(auth) !== r.session || /attestation/i.test(auth) !== r.attest || /rate limit/i.test(auth) !== Boolean(r.rateLimit);
    }).map((r) => `${r.method} ${r.path}`);
    expect(wrong, `Auth line disagrees with the route table: ${wrong.join(", ")}`).toEqual([]);
  });
});
