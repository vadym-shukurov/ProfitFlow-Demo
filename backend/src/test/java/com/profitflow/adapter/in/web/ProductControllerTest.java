package com.profitflow.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.profitflow.application.port.in.ProductCatalogUseCase;
import com.profitflow.domain.Product;
import com.profitflow.config.SecurityMvcSliceImports;
import com.profitflow.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link WebMvcTest} slice for {@link ProductController}.
 *
 * <p>Tests RBAC (ANALYST read-only, FINANCE_MANAGER write), request validation,
 * and correct delegation to {@link ProductCatalogUseCase}.
 */
@WebMvcTest(controllers = ProductController.class)
@Import({SecurityConfig.class, SecurityMvcSliceImports.class})
class ProductControllerTest {

    @Autowired private MockMvc       mockMvc;
    @Autowired private ObjectMapper  objectMapper;

    @MockBean private ProductCatalogUseCase productCatalogUseCase;

    @MockBean JwtDecoder jwtDecoder;
    @MockBean JwtEncoder jwtEncoder;

    // ── GET /api/v1/products ──────────────────────────────────────────────────

    @Test
    void listReturnsProductsForAnalyst() throws Exception {
        when(productCatalogUseCase.listProducts()).thenReturn(List.of(
                new Product("p-1", "Product Alpha"),
                new Product("p-2", "Product Beta")));

        mockMvc.perform(get("/api/v1/products")
                        .with(jwt().authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ANALYST"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", is("p-1")))
                .andExpect(jsonPath("$[0].name", is("Product Alpha")));
    }

    @Test
    void listReturnsEmptyListWhenNoneExist() throws Exception {
        when(productCatalogUseCase.listProducts()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/products")
                        .with(jwt().authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ANALYST"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void listWithoutAuthReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /api/v1/products ─────────────────────────────────────────────────

    @Test
    void createReturns201ForFinanceManager() throws Exception {
        when(productCatalogUseCase.createProduct("New Product"))
                .thenReturn(new Product("p-new", "New Product"));

        mockMvc.perform(post("/api/v1/products")
                        .with(csrf())
                        .with(jwt().authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_FINANCE_MANAGER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "New Product"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is("p-new")))
                .andExpect(jsonPath("$.name", is("New Product")));
    }

    @Test
    void createWithBlankNameReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .with(csrf())
                        .with(jwt().authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_FINANCE_MANAGER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", ""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createByAnalystReturns403() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .with(csrf())
                        .with(jwt().authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ANALYST")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Product"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void createWithoutAuthReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Product"))))
                .andExpect(status().isUnauthorized());
    }
}
