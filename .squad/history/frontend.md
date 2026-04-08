## Frontend agent history

- 2026-04-06: Initialized agent charter and repo-native memory.

- **2026-04-08**: Unblocked Activity/Product creation flows in UI
 - **Change**:
   - Added Activities and Products catalogue pages with create + list flows.
   - Added signal-based stores to POST/GET `'/api/v1/activities'` and `'/api/v1/products'`.
   - Wired routes + sidebar navigation so users can reach these flows.
 - **Tests**: `cd frontend && npm ci && npx tsc --noEmit` (pass)
 - **Risk/Follow-ups**: none

