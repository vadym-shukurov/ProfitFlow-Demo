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

- **2026-04-09**: Support deleting resource costs
 - **Change**: Added `DELETE /api/v1/resource-costs/{id}` endpoint with 404 (missing), 400 (bad id), and 409 (in-use) behavior; evicts resource-cost cache on delete.
 - **Tests**: `cd backend && mvn verify -DskipITs=true` (pass)
 - **Risk/Follow-ups**: Deleting a cost referenced by rules returns 409; UI should surface the error (it does via toast).

- **2026-04-09**: Tightened quality gates and documented Cost Ledger API
 - **Change**:
   - Removed an invalid `maven-pmd-plugin` parameter to eliminate noisy build warnings.
   - Documented resource-cost endpoints (including CSV import + 1 MiB limit) in `README.md`.
 - **Tests**: `cd backend && mvn verify -DskipITs=true` (pass); `./scripts/check-all.sh` (pass)
 - **Risk/Follow-ups**: Integration tests are skipped automatically when Docker is unavailable; CI still runs them on Linux runners with Docker.

