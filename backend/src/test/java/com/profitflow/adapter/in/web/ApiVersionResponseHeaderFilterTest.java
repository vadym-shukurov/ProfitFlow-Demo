package com.profitflow.adapter.in.web;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ApiVersionResponseHeaderFilterTest {

    private final ApiVersionResponseHeaderFilter filter = new ApiVersionResponseHeaderFilter();

    @Test
    void addsVersionHeaderAfterChainForApiV1Paths() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/allocations/run");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> { };

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(ApiVersionResponseHeaderFilter.HEADER_NAME))
                .isEqualTo(ApiVersionResponseHeaderFilter.VERSION_ONE);
    }

    @Test
    void skipsVersionHeaderForNonApiPaths() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> { };

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(ApiVersionResponseHeaderFilter.HEADER_NAME)).isNull();
    }
}
