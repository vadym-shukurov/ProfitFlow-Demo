# ADR 002 — Architecture quality tier (boundaries, material traceability, contracts)

## Status

Accepted

## Context

The codebase is a single deployable (“modular monolith”) with hexagonal layering. To move toward a 9/10 architecture quality bar without premature multi-repo splits, we enforce boundaries in CI, clarify security-facing contracts, persist material calculation history, and gate API–TypeScript drift.

## Decision

1. **Package boundaries (ArchUnit)**  
   - `com.profitflow.domain..` must not depend on Spring, Jakarta persistence, Hibernate, or Jackson.  
   - `com.profitflow.application..` must not depend on `adapter`, `infrastructure`, or `security` packages.  
   Cache region names live in `application.cache.CacheNames` so services do not reference `CacheConfig`.

2. **Application-facing security contract**  
   - `@AuditedOperation` lives in `com.profitflow.application.audit`; `AuditAspect` remains in `security`.  
   - `CurrentUserPort` abstracts the authenticated principal for use cases that persist actor identity.

3. **Material allocation traceability**  
   - Each successful allocation run is stored in `allocation_run` (monotonic `run_number`, actor, input hash, JSON result).  
   - A `domain_event_outbox` row `ALLOCATION_RUN_COMPLETED` is written in the **same transaction** (outbox pattern). A future worker can publish to messaging without coupling the domain to Kafka/SNS.

4. **Explicit API versioning**  
   - REST remains under `/api/v1/`.  
   - Responses set `X-API-Version: 1`. OpenAPI `info.version` is `1` when SpringDoc is enabled.

5. **Contract-driven clients**  
   - CI exports OpenAPI and runs `npm run gen:api-types`; `git diff` on `api-types.generated.ts` fails the pipeline if the committed types drift.

6. **AI governance (application layer)**  
   - Configurable `profitflow.ai.max-input-chars` with `InvalidInputException` when exceeded.  
   - `AiAllocatorPort` documents production expectations (timeout, PII, prompt injection, resilience).

## Consequences

- Allocation runs are **read+write** in one transaction (audit + history + outbox).  
- Optional next steps: extract `profitflow-domain` as a JAR module; add an outbox dispatcher; add approval workflow state machines without changing the core engine.
