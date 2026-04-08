## Maintainer agent history

- **2026-04-08**: Documented one-command local run
 - **Change**:
   - Added `docker-compose.app.yml` overlay + `scripts/dev-up.sh` for full-stack `docker compose up -d --build`.
   - Updated root `README.md` with explicit local run options, required `.env` fields, and lifecycle commands.
 - **Tests**: `cd backend && mvn verify -DskipITs=true`; `cd frontend && npm run build -- --configuration=production` (pass)
 - **Risk/Follow-ups**: Docker required for Option C; ensure `POSTGRES_PASSWORD`/`DB_PASSWORD` set in `.env`.
- 2026-04-06: Initialized agent charter and repo-native memory.
- 2026-04-06: Improved local testability on newer JDKs (e.g., 25):
  - Forced Mockito to use subclass mock maker via `backend/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`.
  - Added Surefire/Failsafe `argLine` flags for dynamic agent loading (harmless in CI on Java 21).
- 2026-04-07: Switched staging CD target to Fly.io.
  - **Change**: Made Slack notifications optional; added Fly staging deploy (API + UI) and Fly service discovery proxying from UI to API.
  - **Tests**: Not run locally (workflow change only).
  - **Risk/Follow-ups**: Ensure `FLY_API_TOKEN` + backend DB secrets are configured; consider templating nginx upstream for production vs staging.

