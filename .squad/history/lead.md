## 2026-04-08

- **2026-04-08**: Industrial-grade quality enforcement + doc drift fix
  - **Change**:
    - Enforced repo-wide quality thresholds (coverage ≥ 85%, duplication < 5%, ratings A/A/A) via Sonar measures in CI workflow.
    - Fixed docs drift in `frontend/README.md` (Angular version).
  - **Tests**:
    - `./scripts/check-all.sh` (pass; run outside sandbox to avoid CPD permissions issue)
  - **Risk/Follow-ups**:
    - Sonar enforcement step requires `SONAR_TOKEN` + `SONAR_PROJECT_KEY` (and org/host depending on mode); if secrets are absent the existing workflow already skips analysis.

