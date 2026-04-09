## Backend agent history

- 2026-04-06: Initialized agent charter and repo-native memory.

- **2026-04-08**: Verified backend compiles out-of-box; improved local onboarding docs
 - **Change**:
   - Documented `local` profile (H2 in-memory) quickstart path in root `README.md`.
   - Confirmed `mvn verify -DskipITs=true` passes (no code changes needed in backend config/deps).
 - **Tests**: `cd backend && mvn verify -DskipITs=true` (pass)
 - **Risk/Follow-ups**: none

- **2026-04-09**: Keep staging API machine always running
 - **Change**: Set `min_machines_running = 1` in `backend/fly.staging.toml` to avoid scale-to-zero.
 - **Tests**: none (config-only)
 - **Risk/Follow-ups**: Slightly higher baseline cost for staging; adjust if needed.

