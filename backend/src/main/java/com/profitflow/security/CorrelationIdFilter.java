package com.profitflow.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Assigns a unique correlation ID to every HTTP request and propagates it through:
 * <ol>
 *   <li>SLF4J MDC — every log line during this request includes
 *       {@code correlationId=<uuid>}</li>
 *   <li>{@code X-Correlation-Id} response header — clients can quote this ID
 *       in support tickets to allow exact log lookup</li>
 * </ol>
 *
 * <p>If the inbound request carries a trusted {@code X-Correlation-Id} header
 * (e.g. set by an upstream service mesh or load balancer) that value is re-used
 * after validation. An invalid or absent header causes a new server-generated ID
 * to be issued, preventing log-forging and log-injection attacks.
 *
 * <h2>Validation rules for inbound correlation IDs</h2>
 * <ul>
 *   <li>Max length: {@value #MAX_CID_LENGTH} characters</li>
 *   <li>Allowed characters: {@code A-Z a-z 0-9 - _} (alphanumeric and hyphen/underscore)</li>
 * </ul>
 * Any value that fails these checks is silently replaced with a fresh server-generated UUID.
 */
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Correlation-Id";
    public static final String MDC_KEY     = "correlationId";

    /** Maximum accepted length for a client-supplied correlation ID. */
    static final int MAX_CID_LENGTH = 128;

    /** Only alphanumeric characters plus hyphens and underscores are permitted. */
    private static final Pattern SAFE_CID = Pattern.compile("[A-Za-z0-9\\-_]{1," + MAX_CID_LENGTH + "}");

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        String correlationId = sanitize(request.getHeader(HEADER_NAME));

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER_NAME, correlationId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * Validates the candidate correlation ID from the inbound request.
     * Returns the value unchanged if it passes; returns a fresh UUID otherwise.
     *
     * @param candidate the raw header value, may be null
     * @return a safe correlation ID string
     */
    static String sanitize(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return UUID.randomUUID().toString();
        }
        if (SAFE_CID.matcher(candidate).matches()) {
            return candidate;
        }
        // Client-supplied ID failed validation — generate a server-side ID
        return UUID.randomUUID().toString();
    }
}
