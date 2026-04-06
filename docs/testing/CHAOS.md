# Chaos engineering (Gremlin-aligned)

The repository [gremlin/chaos-engineering-tools](https://github.com/gremlin/chaos-engineering-tools) is a curated list of practices and tools (incident management, observability, availability math). **Gremlin itself** runs controlled failure experiments against your infrastructure and requires a [Gremlin](https://www.gremlin.com) account, team configuration, and API credentials.

## What we do in-repo

- Document **recommended experiments** for ProfitFlow (below).
- Provide an optional **manual GitHub Actions workflow** (`.github/workflows/chaos-gremlin.yml`) that does **not** execute attacks without secrets — it is a checklist trigger for operators.

## What we do *not* do

- Store `GREMLIN_API_KEY` or team identifiers in the repository.
- Run destructive scenarios against production from CI without explicit approval and secret configuration.

## Suggested experiments (staging first)

1. **API process kill / CPU stress** on the Spring Boot workload while hitting `/actuator/health` and a read-only authenticated GET — expect graceful recovery or clear circuit behavior.
2. **Latency injection** on the database dependency — allocation runs should time out or surface structured errors, never corrupt partial state.
3. **Network loss** between UI and API — Angular client should show user-visible errors, not spin silently.

## Secrets (GitHub)

Configure in **Settings → Secrets and variables → Actions** (names illustrative; follow your Gremlin docs):

- `GREMLIN_API_KEY` — never log in workflow output.
- Any team / cluster identifiers your Gremlin integration requires.

## Further reading

- [Gremlin — Chaos Engineering](https://www.gremlin.com)
- [gremlin/chaos-engineering-tools](https://github.com/gremlin/chaos-engineering-tools)
