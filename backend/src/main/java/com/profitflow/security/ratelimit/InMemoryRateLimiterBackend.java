package com.profitflow.security.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process token-bucket rate limiter (single JVM). Default when Redis is not enabled.
 */
@Component
@ConditionalOnProperty(name = "profitflow.rate-limit.redis.enabled", havingValue = "false",
                       matchIfMissing = true)
public class InMemoryRateLimiterBackend implements RateLimiterBackend {

    private final long authRateLimit;
    private final long runRateLimit;
    private final long generalRateLimit;

    private final Map<String, Bucket> authBuckets    = new ConcurrentHashMap<>();
    private final Map<String, Bucket> runBuckets     = new ConcurrentHashMap<>();
    private final Map<String, Bucket> generalBuckets = new ConcurrentHashMap<>();

    public InMemoryRateLimiterBackend(
            @Value("${profitflow.security.rate-limit.auth:10}")    long authRateLimit,
            @Value("${profitflow.security.rate-limit.run:5}")      long runRateLimit,
            @Value("${profitflow.security.rate-limit.general:120}") long generalRateLimit) {
        this.authRateLimit    = authRateLimit;
        this.runRateLimit     = runRateLimit;
        this.generalRateLimit = generalRateLimit;
    }

    @Override
    public boolean tryConsume(String clientIp, String path) {
        return selectBucket(clientIp, path).tryConsume(1);
    }

    private Bucket selectBucket(String ip, String path) {
        if (path.startsWith("/api/v1/auth/")) {
            return authBuckets.computeIfAbsent(ip,
                    k -> buildBucket(authRateLimit, Duration.ofMinutes(1)));
        }
        if (path.equals("/api/v1/allocations/run")) {
            return runBuckets.computeIfAbsent(ip,
                    k -> buildBucket(runRateLimit, Duration.ofMinutes(1)));
        }
        return generalBuckets.computeIfAbsent(ip,
                k -> buildBucket(generalRateLimit, Duration.ofMinutes(1)));
    }

    private static Bucket buildBucket(long capacity, Duration refillPeriod) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, refillPeriod)
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
