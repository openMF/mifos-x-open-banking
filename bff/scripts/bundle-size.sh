#!/usr/bin/env bash
# Copyright 2026 Mifos Initiative — MPL-2.0. See https://github.com/openMF/mifos-x-open-banking/blob/dev/LICENSE
#
# bundle-size.sh — build the Worker the way `wrangler deploy` would (no upload) and check its size.
# Fails (exit 3) at 950 KB, 90% of a 1 MiB budget: well inside the Workers Free script limit, and the
# bundle is parsed on every cold isolate, which costs CPU (NFR-012).
set -euo pipefail
BFF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; cd "$BFF_DIR"
OUT=$(mktemp -d); trap 'rm -rf "$OUT"' EXIT
npx --yes wrangler@4 deploy --dry-run --outdir "$OUT" >/dev/null
SIZE=$(wc -c < "$OUT/index.js" | tr -d ' ')
GZ=$(gzip -9c "$OUT/index.js" | wc -c | tr -d ' ')
echo "worker bundle: $SIZE bytes ($GZ gzipped)"
[ "$SIZE" -lt 972800 ] || { echo "❌ bundle >= 950 KB" >&2; exit 3; }
