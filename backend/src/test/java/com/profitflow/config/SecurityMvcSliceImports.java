package com.profitflow.config;

import com.profitflow.security.ClientIpResolver;
import com.profitflow.security.CsrfTokenResponseHeaderFilter;
import com.profitflow.security.CorrelationIdFilter;
import com.profitflow.security.RateLimitingFilter;
import com.profitflow.security.RsaKeyLoader;
import com.profitflow.security.SensitiveApiCacheHeaderFilter;
import com.profitflow.security.RsaKeyProperties;
import com.profitflow.security.ratelimit.InMemoryRateLimiterBackend;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Supplies beans required by {@link com.profitflow.security.SecurityConfig} in
 * {@code @WebMvcTest} slices (filters, in-memory rate limiter, JWT key material).
 */
@Configuration
@EnableConfigurationProperties(RsaKeyProperties.class)
@Import({
        ClientIpResolver.class,
        CsrfTokenResponseHeaderFilter.class,
        CorrelationIdFilter.class,
        InMemoryRateLimiterBackend.class,
        RateLimitingFilter.class,
        RsaKeyLoader.class,
        SensitiveApiCacheHeaderFilter.class
})
public class SecurityMvcSliceImports {
}
