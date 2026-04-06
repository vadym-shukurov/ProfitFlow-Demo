package com.profitflow.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.profitflow.application.port.in.ActivityCatalogUseCase;
import com.profitflow.domain.Activity;
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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link WebMvcTest} slice for {@link ActivityController}.
 *
 * <p>Tests RBAC (ANALYST read-only, FINANCE_MANAGER write), request validation,
 * and correct delegation to {@link ActivityCatalogUseCase}.
 */
@WebMvcTest(controllers = ActivityController.class)
@Import({SecurityConfig.class, SecurityMvcSliceImports.class})
class ActivityControllerTest {

    @Autowired private MockMvc      mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private ActivityCatalogUseCase activityCatalogUseCase;

    @MockBean JwtDecoder jwtDecoder;
    @MockBean JwtEncoder jwtEncoder;

    // ── GET /api/v1/activities ────────────────────────────────────────────────

    @Test
    void listReturnsActivitiesForAnalyst() throws Exception {
        when(activityCatalogUseCase.listActivities()).thenReturn(List.of(
                new Activity("a-1", "Customer Support"),
                new Activity("a-2", "Infrastructure")));

        mockMvc.perform(get("/api/v1/activities")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ANALYST"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", is("a-1")))
                .andExpect(jsonPath("$[0].name", is("Customer Support")));
    }

    @Test
    void listReturnsEmptyListWhenNoneExist() throws Exception {
        when(activityCatalogUseCase.listActivities()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/activities")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ANALYST"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void listWithoutAuthReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/activities"))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /api/v1/activities ───────────────────────────────────────────────

    @Test
    void createReturns201ForFinanceManager() throws Exception {
        when(activityCatalogUseCase.createActivity("New Activity"))
                .thenReturn(new Activity("a-new", "New Activity"));

        mockMvc.perform(post("/api/v1/activities")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_FINANCE_MANAGER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "New Activity"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is("a-new")))
                .andExpect(jsonPath("$.name", is("New Activity")));
    }

    @Test
    void createWithBlankNameReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/activities")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_FINANCE_MANAGER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", ""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createByAnalystReturns403() throws Exception {
        mockMvc.perform(post("/api/v1/activities")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ANALYST")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Activity"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void createWithoutAuthReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Activity"))))
                .andExpect(status().isUnauthorized());
    }
}
