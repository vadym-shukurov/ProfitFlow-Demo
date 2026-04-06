package com.profitflow.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CorrelationIdFilterTest {

    @Test
    void sanitizeReturnsUuidWhenMissingOrBlank() {
        assertThat(isUuid(CorrelationIdFilter.sanitize(null))).isTrue();
        assertThat(isUuid(CorrelationIdFilter.sanitize(""))).isTrue();
        assertThat(isUuid(CorrelationIdFilter.sanitize("   "))).isTrue();
    }

    @Test
    void sanitizeReturnsCandidateWhenSafe() {
        assertThat(CorrelationIdFilter.sanitize("abc-DEF_123")).isEqualTo("abc-DEF_123");
    }

    @Test
    void sanitizeReturnsUuidWhenUnsafe() {
        assertThat(isUuid(CorrelationIdFilter.sanitize("bad value with spaces"))).isTrue();
        assertThat(isUuid(CorrelationIdFilter.sanitize("bad\nvalue"))).isTrue();
        assertThat(isUuid(CorrelationIdFilter.sanitize("bad$value"))).isTrue();
    }

    @Test
    void filterPropagatesCorrelationIdAndCleansUpMdc() throws Exception {
        CorrelationIdFilter filter = new CorrelationIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "abc-DEF_123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo("abc-DEF_123");
        // MDC key is removed in finally, even if the chain succeeds.
        assertThat(org.slf4j.MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
        verify(chain).doFilter(request, response);
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

