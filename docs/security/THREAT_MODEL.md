# ProfitFlow — lightweight threat model

## Scope

Web API + Angular SPA for activity-based costing. Assumptions: TLS terminates at ingress; PostgreSQL is not internet-facing.

## Assets

- Financial allocation data (resource costs, rules, allocation results)
- Credentials and refresh tokens (hashed at rest)
- Audit log (immutability enforced in DB)
- JWT signing keys

## Threats and mitigations (summary)

| Threat | Mitigation |
|--------|------------|
| Credential stuffing | Rate limits on `/auth/*`, account lockout, generic 401 messages |
| Token theft (access) | Short TTL, JTI denylist on logout |
| Token theft (refresh) | Rotation; reuse of rotated token revokes **all** sessions + metric |
| JWT forgery | RS256, prod keys from env, `iss`/`aud` validation |
| SQL injection | JPA parameterised queries |
| XSS (SPA) | CSP, no dangerous DOM APIs in codebase; refresh in `sessionStorage` (XSS risk documented) |
| CSRF | Stateless Bearer API (no cookie session) |
| IP spoofing | Trusted-proxy model for `X-Forwarded-For` |
| Abuse / DoS | Rate limits (Redis optional multi-node), body size limits |
| Audit tampering | DB triggers prevent UPDATE/DELETE on `audit_log` |
| OpenAPI / schema disclosure | SpringDoc off by default; when on, `/v3/api-docs` and Swagger UI require **ADMIN** JWT unless `openapi-unauthenticated-access` is set (CI `openapi-export` profile only). Prod blocks the anonymous + SpringDoc combination at startup. |
| Shared-cache leakage of API JSON | `Cache-Control: no-store, private` and `Pragma: no-cache` on `/api/**` responses |
| Browser feature abuse | `Permissions-Policy` restricts camera, mic, geolocation, payment, USB, etc. |

## Residual risks

- **Per-node** limits unless Redis is enabled.
- **No built-in MFA** — enterprise deployments should front the app with an IdP (see `OIDC-MFA.md`).
- **Dependency CVEs** — CI runs Maven/npm audits; periodic review required.

## Assurance cadence (recommended)

- **Quarterly**: dependency updates, review of `TRUSTED_PROXIES` and secrets rotation.
- **Annual**: external penetration test, DAST (see `.github/workflows/dast.yml`).
- **Incident**: correlate `correlationId` with audit log and auth metrics.
