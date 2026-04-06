package com.profitflow.adapter.in.web;

import com.profitflow.application.model.AllocationRunResult;
import com.profitflow.application.port.in.AllocationRunUseCase;
import com.profitflow.domain.exception.AllocationDomainException;
import com.profitflow.config.SecurityMvcSliceImports;
import com.profitflow.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link WebMvcTest} slice for {@link AllocationRunController}.
 *
 * <p>Uses {@code .with(jwt())} post-processors for stateless JWT security testing.
 */
@WebMvcTest(controllers = AllocationRunController.class)
@Import({SecurityConfig.class, SecurityMvcSliceImports.class, ApiVersionResponseHeaderFilter.class})
class AllocationRunControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AllocationRunUseCase allocationRunUseCase;

    @MockBean JwtDecoder jwtDecoder;
    @MockBean JwtEncoder jwtEncoder;

    @Test
    void runReturns200WithResult() throws Exception {
        AllocationRunResult result = new AllocationRunResult(
                Map.of(), Map.of(), List.of(), List.of());
        when(allocationRunUseCase.runAllocation()).thenReturn(result);

        mockMvc.perform(post("/api/v1/allocations/run")
                        .with(csrf())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_FINANCE_MANAGER"))))
                .andExpect(status().isOk())
                .andExpect(header().string(ApiVersionResponseHeaderFilter.HEADER_NAME,
                        ApiVersionResponseHeaderFilter.VERSION_ONE))
                .andExpect(jsonPath("$.unallocatedResourceIds").isArray());
    }

    @Test
    void runReturns422WhenDomainRuleViolated() throws Exception {
        when(allocationRunUseCase.runAllocation())
                .thenThrow(new AllocationDomainException("Activity 'act-1' has no product rules"));

        mockMvc.perform(post("/api/v1/allocations/run")
                        .with(csrf())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_FINANCE_MANAGER")))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message", is("Activity 'act-1' has no product rules")));
    }

    @Test
    void runByAnalystReturns403() throws Exception {
        mockMvc.perform(post("/api/v1/allocations/run")
                        .with(csrf())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ANALYST"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void runWithoutAuthReturns401() throws Exception {
        // No JWT token → 401 with WWW-Authenticate: Bearer
        mockMvc.perform(post("/api/v1/allocations/run")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
