## Frontend agent history

- 2026-04-06: Initialized agent charter and repo-native memory.

- **2026-04-08**: Unblocked Activity/Product creation flows in UI
 - **Change**:
   - Added Activities and Products catalogue pages with create + list flows.
   - Added signal-based stores to POST/GET `'/api/v1/activities'` and `'/api/v1/products'`.
   - Wired routes + sidebar navigation so users can reach these flows.
 - **Tests**: `cd frontend && npm ci && npx tsc --noEmit` (pass)
 - **Risk/Follow-ups**: none

- **2026-04-08**: Improved allocation-rule UX and errors
 - **Change**:
   - CFO Dashboard: mapped “missing Activity → Product rules” allocation error IDs to activity names and added actionable guidance.
   - Allocation Rules: warned after saving Resource → Activity rules if referenced activities lack Activity → Product rules.
 - **Tests**: `cd frontend && npx tsc --noEmit && npx ng test --watch=false --browsers=ChromeHeadless --code-coverage && npm run build -- --configuration=production` (pass)
 - **Risk/Follow-ups**: none

- **2026-04-09**: Fix Fly UI crash loop (duplicate entrypoint/cmd merge)
 - **Change**: Set `[experimental] exec` in `fly.staging.toml` and `fly.production.toml` so Machines run `/docker-entrypoint.sh nginx -g daemon off;` once (avoids `nginx: invalid option: "nginx"`).
 - **Tests**: none (config-only)
 - **Risk/Follow-ups**: Redeploy UI app; if Fly promotes a non-experimental knob for `exec`, migrate when documented.

