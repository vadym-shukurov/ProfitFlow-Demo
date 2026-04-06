package com.profitflow.application.port.out;

/**
 * Resolves the authenticated principal for auditing and persisted material runs.
 *
 * <p>Implemented in the security adapter; application services must not touch
 * {@code SecurityContextHolder} directly.
 */
public interface CurrentUserPort {

    /**
     * Username from the security context, or {@code "system"} if none (should not
     * occur for JWT-protected API calls).
     */
    String currentUsernameOrSystem();
}
