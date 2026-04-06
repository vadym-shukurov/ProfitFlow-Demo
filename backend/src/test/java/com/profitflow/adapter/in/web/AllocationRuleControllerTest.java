package com.profitflow.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.profitflow.application.model.ActivityProductRuleRow;
import com.profitflow.application.model.ResourceActivityRuleRow;
import com.profitflow.application.port.in.AllocationRuleUseCase;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link WebMvcTest} slice for {@link AllocationRuleController}.
 */
@WebMvcTest(controllers = AllocationRuleController.class)
@Import({SecurityConfig.class, SecurityMvcSliceImports.class})
class AllocationRuleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AllocationRuleUseCase allocationRuleUseCase;

    @MockBean JwtDecoder jwtDecoder;
    @MockBean JwtEncoder jwtEncoder;

    // ── Resource → Activity ────────────────────────────────────────────────

    @Test
    void listResourceToActivityReturns200WithRules() throws Exception {
        when(allocationRuleUseCase.listResourceToActivityRules()).thenReturn(List.of(
                new ResourceActivityRuleRow("r1", "a1", new BigDecimal("0.6")),
                new ResourceActivityRuleRow("r1", "a2", new BigDecimal("0.4"))));

        mockMvc.perform(get("/api/v1/rules/resource-to-activity")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ANALYST"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].resourceId", is("r1")))
                .andExpect(jsonPath("$[0].activityId", is("a1")))
                .andExpect(jsonPath("$[0].driverWeight", is(0.6)));
    }

    @Test
    void replaceResourceToActivityReturns200AndDelegates() throws Exception {
        List<Map<String, Object>> body = List.of(
                Map.of("resourceId", "r1", "activityId", "a1", "driverWeight", 1.0));

        mockMvc.perform(put("/api/v1/rules/resource-to-activity")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_FINANCE_MANAGER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        verify(allocationRuleUseCase).replaceResourceToActivityRules(any());
    }

    @Test
    void replaceResourceToActivityWithoutAuthReturns401() throws Exception {
        mockMvc.perform(put("/api/v1/rules/resource-to-activity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isUnauthorized());
    }

    // ── Activity → Product ─────────────────────────────────────────────────

    @Test
    void listActivityToProductReturns200WithRules() throws Exception {
        when(allocationRuleUseCase.listActivityToProductRules()).thenReturn(List.of(
                new ActivityProductRuleRow("a1", "p1", new BigDecimal("0.5")),
                new ActivityProductRuleRow("a1", "p2", new BigDecimal("0.5"))));

        mockMvc.perform(get("/api/v1/rules/activity-to-product")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ANALYST"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].activityId", is("a1")))
                .andExpect(jsonPath("$[0].productId", is("p1")));
    }

    @Test
    void replaceActivityToProductReturns200AndDelegates() throws Exception {
        List<Map<String, Object>> body = List.of(
                Map.of("activityId", "a1", "productId", "p1", "driverWeight", 1.0));

        mockMvc.perform(put("/api/v1/rules/activity-to-product")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_FINANCE_MANAGER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        verify(allocationRuleUseCase).replaceActivityToProductRules(any());
    }

    @Test
    void listResourceToActivityWithoutAuthReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/rules/resource-to-activity"))
                .andExpect(status().isUnauthorized());
    }
}
