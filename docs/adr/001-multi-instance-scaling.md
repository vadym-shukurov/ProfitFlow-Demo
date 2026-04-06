# ADR 001: Multi-instance scaling — rate limits, IP trust, revocation

## Status

Accepted

## Context

ProfitFlow runs as a stateless Spring Boot API behind nginx (Docker Compose or Kubernetes). Several mechanisms are **per JVM** by default:

- Bucket4j rate limits keyed by client IP
- Caffeine cache for JWT JTI revocation checks

Under horizontal scaling, **per-node rate limits** multiply effective abuse capacity (~N × limit). **Caffeine** does not share entries across nodes; the JTI denylist still **authorizes correctly** because `TokenRevocationService` persists to PostgreSQL and falls back to the database on cache miss.

## Decision

1. **Trusted proxies** — Set `TRUSTED_PROXIES` to the **ingress / nginx pod network** so `ClientIpResolver` trusts `X-Forwarded-For` only from real proxies. Docker Compose production sets this to the default bridge CIDR; tighten to your overlay CIDR in K8s.

2. **Distributed rate limiting** — When `profitflow.rate-limit.redis.enabled=true` and Redis is available, use `RedisRateLimiterBackend` (fixed-window counters in Redis). Otherwise use `InMemoryRateLimiterBackend` (single node).

3. **JWT revocation hot path** — Keep Caffeine + DB. For very high QPS multi-node deployments, optionally add a shared Redis cache for `jti` lookups (not implemented in code; same interface could wrap `TokenRevocationService`).

4. **JWT key rotation** — Support `RSA_PREVIOUS_PUBLIC_KEY_PEM` + `RSA_PREVIOUS_KEY_ID` so two public keys verify tokens while signing uses the current key and `JWT_SIGNING_KEY_ID` (`kid` header).

## Consequences

- Operators **must** configure `TRUSTED_PROXIES` correctly or all clients appear as one IP.
- Enabling Redis requires a reachable Redis instance; startup fails if Redis is enabled but unavailable (by design).
- Fixed-window Redis limits differ slightly from in-memory token buckets; limits remain approximate per minute per key.
