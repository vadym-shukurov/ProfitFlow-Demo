package com.profitflow.security.ratelimit;

/**
 * Abstraction for IP + path based API rate limiting.
 *
 * <p>Production multi-node deployments should enable the Redis-backed implementation
 * via {@code profitflow.rate-limit.redis.enabled=true} (see ADR 001).
 */
public interface RateLimiterBackend {

    /**
     * Attempts to consume one token for the given client IP and request path.
     *
     * @param clientIp resolved client IP (from {@link com.profitflow.security.ClientIpResolverPort})
     * @param path     {@link jakarta.servlet.http.HttpServletRequest#getRequestURI()}
     * @return {@code true} if the request may proceed; {@code false} if rate limited
     */
    boolean tryConsume(String clientIp, String path);
}
