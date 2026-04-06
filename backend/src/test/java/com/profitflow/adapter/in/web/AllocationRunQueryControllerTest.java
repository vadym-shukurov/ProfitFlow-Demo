package com.profitflow.adapter.in.web;

import com.profitflow.application.model.AllocationRunResult;
import com.profitflow.application.model.AllocationRunSummary;
import com.profitflow.application.port.in.AllocationRunQueryUseCase;
import com.profitflow.config.SecurityMvcSliceImports;
import com.profitflow.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AllocationRunQueryController.class)
@Import({SecurityConfig.class, SecurityMvcSliceImports.class, ApiExceptionHandler.class,
        ApiVersionResponseHeaderFilter.class})
class AllocationRunQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AllocationRunQueryUseCase queryUseCase;
    @MockBean
    private JwtDecoder jwtDecoder;
    @MockBean
    private JwtEncoder jwtEncoder;

    @Test
    void listRunsReturnsSummaries() throws Exception {
        Instant t = Instant.parse("2024-01-02T12:00:00Z");
        when(queryUseCase.listRecentRuns(20)).thenReturn(List.of(
                new AllocationRunSummary(5L, t, "alice", "abc")));

        mockMvc.perform(get("/api/v1/allocations/runs")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_FINANCE_MANAGER"))))
                .andExpect(status().isOk())
                .andExpect(header().string(ApiVersionResponseHeaderFilter.HEADER_NAME,
                        ApiVersionResponseHeaderFilter.VERSION_ONE))
                .andExpect(jsonPath("$[0].runNumber", is(5)))
                .andExpect(jsonPath("$[0].executedBy", is("alice")));
    }

    @Test
    void getRunReturns404WhenMissing() throws Exception {
        when(queryUseCase.getRun(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/allocations/runs/99")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_FINANCE_MANAGER"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("Allocation run not found: 99")));
    }

    @Test
    void getRunReturns200WhenPresent() throws Exception {
        AllocationRunResult body = new AllocationRunResult(
                Map.of(), Map.of(), List.of(), List.of());
        when(queryUseCase.getRun(1L)).thenReturn(Optional.of(body));

        mockMvc.perform(get("/api/v1/allocations/runs/1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unallocatedResourceIds").isArray());
    }

    @Test
    void listRunsForbiddenForAnalyst() throws Exception {
        mockMvc.perform(get("/api/v1/allocations/runs")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ANALYST"))))
                .andExpect(status().isForbidden());
    }
}
