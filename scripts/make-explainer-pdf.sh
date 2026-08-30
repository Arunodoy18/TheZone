#!/usr/bin/env bash
# Render docs/EXPLAINER.html to docs/Zone-Explainer.pdf via headless Chrome.
# The web page is a Claude-artifact-style fragment (no <html>/<head>/<body>);
# this wraps it, forces the light "field notebook" palette, and adds a print
# stylesheet (A4, page-break rules, colour-adjust: exact).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/docs/EXPLAINER.html"
OUT="$ROOT/docs/Zone-Explainer.pdf"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

CHROME="${CHROME:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}"
[ -x "$CHROME" ] || CHROME="$(command -v google-chrome || command -v chromium || true)"
[ -n "$CHROME" ] || { echo "no Chrome/Chromium found; set \$CHROME" >&2; exit 1; }

python3 - "$SRC" "$TMP/print.html" <<'PY'
import sys, pathlib
src = pathlib.Path(sys.argv[1]).read_text()
i = src.index('<div class="wrap">')
head, body = src[:i], src[i:]
print_css = """
<style>
  @page { size: A4; margin: 14mm 15mm 16mm; }
  html { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
  body { background:#fff; font-size:10.3pt; line-height:1.48; }
  p { margin-top:0.6rem; } ul { margin-top:0.6rem; } .scroller { margin-top:0.8rem; }
  .wrap { max-width:100%; padding:0; }
  main { border-left:0; padding-left:0; margin-left:0; }
  header { padding:0 0 1.2rem; overflow:visible; }
  .sweep, .rings { display:none !important; }
  h1 { font-size:25pt; } h2 { font-size:15.5pt; margin-top:1.9rem; break-after:avoid; }
  h2 .n, h3 { break-after:avoid; }
  .lede { font-size:11.5pt; }
  .modes { grid-template-columns:repeat(3,1fr); }
  .feat div { grid-template-columns:11rem 1fr; }
  .contrast { grid-template-columns:1fr 1fr; }
  .mode, .step, .tile, .feat div, .pull, .legend { break-inside:avoid; }
  table { break-inside:auto; } thead { display:table-header-group; } tr { break-inside:avoid; }
  .scroller { overflow:visible; }
  .packet { overflow:visible; padding-bottom:1.1rem; }
  .bytes { min-width:0; } .bytes span::after { font-size:6pt; }
  a { color:inherit; text-decoration:none; }
  footer { break-inside:avoid; margin-top:1.6rem; }
</style>
"""
pathlib.Path(sys.argv[2]).write_text(
  f'<!doctype html>\n<html lang="en" data-theme="light">\n<head>\n'
  f'<meta charset="utf-8">\n<meta name="viewport" content="width=device-width, initial-scale=1">\n'
  f'{head}\n{print_css}\n</head>\n<body>\n{body}\n</body>\n</html>\n'
)
PY

"$CHROME" --headless --disable-gpu --no-pdf-header-footer \
  --print-to-pdf="$OUT" --run-all-compositor-stages-before-draw \
  --virtual-time-budget=10000 "file://$TMP/print.html" 2>/dev/null

echo "wrote $OUT"
