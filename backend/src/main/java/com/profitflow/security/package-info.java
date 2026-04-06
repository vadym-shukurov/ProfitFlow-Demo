/**
 * Cross-cutting security for a finance-grade API: JWT issuance and validation (RS256),
 * refresh-token lifecycle (rotation, revocation, reuse detection), RBAC enforcement,
 * rate limiting, trusted-proxy client IP resolution, audit hooks, and environment
 * validation for production deployments.
 *
 * <p>Controllers remain thin; authorization rules are centralized in
 * {@link com.profitflow.security.SecurityConfig} and method-level
 * {@code @PreAuthorize} where appropriate. Token denial and refresh storage are
 * designed so a stolen refresh token cannot be replayed without detection.
 */
package com.profitflow.security;
