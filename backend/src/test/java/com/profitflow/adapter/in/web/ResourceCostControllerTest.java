package com.profitflow.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.profitflow.adapter.in.web.dto.CreateResourceCostRequest;
import com.profitflow.application.port.in.ResourceCostUseCase;
import com.profitflow.domain.Money;
import com.profitflow.domain.ResourceCost;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.profitflow.config.SecurityMvcSliceImports;
import com.profitflow.security.SecurityConfig;
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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link WebMvcTest} slice for {@link ResourceCostController}.
 *
 * <p>Uses {@code .with(jwt())} post-processors (Spring Security 6 stateless-aware)
 * rather than {@code @WithMockUser}, which relies on the HttpSession and does not
 * propagate correctly with {@code STATELESS} session management.
 */
@WebMvcTest(controllers = ResourceCostController.class)
@Import({SecurityConfig.class, SecurityMvcSliceImports.class})
class ResourceCostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ResourceCostUseCase resourceCostUseCase;

    // Provide mock JWT infrastructure so SecurityConfig can load without RSA keys.
    // The real beans are @ConditionalOnMissingBean so they are skipped when these exist.
    @MockBean JwtDecoder jwtDecoder;
    @MockBean JwtEncoder jwtEncoder;

    // ── GET /api/v1/resource-costs ────────────────────────────────────────────

    @Test
    void listReturnsEmptyJsonArray() throws Exception {
        when(resourceCostUseCase.listCosts()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/resource-costs")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ANALYST"))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void listReturnsMappedCosts() throws Exception {
        when(resourceCostUseCase.listCosts()).thenReturn(List.of(
                new ResourceCost("id-1", "IT Servers", Money.usd(new BigDecimal("10000.00"))),
                new ResourceCost("id-2", "Zendesk", Money.usd(new BigDecimal("5000.00")))));

        mockMvc.perform(get("/api/v1/resource-costs")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ANALYST"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].label", is("IT Servers")))
                .andExpect(jsonPath("$[0].amount", is(10000.00)))
                .andExpect(jsonPath("$[1].label", is("Zendesk")));
    }

    @Test
    void listWithoutAuthReturns401() throws Exception {
        // No token → JWT resource server returns 401 WWW-Authenticate: Bearer
        mockMvc.perform(get("/api/v1/resource-costs"))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /api/v1/resource-costs ───────────────────────────────────────────

    @Test
    void createReturns201WithPersisted() throws Exception {
        ResourceCost saved = new ResourceCost("new-id", "Office Rent",
                Money.usd(new BigDecimal("3000.00")));
        when(resourceCostUseCase.createCost(eq("Office Rent"),
                any(BigDecimal.class), eq("USD")))
                .thenReturn(saved);

        CreateResourceCostRequest body = new CreateResourceCostRequest(
                "Office Rent", new BigDecimal("3000.00"), "USD");

        mockMvc.perform(post("/api/v1/resource-costs")
                        .with(csrf())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_FINANCE_MANAGER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is("new-id")))
                .andExpect(jsonPath("$.label", is("Office Rent")));
    }

    @Test
    void createByAnalystReturns403() throws Exception {
        CreateResourceCostRequest body = new CreateResourceCostRequest(
                "Office Rent", new BigDecimal("3000.00"), "USD");

        mockMvc.perform(post("/api/v1/resource-costs")
                        .with(csrf())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ANALYST")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createWithBlankLabelReturns400() throws Exception {
        CreateResourceCostRequest body = new CreateResourceCostRequest(
                "", new BigDecimal("100"), "USD");

        mockMvc.perform(post("/api/v1/resource-costs")
                        .with(csrf())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_FINANCE_MANAGER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWithNegativeAmountReturns400() throws Exception {
        CreateResourceCostRequest body = new CreateResourceCostRequest(
                "Valid Label", new BigDecimal("-10"), "USD");

        mockMvc.perform(post("/api/v1/resource-costs")
                        .with(csrf())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_FINANCE_MANAGER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/v1/resource-costs/import ───────────────────────────────────

    @Test
    void importCsvReturns201WithImportedRows() throws Exception {
        when(resourceCostUseCase.importCostsFromCsv(any())).thenReturn(List.of(
                new ResourceCost("i1", "Zendesk", Money.usd(new BigDecimal("5000.00")))));

        mockMvc.perform(post("/api/v1/resource-costs/import")
                        .with(csrf())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_FINANCE_MANAGER")))
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("Zendesk,5000"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].label", is("Zendesk")));
    }

    @Test
    void importCsvCallsUseCaseWithRawBody() throws Exception {
        String csv = "label,amount\nRent,8000";
        when(resourceCostUseCase.importCostsFromCsv(csv)).thenReturn(List.of());

        mockMvc.perform(post("/api/v1/resource-costs/import")
                        .with(csrf())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_FINANCE_MANAGER")))
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(csv))
                .andExpect(status().isCreated());

        verify(resourceCostUseCase).importCostsFromCsv(csv);
    }
}
