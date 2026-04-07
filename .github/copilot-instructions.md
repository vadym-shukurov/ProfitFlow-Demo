## Copilot / agent instructions (ProfitFlow)

### Source of truth for context

- Read `README.md` first (architecture + test commands).
- Use `decisions.md` as shared, versioned memory.
- Follow role charters in `.squad/charters/`.

### Quality bar (non-negotiable)

- Keep CI green (see `.github/workflows/ci.yml` + `security.yml`).
- Do not commit secrets; `.env` and private keys must stay untracked.
- Prefer small PRs with clear test evidence.

### How agents should run autonomously (the contract)

- Start every task by reading your charter in `.squad/charters/<role>.md` and the relevant `.squad/history/<role>.md`.
- Execute work as a loop: **run checks → fix root cause → re-run checks → record results**.
- If a requirement is ambiguous, encode assumptions in `decisions.md` and proceed.
- Treat any failing tests/CI as **stop-ship** (no partial completion).

### How to report work

- Update the relevant `.squad/history/*.md` file with:
  - what changed
  - commands executed
  - outcomes (pass/fail) and next steps

