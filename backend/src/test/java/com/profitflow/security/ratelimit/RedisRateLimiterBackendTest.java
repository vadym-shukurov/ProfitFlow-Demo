package com.profitflow.security.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RedisRateLimiterBackend}. Uses a lightweight
 * {@link StringRedisTemplate} subclass because Mockito cannot instrument
 * {@link StringRedisTemplate} on some JVM versions.
 */
@ExtendWith(MockitoExtension.class)
class RedisRateLimiterBackendTest {

    @Mock
    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> valueOps;

    private FakeRedis redis;
    private RedisRateLimiterBackend backend;

    @BeforeEach
    void setUp() {
        redis = new FakeRedis(valueOps);
        backend = new RedisRateLimiterBackend(redis, 10L, 5L, 120L);
    }

    @Test
    void firstIncrementSetsExpiryAndAllows() {
        when(valueOps.increment(anyString())).thenReturn(1L);

        assertThat(backend.tryConsume("10.0.0.1", "/api/v1/auth/login")).isTrue();

        assertThat(redis.expireCalls.get()).isEqualTo(1);
    }

    @Test
    void secondIncrementDoesNotCallExpireAgain() {
        when(valueOps.increment(anyString())).thenReturn(2L);

        assertThat(backend.tryConsume("10.0.0.2", "/api/v1/other")).isTrue();

        assertThat(redis.expireCalls.get()).isZero();
    }

    @Test
    void overAuthLimitDenied() {
        when(valueOps.increment(anyString())).thenReturn(11L);

        assertThat(backend.tryConsume("1.1.1.1", "/api/v1/auth/login")).isFalse();
    }

    @Test
    void authLimitBoundaryAllows() {
        when(valueOps.increment(anyString())).thenReturn(10L);

        assertThat(backend.tryConsume("5.5.5.5", "/api/v1/auth/login")).isTrue();
    }

    @Test
    void runPathUsesRunLimit() {
        when(valueOps.increment(anyString())).thenReturn(6L);

        assertThat(backend.tryConsume("2.2.2.2", "/api/v1/allocations/run")).isFalse();
    }

    @Test
    void generalPathUsesGeneralLimit() {
        when(valueOps.increment(anyString())).thenReturn(121L);

        assertThat(backend.tryConsume("3.3.3.3", "/api/v1/resources/costs")).isFalse();
    }

    @Test
    void nullCountDenied() {
        when(valueOps.increment(anyString())).thenReturn(null);

        assertThat(backend.tryConsume("4.4.4.4", "/api/v1/resources/costs")).isFalse();
    }

    private static final class FakeRedis extends StringRedisTemplate {

        private final ValueOperations<String, String> ops;
        private final AtomicInteger expireCalls = new AtomicInteger();

        private FakeRedis(ValueOperations<String, String> ops) {
            this.ops = ops;
        }

        @Override
        public ValueOperations<String, String> opsForValue() {
            return ops;
        }

        @Override
        public Boolean expire(String key, Duration timeout) {
            expireCalls.incrementAndGet();
            return Boolean.TRUE;
        }
    }
}
