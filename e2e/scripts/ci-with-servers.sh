#!/usr/bin/env bash
# Start Spring Boot (e2e profile) + Angular dev server, then run Playwright, LHCI, Artillery.
# Security: do not echo tokens; logs go to temp files only on failure.
#
# Uses API_PORT (default 18080) to avoid colliding with a developer's API on 8080 —
# a stale process on 8080 was causing health checks to hit the wrong server (404 on /api/...).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="${RUNNER_TEMP:-${TMPDIR:-/tmp}}"
API_LOG="$TMP/pf-api.log"
FE_LOG="$TMP/pf-fe.log"
PROXY_JSON="$TMP/pf-e2e-proxy.json"
API_PORT="${API_PORT:-18080}"
API_BASE="http://127.0.0.1:${API_PORT}"

JAR="$(ls -t "$ROOT"/backend/target/profitflow-api-*.jar 2>/dev/null | head -1 || true)"
if [[ -z "$JAR" ]]; then
  echo "Missing backend JAR. Run: (cd backend && mvn -Pe2e -DskipTests package)" >&2
  exit 1
fi

cat >"$PROXY_JSON" <<EOF
{
  "/api": {
    "target": "${API_BASE}",
    "secure": false,
    "changeOrigin": true
  }
}
EOF

java -jar "$JAR" --spring.profiles.active=e2e --server.port="${API_PORT}" >"$API_LOG" 2>&1 &
API_PID=$!

cleanup() {
  kill "$API_PID" 2>/dev/null || true
  if [[ -n "${FE_PID:-}" ]]; then
    kill "$FE_PID" 2>/dev/null || true
  fi
  pkill -f "ng serve.*127.0.0.1:4200" 2>/dev/null || true
}
trap cleanup EXIT

echo "Waiting for API health on ${API_BASE}…"
for _ in $(seq 1 90); do
  if curl -sf "${API_BASE}/actuator/health" >/dev/null; then
    break
  fi
  if ! kill -0 "$API_PID" 2>/dev/null; then
    echo "API process exited early. Log:" >&2
    tail -n 80 "$API_LOG" >&2 || true
    exit 1
  fi
  sleep 2
done

# The health endpoint can go UP before demo users are committed (seed runs async after startup).
# E2E depends on demo credentials; wait until login succeeds to avoid flaky 401s.
# CSRF: obtain XSRF-TOKEN cookie (Spring CookieCsrfTokenRepository) then send X-XSRF-TOKEN on POST.
API_COOKIE_JAR="$TMP/pf-api-cookies.txt"
rm -f "$API_COOKIE_JAR"
curl -sf -c "$API_COOKIE_JAR" "${API_BASE}/actuator/health" >/dev/null || true
csrf_token_from_jar() {
  awk '$6 == "XSRF-TOKEN" { print $7; exit }' "$API_COOKIE_JAR"
}
echo "Waiting for demo credentials to be ready…"
for _ in $(seq 1 60); do
  CSRF_TOKEN="$(csrf_token_from_jar)"
  if [[ -n "${CSRF_TOKEN}" ]] && curl -sf -X POST "${API_BASE}/api/v1/auth/login" \
      -b "$API_COOKIE_JAR" -c "$API_COOKIE_JAR" \
      -H "Content-Type: application/json" \
      -H "X-XSRF-TOKEN: ${CSRF_TOKEN}" \
      -d '{"username":"admin","password":"Admin1234!"}' >/dev/null; then
    break
  fi
  if ! kill -0 "$API_PID" 2>/dev/null; then
    echo "API process exited early. Log:" >&2
    tail -n 80 "$API_LOG" >&2 || true
    exit 1
  fi
  sleep 1
done

echo "Starting Angular dev server (proxy → ${API_BASE})…"
(cd "$ROOT/frontend" && npx ng serve --host 127.0.0.1 --port 4200 \
    --proxy-config "$PROXY_JSON") >"$FE_LOG" 2>&1 &
FE_PID=$!

for _ in $(seq 1 120); do
  if curl -sf "http://127.0.0.1:4200/" >/dev/null; then
    break
  fi
  sleep 2
done

export PLAYWRIGHT_BASE_URL="${PLAYWRIGHT_BASE_URL:-http://127.0.0.1:4200}"
export PLAYWRIGHT_API_BASE_URL="${PLAYWRIGHT_API_BASE_URL:-${API_BASE}}"
export CI="${CI:-true}"

cd "$ROOT/e2e"

npx playwright install chromium

echo "Playwright (UI + API)…"
npx playwright test

echo "Lighthouse CI (accessibility)…"
export LHCI_BUILD_CONTEXT__EXTERNAL_CHROME_PATH="$(
  node -e "const { chromium } = require('playwright'); console.log(chromium.executablePath());"
)"

# LHCI/Lighthouse fails on Apple Silicon when running under an x64 Node binary (Rosetta translation).
# CI runners are Linux, so keep it enforced there; locally we skip to avoid a hard failure.
IS_DARWIN=false
if [[ "$(uname -s)" == "Darwin" ]]; then IS_DARWIN=true; fi
IS_APPLE_SILICON=false
if $IS_DARWIN && [[ "$(sysctl -n hw.optional.arm64 2>/dev/null || echo 0)" == "1" ]]; then
  IS_APPLE_SILICON=true
fi
IS_NODE_X64=false
if node -e "process.exit(process.arch === 'x64' ? 0 : 1)"; then IS_NODE_X64=true; fi

if $IS_APPLE_SILICON && $IS_NODE_X64; then
  echo "Skipping Lighthouse CI: Apple Silicon + x64 Node (Rosetta) detected."
else
  npx lhci autorun --config=lighthouserc.cjs
fi

echo "Artillery probe…"
sed "s|__API_TARGET__|${API_BASE}|g" "$ROOT/e2e/artillery/probe.yml" >"$TMP/pf-artillery-probe.yml"
npx artillery run "$TMP/pf-artillery-probe.yml"

echo "Extended tests completed successfully."
