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
import java.util.regex.Pattern;

/**
 * IP-level rate limiting delegating to {@link RateLimiterBackend}.
 *
 * <p>Default backend is in-memory Bucket4j. Enable {@code profitflow.rate-limit.redis.enabled}
 * for Redis-backed limits across nodes (see {@code docs/adr/001-multi-instance-scaling.md}).
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);
    private static final Pattern LOG_SAFE = Pattern.compile("[A-Za-z0-9 ._:/?\\-]{1,200}");

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
        if (value == null) {
            return "n/a";
        }
        // Prevent log forging and keep logs parseable:
        // - remove control chars
        // - allowlist a small set of characters (drop everything else)
        // - cap length to avoid unbounded log amplification
        String stripped = value.replaceAll("[\\r\\n\\t\\u0000\\f]", "");
        String candidate = stripped.length() > 200 ? stripped.substring(0, 200) : stripped;
        if (LOG_SAFE.matcher(candidate).matches()) {
            return candidate;
        }
        return candidate.replaceAll("[^A-Za-z0-9 ._:/?\\-]", "_");
    }
}
