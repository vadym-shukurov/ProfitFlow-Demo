# ProfitFlow - Executive-ready Activity‑Based Costing

ProfitFlow turns **general ledger spend** into a **decision‑grade view of unit economics**.  
It runs a two-stage Activity‑Based Costing (ABC) allocation and visualizes the complete cost chain as a **Sankey graph**:

- **Flow**: Resources (GL spend) → Activities (drivers) → Products/Services (unit economics)

## Table of contents

1. [Why this product exists](#why-this-product-exists-pm-overview)
2. [What’s inside](#whats-inside-product-tour)
3. [Quickstart](#quickstart-local)
4. [Core allocation algorithm](#how-the-core-allocation-algorithm-works)
5. [Architecture](#architecture-engineering-view)
6. [Configuration](#configuration-env-vars--ports)
7. [Testing](#testing)
8. [API](#api-high-level)
9. [Repo layout](#repo-layout)
10. [Roadmap](#roadmap-product-minded)

---

## Why this product exists (PM overview)

Most organizations can answer “what did we spend?” but struggle to answer “**what does it cost to deliver Product X**?” because costs sit in the GL while delivery happens through activities (support, infra, onboarding, etc.).

ProfitFlow helps finance leaders:

- **Explain costs, not just report them**: every dollar is traceable from a resource line to an activity driver to a product.
- **Standardize allocation governance**: allocation rules are explicit, validated, and auditable.
- **Drive action**: spotlight cost hubs and cost-to-serve differences across products/services.

### Ideal users

- **CFO / FP&A**: product profitability, cost-to-serve, scenario conversations
- **Finance managers**: driver model ownership, allocation control, close readiness
- **Ops / Eng**: cost accountability with an explicit causal chain

---

## What’s inside (product tour)

- **CFO Dashboard**: run allocation and explore a Sankey (Resources → Activities → Products).
- **Cost Ledger**: manage resource costs (GL-like line items).
- **Allocation Rules**:
  - **Stage 1**: Resource → Activity (driver weights)
  - **Stage 2**: Activity → Product (driver weights)
- **AI Allocator (mock)**: demonstrates how suggestion workflows can be integrated without coupling the domain to an LLM.
- **Observability**: Prometheus metrics + Grafana dashboards (local stack under `monitoring/`).

---

## Quickstart (local)

### Prereqs

- **Java 21** (CI baseline)
- **Node.js** (project includes `frontend/` + `e2e/`)
- **Docker** (optional for basic dev; required for integration tests and the full local stack)

### Run the app (frontend + backend)

Start services (Postgres and any monitoring stack defined in compose):

```bash
cp .env.example .env
# edit .env (POSTGRES_PASSWORD is required)
docker compose up -d
```

Run backend:

```bash
cd backend && mvn spring-boot:run
```

Run frontend:

```bash
cd frontend && npm install && npx ng serve --host 127.0.0.1 --port 4200
```

Open `http://127.0.0.1:4200`.

### Demo credentials

- **admin / Admin1234!** (ADMIN)
- **manager / Manager123!** (FINANCE_MANAGER)
- **analyst / Analyst123!** (ANALYST)

---

## How the core allocation algorithm works

The core allocation engine is implemented in pure Java under:

- `backend/src/main/java/com/profitflow/domain/allocation/AllocationEngine.java`
- `backend/src/main/java/com/profitflow/domain/allocation/ProportionalAllocator.java`

It is **stateless** and built around two explicit ABC stages.

### Data model (conceptual)

1. **Resources** represent spend lines (e.g., AWS, Zendesk, Servers).
2. **Activities** represent how the organization delivers value (e.g., Support, Infrastructure).
3. **Products** represent what you sell/ship.

The engine produces:

- **Per-activity totals** after stage 1
- **Per-product totals** after stage 2
- A full list of **directed flow edges** for Sankey visualization

### Stage inputs

The engine consumes two lists:

- **Stage 1** inputs: `List<ResourceStageInput>`
  - Each item is one resource cost + a non-empty list of activity driver shares.
  - Type: `ResourceStageInput(resource, toActivities)`

- **Stage 2** inputs: `List<ActivityStageInput>`
  - Each item is one activity + a non-empty list of product driver shares.
  - Type: `ActivityStageInput(activityId, toProducts)`

Driver shares are represented as:

- `DriverShare(targetId, weight)` where `weight >= 0`

### Stage 1 — Resources → Activities

For each `ResourceStageInput`:

1. Take the resource amount \(A_r\).
2. Split \(A_r\) across target activities proportionally by weights using:
   - `ProportionalAllocator.split(resource.amount(), toActivities)`
3. Aggregate activity totals (an activity can receive cost from many resources).
4. Record flow edges:
   - `ResourceNode(resourceId) → ActivityNode(activityId)` with the allocated `Money`.

### Stage 2 — Activities → Products

For each activity that received non-zero cost in stage 1:

1. Find a matching `ActivityStageInput` by `activityId`.
2. If missing, `AllocationEngine` throws `AllocationDomainException`:
   - **A funded activity must have stage‑2 rules** so product costs can be computed.
3. Split the activity’s loaded cost across products using `ProportionalAllocator.split(...)`.
4. Aggregate product totals.
5. Record flow edges:
   - `ActivityNode(activityId) → ProductNode(productId)` with the allocated `Money`.

### Proportional split — the exact rounding rule (cash conservation)

`ProportionalAllocator.split(total, shares)` enforces:

- **Unique target IDs** (duplicates are rejected)
- **Positive sum of weights**
- **Cash conservation**: outputs always sum to the input total
- **Determinism**: for an ordered share list, the output is stable; the remainder always goes to the last share

Algorithm:

1. Compute \(W = \sum w_i\).
2. For each share except the last:
   - compute \(raw_i = total \times w_i / W\) using higher precision
   - round to the currency scale (e.g., 2 decimals for USD)
3. The **last share** receives the remainder:

\[
last = total - \sum_{i=1}^{n-1} rounded_i
\]

This eliminates “penny drift” in financial reporting.

### Worked example (USD)

Resource `AWS` = **$100.00** split into activities:

- Support weight 1
- Infrastructure weight 2

\(W = 3\)

- Support: round(\(100×1/3\)) = **$33.33**
- Infrastructure (last): remainder \(100.00 − 33.33\) = **$66.67**

If Infrastructure is then split into products:

- Product A weight 1
- Product B weight 1

\(W = 2\)

- Product A: **$33.34**
- Product B (last): **$33.33**

Totals still equal the original $100.00.

---

## Architecture (engineering view)

ProfitFlow follows **Hexagonal Architecture (Ports & Adapters)** so the algorithm stays testable and framework-free:

```
Angular UI → REST Controllers → Application Services → Domain Engine → Persistence / External ports
```

- **Domain** (`backend/.../domain/**`): pure Java allocation math and value objects
- **Application** (`backend/.../application/**`): orchestration, transactions, ports
- **Adapters**
  - `adapter.in.web`: REST controllers + DTOs
  - `adapter.out.persistence`: JPA repositories + mappers
  - `adapter.out.ai`: mock AI adapter

---

## Configuration (env vars & ports)

ProfitFlow is configured via environment variables. See `.env.example` (copy to `.env`) and `backend/src/main/resources/application.yml`.

### Required for `docker compose up`

- **`POSTGRES_PASSWORD`**: required (Compose will fail fast without it)

### Common app settings

- **Database**
  - **`DB_URL`**: default `jdbc:postgresql://localhost:5432/profitflow`
  - **`DB_USERNAME`**: default `profitflow`
  - **`DB_PASSWORD`**: blank by default (warns in non-prod; prod profile blocks)
- **Server**
  - **`SERVER_PORT`**: default `8080`
- **CORS**
  - **`ALLOWED_ORIGINS`**: set to your UI origin(s). Local dev supports `http://localhost:4200` and `http://127.0.0.1:4200`.

### Local observability endpoints

From `docker-compose.yml`:

- **Prometheus**: `http://127.0.0.1:9090`
- **Grafana**: `http://127.0.0.1:3001` (user `admin`, password `GF_ADMIN_PASSWORD` or default `profitflow`)

---

## Testing

### Backend

Unit tests (fast, no Docker):

```bash
cd backend && mvn verify -DskipITs=true
```

Integration tests (Testcontainers; **requires Docker**):

```bash
cd backend && mvn verify -DskipITs=false
```

Notes:
- **Docker required**: integration tests use Testcontainers and will fail without a Docker engine.
- **JDK parity**: CI uses **Java 21**. Local builds on newer JDKs (e.g. 25) are supported, but static analysis depends on tooling versions (SpotBugs is pinned accordingly).

### Frontend

```bash
cd frontend && npx ng test --watch=false --browsers=ChromeHeadless --code-coverage
```

### E2E (Playwright + Artillery; optional Lighthouse)

```bash
cd e2e && npm run typecheck
cd e2e && ./scripts/ci-with-servers.sh
```

Note: Lighthouse can fail on Apple Silicon when using an **x64 Node** binary (Rosetta). The script auto-skips LHCI in that local setup; CI (Linux) still runs it.

What `./scripts/ci-with-servers.sh` does:
- Runs a packaged backend JAR with `--spring.profiles.active=e2e` on **API_PORT=18080** (default)
- Starts Angular dev server on `127.0.0.1:4200` with a temporary proxy to the API
- Runs Playwright UI + API tests
- Runs LHCI (unless auto-skipped locally) and Artillery probe
- Writes API/UI logs to temp files **only on failure**

---

## API (high-level)

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/allocations/run`
- `GET/PUT /api/v1/rules/resource-to-activity`
- `GET/PUT /api/v1/rules/activity-to-product`

---

## Repo layout

```
backend/     Spring Boot API + domain allocation engine
frontend/    Angular SPA (Sankey, rule editor, ledger)
e2e/         Playwright + LHCI + Artillery scripts
monitoring/  Prometheus + Grafana provisioning
docs/        ADRs, security docs, testing docs
```

---

## Roadmap (product-minded)

- **Scenario comparison**: save snapshots and diff product costs across driver models
- **Real AI adapter**: replace mock suggestions with a production LLM integration
- **Allocation governance**: rule versioning, approvals, and change impact previews
- **Data onboarding**: CSV mapping UI + validation for ledger imports

