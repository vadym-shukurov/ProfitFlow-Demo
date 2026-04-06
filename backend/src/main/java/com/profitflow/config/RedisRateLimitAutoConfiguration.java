package com.profitflow.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Pulls in Spring Data Redis auto-configuration only when distributed rate limiting
 * is enabled. The main application excludes {@link RedisAutoConfiguration} by default
 * so local / test runs do not require a Redis server.
 */
@Configuration
@ConditionalOnProperty(name = "profitflow.rate-limit.redis.enabled", havingValue = "true")
@Import(RedisAutoConfiguration.class)
public class RedisRateLimitAutoConfiguration {
}
