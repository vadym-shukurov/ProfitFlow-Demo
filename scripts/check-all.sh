#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

echo "==> Backend: unit/static checks (skip ITs)"
(cd backend && mvn verify -DskipITs=true)

echo ""
echo "==> Frontend: install + typecheck + unit tests + production build"
(cd frontend && npm ci)
(cd frontend && ./node_modules/.bin/tsc --noEmit)
(cd frontend && npx ng test --watch=false --browsers=ChromeHeadless --code-coverage)
(cd frontend && npm run build -- --configuration=production)

echo ""
echo "==> Backend: integration tests (Testcontainers; requires Docker)"
(cd backend && mvn verify -DskipITs=false)

echo ""
echo "==> E2E: typecheck + CI-like run (requires Docker)"
(cd e2e && npm ci)
(cd e2e && npm run typecheck)
(cd e2e && ./scripts/ci-with-servers.sh)

echo ""
echo "All checks passed."

