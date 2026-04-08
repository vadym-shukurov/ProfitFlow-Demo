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

## Next tasks (Principal SWE / Finance architecture)

Act as a **Principal Software Engineer & Software Architect (20+ years)** with deep experience building **finance software**. Execute this checklist as a single “make it merge-ready” loop:

- **1) Run all tests and checks**: run the full relevant backend + frontend + e2e pipelines and any repo quality/security gates. Target: everything green.
- **2) Fix failures properly**: if anything fails, fix the **root cause** in a secure, maintainable way (no workarounds, no scope creep).
- **3) Keep architecture strong**: prioritize scalable solutions with high reliability/maintainability; avoid fragile coupling and “quick hacks”.
- **4) Coverage gate**: ensure unit + integration test coverage is **≥ 85%** (raise coverage by adding targeted tests; don’t game metrics).
- **5) Duplication gate**: ensure code duplication is **< 5%** (deduplicate via clear abstractions; avoid over-generalization).
- **6) Commenting standard**: add comments for **non-obvious intent, invariants, financial domain rules, and tricky edge cases** (avoid narrating code).
- **7) Repo cleanup**: review again and remove dead code, unused configs, and obvious errors (keep diffs minimal and safe).
- **8) Re-run everything**: re-run all tests/checks to confirm everything is green after fixes.
- **9) Docs**: update `README.md` (and other runbooks) if behavior, setup, or quality gates changed.

