## Agent team runbook

This repository is set up for coordinated AI work using **repository-native artifacts** (charters, shared decisions, and per-agent histories). This keeps context inspectable and reviewable in PRs.

Reference pattern: `https://github.blog/ai-and-ml/github-copilot/how-squad-runs-coordinated-ai-agents-inside-your-repository/`.

### Where the “team” lives

- **Shared memory**: `decisions.md`
- **Charters**: `.squad/charters/`
- **Agent execution notes**: `.squad/history/`

### How to “ask the agents”

Use these files as the contract:

- Put cross-cutting decisions in `decisions.md`.
- Put a task brief in the relevant agent history file (1–5 bullets).
- The agent should respond by:
  - implementing the change
  - running the test plan relevant to the scope
  - writing back a short result note into their history file

### Standard test commands (source of truth)

Backend:

```bash
cd backend && mvn verify -DskipITs=true
cd backend && mvn verify -DskipITs=false
```

Frontend:

```bash
cd frontend && npm ci
cd frontend && npx tsc --noEmit
cd frontend && npx ng test --watch=false --browsers=ChromeHeadless --code-coverage
cd frontend && npm run build -- --configuration=production
```

E2E:

```bash
cd e2e && npm ci
cd e2e && npm run typecheck
cd e2e && ./scripts/ci-with-servers.sh
```

