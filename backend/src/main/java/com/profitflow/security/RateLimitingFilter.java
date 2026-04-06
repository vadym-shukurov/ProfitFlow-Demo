package com.profitflow.security;

import com.profitflow.security.ratelimit.RateLimiterBackend;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * IP-level rate limiting delegating to {@link RateLimiterBackend}.
 *
 * <p>Default backend is in-memory Bucket4j. Enable {@code profitflow.rate-limit.redis.enabled}
 * for Redis-backed limits across nodes (see {@code docs/adr/001-multi-instance-scaling.md}).
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    private final ClientIpResolverPort clientIpResolver;
    private final RateLimiterBackend   rateLimiterBackend;

    public RateLimitingFilter(ClientIpResolverPort clientIpResolver,
                              RateLimiterBackend rateLimiterBackend) {
        this.clientIpResolver   = clientIpResolver;
        this.rateLimiterBackend = rateLimiterBackend;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        String ipRaw = clientIpResolver.resolve(request);
        String pathRaw = request.getRequestURI();
        String ip = sanitizeForLogs(ipRaw);
        String path = sanitizeForLogs(pathRaw);

        if (rateLimiterBackend.tryConsume(ip, path)) {
            chain.doFilter(request, response);
        } else {
            String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
            log.warn("Rate limit exceeded for IP={} path={} correlationId={}",
                    ip, path, correlationId);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", "60");
            String cid = correlationId != null ? correlationId : "n/a";
            response.getWriter().write(
                    "{\"message\":\"Too many requests — please wait before retrying.\","
                    + "\"correlationId\":\"" + cid + "\"}");
        }
    }

    private static String sanitizeForLogs(String value) {
        if (value == null) return "n/a";
        // Prevent log forging by stripping control characters.
        return value.replaceAll("[\\r\\n\\t\\0\\f]", "");
    }
}
