package com.profitflow.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Port for resolving the true client IP address from HTTP requests.
 *
 * <p>Implemented by {@link ClientIpResolver}, which applies a trusted-proxy model.
 * Extracted as an interface so that:
 * <ul>
 *   <li>Tests can substitute a simple stub without needing to instrument the
 *       concrete class (required on Java 21+ with strict JVM encapsulation where
 *       Mockito's inline mocking cannot subclass all concrete classes).</li>
 *   <li>Alternative implementations (e.g. for AWS ALB or Cloudflare) can be
 *       provided without touching the consuming classes.</li>
 * </ul>
 */
public interface ClientIpResolverPort {

    /**
     * Resolves the client IP from the given HTTP request.
     *
     * @param request the current HTTP request
     * @return best-effort client IP string; never {@code null}
     */
    String resolve(HttpServletRequest request);

    /**
     * Resolves the client IP from the active Spring Web request context.
     * Safe to call outside of a servlet thread — returns {@code null} if no
     * request context is available (e.g. async tasks, scheduled jobs).
     *
     * @return client IP or {@code null} if no request context is active
     */
    String resolveFromContext();
}
