# OIDC / SAML and MFA (enterprise identity)

## Current default

ProfitFlow issues **local JWTs** after `POST /api/v1/auth/login` (username/password). This is appropriate for demos and small deployments.

## Enterprise pattern (recommended for regulated finance)

1. **Identity provider** — Azure Entra ID, Okta, Keycloak, or Ping with **MFA enforced at the IdP** for all users (especially `ADMIN`).
2. **Protocols** — Prefer **OIDC** (OAuth 2.0 + OIDC) for greenfield; SAML 2.0 where legacy IdPs require it.
3. **Application changes** (high level):
   - **Option A — Resource server only**: Validate access tokens issued by the IdP (`spring.security.oauth2.resourceserver.jwt.issuer-uri`). Requires aligning `aud`/`roles` claims with Spring Security’s `JwtAuthenticationConverter` or a custom converter mapping IdP groups → `ROLE_*`.
   - **Option B — BFF**: Browser talks only to a backend-for-frontend; refresh tokens stay in **httpOnly** cookies; CSRF protections apply. Strongest against XSS stealing refresh tokens.
4. **Step-up / sensitive actions**: Use IdP **conditional access** (e.g. re-auth for “high” sign-in risk) or separate OAuth2 scope for allocation run / bulk import.

## MFA

Do **not** implement MFA inside ProfitFlow’s password table for enterprise use. Delegate to the IdP so policies, hardware keys, and compliance reporting stay centralized.

## Configuration sketch (Option A)

```yaml
# Example only — enable when moving to IdP-issued JWTs
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://login.microsoftonline.com/<tenant>/v2.0
```

A custom `SecurityFilterChain` or claim mapping is required so `roles` align with existing `@PreAuthorize` / `SecurityConfig` matchers.

## This repository

Local JWT issuance remains the default. Treat this document as the **integration runbook** for security review with your IdP team.
