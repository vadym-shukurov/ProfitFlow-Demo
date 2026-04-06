## ProfitFlow agent team (repo-native)

This repository uses a lightweight, repository-native agent-team setup:

- **Charters**: `.squad/charters/` (who each agent is, and the rules of engagement)
- **History**: `.squad/history/` (what each agent has done; keep it short and high-signal)
- **Shared memory**: `decisions.md` (cross-cutting decisions and conventions)

### How to use this locally (optional)

If you want to use the open-source “Squad” CLI described by GitHub, you can install and initialize it:

```bash
npm install -g @bradygaster/squad-cli
squad init
```

Then align the generated Squad files with the charters in this folder, and keep `decisions.md` as the single shared decision log.

Reference article: `https://github.blog/ai-and-ml/github-copilot/how-squad-runs-coordinated-ai-agents-inside-your-repository/`.

