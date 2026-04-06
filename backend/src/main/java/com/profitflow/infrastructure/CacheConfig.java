package com.profitflow.infrastructure;

import com.profitflow.application.cache.CacheNames;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * In-process Caffeine cache configuration.
 *
 * <h2>Cache names and policies</h2>
 * <table border="1">
 *   <caption>Cache configuration</caption>
 *   <tr><th>Cache</th><th>TTL</th><th>Max entries</th><th>Rationale</th></tr>
 *   <tr><td>{@value com.profitflow.application.cache.CacheNames#RESOURCE_COSTS}</td><td>60 s</td><td>500</td>
 *       <td>Full list; evicted on any write mutation</td></tr>
 *   <tr><td>{@value com.profitflow.application.cache.CacheNames#ACTIVITIES}</td><td>60 s</td><td>500</td>
 *       <td>Reference data; rarely changes</td></tr>
 *   <tr><td>{@value com.profitflow.application.cache.CacheNames#PRODUCTS}</td><td>60 s</td><td>500</td>
 *       <td>Reference data; rarely changes</td></tr>
 * </table>
 *
 * <h2>High-traffic behaviour</h2>
 * Under concurrent load, all three cached endpoints are hit for every page load
 * of the Allocation Rules form. The 60-second TTL reduces database round-trips
 * from O(concurrent_users) to O(1/60s) with at most 1-minute stale-read windows.
 *
 * <h2>Multi-node note</h2>
 * Caffeine is a per-JVM cache. In a multi-node deployment, replace it with a
 * Redis-backed {@link CacheManager} using Spring Data Redis. Cache eviction
 * must then be propagated via pub/sub or keyspace notifications.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
                CacheNames.RESOURCE_COSTS, CacheNames.ACTIVITIES, CacheNames.PRODUCTS);

        manager.setCaffeine(
                Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofSeconds(60))
                        .maximumSize(500)
                        .recordStats()   // Enables Micrometer cache hit/miss metrics
        );

        manager.setAllowNullValues(false);
        return manager;
    }
}
