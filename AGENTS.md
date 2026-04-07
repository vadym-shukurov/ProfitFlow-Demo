## Agent team runbook

This repository is set up for coordinated AI work using **repository-native artifacts** (charters, shared decisions, and per-agent histories). This keeps context inspectable and reviewable in PRs.

Reference pattern: `https://github.blog/ai-and-ml/github-copilot/how-squad-runs-coordinated-ai-agents-inside-your-repository/`.

### Workflow contract (gstack-inspired)

To make agents reliable across sessions, we follow a single pipeline and treat failures as **stop-ship** signals.

- **Think (scope + risks)**: restate the goal, list assumptions, identify impacted areas (backend/frontend/e2e/ci).
- **Plan (small + testable)**: pick the narrowest shippable slice; define “done” before coding.
- **Build (minimal diff)**: implement only what the plan requires; keep changes local to the slice.
- **Review (self-check)**: re-read the diff for correctness, security, and maintainability.
- **Test (right-sized)**: run the smallest suite that proves the change, then expand if risk warrants.
- **Document (only if drift)**: update runbooks when you changed behavior or setup.
- **Record (durable memory)**: write 3–6 bullets into the relevant `.squad/history/*.md`.

### Stop conditions (non-negotiable)

- **Red tests / red CI**: stop and fix root cause (or revert/narrow scope). Never “leave it for later”.
- **Ambiguous requirement**: encode assumptions in `decisions.md` (and proceed).
- **Potential secrets / credentials**: stop; do not commit; remove and rotate if already leaked.
- **Large refactor temptation**: stop; split into a separate task/PR or explicitly justify in `decisions.md`.

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

### History note format (keep it consistent)

When you write to `.squad/history/*.md`, use this shape:

- **YYYY-MM-DD**: <what you did in one line>
  - **Change**: <1–3 bullets>
  - **Tests**: <exact command(s) + pass/fail>
  - **Risk/Follow-ups**: <1–2 bullets or “none”>

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

