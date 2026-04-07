package com.profitflow.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DeferredCsrfToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Echoes the current {@link CsrfToken} value in {@value #CSRF_TOKEN_HEADER} so browser
 * clients can send the double-submit header without reading the HttpOnly {@code XSRF-TOKEN}
 * cookie (Sonar java:S3330).
 */
@Component
public class CsrfTokenResponseHeaderFilter extends OncePerRequestFilter {

    /** Name of the double-submit header (Spring default for cookie CSRF). */
    public static final String CSRF_TOKEN_HEADER = "X-XSRF-TOKEN";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        CsrfToken token = resolveCsrfToken(request);
        if (token != null) {
            String value = token.getToken();
            if (StringUtils.hasText(value)) {
                response.setHeader(CSRF_TOKEN_HEADER, value);
            }
        }
        filterChain.doFilter(request, response);
    }

    private static CsrfToken resolveCsrfToken(HttpServletRequest request) {
        Object deferred = request.getAttribute(DeferredCsrfToken.class.getName());
        if (deferred instanceof DeferredCsrfToken d) {
            return d.get();
        }
        Object direct = request.getAttribute(CsrfToken.class.getName());
        if (direct instanceof CsrfToken t) {
            return t;
        }
        Object legacy = request.getAttribute("_csrf");
        if (legacy instanceof CsrfToken t) {
            return t;
        }
        return null;
    }
}
