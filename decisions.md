---
title: ProfitFlow Decisions (shared agent memory)
owner: Engineering
last_updated: 2026-04-06
---

This file is the **shared, versioned memory** for humans and agents working in this repository.

Add entries when you make a decision that should persist beyond a single PR (tooling, conventions, architectural choices, CI gates, etc.).

## Decision log

### 2026-04-06 — Repository-native agent team

- **Decision**: Adopt a repository-native “drop-box memory” pattern for coordinated AI work.
- **Why**: Keep context **inspectable, predictable, and durable** across sessions/agents, without relying on hidden chat history.
- **How**:
  - Charters live in `.squad/charters/`.
  - Each agent writes progress/notes in `.squad/history/`.
  - Cross-cutting decisions (like this one) go in `decisions.md`.
- **Source**: Inspired by GitHub’s write-up on Squad orchestration (see: `https://github.blog/ai-and-ml/github-copilot/how-squad-runs-coordinated-ai-agents-inside-your-repository/`).

### 2026-04-07 — Single workflow contract + stop-ship rules

- **Decision**: Standardize all agent work on a single pipeline (think → plan → build → review → test → document → record) with explicit stop conditions.
- **Why**: Reduce “agent drift” (inconsistent behavior across sessions), improve merge readiness, and prevent half-finished work from accumulating.
- **How**:
  - Treat any failing tests/CI as a stop-ship.
  - Encode ambiguity as assumptions in `decisions.md`, then proceed.
  - Require a short `.squad/history/*` note for every non-trivial change (what/tests/risks).
- **Source**: Inspired by `gstack`’s role + pipeline framing (skills like plan/review/qa/ship) and its emphasis on operational guardrails.

