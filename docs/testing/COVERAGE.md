# Test coverage semantics

## End-to-end (Playwright)

Playwright does not measure JavaScript line coverage for the Angular app in this pipeline unless you add a separate instrumentation build. The **95% target** is therefore defined as **critical-path story coverage**:

- **Denominator:** 20 UI acceptance stories listed in `e2e/tests/ui/critical-flows.spec.ts` (`UI_STORY_IDS`).
- **Gate:** every automated story test in that file must pass in CI (20/20).

This matches how finance teams usually gate releases: enumerated user journeys and navigation depth, not Istanbul-style percentages.

## API (Playwright `request`)

Same approach:

- **Denominator:** 20 HTTP operations in `e2e/tests/api/critical-api.spec.ts` (health, auth lifecycle, catalogues, allocation run + history, AI suggest, OpenAPI).
- **Gate:** serial suite must pass end-to-end (20/20).

## Load (Artillery)

`e2e/artillery/probe.yml` is a **low-rate smoke** (health + anonymous 401 probe). It validates that the API tolerates light concurrent traffic without credential stuffing or hammering `POST /allocations/run` (rate limits).

## Backend profile for packaged E2E

CI and `e2e/scripts/ci-with-servers.sh` start the API with `--spring.profiles.active=e2e`, backed by `backend/src/main/resources/application-e2e.yml` (H2 in-memory, Flyway off, OpenAPI JSON enabled). JUnit integration tests keep using `@ActiveProfiles("test")` and `src/test/resources/application-test.yml`.

Build the API JAR with the Maven `e2e` profile so H2 is on the runtime classpath:

`mvn -f backend -Pe2e -DskipTests package`

The E2E script starts the API on **`API_PORT` (default `18080`)** so it does not collide with another process bound to `8080` (a collision previously caused health checks to hit the wrong server and return `404` on `/api/...`). The Angular dev server uses a generated proxy to the same base URL as `PLAYWRIGHT_API_BASE_URL`.

## Accessibility (Lighthouse CI)

`e2e/lighthouserc.cjs` asserts the **Accessibility category score ≥ 0.95** for `/login` against the dev server.

## Chaos (Gremlin)

Gremlin experiments are **environment-specific** and require Gremlin credentials. See [CHAOS.md](./CHAOS.md) and the [gremlin/chaos-engineering-tools](https://github.com/gremlin/chaos-engineering-tools) resource list.
