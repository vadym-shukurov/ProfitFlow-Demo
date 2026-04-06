## Copilot / agent instructions (ProfitFlow)

### Source of truth for context

- Read `README.md` first (architecture + test commands).
- Use `decisions.md` as shared, versioned memory.
- Follow role charters in `.squad/charters/`.

### Quality bar (non-negotiable)

- Keep CI green (see `.github/workflows/ci.yml` + `security.yml`).
- Do not commit secrets; `.env` and private keys must stay untracked.
- Prefer small PRs with clear test evidence.

### How to report work

- Update the relevant `.squad/history/*.md` file with:
  - what changed
  - commands executed
  - outcomes (pass/fail) and next steps

