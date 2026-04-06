package com.profitflow.security.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Distributed fixed-window rate limiter using Redis INCR. One counter per minute bucket
 * per category and client IP — aligned across all API instances.
 */
@Component
@ConditionalOnProperty(name = "profitflow.rate-limit.redis.enabled", havingValue = "true")
public class RedisRateLimiterBackend implements RateLimiterBackend {

    private static final String KEY_PREFIX = "profitflow:rl:";

    private final StringRedisTemplate redis;
    private final long authRateLimit;
    private final long runRateLimit;
    private final long generalRateLimit;

    public RedisRateLimiterBackend(
            StringRedisTemplate redis,
            @Value("${profitflow.security.rate-limit.auth:10}")    long authRateLimit,
            @Value("${profitflow.security.rate-limit.run:5}")      long runRateLimit,
            @Value("${profitflow.security.rate-limit.general:120}") long generalRateLimit) {
        this.redis            = redis;
        this.authRateLimit    = authRateLimit;
        this.runRateLimit     = runRateLimit;
        this.generalRateLimit = generalRateLimit;
    }

    @Override
    public boolean tryConsume(String clientIp, String path) {
        String category = categoryForPath(path);
        long limit      = limitForCategory(category);
        long minute     = Instant.now().getEpochSecond() / 60;
        String key      = KEY_PREFIX + category + ":" + clientIp + ":" + minute;

        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, Duration.ofMinutes(2));
        }
        return count != null && count <= limit;
    }

    private static String categoryForPath(String path) {
        if (path.startsWith("/api/v1/auth/")) {
            return "auth";
        }
        if (path.equals("/api/v1/allocations/run")) {
            return "run";
        }
        return "general";
    }

    private long limitForCategory(String category) {
        return switch (category) {
            case "auth" -> authRateLimit;
            case "run" -> runRateLimit;
            default -> generalRateLimit;
        };
    }
}
