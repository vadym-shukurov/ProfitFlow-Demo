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
            String correlationId = sanitizeForLogs(MDC.get(CorrelationIdFilter.MDC_KEY));
            log.warn("Rate limit exceeded for IP={} path={} correlationId={}",
                    ip, path, correlationId);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", "60");
            response.getWriter().write(
                    "{\"message\":\"Too many requests — please wait before retrying.\","
                    + "\"correlationId\":\"" + correlationId + "\"}");
        }
    }

    private static String sanitizeForLogs(String value) {
        if (value == null || value.isBlank()) {
            return "n/a";
        }
        return allowlistedLogValue(value, 200);
    }

    private static String allowlistedLogValue(String value, int maxLen) {
        // Log-forging defense (CWE-117): keep logs single-line and parseable.
        // We use a strict allowlist and cap length to prevent amplification.
        StringBuilder out = new StringBuilder(Math.min(value.length(), maxLen));
        for (int i = 0; i < value.length() && out.length() < maxLen; i++) {
            char c = value.charAt(i);
            // Strip line breaks and other control chars completely.
            if (isControlChar(c)) {
                continue;
            }
            boolean allowed = isAllowedLogChar(c);
            out.append(allowed ? c : '_');
        }
        return out.toString();
    }

    private static boolean isControlChar(char c) {
        return c == '\r' || c == '\n' || c == '\t' || c == '\f' || c == 0;
    }

    private static boolean isAllowedLogChar(char c) {
        if (c >= 'a' && c <= 'z') {
            return true;
        }
        if (c >= 'A' && c <= 'Z') {
            return true;
        }
        if (c >= '0' && c <= '9') {
            return true;
        }
        return c == ' ' || c == '.' || c == '_' || c == ':' || c == '/' || c == '?' || c == '-';
    }
}
