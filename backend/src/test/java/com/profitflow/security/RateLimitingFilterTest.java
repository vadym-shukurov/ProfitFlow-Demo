package com.profitflow.security;

import com.profitflow.security.ratelimit.RateLimiterBackend;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RateLimitingFilterTest {

    @AfterEach
    void clearMdc() {
        org.slf4j.MDC.clear();
    }

    @Test
    void allowsRequestWhenBackendGrantsToken() throws Exception {
        ClientIpResolverPort ipResolver = new ClientIpResolverPort() {
            @Override
            public String resolve(HttpServletRequest request) {
                return "127.0.0.1";
            }

            @Override
            public String resolveFromContext() {
                return null;
            }
        };
        RateLimiterBackend backend = mock(RateLimiterBackend.class);
        when(backend.tryConsume("127.0.0.1", "/api/v1/activities")).thenReturn(true);

        RateLimitingFilter filter = new RateLimitingFilter(ipResolver, backend);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/activities");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isNotEqualTo(429);
    }

    @Test
    void returns429JsonWhenRateLimitedAndIncludesCorrelationIdWhenPresent() throws Exception {
        ClientIpResolverPort ipResolver = new ClientIpResolverPort() {
            @Override
            public String resolve(HttpServletRequest request) {
                return "127.0.0.1\r\nforged";
            }

            @Override
            public String resolveFromContext() {
                return null;
            }
        };
        RateLimiterBackend backend = mock(RateLimiterBackend.class);
        when(backend.tryConsume(anyString(), anyString())).thenReturn(false);

        org.slf4j.MDC.put(CorrelationIdFilter.MDC_KEY, "cid-123");

        RateLimitingFilter filter = new RateLimitingFilter(ipResolver, backend);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/activities\t");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getHeader("Retry-After")).isEqualTo("60");
        assertThat(response.getContentAsString())
                .contains("\"correlationId\":\"cid-123\"");
    }

    @Test
    void returns429JsonWithNaWhenNoCorrelationIdInMdc() throws Exception {
        ClientIpResolverPort ipResolver = new ClientIpResolverPort() {
            @Override
            public String resolve(HttpServletRequest request) {
                return "127.0.0.1";
            }

            @Override
            public String resolveFromContext() {
                return null;
            }
        };
        RateLimiterBackend backend = mock(RateLimiterBackend.class);
        when(backend.tryConsume(anyString(), anyString())).thenReturn(false);

        RateLimitingFilter filter = new RateLimitingFilter(ipResolver, backend);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> fail("chain should not be called"));

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString()).contains("\"correlationId\":\"n/a\"");
    }

    @Test
    void sanitizesNullIpToNaForBackend() throws Exception {
        ClientIpResolverPort ipResolver = new ClientIpResolverPort() {
            @Override
            public String resolve(HttpServletRequest request) {
                return null;
            }

            @Override
            public String resolveFromContext() {
                return null;
            }
        };
        RateLimiterBackend backend = mock(RateLimiterBackend.class);
        when(backend.tryConsume(eq("n/a"), anyString())).thenReturn(true);

        RateLimitingFilter filter = new RateLimitingFilter(ipResolver, backend);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/x");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(backend).tryConsume(eq("n/a"), eq("/api/v1/x"));
        verify(chain).doFilter(request, response);
    }

    @Test
    void sanitizesDisallowedCharactersInPathForBackend() throws Exception {
        ClientIpResolverPort ipResolver = new ClientIpResolverPort() {
            @Override
            public String resolve(HttpServletRequest request) {
                return "127.0.0.1";
            }

            @Override
            public String resolveFromContext() {
                return null;
            }
        };
        RateLimiterBackend backend = mock(RateLimiterBackend.class);
        when(backend.tryConsume(anyString(), anyString())).thenReturn(true);

        RateLimitingFilter filter = new RateLimitingFilter(ipResolver, backend);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/foo!bar@baz");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
        verify(backend).tryConsume(eq("127.0.0.1"), path.capture());
        assertThat(path.getValue()).isEqualTo("/api/v1/foo_bar_baz");
    }

    @Test
    void stripsControlCharactersFromIpBeforeConsume() throws Exception {
        ClientIpResolverPort ipResolver = new ClientIpResolverPort() {
            @Override
            public String resolve(HttpServletRequest request) {
                return "1.1.1.1\f\0extra";
            }

            @Override
            public String resolveFromContext() {
                return null;
            }
        };
        RateLimiterBackend backend = mock(RateLimiterBackend.class);
        when(backend.tryConsume(anyString(), anyString())).thenReturn(true);

        RateLimitingFilter filter = new RateLimitingFilter(ipResolver, backend);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/ping");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(backend).tryConsume(eq("1.1.1.1extra"), eq("/api/ping"));
    }
}

