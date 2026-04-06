package com.profitflow.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.profitflow.application.model.AiSuggestion;
import com.profitflow.application.port.in.AiSuggestionUseCase;
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

import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link WebMvcTest} slice for {@link AiSuggestionController}.
 */
@WebMvcTest(controllers = AiSuggestionController.class)
@Import({SecurityConfig.class, SecurityMvcSliceImports.class})
class AiSuggestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AiSuggestionUseCase aiSuggestionUseCase;

    @MockBean JwtDecoder jwtDecoder;
    @MockBean JwtEncoder jwtEncoder;

    @Test
    void suggestReturns200WithSuggestion() throws Exception {
        when(aiSuggestionUseCase.suggest(eq("Zendesk subscription")))
                .thenReturn(new AiSuggestion("Customer Support", "Tickets resolved"));

        mockMvc.perform(post("/api/v1/ai/suggest")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ANALYST")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("text", "Zendesk subscription"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestedActivityName", is("Customer Support")))
                .andExpect(jsonPath("$.suggestedAllocationDriver", is("Tickets resolved")));
    }

    @Test
    void suggestWithBlankTextReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/ai/suggest")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ANALYST")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("text", ""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void suggestWithoutAuthReturns401() throws Exception {
        // No JWT token → 401 with WWW-Authenticate: Bearer
        mockMvc.perform(post("/api/v1/ai/suggest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("text", "test"))))
                .andExpect(status().isUnauthorized());
    }
}
