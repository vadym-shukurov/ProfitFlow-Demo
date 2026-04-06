package com.profitflow.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveApiCacheHeaderFilterTest {

    @Test
    void addsNoStoreHeadersForApiPaths() throws Exception {
        SensitiveApiCacheHeaderFilter filter = new SensitiveApiCacheHeaderFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/products");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store, private, max-age=0");
        assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
    }

    @Test
    void skipsCacheHeadersForNonApiPaths() throws Exception {
        SensitiveApiCacheHeaderFilter filter = new SensitiveApiCacheHeaderFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getHeader("Cache-Control")).isNull();
        assertThat(response.getHeader("Pragma")).isNull();
    }
}
