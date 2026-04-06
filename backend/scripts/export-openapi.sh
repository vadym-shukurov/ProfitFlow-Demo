#!/usr/bin/env bash
# Exports the OpenAPI 3 document from a running instance (SpringDoc).
# Used locally and in CI after `mvn verify` / `mvn package`.
#
# Requires: curl, JVM, packaged fat JAR under target/
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

PORT="${SERVER_PORT:-18080}"
OUT_DIR="${OPENAPI_OUT_DIR:-$ROOT/target/api-contract}"
mkdir -p "$OUT_DIR"

JAR="$(ls "$ROOT"/target/profitflow-api-*.jar 2>/dev/null | head -1 || true)"
if [[ -z "$JAR" || ! -f "$JAR" ]]; then
  echo "No packaged JAR found. Run: mvn -q -DskipTests package" >&2
  exit 1
fi

export SPRINGDOC_ENABLED=true
# openapi-export profile: in-memory H2 (see application-openapi-export.yml)
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-openapi-export}"
export SERVER_PORT="$PORT"

java -jar "$JAR" >/tmp/profitflow-openapi-export.log 2>&1 &
PID=$!
trap 'kill "$PID" 2>/dev/null || true' EXIT

READY=
for _ in $(seq 1 90); do
  if curl -sf "http://127.0.0.1:${PORT}/actuator/health" >/dev/null 2>&1; then
    READY=1
    break
  fi
  sleep 1
done

if [[ -z "${READY:-}" ]]; then
  echo "App did not become healthy in time (see /tmp/profitflow-openapi-export.log)" >&2
  exit 1
fi

if ! curl -sf "http://127.0.0.1:${PORT}/v3/api-docs" -o "$OUT_DIR/openapi.json"; then
  echo "Failed to fetch /v3/api-docs (see /tmp/profitflow-openapi-export.log)" >&2
  exit 1
fi

echo "Wrote $OUT_DIR/openapi.json"
