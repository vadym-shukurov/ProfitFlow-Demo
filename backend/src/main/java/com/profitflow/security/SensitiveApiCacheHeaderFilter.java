package com.profitflow.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Prevents shared caches (proxies, browsers) from storing JSON API responses that may
 * contain financial or personal data.
 *
 * <p>Does not apply to static assets or actuator health probes.
 */
@Component
public class SensitiveApiCacheHeaderFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        filterChain.doFilter(request, response);

        String path = request.getRequestURI();
        if (path != null && path.startsWith("/api/")) {
            response.setHeader("Cache-Control", "no-store, private, max-age=0");
            response.setHeader("Pragma", "no-cache");
        }
    }
}
