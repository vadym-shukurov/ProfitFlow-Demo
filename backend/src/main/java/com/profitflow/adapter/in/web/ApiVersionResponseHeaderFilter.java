package com.profitflow.adapter.in.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Exposes the public API revision on every {@code /api/v1/**} response for client debugging
 * and future multi-version routing.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ApiVersionResponseHeaderFilter extends OncePerRequestFilter {

    static final String HEADER_NAME  = "X-API-Version";
    static final String VERSION_ONE  = "1";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        filterChain.doFilter(request, response);
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/api/v1/")) {
            response.setHeader(HEADER_NAME, VERSION_ONE);
        }
    }
}
